## 0.1.43

<p><img alt="Ala Industrial 0.1.43 fix and patch update banner" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.43/release-media/v0.1.43/changelog.jpeg" width="720"></p>

A bug-fix release: the guide book now shows every recipe correctly, and a long-session memory leak is gone.

### Fixed

- **Guide book recipes now show every ingredient.** Tag ingredients like planks, wool and coal now display a real item icon instead of a blank slot — recipes (mute chip, water wheel, wind rotor, uranium torch, wind mill, water mill) no longer look cut off.
- **Guide book: no more duplicate entries.** Turning the page on the Energy tab no longer repeats the previous page's last rows, so each block is listed exactly once.
- **Guide book: multi-variant crafts now cycle through their tiers.** The scythe and tempered-iron tools show every tier once per second, the uranium torch shows both crafting methods, and the chests in the Other tab are grouped iron → silver → gold.
- **Fixed a slow memory leak.** In long play sessions, another mod reading energy or fluid from the mod's machines could slowly leak memory. Not anymore.
