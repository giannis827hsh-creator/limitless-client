<p align="center"><img src="src/main/resources/assets/limitless/icon.png" alt="limitless logo" width="200"></p>

# limitless client

A free Fabric client mod for Minecraft 1.21.11. The client is in beta, so expect some features to change.

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

Copy `build/libs/limitless-client.jar` into the instance's `mods` folder. This repository does not currently provide a prebuilt release.

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

Some implementations were adapted from Anubis 0.9.9 and 1.0, whose supplied source declares GPL-3.0-only and lists 4ldenz as an author. Those areas include Anti Vanish, Sus Chunk Finder, ESP and tracers, Spawner Notifier, and the DonutSMP Region Map.
