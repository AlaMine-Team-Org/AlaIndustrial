## 0.1.143

<p><img alt="Ala Industrial 0.1.143 fix patch" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.143/release-media/v0.1.143/changelog.jpeg" width="720"></p>

If the game seemed to hang on startup with 0.1.141 or 0.1.142 installed, this release fixes it.

### Fixed

- **The game starts normally again.** Loading the mod could take around nine minutes instead of a minute and a half — long enough to look like a freeze rather than a slow load. Worlds already saved are unaffected.
- **Pipes and cables no longer recompute their shape over and over.** The game asks every block for its shape twenty times per state while loading, and after pipes learned to bend down to low neighbours they had a lot of states. The shape is now worked out once per distinct geometry.
