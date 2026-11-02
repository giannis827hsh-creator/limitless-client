# limitless client

Free Minecraft client mod for **Fabric 1.21.11**. The client starts without an account or license key. Press **Right Shift** in game to open its interface.

## Build

Install Java 21 or newer, then run:

```powershell
.\gradlew.bat limitlessRelease
```

The distributable file is `build/libs/limitless-client.jar`. Install Fabric Loader and Fabric API for Minecraft 1.21.11, then place the JAR in your instance's `mods` folder.

## Source

The `src/main` tree contains the mod, its modules, GUI, mixins, and resources. Old customer-specific licensing and server tooling are not part of this free release.

## License

limitless client is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

## Credits

Some feature implementations were adapted from **Anubis 0.9.9** by **4ldenz**, whose supplied source is licensed `GPL-3.0-only`. The adapted work includes Anti Vanish, Sus Chunk Finder, ESP and tracers, and Spawner Notifier. These features were integrated and modified for limitless client.
