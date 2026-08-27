# Changelog

## 0.1.122

<p><img alt="Ala Industrial 0.1.122 fix and patch update preview" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.122/release-media/v0.1.122/changelog.jpeg" width="720"></p>

A fix-only update: compatibility with loot chests from other mods, the greenhouse door, and the guide site.

### Fixed

- **Loot chests from other mods come out full again.** This one was ours: our uranium safety check looked inside chests near you, and looking inside an unopened loot chest is the same as opening it — so we opened them before you did and they came out empty. Unopened chests are now left alone.
- **Dungeon chests keep your luck.** The same check was rolling vanilla chest contents early, without the luck you were carrying.
- **The greenhouse door closes itself again.** Opened by hand it used to stay open for good; it now shuts five seconds later, and still waits for anyone standing in the doorway.
- **Messages from the mod read better in chat.** Every line is signed, the greenhouse verdict shows its state by colour, and volume, seedbed count and coordinates stand out from the text.

### Improved

- **Guide pages are fuller and render correctly.** Translations that had quietly shrunk to a third of the original are rewritten, missing recipes are back, and item names no longer vanish from a page.
