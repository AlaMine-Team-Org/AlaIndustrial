#!/usr/bin/env python3
"""Self-test for compose-announcement.py — no network, no test framework.

Runs as the first step of the Announce workflow. The fixtures are the real 0.1.183 changelogs:
the release that sent two near-identical posts to Discord (MOD-647).

Usage:  python .github/scripts/test-announcement.py
"""
from __future__ import annotations

import importlib.util
import pathlib
import sys

SCRIPT = pathlib.Path(__file__).with_name("compose-announcement.py")
spec = importlib.util.spec_from_file_location("compose_announcement", SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

NEW = """### New

- **The Mob Repeller pings.** A soft sonar-like ping plays while the field is on, on all three
  tiers. It goes quiet when the field does: no power, a redstone signal, or a Mute Chip.
- **An upgrade panel for the Mob Repeller.** A Mute Chip silences it and a Statistics Chip
  tracks the field's upkeep.
"""
FIXED = """
### Fixed

- **Worlds saved on Minecraft 26.2 no longer lose incubator and kok-sagyz data.** The game
  changed how block states are saved.
"""


def changelog(tag: str, count: int, body: str) -> str:
    return (f"# Changelog\n\n## 0.1.183\n\n<p><img alt=\"x\" src=\"https://example/{tag}.png\" width=\"720\"></p>\n\n"
            f"{count} updates in this release. The Mob Repeller finds its voice.\n\n{body}")


LINE_263 = ("v0.1.183-mc26.3", changelog("v0.1.183-mc26.3", 3, NEW + FIXED))
LINE_262 = ("v0.1.183-mc26.2", changelog("v0.1.183-mc26.2", 2, NEW))

failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    if not ok:
        failures.append(f"{name}: {detail}")


# Paired release: one post, shared entries once, the 26.3-only fix labelled, order independent.
for order in ([LINE_263, LINE_262], [LINE_262, LINE_263]):
    post = mod.compose("0.1.183", list(order))
    d = post["description"]
    check("title", post["title"] == "Ala Industrial 0.1.183 — Minecraft 26.3 & 26.2", post["title"])
    check("shared once", d.count("The Mob Repeller pings.") == 1, d)
    check("panel once", d.count("An upgrade panel for the Mob Repeller.") == 1, d)
    check("own block", "**Only on Minecraft 26.3**" in d and "Only on Minecraft 26.2" not in d, d)
    check("own entry", d.count("no longer lose incubator") == 1
          and d.index("**Only on Minecraft 26.3**") < d.index("no longer lose incubator"), d)
    check("no count", "updates in this release" not in d, d)
    check("intro", d.startswith("The Mob Repeller finds its voice."), d)
    check("no html", "<img" not in d and "## " not in d, d)
    check("links both", "v0.1.183-mc26.3+fabric" not in post["downloads"]
          and "0.1.183-mc26.3+fabric" in post["downloads"] and "0.1.183-mc26.2+neoforge" in post["downloads"],
          post["downloads"])
    check("media leader", post["media_tag"] == "v0.1.183-mc26.3", post["media_tag"])
    check("url leader", post["url"].endswith("/releases/tag/v0.1.183-mc26.3"), post["url"])

# Wrapped lines must not break the "same entry on both lines" match.
rewrapped = (LINE_262[0], LINE_262[1].replace("on all three\n  tiers.", "on all\n  three tiers."))
post = mod.compose("0.1.183", [LINE_263, rewrapped])
check("rewrap", post["description"].count("The Mob Repeller pings.") == 1, post["description"])

# A single line is announced alone, with no "Only on" block.
post = mod.compose("0.1.183", [LINE_262])
check("single title", post["title"] == "Ala Industrial 0.1.183 — Minecraft 26.2", post["title"])
check("single no own", "Only on" not in post["description"], post["description"])
check("single entries", post["description"].count("The Mob Repeller pings.") == 1, post["description"])

# A release too long for one embed is announced by headlines, never refused: every entry stays,
# as its bold opening or its first sentence, and a note points at the full notes.
LONG_TAIL = " ".join(["This sentence only pads the entry so the whole post cannot fit."] * 9)
LONG = "### Fixed\n\n" + "".join(
    (f"- **Entry {i} is bold.** {LONG_TAIL}\n" if i % 2 else f"- Entry {i} is plain. {LONG_TAIL}\n")
    for i in range(1, 13))
LONG_263 = ("v0.1.183-mc26.3", changelog("v0.1.183-mc26.3", 12, LONG))
LONG_262 = ("v0.1.183-mc26.2", changelog("v0.1.183-mc26.2", 12, LONG))
post = mod.compose("0.1.183", [LONG_263, LONG_262])
d = post["description"]
check("long fits", len(d) <= mod.EMBED_DESCRIPTION_LIMIT, str(len(d)))
check("long keeps every entry", all(f"Entry {i} is " in d for i in range(1, 13)), d)
check("long bold lead", "- **Entry 1 is bold.**\n" in d, d)
check("long plain lead", "- Entry 2 is plain.\n" in d, d)
check("long drops the padding", "only pads the entry" not in d, d)
check("long note", d.endswith(mod.SHORTENED_NOTE), d[-120:])
check("short has no note", mod.SHORTENED_NOTE not in mod.compose("0.1.183", [LINE_263, LINE_262])["description"])
sentence = "On Minecraft 26.3 the pipe is washed away. Before, it stood."
check("version inside a sentence", mod.lead(sentence) == "On Minecraft 26.3 the pipe is washed away.", mod.lead(sentence))

# Refusals: a tag of another version, and nothing to announce.
for name, args in (("foreign tag", ("0.1.184", [LINE_263])), ("empty", ("0.1.183", []))):
    try:
        mod.compose(*args)
        failures.append(f"{name}: accepted, expected ValueError")
    except ValueError:
        pass

if failures:
    print("compose-announcement self-test FAILED:")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("compose-announcement self-test OK")
