<p align="center"><img src="src/main/resources/assets/limitless/icon.png" alt="limitless logo" width="200"></p>

# limitless client

A free Fabric client mod for Minecraft 1.21.11. The client is in beta, so expect some features to change.

Discord: [discord.gg/limitlessclient](https://discord.gg/limitlessclient)

Press **Right Shift** in game to open the module menu. Settings and keybinds are saved in `config/limitless-gui.properties` inside your Minecraft instance.

## Build and install

You'll need Java 21, Fabric Loader, and Fabric API for Minecraft 1.21.11.

On Windows:

```powershell
.\gradlew.bat limitlessRelease
```

On macOS or Linux:

```sh
./gradlew limitlessRelease
```

Copy `build/libs/limitless-client-1.25v.jar` into the instance's `mods` folder. A prebuilt JAR is also available on the [GitHub releases page](https://github.com/giannis827hsh-creator/limitless-client/releases).

## Modules

- **Visuals:** player and mob ESP, block and storage ESP, tracers, Nametags, Spawner Nametags, Fullbright, and Target HUD.
- **Exploration:** Sus Chunk Finder, Player Detection, Freecam, and Freelook.
- **Combat:** Auto Totem, Auto Inventory Totem, Shield Breaker, and Trigger Bot.
- **DonutSMP:** Region Map, Anti Vanish, staff alerts, Auto Sell, and TPA macros.

See [UPDATES.md](UPDATES.md) for recent additions. World scanners can only use chunks and entity data sent to the client.

## Contributing

If you report a bug, include your Minecraft and Fabric versions, the steps to reproduce it, and the relevant log. Run `limitlessRelease` before submitting a code change.

## License and credits

limitless client is licensed under [GPL-3.0-only](LICENSE).

Some code was adapted from Anubis 0.9.9 and 1.0 (GPL-3.0-only), which lists 4ldenz as an author.
