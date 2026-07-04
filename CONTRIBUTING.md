# Contributing to AlaIndustrial

Thanks for your interest in contributing! AlaIndustrial is an IndustrialCraft-inspired
tech mod for Minecraft (Java Edition) built on Fabric. Bug reports, fixes, and features
are all welcome.

## Getting started

Make sure you have the JDK required by the project installed (see `docs/BUILD.md` for the
exact toolchain and setup notes).

Build the mod:

```bash
./gradlew build
```

Run the dev client to try your changes in-game:

```bash
./gradlew runClient
```

Run the tests:

```bash
./gradlew runGameTest   # in-game integration tests
./gradlew test          # unit tests
```

Please make sure `./gradlew build` passes and the tests are green before opening a pull
request. For gameplay changes, verify the behaviour in the dev client.

## Commit style

We use [Conventional Commits](https://www.conventionalcommits.org/). Prefix each commit
with the type of change:

- `feat:` — a new feature
- `fix:` — a bug fix
- `docs:` — documentation only
- `refactor:` — a code change that neither fixes a bug nor adds a feature
- `chore:` — build, tooling, or housekeeping

Keep commits small and focused — one logical change each — and write messages in the
imperative mood (for example, `Add LV cable block entity`).

## Pull requests

1. Fork the repository and create a branch for your change.
2. Make your change in small, focused commits.
3. Run `./gradlew build` and the tests locally.
4. Open a pull request with a clear description of what you changed and why.

If you're unsure about an approach or want feedback before investing a lot of time, feel
free to open a draft pull request or an issue to discuss it first.

## Conventions

- Code identifiers, registry IDs, `type` values, and tags are always in English.
- Game text lives in the localization files and follows their existing conventions.
- See `docs/ROADMAP.md` for what's planned and where the project is headed.

Happy modding!
