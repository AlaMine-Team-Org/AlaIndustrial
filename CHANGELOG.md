## 0.1.125

<p><img alt="Ala Industrial 0.1.125 - an oil deposit in a cave before and after the fix" src="https://raw.githubusercontent.com/AlaMine-Team-Org/AlaIndustrial/v0.1.125/release-media/v0.1.125/changelog.png" width="720"></p>

One fix this time: oil deposits no longer end up floating inside a cave.

### Bug Fixes

- **Oil no longer hangs in a cave as a stone bowl.** A deposit that fell into a cave hall used to be sealed into a bowl of oil floating in mid-air; such a spot is now skipped, and deposits stay embedded in rock.
- Note: this does not repair caves in an existing world - world generation is never replayed, so the fix applies to new chunks only.
