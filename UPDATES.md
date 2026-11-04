# Update log

The 1.15v source build completed successfully. Gameplay and visual changes still need in-game verification.

## 1.15v — November 4, 2026

### Added

- Added DiscordPresence to the Client panel. It sends the `limitless client` name and `limitless` Rich Presence logo asset to Discord, with a setting to hide the server address.
- Renamed the Hub panel heading to Client while keeping saved panel positions compatible.
- Added a Freelook option that lets the third-person camera pass through nearby blocks.
- Added a Map Scale setting for the DonutSMP Region Map.

### Changed

- The client version and built JAR filename now include `1.15v`.
- Module on/off states now save automatically with settings and keybinds and are restored when entering a world. No module or setting is needed to turn this on.
- Moved Auto Eat, Auto Tool, Auto Reconnect, Auto Walk, and Sprint from Combat to Misc.
- Reworked Auto Tool switching around predicted block-breaking progress, with a two-tick delay option and Shard Pickaxe preference.
- DiscordPresence now handles replies with a null event field, retries without the image if Discord rejects the asset, and reconnects after a lost connection.
- Changed ESP tracer rendering and Freecam camera positioning to address flicker and missing lines.
- Changed SusChunk rendering to address highlights appearing through foreground terrain and leaves.
- Storage ESP now shows the 48 nearest outlines, with one tracer for each highlighted block.
- Reduced the Spawner Notifier popup and the Region Map so they take less screen space by default.
- Changed Nametags and Spawner Nametags so off-screen targets no longer consume the on-screen label limit.

## November 3, 2026

- Added the DonutSMP Region Map with named regions, Goliath numbers, coordinates, a player marker, and display options.
- Added player Nametags with compact and detailed layouts and options for health, ping, distance, and equipment.
- Added Spawner Nametags with mob names, distance, and an optional activation range ring.
- Added the Discord community link and updated the README for the beta source release.

## November 2, 2026

- Published the limitless client source for Fabric 1.21.11 as a free beta under GPL-3.0-only.
- Added the limitless branding, logo, ClickGUI, saved settings and keybinds, and the `limitlessRelease` JAR build.
- Included combat modules: Auto Eat, Auto Inventory Totem, Auto Reconnect, Auto Tool, Auto Totem, Auto Walk, Elytra Swap, Shield Breaker, Sprint, and Trigger Bot.
- Included visual modules: Entity ESP, Player Detection, Block ESP, Storage ESP, Hole ESP, Item ESP, Light ESP, Glow ESP, Target ESP, and tracers.
- Included exploration and utility modules: SusChunk, Freecam, Freelook, Fullbright, and Skin Override.
- Included DonutSMP modules: Anti Vanish, Online Admins, Spawner Notifier, Auto Sell, and TPA Macro.
- Included HUD modules: Array List, Watermark, and Target HUD.
