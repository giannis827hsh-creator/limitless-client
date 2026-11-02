<p align="center"><img src="src/main/resources/assets/limitless/icon.png" alt="limitless client logo" width="128"></p>
<h1 align="center">limitless client</h1>
<p align="center"><strong>A free, open source Minecraft client mod for Fabric 1.21.11.</strong></p>
<p align="center"><strong>Beta release</strong> — limitless client is still in beta. Some features may have bugs or change as development continues.</p>
<p align="center"><a href="#features">Features</a> · <a href="#install">Install</a> · <a href="#build-from-source">Build from source</a> · <a href="#license-and-credits">License</a></p>

## At a glance

| | |
| --- | --- |
| Minecraft | 1.21.11 |
| Mod loader | Fabric |
| Java | 21 or newer |
| License | GPL-3.0-only |
| Access | Free; no account or license key |

Press **Right Shift** in game to open the limitless interface. Enable modules there, adjust their settings, and assign keybinds. Module settings and GUI positions are saved in the Minecraft instance's `config/limitless-gui.properties` file.

## Features

| Area | Included modules |
| --- | --- |
| Visuals | Player and mob ESP, Block ESP, Storage ESP, Item ESP, Hole ESP, Light ESP, tracers, Fullbright, Target HUD, and Spawner Notifier |
| Exploration | Sus Chunk Finder, Player Detection, Freecam, and Freelook |
| Combat | Auto Totem, Auto Inventory Totem, Shield Breaker, and Trigger Bot |
| DonutSMP tools | Anti Vanish and staff alerts, Auto Sell, and TPA macros |

This is a selection of the modules in the source. Features that rely on world or server information can only use data the client receives.

## Install

1. Install **Minecraft 1.21.11** with **Fabric Loader** and **Fabric API**.
2. Build the JAR using the steps below.
3. Put `build/libs/limitless-client.jar` in that Minecraft instance's `mods` folder.
4. Launch the Fabric profile and press **Right Shift**.

The GitHub repository contains source code. There is no prebuilt JAR release attached to it at this time.

## Build from source

Install **Java 21 or newer**, then run from the repository root:

```powershell
# Windows
.\gradlew.bat limitlessRelease
```

```sh
# macOS or Linux
./gradlew limitlessRelease
```

The build creates `build/libs/limitless-client.jar`.

## Project layout

- `src/main/java` — client, GUI, modules, rendering, and mixins
- `src/main/resources` — Fabric metadata, mixin configuration, translations, and icon
- `gradle.properties` — Minecraft, Fabric, and mod versions
- `build.gradle` — dependencies and release build task

## Contributing

Issues and pull requests are welcome. Please include the Minecraft version, Fabric Loader version, steps to reproduce, and a relevant log when reporting a bug. Keep changes focused and check that `limitlessRelease` builds before submitting a pull request.

## License and credits

limitless client is distributed under the **GNU General Public License v3.0 only**. See [LICENSE](LICENSE) for the full terms. If you distribute a modified version, follow the GPLv3 source and notice requirements.

Some feature implementations were adapted from **Anubis 0.9.9**. Its supplied source declares `GPL-3.0-only` and lists **4ldenz** as an author. Adapted areas include Anti Vanish, Sus Chunk Finder, ESP and tracers, and Spawner Notifier; they were integrated and modified for limitless client.
