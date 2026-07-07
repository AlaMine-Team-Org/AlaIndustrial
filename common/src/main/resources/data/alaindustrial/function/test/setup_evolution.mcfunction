# Solar evolution — day chip -> daylight panel, night chip -> moonlit panel (~33600 sky-ticks at the chip's time, ~2.8 active half-days, ~28 real minutes).
fill ~1 ~-1 ~-3 ~12 ~4 ~4 minecraft:air replace
fill ~1 ~-1 ~-3 ~12 ~-1 ~4 minecraft:smooth_stone replace
fill ~3 ~1 ~0 ~5 100 ~0 minecraft:air replace
setblock ~3 ~ ~0 alaindustrial:solar_panel
setblock ~5 ~ ~0 alaindustrial:solar_panel
give @s alaindustrial:alignment_chip_day 1
give @s alaindustrial:alignment_chip_night 1
tellraw @s {"text":"[evolution] Two base solar panels, open sky. Put an evolution chip in the panel's slot.","color":"green"}
tellraw @s {"text":"DAY evolution chip + env_noon -> after ~33600 day sky-ticks (~2.8 active half-days, ~28 real min) the panel becomes daylight_solar_panel (carries its EU).","color":"gray"}
tellraw @s {"text":"NIGHT evolution chip + env_midnight -> becomes moonlit_solar_panel. Re-run env_noon/env_midnight so the clock doesn't drift off the chip's time.","color":"gray"}
