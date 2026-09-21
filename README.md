<p align="center"><img src="docs/branding/logo_pixel_art_2k.jpeg" alt="Ala Industrial" width="100%"></p>

# Ala Industrial

An EU/FE technology mod for Minecraft Java Edition, available for **Fabric and NeoForge**.
Build power networks, automate processing, develop agriculture, and progress through industrial machines.

## Minecraft versions

| Branch | Minecraft | Loaders |
|---|---|---|
| `main` | 26.3 | Fabric + NeoForge |
| `mc/26.2` | 26.2 | Fabric + NeoForge |

Both game versions receive features and fixes. Download the file matching your Minecraft version
and loader. Minecraft 26.3 builds using a beta NeoForge dependency are marked beta.
The source on a branch may be ahead of the latest published file; tagged releases identify downloads.

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ala-industrial)
- [Modrinth](https://modrinth.com/mod/ala-industrial)
- [GitHub releases](https://github.com/AlaMine-Team-Org/AlaIndustrial/releases)
- [Player guide](https://alamine-team-org.github.io/AlaIndustrial/)

## Build and contribute

Use Java 25 and the included Gradle wrapper. Dependencies are pinned in `gradle.properties`.

```bash
./gradlew build :neoforge:runGameTests
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

Shared gameplay lives in `common/`; `fabric/` and `neoforge/` contain loader integration.
See [build instructions](docs/BUILD.md), [contributing](CONTRIBUTING.md), and
[version support](docs/BRANCHES.md). Report bugs with your Minecraft version, loader, mod version,
reproduction steps, and relevant log output.

## License

[MIT](LICENSE).
