#!/usr/bin/env python3
"""Self-test for fetch-stats.py — no network, no clock, no test framework.

It runs as the first step of the Stats workflow, so the rules below are checked on
the same morning they are relied upon. Every case here is a bug that actually
reached the site (MOD-520, 2026-08-27):

  * backfill() appended the current, still-growing day as if it were a closed one.
    A dropped schedule then left that stub as "yesterday" and the chart drew a
    crash to 1 download instead of simply ending a day earlier.
  * the catch-up slot must not re-collect when the night run already succeeded:
    counters read at 06:43 would pour hours of TODAY into yesterday's bar.
  * a midday run must not rewrite yesterday either — until MOD-520 an accident
    prevented it (the stub row sat at the end of the file and took another branch).

Usage:  python .github/scripts/test-stats.py
"""
from __future__ import annotations

import datetime
import importlib.util
import json
import pathlib
import sys
import tempfile

SCRIPT = pathlib.Path(__file__).with_name("fetch-stats.py")
YESTERDAY = "2026-08-26"
DAY_BEFORE = "2026-08-25"

calls = {"modrinth": 0, "curseforge": 0, "history": 0}


def load(hour: int, history: dict | None = None):
    """A fresh copy of the collector with the clock frozen and the network faked."""
    spec = importlib.util.spec_from_file_location("fetch_stats_%d" % hour, SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    class FrozenDateTime(datetime.datetime):
        @classmethod
        def now(cls, tz=None):
            return datetime.datetime(2026, 8, 27, hour, 30, tzinfo=tz)

    module.datetime.datetime = FrozenDateTime

    def modrinth():
        calls["modrinth"] += 1
        return {"downloads": 4800, "followers": 8, "version": "0.1.119",
                "loaders": {"fabric": 2470, "neoforge": 2330}}

    def curseforge():
        calls["curseforge"] += 1
        return 4300

    def analytics():
        calls["history"] += 1
        # The current day always has a bucket of its own by the time any run reads it.
        return dict(history if history is not None else {})

    module.fetch_modrinth = modrinth
    module.fetch_curseforge = curseforge
    module.fetch_modrinth_history = analytics
    return module


def run(hour, series, extra=(), history=None):
    module = load(hour, history)
    target = pathlib.Path(tempfile.mkdtemp()) / "stats.json"
    target.write_text(json.dumps({"generated": "2026-08-26T02:10:50Z",
                                  "totals": {"modrinth": 4568, "curseforge": 4146},
                                  "series": series}), encoding="utf-8")
    for key in calls:
        calls[key] = 0
    sys.argv = ["fetch-stats.py", "--out", str(target)] + list(extra)
    code = module.main()
    return code, json.loads(target.read_text(encoding="utf-8")), dict(calls)


FAILURES = []


def check(name, condition, detail=""):
    print(("  ok   " if condition else "  FAIL ") + name + (("  <- " + detail) if not condition else ""))
    if not condition:
        FAILURES.append(name)


def main() -> int:
    full = [[DAY_BEFORE, 4568, 4146], [YESTERDAY, 4725, 4229]]
    only_before = [[DAY_BEFORE, 4568, 4146]]
    today_bucket = {YESTERDAY: 157, "2026-08-27": 4}

    print("backfill never appends a day that is not over")
    _, data, _ = run(2, list(only_before), history=today_bucket)
    dates = [row[0] for row in data["series"]]
    check("no row dated today", "2026-08-27" not in dates, str(dates))
    check("series ends on yesterday", dates[-1] == YESTERDAY, str(dates[-1]))

    print("--skip-if-current: yesterday complete -> no work at all")
    code, data, net = run(6, list(full), ["--skip-if-current"])
    check("exit 0", code == 0)
    check("series untouched", data["series"] == full, str(data["series"]))
    check("no HTTP request made", net == {"modrinth": 0, "curseforge": 0, "history": 0}, str(net))

    print("--skip-if-current: yesterday missing -> collects")
    code, data, net = run(6, list(only_before), ["--skip-if-current"])
    check("row appended", data["series"][-1] == [YESTERDAY, 4800, 4300], str(data["series"][-1]))
    check("sources queried", net["modrinth"] == 1 and net["curseforge"] == 1, str(net))

    print("--skip-if-current: yesterday has no CurseForge figure -> not 'in full'")
    _, data, _ = run(6, [[DAY_BEFORE, 4568, 4146], [YESTERDAY, 4700, None]], ["--skip-if-current"])
    check("row completed", data["series"][-1] == [YESTERDAY, 4800, 4300], str(data["series"][-1]))

    print("--skip-if-current: a stray row dated today does not hide yesterday")
    _, _, net = run(6, full + [["2026-08-27", 4725, None]], ["--skip-if-current"])
    check("still a no-op", net == {"modrinth": 0, "curseforge": 0, "history": 0}, str(net))

    print("rewriting yesterday is allowed in the morning window only")
    _, data, _ = run(0, list(full))
    check("00:30 rewrites", data["series"][-1] == [YESTERDAY, 4800, 4300], str(data["series"][-1]))
    _, data, _ = run(6, list(full))
    check("06:30 rewrites", data["series"][-1] == [YESTERDAY, 4800, 4300], str(data["series"][-1]))
    _, data, _ = run(15, list(full))
    check("15:30 leaves history alone", data["series"][-1] == [YESTERDAY, 4725, 4229],
          str(data["series"][-1]))
    check("15:30 still refreshes the totals", data["totals"]["modrinth"] == 4800,
          str(data["totals"]))
    _, data, _ = run(15, list(full), ["--force"])
    check("--force rewrites at any hour", data["series"][-1] == [YESTERDAY, 4800, 4300],
          str(data["series"][-1]))

    print()
    if FAILURES:
        print("FAILED: " + ", ".join(FAILURES))
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
