# MOD-294: 26.2 renamed the cycle rules — advance_time / advance_weather (the old camelCase
# ids do not parse; see AlaCommandCommon's /ala demo build, which sets the same rules via API).
gamerule advance_time false
gamerule advance_weather false
tellraw @s {"text":"[env] cycles frozen: time + weather (advance_time / advance_weather = false)","color":"yellow"}
