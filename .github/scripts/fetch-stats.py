#!/usr/bin/env python3
"""Collect daily download stats for the guide site (data/stats.json).

Neither marketplace exposes a public day-by-day history, so the history is built
here: one cumulative snapshot per day, and the site turns snapshots into daily
figures. Sources:

  * Modrinth  — public API, no key: project totals, followers and per-version
                downloads (the latter give the Fabric / NeoForge split).
  * CurseForge — official API when CURSEFORGE_API_KEY is set (header x-api-key,
                a Core API key from the CurseForge for Studios console — NOT the
                upload token), otherwise the keyless cfwidget mirror.

A source that fails does not break the run: the previous known total is carried
over, so the chart never shows a fake dip. The script exits non-zero only if it
cannot produce any usable snapshot at all.

Usage:  python .github/scripts/fetch-stats.py [--out data/stats.json]
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

MODRINTH_ID = "ACLWFBlU"
CURSEFORGE_ID = 1597723
UA = "Ma3auka/AlaIndustrial-stats (+https://github.com/AlaMine-Team-Org/AlaIndustrial)"
TIMEOUT = 30


def get_json(url: str, headers: dict | None = None):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.load(resp)


def fetch_modrinth() -> dict:
    """Totals, followers and the loader split (summed over every version)."""
    project = get_json(f"https://api.modrinth.com/v2/project/{MODRINTH_ID}")
    versions = get_json(f"https://api.modrinth.com/v2/project/{MODRINTH_ID}/version")
    loaders: dict[str, int] = {}
    for version in versions:
        for loader in version.get("loaders", []):
            loaders[loader] = loaders.get(loader, 0) + version.get("downloads", 0)
    return {
        "downloads": int(project["downloads"]),
        "followers": int(project.get("followers", 0)),
        "version": (versions[0]["version_number"].split("+")[0] if versions else ""),
        "loaders": loaders,
    }


def fetch_modrinth_history() -> dict:
    """Day-by-day Modrinth downloads, oldest first, as {date: downloads}.

    Needs MODRINTH_TOKEN with the analytics scope. The endpoint lives on v3 and is
    absent from the published OpenAPI document; it answers
    {project_id: {unix_seconds: downloads}} at resolution_minutes=1440.
    """
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        return {}
    url = ("https://api.modrinth.com/v3/analytics/downloads"
           '?project_ids=%5B%22' + MODRINTH_ID + '%22%5D&resolution_minutes=1440'
           "&start_date=2026-01-01T00:00:00Z")
    data = get_json(url, {"Authorization": token})
    buckets = data.get(MODRINTH_ID, {})
    return {
        datetime.datetime.fromtimestamp(int(ts), datetime.timezone.utc).date().isoformat(): int(n)
        for ts, n in buckets.items()
    }


def backfill(series: list, history: dict) -> list:
    """Prepend days recovered from Modrinth analytics to the snapshot series.

    CurseForge has no public history, so those days carry None in its slot: the site
    counts Modrinth alone for them and marks the figure as partial. Inventing a
    CurseForge number would look precise and be wrong.
    """
    if not history:
        return series
    known = {row[0] for row in series}
    restored, running = [], 0
    for date in sorted(history):
        running += history[date]
        if date not in known:
            restored.append([date, running, None])
    return sorted(series + restored, key=lambda row: row[0])


def fetch_curseforge() -> int:
    """Total downloads. Official API when a key is present, cfwidget otherwise."""
    key = os.environ.get("CURSEFORGE_API_KEY", "").strip()
    if key:
        data = get_json(f"https://api.curseforge.com/v1/mods/{CURSEFORGE_ID}",
                        {"x-api-key": key, "Accept": "application/json"})
        return int(data["data"]["downloadCount"])
    data = get_json(f"https://api.cfwidget.com/{CURSEFORGE_ID}")
    return int(data["downloads"]["total"])


def dump(payload: dict) -> str:
    """Serialise with one day per line.

    Default indenting spreads every snapshot over four lines: after a year the
    file is thousands of lines and each daily commit shows a wall of diff. One
    line per day keeps the history readable in `git log -p`.
    """
    head = json.dumps({k: v for k, v in payload.items() if k != "series"},
                      ensure_ascii=False, indent=1)[1:-1].rstrip().rstrip(",")
    rows = ",\n  ".join(json.dumps(point, ensure_ascii=False) for point in payload["series"])
    return "{" + head + ",\n \"series\": [\n  " + rows + "\n ]\n}\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="data/stats.json")
    args = parser.parse_args()

    out = pathlib.Path(args.out)
    previous = {}
    if out.exists():
        previous = json.loads(out.read_text(encoding="utf-8"))
    series: list[list] = list(previous.get("series", []))
    last = series[-1] if series else None

    modrinth, cf_total, failures = None, None, []
    try:
        modrinth = fetch_modrinth()
    except (urllib.error.URLError, KeyError, ValueError) as exc:
        failures.append(f"modrinth: {exc}")
    try:
        cf_total = fetch_curseforge()
    except (urllib.error.URLError, KeyError, ValueError) as exc:
        failures.append(f"curseforge: {exc}")

    for message in failures:
        print(f"WARN  {message}", file=sys.stderr)
    if modrinth is None and cf_total is None:
        print("ERROR both sources failed — nothing to record", file=sys.stderr)
        return 1
    if last is None and (modrinth is None or cf_total is None):
        # Nothing to carry over on the first run: a half-filled snapshot would skew
        # the very start of the series.
        print("ERROR first run needs both sources", file=sys.stderr)
        return 1

    mr_total = modrinth["downloads"] if modrinth else last[1]
    cf_total = cf_total if cf_total is not None else last[2]

    today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()
    if series and series[-1][0] == today:
        series[-1] = [today, mr_total, cf_total]   # re-run on the same UTC day
    else:
        series.append([today, mr_total, cf_total])

    # Restore the days that predate the first snapshot (once — later runs find them
    # already present and change nothing).
    try:
        series = backfill(series, fetch_modrinth_history())
    except (urllib.error.URLError, KeyError, ValueError) as exc:
        print(f"WARN  backfill skipped: {exc}", file=sys.stderr)

    totals = dict(previous.get("totals", {}))
    totals.update({"modrinth": mr_total, "curseforge": cf_total})
    if modrinth:
        loaders = modrinth["loaders"]
        totals["followers"] = modrinth["followers"]
        totals["version"] = modrinth["version"]
        totals["loaders"] = {"fabric": loaders.get("fabric", 0),
                             "neoforge": loaders.get("neoforge", 0)}

    payload = {
        "generated": datetime.datetime.now(datetime.timezone.utc)
                     .replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "totals": totals,
        "series": series,
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(dump(payload), encoding="utf-8")
    print(f"OK    {today}: modrinth={mr_total} curseforge={cf_total} "
          f"total={mr_total + cf_total} points={len(series)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
