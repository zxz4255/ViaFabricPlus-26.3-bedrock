<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
  <img src="src/main/resources/assets/viafabricplus/icon.png" width="150" alt="ViaFabricPlus logo">
  <h1>ViaFabricPlus</h1>
  <a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Mod%20Loader-Fabric-lightyellow?logo=fabric" alt="Mod Loader: Fabric"></a>
  <img src="https://img.shields.io/badge/Environment-Client-purple" alt="Environment: Client">
  <a href="https://discord.gg/viaversion"><img src="https://img.shields.io/discord/316206679014244363?color=0098DB&label=Discord&logo=discord&logoColor=0098DB" alt="Discord"></a><br/>
  <a href="https://modrinth.com/mod/viafabricplus"><img src="https://img.shields.io/badge/dynamic/json?color=158000&label=downloads&prefix=+%20&query=downloads&url=https://api.modrinth.com/v2/project/rIC2XJV4&logo=modrinth" alt="Modrinth Downloads"></a>
  <a href="https://curseforge.com/minecraft/mc-mods/viafabricplus"><img src="https://cf.way2muchnoise.eu/full_830604_downloads.svg" alt="CurseForge Downloads"></a>
  <a href="https://github.com/ViaVersion/ViaFabricPlus/actions/workflows/build.yml"><img src="https://github.com/ViaVersion/ViaFabricPlus/actions/workflows/build.yml/badge.svg" alt="Build Status"></a>

  <p><strong>Minecraft Fabric mod that allows you to join <em>every</em> Minecraft server version (Classic, Alpha, Beta, Release, April Fools, Bedrock)</strong></p>
</div>

**ViaFabricPlus** is a Minecraft mod for [Fabric](https://fabricmc.net/) that builds on
the [ViaVersion plugin](https://github.com/ViaVersion/ViaVersion).
It lets you connect to servers from almost every Minecraft version while fixing issues that the original project
couldn't.

These fixes make older servers feel much closer to how they originally played, with improvements to movement, block and
entity interactions, graphics, and more. In short, it recreates the classic Minecraft experience on today's client.

## Important to know

- Works **only with the newest Minecraft client version**
- Runs **only on [Fabric](https://fabricmc.net/)**. (There won't be a Forge or NeoForge version)
- **Multiplayer only** – it does not affect singleplayer worlds
- **Clientside only** – it does not run on Multiplayer servers
- **No cross-version resource packs** – resource packs from older versions are not supported
- If you want to play using **older Minecraft clients**, you should use the
  original [ViaFabric](https://viaversion.com/fabric) instead.
  For a detailed comparison between the two projects, check out
  the [ViaFabric vs ViaFabricPlus section](https://github.com/ViaVersion/ViaFabric?tab=readme-ov-file#differences-with-viafabricplus).

## How to use

- [Step-by-step installation and usage guide](docs/USAGE.md)
- Found a bug? Please report it on the [issue tracker](https://github.com/ViaVersion/ViaFabricPlus/issues)
- Got questions? Join the [ViaVersion Discord](https://discord.gg/viaversion)

### Supported Client versions

| **Version**                    | **Feature Updates** | **Bug Fixes** |
|--------------------------------|---------------------|---------------|
| Minecraft 26.3                 | Yes                 | Yes           |
| Minecraft 26.2                 | No                  | Yes           |
| Minecraft 26.1.x *(and older)* | No                  | No            |

### Supported Server versions

- Release (1.0.0–26.3*)
- Beta (b1.0 – b1.8.1)
- Alpha (a1.0.15 – a1.2.6)
- Classic (c0.0.15 – c0.30 including [CPE](https://wiki.vg/Classic_Protocol_Extension))
- April Fools (3D Shareware, 20w14infinite, 25w14craftmine)
- Combat Snapshots (Combat Test 8c)

*[Support for new Mojang releases is usually added within a few days](https://github.com/ViaVersion/ViaVersion#snapshot-support)

## Bedrock Edition

Support for Minecraft: Bedrock Edition servers and Realms is built into this build of ViaFabricPlus (the
[ViaFabricPlus Bedrock](https://github.com/ViaVersionAddons/viafabricplus-bedrock) addon is merged into the mod
instead of being installed separately). It is based on [ViaBedrock](https://github.com/RaphiMC/ViaBedrock).

### Container GUI translation

Bedrock is server authoritative for inventory manipulation and never sees Java "window clicks": it only
accepts item stack requests. Without a translation layer every click in a Bedrock chest, barrel, shulker box,
hopper, ... is dropped, so items cannot be moved around.

This build adds that translation:

- `com.viaversion.viafabricplus.bedrock.gui` builds Bedrock `ItemStackRequest` packets (take / place / swap /
  drop / consume / collect / drag) from Java container clicks, including hotbar slot swapping;
- output slots work too: crafting tables and the player 2x2 grid request the recipe (`CraftRecipe` +
  `Take` from the created output container), machine results (furnace, anvil, stonecutter, smithing table,
  cartography table, loom, grindstone, villager trades) are taken straight out of the result container, and
  middle clicking in creative mode creates items through `CraftCreative`;
- item drags are replayed as one multi-slot action set once the drag ends, like the Bedrock client does;
- slot mapping for chests, barrels, shulker boxes, hoppers, dispensers, droppers, furnaces, smokers, blast
  furnaces, brewing stands, crafting tables, anvils, enchanting tables, beacons, stonecutters, smithing tables,
  cartography tables, looms, grindstones, villager trades, the player inventory, armor, offhand and the cursor;
- switchable in the Bedrock settings (`Container GUI translation`).

Details: [`docs/BEDROCK_26_3_ANALYSIS.md`](docs/BEDROCK_26_3_ANALYSIS.md) and
[`docs/BEDROCK_CONTAINER_TRANSLATION.md`](docs/BEDROCK_CONTAINER_TRANSLATION.md).

## For Developers & Contributors

- [Contribution guide & dev setup](CONTRIBUTING.md)
- [API docs & integration examples](docs/DEVELOPER_API.md)

## Credits

Huge thanks to all our [contributors](https://github.com/ViaVersion/ViaFabricPlus/graphs/contributors) who made this
project possible.

## Disclaimer

We cannot guarantee this mod will be allowed on every server.
Some servers may treat it as suspicious and flag it with anti-cheat plugins.
**Use responsibly and at your own risk!**
