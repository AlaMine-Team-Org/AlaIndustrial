## 0.1.92

<p><img alt="Two machines powered evenly from one cable fork in Ala Industrial 0.1.92" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.92/release-media/v0.1.92/changelog.png" width="720"></p>

> **Thanks for the report on our Discord.** This release is built around it — power now splits fairly at a cable fork.

### Fixed

- **A fork no longer starves one machine.** With a weak source behind a fork — say *one solar panel feeding two machines* — every tick went to the same machine while the other sat dead. Which one won depended on **where you built**, and an extra cable on the corner only ever fixed it by luck.
- **A rare network freeze is gone.** A branch toward an already-full machine could keep stealing the packet meant for the hungry machine on the other branch. The result looked like a bug in the wire: *the line sat full, and nothing charged*.

### Changes

- The guide site's front page now shows **live download stats**, refreshed daily.
