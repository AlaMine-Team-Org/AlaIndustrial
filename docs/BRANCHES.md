# Supported Minecraft versions

`main` develops Minecraft 26.3. `mc/26.2` develops Minecraft 26.2. Both receive features and fixes,
and both build Fabric and NeoForge. Target pull requests at the branch matching the game API.
Cross-version changes are adapted with `git cherry-pick -x` and tested on the destination branch.
Do not merge the complete Minecraft API port back into the older game branch.

New release tags use `vX.Y.Z-mc26.3` and `vX.Y.Z-mc26.2`. Historical `vX.Y.Z` tags remain unchanged.
Each tag publishes two loader files on CurseForge and two loader versions on Modrinth.
Modrinth numbers include the game and loader: `X.Y.Z-mc26.3+fabric`, for example.
The mod version inside each JAR remains X.Y.Z. One game release cannot replace another game's file.

A beta NeoForge dependency produces a beta NeoForge file and a GitHub prerelease. Fabric files
retain their own release channel. Stable Minecraft 26.2 builds remain on the release channel.
The guide site is deployed from main independently of JAR publication.
