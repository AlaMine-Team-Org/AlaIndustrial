## 0.1.6

This update improves recipe browsing from machine screens and cleans up the advancement tab. It also fixes a fluid-transfer edge case in the hidden pump implementation.

### Quality of Life

- **Machine screens can now open matching processing recipes from the progress area.** The macerator, electric furnace, compressor, and extractor use shared click targets so recipe browsing behaves consistently across builds.
- **Processing recipes now show clearer machine categories.** Machine recipe categories include the machine icon, input and output slots, and the EU cost with processing time.

### Bug Fixes

- **Fixed the Ala Industrial advancement tab root.** The tab now has a proper Ala Industrial title, the Geologist entry sits under it correctly, and obsolete leftover advancement entries have been removed.
- **Fixed machine recipe browsing opening an empty recipe list in some cases.** Processing recipes are now collected from the synced in-world recipe data before the recipe viewer displays them.
- **Fixed pump pull-and-return behavior.** When a pump pulls lava from an adjacent fluid storage, it no longer pushes that same bucket back into the source during the same tick.
