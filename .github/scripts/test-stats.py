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
        return 4300, {"11": {"name": "v1 [Fabric]", "downloads": 4000},
                      "12": {"name": "v1 [NeoForge]", "downloads": 300}}, "curseforge-site"

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

    print("a source that stalls past the timeout does not kill the run (2026-09-14)")
    module = load(2)

    def stalled():
        raise TimeoutError("The read operation timed out")

    module.fetch_curseforge = stalled
    target = pathlib.Path(tempfile.mkdtemp()) / "stats.json"
    target.write_text(json.dumps({"generated": "2026-08-26T02:10:50Z",
                                  "totals": {"modrinth": 4568, "curseforge": 4146},
                                  "series": list(only_before)}), encoding="utf-8")
    sys.argv = ["fetch-stats.py", "--out", str(target)]
    try:
        code = module.main()
    except Exception as exc:  # the regression itself: the error escaped main()
        code = repr(exc)
    check("exit 0", code == 0, str(code))
    data = json.loads(target.read_text(encoding="utf-8"))
    check("yesterday recorded, CurseForge carried over",
          data["series"][-1] == [YESTERDAY, 4800, 4146], str(data["series"][-1]))

    print("CurseForge batches are smoothed, the sum is kept (MOD-749, 2026-10-04)")
    module = load(2)
    # The real fortnight before the +514: three stalled days, then the catch-up.
    raw = [83, 86, 84, 57, 102, 66, 40, 91, 43, 114, 89, 105, 107, 118,
           81, 39, 115, 64, 38, 31, 514]
    smooth, moved = module.smooth_batches([float(v) for v in raw])
    check("sum preserved", abs(sum(smooth) - sum(raw)) < 1e-6, f"{sum(smooth)} vs {sum(raw)}")
    check("catch-up day no longer a spike", smooth[-1] < 2 * 85, f"{smooth[-1]:.0f}")
    check("stalled days lifted", min(smooth[-4:-1]) > 64, str([round(v) for v in smooth[-4:-1]]))
    check("days before the week untouched", smooth[:13] == [float(v) for v in raw[:13]])
    check("moved days flagged", moved[-1] and moved[-2] and not moved[0])
    check("unknown days stay unknown",
          module.smooth_batches([None, None, 50.0])[0][:2] == [None, None])

    print("daily rows: exact Modrinth days, flags, no unclosed day")
    series = [["2026-10-01", 100, 1000], ["2026-10-02", 160, 1050],
              ["2026-10-04", 260, 1150], ["2026-10-05", 300, None]]
    daily = module.build_daily(series, {"2026-10-02": 58}, "2026-10-05")
    dates = [row[0] for row in daily]
    check("dates", dates == ["2026-10-02", "2026-10-03", "2026-10-04"], str(dates))
    check("analytics wins for Modrinth", daily[0][1] == 58, str(daily[0]))
    check("gap shared for both", daily[1][1] == 50 and daily[1][2] == 50 and daily[1][3] == "eg",
          str(daily[1]))
    check("today never drawn", "2026-10-05" not in dates)
    daily = module.build_daily(series, {}, "2026-10-06")
    check("unknown CurseForge stays null", daily[-1][2] is None and daily[-1][3] == "", str(daily[-1]))

    print("Modrinth analytics answer is parsed slice by slice (POST /v3/analytics)")
    answer = {"metrics": [
        [{"source_project": "ACLWFBlU", "metric_kind": "downloads", "downloads": 70}],
        [],
        [{"source_project": "ACLWFBlU", "metric_kind": "downloads", "downloads": 64},
         {"source_project": "ACLWFBlU", "metric_kind": "views", "views": 300}],
    ], "project_events": []}
    parsed = module.parse_modrinth_history(answer, datetime.date(2026, 10, 1))
    check("days from the start, empty slice is zero, other metrics ignored",
          parsed == {"2026-10-01": 70, "2026-10-02": 0, "2026-10-03": 64}, str(parsed))
    lead = module.parse_modrinth_history({"metrics": [[], []] + answer["metrics"]},
                                         datetime.date(2026, 9, 29))
    check("leading empty days dropped", min(lead) == "2026-10-01", str(lead))
    hole = module.backfill([["2026-10-01", 100, 50], ["2026-10-04", 200, 80]],
                           {"2026-09-30": 30, "2026-10-01": 70, "2026-10-02": 40}, "2026-10-05")
    check("backfill restores only before the first snapshot",
          [row[0] for row in hole] == ["2026-09-30", "2026-10-01", "2026-10-04"], str(hole))

    print("the CurseForge site source refuses a paging loop")
    import json as _json

    def fake_get(url, headers=None):
        return {"pagination": {"totalCount": 3},
                "data": [{"id": 1, "displayName": "a", "totalDownloads": 5},
                         {"id": 2, "displayName": "b", "totalDownloads": 7}]}
    module.get_json = fake_get
    try:
        module.fetch_curseforge_files()
        check("repeated page raises", False, "no error")
    except ValueError:
        check("repeated page raises", True)

    print("per-file counters are filed with the row and pruned")
    module = load(2)
    folder = pathlib.Path(tempfile.mkdtemp())
    for i in range(35):
        module.write_curseforge_files(folder / "cf.json", f"2026-09-{i + 1:02d}" if i < 30 else f"2026-10-{i - 29:02d}",
                                      {"7": {"name": "v [Fabric]", "downloads": i}}, "t")
    kept = _json.loads((folder / "cf.json").read_text(encoding="utf-8"))
    check("keeps the last days only", len(kept["days"]) == module.CF_FILES_KEEP_DAYS, str(len(kept["days"])))
    check("names kept", kept["names"] == {"7": "v [Fabric]"}, str(kept["names"]))
    code, data, _ = run(2, list(only_before))
    check("stats.json carries daily", isinstance(data.get("daily"), list) and data["daily"], str(data.get("daily")))
    check("source recorded", data["totals"].get("curseforge_source") == "curseforge-site",
          str(data["totals"]))

    print()
    if FAILURES:
        print("FAILED: " + ", ".join(FAILURES))
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
