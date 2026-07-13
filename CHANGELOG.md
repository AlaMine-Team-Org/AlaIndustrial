## 0.1.23

<p><img alt="Ala Industrial 0.1.23 update preview" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.23/release-media/v0.1.23/changelog.png" width="720"></p>

A small but tidy update — cleaner machine fronts, honest recipe lookups, and scythes that hit like they should.

### Bug Fixes

- **Cables no longer connect to the front face of machines.** The copper cable used to draw a connection sleeve toward the front of every generator, macerator, electric furnace, extractor, compressor and geothermal generator — even though no energy actually flows through that face. The misleading sleeve is gone: cables now connect only to the five working faces, and the machine's front stays clean and readable. The water mill gets the same treatment for free, and the pump now follows the same rule (its front is no longer an exception). Fluid intake on the pump is unchanged — it still draws from the block in front of it. Existing worlds migrate automatically: stale sleeves disappear the first time the chunk loads.
- **Scythes now deal at least 1 damage.** The wooden, stone, copper and gold scythes showed **0 Attack Damage** on their tooltip, which made them look broken. Each tier now has a sensible attack value: wood / stone / copper / gold / iron / tempered iron → 1, diamond → 2, netherite → 3. Attack speed is unchanged.

### Quality of Life

- **The electric furnace shows up next to vanilla smelting recipes.** When you look up how to smelt tin ore, sand into glass, raw beef into steak or any other vanilla furnace recipe, the electric furnace now appears as an option alongside the vanilla furnace — because it can actually perform all of those recipes. One small change that makes the recipe viewer honest about what the machine can do.
