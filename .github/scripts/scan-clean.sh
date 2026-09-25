#!/usr/bin/env bash
# =============================================================================
# Cleanliness gate for the PUBLIC AlaIndustrial repository.
#
# Scans the working tree and reports every violation as file:line so it can be
# reviewed instantly. Runs identically in two places:
#   1. Locally, inside the /alamod-publish skill, BEFORE the release commit.
#   2. In CI (.github/workflows/guard.yml) on every push/PR — the safety net
#      that catches anything pushed by hand, bypassing the skill.
#
# Layers:
#   L1  Cyrillic text            -> the public repo is English-only.
#   L3  Secrets / local paths    -> tokens, keys, C:\Users\... never leak.
#   L4  Denylisted paths         -> private-agent dirs, docs internals, PRD, etc. never publish.
# Wording checks are not done here: they run in the private publishing pipeline before
# anything is pushed, so this public file lists nothing it looks for.
#
# Exit: 0 = clean, 1 = violations found, 2 = usage error.
# =============================================================================
set -uo pipefail

# Force the C locale so byte-oriented patterns behave identically in Git Bash
# (where LANG may be unset) and in CI. Cyrillic is matched by its UTF-8 byte
# sequence below, which is locale-independent.
export LC_ALL=C

ROOT="${1:-.}"
ROOT="${ROOT//\\//}"
if [[ "$ROOT" =~ ^([A-Za-z]):/(.*)$ ]]; then
  drive="${BASH_REMATCH[1]}"
  rest="${BASH_REMATCH[2]}"
  lower="$(printf '%s' "$drive" | tr '[:upper:]' '[:lower:]')"
  [[ -d "/mnt/$lower/$rest" ]] && ROOT="/mnt/$lower/$rest"
fi
cd "$ROOT" || { echo "scan: cannot cd to $ROOT" >&2; exit 2; }

# Cyrillic block U+0400–U+04FF encodes in UTF-8 as lead byte 0xD0–0xD3 followed
# by a 0x80–0xBF continuation byte — exactly this range, no other block overlaps.
CYRILLIC='[\xd0-\xd3][\x80-\xbf]'

# Self-test: if the regex engine can't match a known Cyrillic byte pair, the
# scanner is broken — fail loud instead of silently passing dirty content.
if ! printf '\xd0\xa0' | grep -qP "$CYRILLIC"; then
  echo "scan: FATAL — grep -P cannot match Cyrillic bytes; aborting to avoid a false pass" >&2
  exit 2
fi

# Enumerate the files that would actually be committed/published: tracked + new
# untracked, MINUS anything gitignored (build/, runs/, .gradle/, …). Using git so
# .gitignore is honoured automatically — a build artifact (e.g. a NeoForge gametest
# run log whose timestamps carry localized Cyrillic month names like "07июл") can
# never trip the gate, and the exclusion list can't drift from .gitignore as new
# build/run dirs appear. Falls back to a pruned find outside a git work tree.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  mapfile -t allfiles < <(git ls-files --cached --others --exclude-standard)
else
  mapfile -t allfiles < <(find . \
    -type d \( -name .git -o -name build -o -name .gradle -o -name run -o -name runs \
               -o -name .idea -o -name .fabric \) -prune -o \
    -type f -print)
fi
# Text files only: skip binary assets by extension.
mapfile -t files < <(printf '%s\n' "${allfiles[@]}" \
  | grep -viE '\.(png|jpg|jpeg|webp|bmp|gif|ico|jar|zip|ogg|wav|class|woff2|ttf)$' || true)

# This scanner contains the very secret patterns it searches for (its own L3 regex).
# Exclude it (and guard.yml, which runs it) from content scans so it never flags
# itself — it is CI infrastructure, not published mod content.
mapfile -t files < <(printf '%s\n' "${files[@]}" | grep -vE '(^|/)(scan-clean\.sh|guard\.yml)$' || true)

# In-game translation files legitimately contain every language (incl. Cyrillic
# ru_ru/uk_ua/...). They are a shipped feature, not a leak — excluded from the
# English-only (L1) check, but still scanned by L3 (secrets).
# This covers both the per-item lang files (/lang/<locale>.json) and the guide
# book's per-locale content (/guide_book/<locale>.json) — same shipped-translation
# class. The guide site ships intentional localized editions: Russian (site/ru/)
# and Ukrainian (site/uk/), plus the two root language-picker pages (site/index.html,
# site/404.html) whose Cyrillic is the "Русский"/"Українська" link labels. All are
# excluded from L1 (MOD-224: third site language uk).
mapfile -t l1_files < <(printf '%s\n' "${files[@]}" \
  | grep -vE '/(lang|guide_book)/[a-z_]+\.json$|^site/(ru|uk)/|^site/(index|404)\.html$' || true)

fail=0
# scan <label> <grep-flags> <pattern> [filelist-var] [whitelist-text-regex]
#
# -H: force the filename prefix. Without it grep omits the name whenever an xargs batch happens to
# hold exactly one file, and a finding is reported as a bare "12:offending line" with no path.
scan() {
  local label="$1" flags="$2" pattern="$3" listvar="${4:-files}" whitelist="${5:-}" hits
  local -n _list="$listvar"
  [[ ${#_list[@]} -eq 0 ]] && return 0
  if [[ -n "$whitelist" ]]; then
    # Cut the whitelisted TEXT out of each candidate line, then re-test what is left. Discarding
    # the whole line instead (grep -v) would let a real marker hide behind a whitelisted token on
    # the same line: a javadoc line naming net.minecraft.world.entity.ai.attributes AND carrying a
    # forbidden word would sail straight through. That hole was real — verified with a probe file.
    hits=$(printf '%s\0' "${_list[@]}" | xargs -0 grep -HnI $flags -e "$pattern" 2>/dev/null \
           | sed -E "s/($whitelist)//g" | grep $flags -e "$pattern" || true)
  else
    hits=$(printf '%s\0' "${_list[@]}" | xargs -0 grep -HnI $flags -e "$pattern" 2>/dev/null || true)
  fi
  if [[ -n "$hits" ]]; then
    printf '❌ %s\n' "$label"
    printf '%s\n' "$hits" | sed 's/^/   /'
    fail=1
  fi
}

# L1 — Cyrillic (matched by UTF-8 byte range; see $CYRILLIC above).
# Scans everything EXCEPT in-game lang translation files.
# L1 whitelist: curated public copy (the changelog, the guide-site language picker)
# lists supported languages by their own endonym, so two of them are legitimately
# Cyrillic. The whitelist cuts ONLY these exact endonym tokens before re-testing, so
# any real Russian prose — which carries other Cyrillic words on the line — is still
# caught. Keep this list to intentional language-name endonyms, nothing else.
L1_ENDONYM_WHITELIST='Русский|Українська'
scan "L1 Cyrillic text (non-English)"        "-P"  "$CYRILLIC"  l1_files  "$L1_ENDONYM_WHITELIST"

# L3 — secrets and machine-local paths.
scan "L3 secrets / local machine paths"      "-E"  \
  'PRIVATE KEY|ghp_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]+|AKIA[0-9A-Z]{16}|C:\\Users\\|[A-Za-z]:/Users/|/mnt/[a-z]/Users/|/Users/[A-Za-z0-9._-]+/|/home/[A-Za-z0-9._-]+/'

# L3b — sensitive filenames anywhere in the tree.
badnames=$(printf '%s\n' "${files[@]}" \
  | grep -iE '(^|/)\.env($|\.)|\.pem$|\.key$|(^|/)id_rsa|_secret|credentials' || true)
if [[ -n "$badnames" ]]; then
  printf '❌ L3 sensitive filenames\n'
  printf '%s\n' "$badnames" | sed 's/^/   /'
  fail=1
fi

# L4 — denylisted paths that must NEVER be published.
# Agent/tool dirs live only at the repo root, so anchor them with ^.../ and do
# NOT match a bare "/tools/" segment anywhere in the path: a vanilla tag
# namespace like data/c/tags/item/tools/ (e.g. mining_tool.json for powered tools)
# is legitimate shipped content and must not be flagged as the private tools/ dir.
denied=$(printf '%s\n' "${files[@]}" \
  | grep -iE '^(\.c[l]aude|\.agents|\.githooks|tools)/|(^|/)(PRD|AGENTS|C[L]AUDE)\.md$' || true)
if [[ -n "$denied" ]]; then
  printf '❌ L4 denylisted path present\n'
  printf '%s\n' "$denied" | sed 's/^/   /'
  fail=1
fi

if [[ $fail -eq 0 ]]; then
  echo "✅ Cleanliness gate passed — no Cyrillic, secrets, or denylisted paths."
fi
exit $fail
