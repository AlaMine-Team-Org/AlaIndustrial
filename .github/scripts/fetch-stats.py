#!/usr/bin/env python3
"""Collect daily download stats for the guide site (data/stats.json).

Neither marketplace exposes a public day-by-day history, so the history is built
here: one cumulative snapshot per day, and the site turns snapshots into daily
figures. The run is scheduled just after midnight UTC and the row it writes is
dated YESTERDAY — the counters it reads are exactly what that day closed with.
Sources:

  * Modrinth  — public API, no key: project totals, followers and per-version
                downloads (the latter give the Fabric / NeoForge split).
  * CurseForge — the counters CurseForge's own site serves for every file of the
                project, summed (MOD-749; keyless); then the official API when
                CURSEFORGE_API_KEY is set (header x-api-key, a Core API key from the
                CurseForge for Studios console — NOT the upload token); then the
                keyless cfwidget mirror. The per-file counters also go to
                data/curseforge-files.json, so a jump can be traced to a version.

Besides the cumulative snapshots the file carries `daily` (MOD-749): the per-day
figures the site and the Telegram report draw, computed here once so the two can
never disagree. Modrinth days come from its analytics (exact calendar days);
CurseForge has no day-by-day figure at all and releases its counter in batches —
a few days barely move, then one day catches them all up (2026-10-04: +514 after
+64, +38, +31) — so its days are smoothed and marked as estimates. Totals and the
sum over any stretch longer than a week are unchanged.

A source that fails does not break the run: the previous known total is carried
over, so the chart never shows a fake dip. The script exits non-zero only if it
cannot produce any usable snapshot at all.

Usage:  python .github/scripts/fetch-stats.py [--out data/stats.json]
"""
from __future__ import annotations

import argparse
import datetime
import http.client
import json
import math
import os
import pathlib
import statistics
import sys
import urllib.request

# Hour (UTC) up to which a run may still rewrite yesterday's row. Both scheduled
# slots — 00:37 and the 06:43 catch-up — sit below it; a manual run later in the day
# needs --force, so it cannot silently mix today's downloads into yesterday.
REWRITE_WINDOW_HOUR = 8

#: CurseForge batching (MOD-749). A day whose increase exceeds SPIKE_RATIO times the
#: median of the previous SPIKE_BASELINE_DAYS is a catch-up, not a rush: no release has
#: ever explained one (0.1.195 drew 44 downloads in the day that showed +514). Its excess
#: first fills the days of the previous SPIKE_LOOKBACK_DAYS that stayed under the
#: median, then whatever is left is spread evenly over that week.
SPIKE_RATIO = 2.0
SPIKE_BASELINE_DAYS = 14
SPIKE_LOOKBACK_DAYS = 7
#: How many days of per-file CurseForge counters data/curseforge-files.json keeps.
CF_FILES_KEEP_DAYS = 30
CF_PAGE_SIZE = 50

MODRINTH_ID = "ACLWFBlU"
CURSEFORGE_ID = 1597723
UA = "Ma3auka/AlaIndustrial-stats (+https://github.com/AlaMine-Team-Org/AlaIndustrial)"
TIMEOUT = 30

# What a source that did not answer raises. URLError covers only a failure to CONNECT:
# a server that accepts the connection and then stalls past TIMEOUT raises a bare
# TimeoutError from the socket read, and a dropped connection an OSError or an
# HTTPException. On 2026-09-14 cfwidget stalled that way and the uncaught
# TimeoutError killed the whole run, so 2026-09-13 was never recorded.
SOURCE_ERRORS = (OSError, http.client.HTTPException, KeyError, ValueError)


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


def fetch_curseforge_files() -> dict:
    """Every file of the project with its download counter, from CurseForge's own site.

    The project total is the sum of these counters (checked 2026-10-05: 427 files,
    8041, exactly what cfwidget reported). The endpoint pages with `pageIndex` and
    silently ignores any other paging parameter, so a page that repeats an id it has
    already seen is a paging failure, not more files — raise instead of looping.
    """
    files: dict[str, dict] = {}
    for page in range(1000):
        data = get_json(f"https://www.curseforge.com/api/v1/mods/{CURSEFORGE_ID}/files"
                        f"?pageSize={CF_PAGE_SIZE}&pageIndex={page}"
                        "&sort=dateCreated&sortDescending=true")
        batch, total = data["data"], int(data["pagination"]["totalCount"])
        for item in batch:
            key = str(item["id"])
            if key in files:
                raise ValueError(f"curseforge site paging repeated file {key} on page {page}")
            files[key] = {"name": str(item["displayName"]), "downloads": int(item["totalDownloads"])}
        if len(files) >= total or not batch:
            break
    if len(files) != total:
        raise ValueError(f"curseforge site listed {len(files)} of {total} files")
    return files


def fetch_curseforge() -> tuple[int, dict | None, str]:
    """(total, per-file counters or None, source name), first source that answers."""
    failures = []
    try:
        files = fetch_curseforge_files()
        return sum(f["downloads"] for f in files.values()), files, "curseforge-site"
    except SOURCE_ERRORS as exc:
        failures.append(f"site: {exc}")
    key = os.environ.get("CURSEFORGE_API_KEY", "").strip()
    if key:
        try:
            data = get_json(f"https://api.curseforge.com/v1/mods/{CURSEFORGE_ID}",
                            {"x-api-key": key, "Accept": "application/json"})
            return int(data["data"]["downloadCount"]), None, "curseforge-api"
        except SOURCE_ERRORS as exc:
            failures.append(f"api: {exc}")
    for message in failures:
        print(f"WARN  curseforge {message} — falling back", file=sys.stderr)
    data = get_json(f"https://api.cfwidget.com/{CURSEFORGE_ID}")
    return int(data["downloads"]["total"]), None, "cfwidget"


# ── per-day figures (MOD-749) ─────────────────────────────────────────────────

def _js_round(value: float) -> int:
    """Math.round, which the site used before: halves go up, never to even."""
    return int(math.floor(value + 0.5))


def _spread(series: list, slot: int) -> list:
    """Cumulative snapshots -> [date, per-day float or None, spread over a gap?].

    A missing snapshot day shares the increase of its gap evenly, as the site always
    did: a recovered collection must not draw a spike."""
    out = []
    for prev, cur in zip(series, series[1:]):
        d0 = datetime.date.fromisoformat(prev[0])
        d1 = datetime.date.fromisoformat(cur[0])
        gap = max(1, (d1 - d0).days)
        per = None if prev[slot] is None or cur[slot] is None else (cur[slot] - prev[slot]) / gap
        for back in range(gap - 1, -1, -1):
            out.append([(d1 - datetime.timedelta(days=back)).isoformat(), per, gap > 1])
    return out


def smooth_batches(values: list) -> tuple[list, list]:
    """Spread CurseForge's catch-up days back over the week they belong to.

    `values` is per-day floats (None = unknown, left alone). Returns the smoothed
    values and a flag per day: True where the figure was moved. The sum over each
    spike's week is preserved exactly; only its distribution changes."""
    out = list(values)
    moved = [False] * len(out)
    for t, value in enumerate(out):
        if value is None:
            continue
        history = [v for v in values[max(0, t - SPIKE_BASELINE_DAYS):t] if v is not None]
        if len(history) < 5:
            continue
        base = statistics.median(history)
        if base <= 0 or value <= SPIKE_RATIO * base:
            continue
        week = [i for i in range(max(0, t - SPIKE_LOOKBACK_DAYS), t) if out[i] is not None]
        excess = value - base
        # 1. the days that stalled get back to the median first
        deficit = {i: base - out[i] for i in week if out[i] < base}
        owed = sum(deficit.values())
        fill = min(excess, owed)
        if fill > 0:
            for i, gap in deficit.items():
                out[i] += fill * gap / owed
                moved[i] = True
            excess -= fill
        # 2. whatever is still left belongs to the week as a whole
        if excess > 0 and week:
            share = excess / (len(week) + 1)
            for i in week:
                out[i] += share
                moved[i] = True
            excess = share
        out[t] = base + excess
        moved[t] = True
    return out, moved


def _round_keeping_sum(values: list) -> list:
    """Whole numbers whose running total follows the exact one (None passes through).

    Rounding each day on its own lets the sum drift by a few downloads; rounding the
    running total and taking differences keeps every stretch's sum exact."""
    out, exact, shown = [], 0.0, 0
    for value in values:
        if value is None:
            out.append(None)
            continue
        exact += value
        step = _js_round(exact) - shown
        shown += step
        out.append(step)
    return out


def build_daily(series: list, history: dict, today: str) -> list:
    """[date, modrinth, curseforge or None, flags] per closed day, oldest first.

    flags: "e" = the CurseForge figure is an estimate (batch smoothed or shared over
    a missed snapshot), "g" = the Modrinth figure was shared over a missed snapshot.
    A None CurseForge slot means "unknown that day" — the site counts Modrinth alone
    and says so. Modrinth's analytics gives exact calendar days; it replaces the
    snapshot difference wherever it has the (closed) day."""
    mr = _spread(series, 1)
    cf = _spread(series, 2)
    cf_values, cf_moved = smooth_batches([row[1] for row in cf])
    cf_values = _round_keeping_sum(cf_values)
    rows = []
    for (date, mr_value, mr_gap), (_, _, cf_gap), cf_value, moved in zip(mr, cf, cf_values, cf_moved):
        if date >= today:
            continue
        exact = history.get(date) if date < today else None
        flags = ""
        if cf_value is not None and (moved or cf_gap):
            flags += "e"
        if exact is None and mr_gap:
            flags += "g"
        rows.append([date,
                     int(exact) if exact is not None else max(0, _js_round(mr_value or 0)),
                     None if cf_value is None else max(0, cf_value),
                     flags])
    return rows


def write_curseforge_files(path: pathlib.Path, day: str, files: dict, stamp: str) -> None:
    """Keep CF_FILES_KEEP_DAYS days of per-file counters, one day per line."""
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    names = dict(data.get("names", {}))
    days = dict(data.get("days", {}))
    for key, item in files.items():
        names[key] = item["name"]
    days[day] = {key: item["downloads"] for key, item in sorted(files.items(), key=lambda kv: int(kv[0]))}
    keep = sorted(days)[-CF_FILES_KEEP_DAYS:]
    days = {d: days[d] for d in keep}
    live = {key for counters in days.values() for key in counters}
    names = {key: names[key] for key in sorted(names, key=int) if key in live}
    rows = ",\n  ".join(json.dumps(d) + ": " + json.dumps(days[d], separators=(",", ":")) for d in keep)
    text = ("{\n \"generated\": " + json.dumps(stamp) + ",\n \"names\": "
            + json.dumps(names, ensure_ascii=False, separators=(",", ":"))
            + ",\n \"days\": {\n  " + rows + "\n }\n}\n")
    path.write_text(text, encoding="utf-8", newline="\n")


def dump(payload: dict) -> str:
    """Serialise with one day per line.

    Default indenting spreads every snapshot over four lines: after a year the
    file is thousands of lines and each daily commit shows a wall of diff. One
    line per day keeps the history readable in `git log -p`.
    """
    lists = ("series", "daily")
    head = json.dumps({k: v for k, v in payload.items() if k not in lists},
                      ensure_ascii=False, indent=1)[1:-1].rstrip().rstrip(",")
    parts = []
    for name in lists:
        if name in payload:
            rows = ",\n  ".join(json.dumps(point, ensure_ascii=False) for point in payload[name])
            parts.append("\"" + name + "\": [\n  " + rows + "\n ]")
    return "{" + head + ",\n " + ",\n ".join(parts) + "\n}\n"


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

    modrinth, cf_total, cf_files, cf_source, failures = None, None, None, None, []
    try:
        modrinth = fetch_modrinth()
    except SOURCE_ERRORS as exc:
        failures.append(f"modrinth: {exc}")
    try:
        cf_total, cf_files, cf_source = fetch_curseforge()
    except SOURCE_ERRORS as exc:
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
    history: dict = {}
    try:
        history = fetch_modrinth_history()
        series = backfill(series, history, now.date().isoformat())
    except SOURCE_ERRORS as exc:
        print(f"WARN  backfill skipped: {exc}", file=sys.stderr)

    totals = dict(previous.get("totals", {}))
    totals.update({"modrinth": mr_total, "curseforge": cf_total})
    if cf_source:
        totals["curseforge_source"] = cf_source
    if modrinth:
        loaders = modrinth["loaders"]
        totals["followers"] = modrinth["followers"]
        totals["version"] = modrinth["version"]
        totals["loaders"] = {"fabric": loaders.get("fabric", 0),
                             "neoforge": loaders.get("neoforge", 0)}

    stamp = (datetime.datetime.now(datetime.timezone.utc)
             .replace(microsecond=0).isoformat().replace("+00:00", "Z"))
    payload = {
        "generated": stamp,
        "totals": totals,
        "series": series,
        "daily": build_daily(series, history, now.date().isoformat()),
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(dump(payload), encoding="utf-8")
    # Per-file counters are filed under the day the series row describes, and only
    # when this run wrote that row: a midday run must not pin today's counters on
    # yesterday here either.
    if cf_files and series and series[-1][0] == day and series[-1][2] == cf_total:
        write_curseforge_files(out.with_name("curseforge-files.json"), day, cf_files, stamp)
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
