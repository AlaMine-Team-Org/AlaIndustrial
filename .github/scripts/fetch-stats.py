#!/usr/bin/env python3
"""Collect daily download stats for the guide site (data/stats.json).

Neither marketplace exposes a public day-by-day history, so the history is built
here: one cumulative snapshot per day, and the site turns snapshots into daily
figures. The run is scheduled just after midnight UTC and the row it writes is
dated YESTERDAY — the counters it reads are exactly what that day closed with.
Sources:

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

# Hour (UTC) up to which a run may still rewrite yesterday's row. Both scheduled
# slots — 00:37 and the 06:43 catch-up — sit below it; a manual run later in the day
# needs --force, so it cannot silently mix today's downloads into yesterday.
REWRITE_WINDOW_HOUR = 8

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


def backfill(series: list, history: dict, today: str) -> list:
    """Prepend days recovered from Modrinth analytics to the snapshot series.

    CurseForge has no public history, so those days carry None in its slot: the site
    counts Modrinth alone for them and marks the figure as partial. Inventing a
    CurseForge number would look precise and be wrong.

    Days from `today` onwards are skipped. Modrinth analytics already has a bucket
    for the current day, holding the handful of downloads it has collected so far,
    and restoring it would append a row that is not a closed day. On a normal
    morning the next run overwrites that stub and nobody sees it — but when GitHub
    drops a scheduled run (2026-08-27: neither Stats nor Stats report fired), the
    stub becomes "yesterday" and the chart draws a crash to 1 download instead of
    simply ending a day earlier, which is the honest symptom.
    """
    if not history:
        return series
    known = {row[0] for row in series}
    restored, running = [], 0
    for date in sorted(history):
        running += history[date]
        if date not in known and date < today:
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
    parser.add_argument("--force", action="store_true",
                        help="rewrite yesterday's row even outside the early-morning "
                             "window (use when the row is known to be wrong)")
    parser.add_argument("--skip-if-current", action="store_true",
                        help="do nothing when yesterday is already recorded in full "
                             "(the catch-up run: it only has to act when the nightly "
                             "one never happened)")
    args = parser.parse_args()

    out = pathlib.Path(args.out)
    previous = {}
    if out.exists():
        previous = json.loads(out.read_text(encoding="utf-8"))
    series: list[list] = list(previous.get("series", []))
    last = series[-1] if series else None

    # The counters read right now describe everything up to this moment, so the day
    # they close is YESTERDAY — the run is scheduled just after midnight UTC. Dating
    # the row with the current day would label it with a day that has not happened
    # yet: the site draws a bar as the difference between two neighbouring snapshots
    # and puts it under the later date.
    now = datetime.datetime.now(datetime.timezone.utc)
    day = (now.date() - datetime.timedelta(days=1)).isoformat()

    # The catch-up run exists only for the morning after GitHub dropped the nightly
    # schedule. On every other morning yesterday is already there in full, and
    # collecting again would pour hours of TODAY into yesterday's row — so bail out
    # before touching the network: no request, no commit, no deploy.
    #
    # Yesterday is looked up BY DATE, not taken as series[-1]: a row for today can
    # sit at the end (an older backfill left one, or a manual run wrote one), and
    # then a "last row is not yesterday" test would send the catch-up collecting on
    # a morning that needs nothing. A row with no CurseForge figure is not "in
    # full" — that is a restored day, not a collected one.
    recorded = next((row for row in reversed(series) if row[0] == day), None)
    if args.skip_if_current and recorded is not None and recorded[2] is not None:
        print(f"OK    {day} already recorded in full — nothing to do")
        return 0

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

    if series and series[-1][0] == day:
        # Rewriting yesterday means replacing counters that closed at midnight with
        # counters read right now — fine for a re-run an hour after midnight, wrong
        # for a curious click at 15:00, which would pour three quarters of TODAY into
        # yesterday's bar and starve today's. So the rewrite is allowed only inside
        # the early-morning window that the two scheduled slots live in (00:37 and
        # the 06:43 catch-up), or when the row is not complete yet, or on --force.
        # Until MOD-520 an accident guarded this: backfill() left a row dated TODAY
        # at the end of the file, so a midday run took the branch below instead.
        stale = series[-1][2] is None
        if args.force or stale or now.hour < REWRITE_WINDOW_HOUR:
            series[-1] = [day, mr_total, cf_total]   # re-run for the same day
        else:
            print(f"NOTE  {day} was recorded before {REWRITE_WINDOW_HOUR:02d}:00 UTC — "
                  f"series left untouched (pass --force to overwrite it)")
    elif series and series[-1][0] > day:
        # A manual run in the middle of a day: the day it would write is already
        # closed, and overwriting it would pour part of today into it. Leave the
        # history alone and refresh the headline totals only.
        print(f"NOTE  {series[-1][0]} is already recorded — series left untouched")
    else:
        series.append([day, mr_total, cf_total])

    # Restore the days that predate the first snapshot (once — later runs find them
    # already present and change nothing).
    try:
        series = backfill(series, fetch_modrinth_history(), now.date().isoformat())
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
    # Report what the FILE ends with, not what was fetched: the two differ whenever a
    # run declines to touch the history (a midday run, a day already recorded), and a
    # log line quoting the fetched numbers would read as if they had been written.
    tail = series[-1]
    tail_cf = tail[2] if tail[2] is not None else 0
    print(f"OK    series reaches {tail[0]}: modrinth={tail[1]} curseforge={tail[2]} "
          f"total={tail[1] + tail_cf} points={len(series)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
