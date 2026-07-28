## 0.1.53

<p><img alt="Ala Industrial 0.1.53 bug fix patch" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.53/release-media/v0.1.53/changelog.jpeg" width="720"></p>

Power lines route more fairly now, and batteries stop leaking their own charge back into themselves.

### Fixed

- **Power flows toward what needs it**, not just away from the source — a generator no longer walls off everything behind it on the same line, even when it's the only one running.
- **A cable junction splits fairly** between both branches based on how much room each has, instead of always favoring whichever one gets served first.
- **A battery wired in and out of the same network** no longer drinks back its own power — it charges what's downstream instead of leaking in a loop.
