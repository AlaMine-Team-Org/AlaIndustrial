## 0.1.36

<p><img alt="Ala Industrial in-game guide book, English UI with section tabs and Open Wiki button" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.36/release-media/v0.1.36/changelog.png" width="720"></p>

A built-in guide book now teaches the mod from inside the game, translated into 20 languages.

### New

- **In-game guide book.** You get a Guide Book the first time you enter a world. Right-click it to open a full-screen guide with sections for machines, blocks and items, a "Getting Started" walkthrough, live item icons and 3×3 crafting grids. Lost it? Craft a new one (book + wooden gear) or run `/ala guide`.
- **Localized into 20 languages.** English, Українська, Русский, বাংলা, Tiếng Việt, Deutsch, Nederlands, Svenska, Español, Français, Italiano, Bahasa Indonesia, हिन्दी, 日本語, 한국어, Polski, Português (Brasil), Türkçe, 简体中文, 繁體中文. Any other language falls back to English.
- **Water mill wheel.** The water mill now takes a separate Water Mill Wheel part — craft it and install it before the generator will run. Its model was reworked to look solid from every side.
- **Starter items in the bonus chest.** If you enable the Bonus Chest when creating a world, the mod now adds a few of its own starter items on top of the vanilla ones — a scythe, a bundle of uranium torches and a gear, and rarely a hardened-iron tool or a battery. Turn it off with `bonusChestEnabled` in the config.

### Changed

- **Electric Furnace recipe.** The Electric Furnace is now built from the mod's own Iron Furnace plus a battery instead of a vanilla furnace, so the mod's progression no longer depends on a vanilla item.

### Server admins

- **Reload balance without a restart.** Operators can run `/ala config reload` to re-read the config file while the server is running. The generated config file now explains every setting right inside the file.
