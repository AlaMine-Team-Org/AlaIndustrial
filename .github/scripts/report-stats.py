#!/usr/bin/env python3
"""Daily Telegram report on mod downloads, plus a health check of the pipeline.

The figures come from data/stats.json — the snapshot history written by
fetch-stats.py — and are computed with exactly the formulas the guide site uses
in docs/tools/guide_site.js (dailyFrom, the week / previous-week sums, and the
rule that drops any day that is not over yet). If a number here disagrees with
the number on the site, this script is wrong.

Two things it verifies beyond the arithmetic:

  * the collector produced a row for the day being reported at all;
  * the deployed site serves the same snapshot file that sits in the repository
    (compared on the FULL `generated` timestamp — a file rebuilt on the same day
    but never deployed carries the same date and a different time).

Usage:
    python .github/scripts/report-stats.py [--dry-run]

Environment (never passed on the command line — argv is visible in the process
list and in workflow logs):
    TELEGRAM_BOT_TOKEN   bot token, from repository secrets
    TELEGRAM_CHAT_ID     chat to post into
"""
from __future__ import annotations

import argparse
import datetime
import http.client
import json
import math
import os
import re
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SITE_URL = "https://alamine-team-org.github.io/AlaIndustrial/data/stats.json"
TELEGRAM_HOST = "https://api.telegram.org"
UA = "Ma3auka/AlaIndustrial-stats-report (+https://github.com/AlaMine-Team-Org/AlaIndustrial)"
TIMEOUT = 30

# Telegram rejects a message longer than 4096 UTF-16 code units, counted AFTER
# the markup is parsed. Stay well below it: the report is a few hundred units, so
# the budget only ever matters when something goes wrong and the problem list
# grows unexpectedly.
MSG_BUDGET = 3900
MAX_LINE = 300          # a single line longer than this is clipped, not dropped
MAX_VERSION = 24        # the version string comes from Modrinth: treat as untrusted

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")


# --------------------------------------------------------------------------- #
# arithmetic                                                                    #
# --------------------------------------------------------------------------- #

def js_round(value: float) -> int:
    """Round half UP, the way JavaScript's Math.round does.

    Python's round() is banker's rounding: round(192.5) is 192 and round(50.5)
    is 50, while the site shows 193 and 51. The two only disagree on an exact
    .5 — which is precisely what a one-day gap in collection produces, since the
    missing day is spread evenly across the gap.
    """
    return math.floor(value + 0.5)


def parse_day(text: str) -> datetime.date:
    return datetime.date(int(text[0:4]), int(text[5:7]), int(text[8:10]))


def fmt_day(day: datetime.date) -> str:
    return "%d %s %d" % (day.day, MONTHS[day.month - 1], day.year)


def is_number(value) -> bool:
    # bool is an int subclass; true/false in the file is corruption, not a count.
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def normalise_series(series):
    """Keep only rows this script can reason about; count what was thrown away.

    A row is [date, modrinth, curseforge]; the CurseForge slot may legitimately
    be null ("unknown for that day"). Anything else — a short row, a bad date, a
    null where the Modrinth counter should be — is corruption, and a corrupt row
    silently treated as zero would invent a download spike.
    """
    if not isinstance(series, list):
        return [], 0 if series is None else 1
    rows, dropped = [], 0
    for row in series:
        if not isinstance(row, (list, tuple)) or len(row) != 3:
            dropped += 1
            continue
        date, modrinth, curseforge = row
        if not isinstance(date, str) or not DATE_RE.match(date):
            dropped += 1
            continue
        if not is_number(modrinth):
            dropped += 1
            continue
        try:
            parse_day(date)
        except ValueError:
            dropped += 1
            continue
        rows.append((date, modrinth, curseforge if is_number(curseforge) else None))
    rows.sort(key=lambda item: item[0])
    return rows, dropped


def daily_from(rows):
    """Cumulative snapshots -> per-day figures (guide_site.js, dailyFrom()).

    A missing day is spread evenly across the gap, so a recovered collection does
    not draw a fake spike. A day whose CurseForge slot is null counts Modrinth
    alone and is marked partial.
    """
    out = []
    for index in range(1, len(rows)):
        date0, mr0, cf0 = rows[index - 1]
        date1, mr1, cf1 = rows[index]
        gap = max(1, js_round((parse_day(date1) - parse_day(date0)).days))
        partial = cf0 is None or cf1 is None
        mr_delta = mr1 - mr0
        cf_delta = 0 if partial else cf1 - cf0
        per = (mr_delta + cf_delta) / gap
        for step in range(gap, 0, -1):
            out.append({
                "date": (parse_day(date1) - datetime.timedelta(days=step - 1)).isoformat(),
                "value": max(0, js_round(per)),
                "spread": gap > 1,
                "partial": partial,
                # A source that fails to answer is not recorded as missing: the
                # collector carries the previous total over. That is byte-for-byte
                # what a genuinely quiet day looks like, so the two cannot be told
                # apart — flag it and let the wording stay neutral.
                "flat_modrinth": mr_delta == 0,
                "flat_curseforge": not partial and cf_delta == 0,
                "gap": gap,
                "from": date0,
            })
    return out


def trim_future(days, today_iso):
    """Drop any day that is not over yet (guide_site.js: the todayUTC loop)."""
    while days and days[-1]["date"] >= today_iso:
        days.pop()
    return days


# --------------------------------------------------------------------------- #
# report                                                                        #
# --------------------------------------------------------------------------- #

def number(value) -> str:
    return "{:,}".format(int(value))


def clip(text: str, limit: int) -> str:
    text = " ".join(str(text).split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def build_report(data, today, site_result):
    """Return (headline lines, problem lines)."""
    problems = []
    day = today - datetime.timedelta(days=1)
    day_iso = day.isoformat()

    totals = data.get("totals")
    if not isinstance(totals, dict):
        if totals is not None:
            problems.append("totals: not an object in the data file")
        totals = {}

    rows, dropped = normalise_series(data.get("series"))
    if dropped:
        problems.append("series: %d unusable row(s) ignored" % dropped)

    days = trim_future(daily_from(rows), today.isoformat())
    # Look the day up BY DATE. Taking the last element instead would pin a
    # diagnosis about a stalled source onto whatever day happens to sit there.
    today_entry = None
    for entry in days:
        if entry["date"] == day_iso:
            today_entry = entry

    lines = ["Ala Industrial — downloads, %s" % fmt_day(day)]

    if today_entry is None:
        lines.append("Day: no snapshot yet")
        newest = rows[-1][0] if rows else "—"
        problems.append("no row for %s — the collector last recorded %s"
                        % (day_iso, newest))
    else:
        marks = []
        if today_entry["spread"]:
            marks.append("estimate")
            problems.append(
                "collection skipped %d day(s) after %s — the figure is the gap "
                "spread evenly" % (today_entry["gap"] - 1, today_entry["from"]))
        if today_entry["partial"]:
            if "estimate" not in marks:
                marks.append("estimate")
            problems.append("CurseForge has no figure for this day — Modrinth only")
        if today_entry["flat_modrinth"]:
            if "estimate" not in marks:
                marks.append("estimate")
            problems.append("Modrinth counter did not move: either a quiet day or a "
                            "value carried over from the previous one")
        if today_entry["flat_curseforge"]:
            if "estimate" not in marks:
                marks.append("estimate")
            problems.append("CurseForge counter did not move: either a quiet day or a "
                            "value carried over from the previous one")
        lines.append("Day: %s%s" % (number(today_entry["value"]),
                                    " (%s)" % ", ".join(marks) if marks else ""))

    # Totals. A missing counter is unknown, not zero: `totals.get(x) or 0` would
    # print a confident 0 and quietly shrink the grand total.
    modrinth = totals.get("modrinth") if is_number(totals.get("modrinth")) else None
    curseforge = totals.get("curseforge") if is_number(totals.get("curseforge")) else None
    if modrinth is None:
        problems.append("totals.modrinth is missing or not a number")
    if curseforge is None:
        problems.append("totals.curseforge is missing or not a number")
    grand = (modrinth or 0) + (curseforge or 0)

    week_note = ""
    if days:
        week = sum(entry["value"] for entry in days[-7:])
        previous = sum(entry["value"] for entry in days[-14:-7])
        # Fewer than two full weeks and the comparison is against a partial week:
        # the first days of collection would report a triumphant +600%.
        if len(days) >= 14 and previous:
            percent = js_round((week - previous) / previous * 100)
            # The sign is written out only when the week actually grew. Deriving
            # it from the rounded percent instead would print "+0%" for a week
            # that fell from 700 to 699.
            sign = "+" if week >= previous else ""
            week_note = " (+%s in 7 days, %s%d%%)" % (number(week), sign, percent)
        else:
            week_note = " (+%s in 7 days)" % number(week)

    lines.append("Total: %s%s" % (number(grand) if (modrinth is not None or curseforge is not None)
                                  else "unknown", week_note))

    followers = totals.get("followers")
    lines.append("Modrinth: %s (%s followers)" % (
        number(modrinth) if modrinth is not None else "unknown",
        number(followers) if is_number(followers) else "?"))
    if not is_number(followers):
        problems.append("totals.followers is missing or not a number")
    lines.append("CurseForge: %s" % (number(curseforge) if curseforge is not None else "unknown"))

    loaders = totals.get("loaders")
    if not isinstance(loaders, dict):
        loaders = {}
    fabric = loaders.get("fabric") if is_number(loaders.get("fabric")) else None
    neoforge = loaders.get("neoforge") if is_number(loaders.get("neoforge")) else None
    if fabric is None or neoforge is None:
        problems.append("totals.loaders is incomplete")
        lines.append("Fabric / NeoForge: unknown")
    else:
        loader_sum = fabric + neoforge
        if loader_sum:
            fabric_pct = js_round(fabric / loader_sum * 100)
            lines.append("Fabric: %s (%d%%) / NeoForge: %s (%d%%)" % (
                number(fabric), fabric_pct, number(neoforge), 100 - fabric_pct))
        else:
            lines.append("Fabric / NeoForge: no downloads recorded")

    version = totals.get("version")
    lines.append("Version: %s" % (clip(version, MAX_VERSION) if isinstance(version, str) and version
                                  else "unknown"))

    generated = data.get("generated")
    if not isinstance(generated, str) or not generated:
        problems.append("generated: missing timestamp in the data file")
    if site_result is not None:
        ok, detail = site_result
        if not ok:
            problems.append(detail)

    return lines, problems


# --------------------------------------------------------------------------- #
# deployed-site check                                                           #
# --------------------------------------------------------------------------- #

def fetch_json(url, headers=None):
    request = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.load(response)


def check_site(url, local_generated, wait_seconds, poll_seconds):
    """Is the deployed site serving the snapshot that sits in the repository?

    Compared on the WHOLE `generated` timestamp. Comparing only its date would
    pass a file that was rebuilt today and never reached Pages — which is exactly
    the incident this report exists to catch (2026-08-14: the deploy ran green on
    the commit before the snapshot).

    Pages needs a couple of minutes after the collector commits, so the check
    waits rather than declaring every healthy day a failed deploy.
    """
    if not isinstance(local_generated, str) or not local_generated:
        return False, "site: cannot verify — the local file has no `generated` stamp"
    deadline = time.monotonic() + max(0, wait_seconds)
    last = None
    while True:
        try:
            # Pages answers with a cacheable response; without a buster the check
            # can keep reading a copy from an edge cache.
            probe = "%s%scb=%d" % (url, "&" if "?" in url else "?", int(time.time()))
            payload = fetch_json(probe)
            if not isinstance(payload, dict):
                last = "site: served something that is not a data object"
            else:
                served = payload.get("generated")
                if served == local_generated:
                    return True, "site is serving the current snapshot"
                last = ("site: deployed snapshot is %s, the repository has %s"
                        % (clip(served, 40) if isinstance(served, str) and served else "unstamped",
                           clip(local_generated, 40)))
        except (urllib.error.URLError, http.client.HTTPException, socket.timeout,
                ValueError) as exc:
            last = "site: data file could not be read (%s)" % type(exc).__name__
        if time.monotonic() + poll_seconds >= deadline:
            return False, last or "site: not checked"
        time.sleep(poll_seconds)


# --------------------------------------------------------------------------- #
# telegram                                                                      #
# --------------------------------------------------------------------------- #

def escape_html(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def utf16_len(text: str) -> int:
    """Telegram counts UTF-16 code units, so an emoji costs two, not one."""
    return len(text.encode("utf-16-le")) // 2


def render(lines, html):
    """Fit the lines into the budget, cutting WHOLE lines.

    Cutting mid-string could sever an HTML tag or an entity and Telegram would
    reject the whole message. A line that does not fit is skipped and the next
    one is still tried — breaking out of the loop instead would let one long
    line silence everything after it.
    """
    kept, used, skipped = [], 0, 0
    for index, line in enumerate(lines):
        line = clip(line, MAX_LINE)
        body = escape_html(line) if html else line
        if html and index == 0:
            body = "<b>%s</b>" % body
        cost = utf16_len(line) + (1 if kept else 0)
        if used + cost > MSG_BUDGET:
            skipped += 1
            continue
        kept.append(body)
        used += cost
    if skipped:
        tail = "... %d more line(s) omitted" % skipped
        if used + utf16_len(tail) + 1 <= MSG_BUDGET:
            kept.append(escape_html(tail) if html else tail)
    return "\n".join(kept)


def post(token, chat_id, text, parse_mode):
    """One send attempt. Returns (ok, retry_after, fatal, note)."""
    payload = {
        "chat_id": chat_id,
        "text": text,
        "link_preview_options": {"is_disabled": True},
    }
    if parse_mode:
        payload["parse_mode"] = parse_mode
    # The token sits in the URL, so nothing about this request — not the URL, not
    # the request object, not an exception carrying either — may be printed.
    # Log masking on the runner is a courtesy, not a guarantee.
    request = urllib.request.Request(
        "%s/bot%s/sendMessage" % (TELEGRAM_HOST, token),
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": UA},
        method="POST")
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            response.read()
        return True, 0, False, ""
    except urllib.error.HTTPError as exc:
        code = exc.code
        description, retry_after = "", 0
        try:
            body = json.loads(exc.read().decode("utf-8", "replace"))
            description = str(body.get("description", ""))
            parameters = body.get("parameters")
            if isinstance(parameters, dict) and is_number(parameters.get("retry_after")):
                retry_after = int(parameters["retry_after"])
        except (ValueError, OSError):
            pass
        lowered = description.lower()
        if code == 429:
            return False, retry_after or 5, False, "rate limited"
        # Bad token, kicked out of the chat, wrong chat id: retrying cannot fix
        # any of these, it only burns the schedule.
        if code in (401, 403) or "chat not found" in lowered or "chat_id" in lowered:
            return False, 0, True, "telegram refused the request (HTTP %d)" % code
        if code == 400 and ("parse" in lowered or "entit" in lowered or "tag" in lowered):
            return False, 0, True, "markup rejected"
        if 500 <= code < 600:
            return False, 5, False, "telegram error (HTTP %d)" % code
        return False, 0, True, "telegram rejected the message (HTTP %d)" % code
    except (urllib.error.URLError, http.client.HTTPException, socket.timeout) as exc:
        return False, 5, False, "network problem (%s)" % type(exc).__name__


def send(token, chat_id, lines, attempts=4, sleep=time.sleep, max_backoff=60):
    """Send the report, degrading to plain text if the markup is refused."""
    modes = [("HTML", render(lines, html=True)), (None, render(lines, html=False))]
    for parse_mode, text in modes:
        for attempt in range(1, attempts + 1):
            ok, retry_after, fatal, note = post(token, chat_id, text, parse_mode)
            if ok:
                return True
            print("send failed: %s" % note, file=sys.stderr)
            if note == "markup rejected":
                break                       # one retry, as plain text
            if fatal or attempt == attempts:
                return False
            sleep(min(max(retry_after, 1), max_backoff))
    return False


# --------------------------------------------------------------------------- #
# entry point                                                                   #
# --------------------------------------------------------------------------- #

def parse_args(argv):
    parser = argparse.ArgumentParser(description="Post the daily download report to Telegram.")
    parser.add_argument("--stats", default="data/stats.json", help="snapshot history to read")
    parser.add_argument("--dry-run", action="store_true",
                        help="print the message instead of sending it")
    parser.add_argument("--site-url", default=SITE_URL, help="deployed copy of the data file")
    parser.add_argument("--no-site-check", action="store_true", help="skip the deploy check")
    parser.add_argument("--site-wait", type=int, default=300,
                        help="seconds to wait for the deploy to catch up (default: 300)")
    parser.add_argument("--site-poll", type=int, default=20, help="seconds between site probes")
    parser.add_argument("--today", default=None,
                        help="pin today's UTC date (YYYY-MM-DD); for testing only")
    return parser.parse_args(argv)


def collect(args):
    """Build the message lines. Raises on anything it cannot handle."""
    # Resolve "today" ONCE. The site check below may wait minutes for a deploy,
    # and a process that starts before midnight and finishes after it would pick
    # the day for the figures and the day for the freshness check from two
    # different calendars.
    if args.today:
        today = parse_day(args.today)
    else:
        today = datetime.datetime.now(datetime.timezone.utc).date()

    with open(args.stats, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError("the data file does not hold an object")

    site_result = None
    if not args.no_site_check:
        site_result = check_site(args.site_url, data.get("generated"),
                                 args.site_wait, args.site_poll)

    lines, problems = build_report(data, today, site_result)
    if problems:
        lines.append("Checks: %d issue(s)" % len(problems))
        lines.extend("- %s" % problem for problem in problems)
    else:
        lines.append("Checks: all clear")
    return lines


def main(argv=None):
    args = parse_args(argv or sys.argv[1:])
    try:
        lines = collect(args)
    except Exception as exc:                       # noqa: BLE001 - see below
        # A broken or unreadable data file is the single most important thing to
        # hear about, and it is exactly the case where an uncaught exception
        # would mean total silence on the worst day. Report the failure through
        # the same channel; the class name is enough to act on and cannot leak a
        # secret the way an exception's own text might.
        lines = ["Ala Industrial — download report FAILED",
                 "The report could not be built: %s" % type(exc).__name__,
                 "Data file: %s" % clip(args.stats, 120),
                 "Check the Stats workflow and data/stats.json."]

    if args.dry_run:
        print(render(lines, html=False))
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat_id:
        print("ERROR TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID are not set", file=sys.stderr)
        return 1
    if not send(token, chat_id, lines):
        print("ERROR the report could not be delivered", file=sys.stderr)
        return 1
    print("OK report sent (%d lines)" % len(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
