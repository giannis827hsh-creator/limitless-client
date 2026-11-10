# Building a limitless addon

An addon is a separate Fabric mod JAR. It declares a `limitless:addon` entrypoint, depends on `limitless`, and registers its modules when limitless starts. Players place both JARs in the same `mods` folder.

The [hello addon](../examples/hello-addon) is a starter project for Minecraft 1.21.11. Build limitless first with `./gradlew limitlessRelease` (or `gradlew.bat` on Windows). Then build the example from the repository root:

```powershell
.\gradlew.bat -p examples/hello-addon build
```

Its JAR appears in `examples/hello-addon/build/libs`. Copy that JAR beside the limitless JAR in the Minecraft instance. Use the remapped JAR, not a `-dev` JAR.

The example's `fabric.mod.json` declares `com.example.limitlessaddon.HelloAddon` under `limitless:addon`. That class implements `LimitlessAddon` and registers a custom `AddonCategory`, an `AddonModule`, and a `/helloaddon` client command. Modules may also choose a built-in `Module.Category`. Their settings, keybinds, and enabled states use the same saved configuration as built-in modules, so module names must be unique. Register a custom category before adding modules to it.

Override `onEnable`, `onDisable`, or `onWorldLeave` for module lifecycle. Override `onClientTick`, `onWorldRender`, or `onHudRender` for enabled-module callbacks. These run on the client thread; world and HUD callbacks are only called while the module is enabled. Check `client.player` and `client.world` before reading world state. A callback error is logged and disables that addon module.

`AddonRegistry` also has these registration points:

| Method | Purpose |
| --- | --- |
| `registerCategory(AddonCategory)` | Adds a draggable ClickGUI panel for addon modules. |
| `registerCommand(dispatcher -> ...)` | Adds a Fabric client command each time commands are rebuilt. |
| `registerHud(AddonHudElement)` | Adds an independent HUD render element with its own namespaced ID. |
| `onTick`, `onJoin`, `onDisconnect` | Receives client lifecycle events without requiring a module toggle. |
| `onWorldRender`, `onHudRender` | Receives render callbacks without requiring a module toggle. |

The registration methods are available only during `LimitlessAddon.onInitialize`; retain your own module or HUD element references for later state changes. Addon modules can use the built-in Boolean, number, integer, string, color, enum, and mode settings. For assets, mixins, keybindings, packets, and Fabric events outside this API, declare them in the addon's own Fabric mod and use Fabric's APIs directly. The addon JAR stays separate from the limitless JAR.

This is a limitless addon API inspired by Meteor's extension points. It is not binary compatible with Meteor addons or a copy of Meteor's internal classes; Meteor addons need a source port to use it.

The example uses a local limitless JAR in `build/libs` as its compile dependency. For an addon in another directory, update that path in its `build.gradle`. Keep the Minecraft, Fabric Loader, Fabric API, and limitless versions aligned with the installed client. Do not bundle limitless inside the addon JAR.
