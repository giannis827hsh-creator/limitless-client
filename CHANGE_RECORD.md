# Change record

This is the working record for future update notes. Add an entry after each project change. State what changed, where it changed, and what was actually checked. `UPDATES.md` is the shorter release history; a Discord post should only include items chosen for announcement.

## November 4, 2026

- Labeled the November 4 release notes as `1.15v` and noted the versioned JAR. Area: `UPDATES.md`. Verification: text reviewed; no client behavior changed.
- Set the client and Fabric metadata version to `1.15v`, made the release JAR name `limitless-client-1.15v.jar`, updated build/install instructions, and recorded the future `0.10` version step in workspace instructions. Areas: Gradle metadata, JAR packaging, README, and workflow. Verification: `limitlessRelease --offline` built successfully, the JAR's `fabric.mod.json` reports `1.15v`, and `git diff --check` passed; Minecraft was not launched.
- Updated `UPDATES.md` for the November 4 source update, covering DiscordPresence, automatic module-state saving, module category moves, Auto Tool, Storage ESP, and visual changes. It marks gameplay verification as pending. Area: release notes. Verification: text reviewed; no client behavior changed.
- Changed Storage ESP so each of its 48 nearest highlighted blocks has its own tracer. This replaces the earlier grouping of nearby blocks and eight-tracer limit. Area: Storage ESP rendering. Verification: `limitlessRelease --offline` built successfully and `git diff --check` passed; Minecraft has not been launched.
- Reduced Storage ESP clutter in dense storage and farm areas by limiting outlines to the 48 nearest matching blocks. An earlier eight-tracer limit and grouping were superseded by the entry above. Existing block type, range, color, and saved settings remain available. Area: Storage ESP rendering. Verification: `limitlessRelease --offline` built successfully; the reduced display has not been tested in Minecraft.
- Saved every module's enabled state alongside its existing settings and keybinds. Enabled modules now restore after the player enters a world; a disconnect preserves the saved state before Freecam cleanup, and a later shutdown from the title screen does not overwrite that snapshot. Areas: GUI config persistence and runtime lifecycle. Verification: `limitlessRelease --offline` built successfully and `git diff --check` passed; restart behavior has not been tested in Minecraft.
- Moved Auto Eat, Auto Tool, Auto Reconnect, Auto Walk, and Sprint from Combat to Misc. Their module names, settings, and behavior stayed the same, so existing saved settings and keybinds continue to use the same keys. Areas: module category declarations. Verification: `limitlessRelease --offline` built successfully, all five declarations show `Category.MISC`, and `git diff --check` passed; Minecraft was not launched.
- Replaced Auto Tool's earlier pre-attack switching with delayed hotbar selection based on predicted breaking progress. Added optional two-tick delay and instant-break netherite pickaxe selection, plus preference for suitable Shard Pickaxes identified by name or lore. Kept the "Auto Tool" module name, Combat category, and existing "Switch back" setting for saved configurations. Removed the pre-attack input hook. Area: Auto Tool. Verification: `limitlessRelease --offline` built successfully and `git diff --check` passed; Minecraft and server anticheat behavior were not tested.
- Earlier in the day, changed Auto Tool to choose its hotbar slot before Minecraft began a block attack. This implementation was superseded by the delayed selection above and is no longer in the source. Verification at that time: `limitlessRelease --offline` built successfully; Minecraft and server anticheat behavior were not tested.
- Added this record and the instruction to update it after every project change. Files: `CHANGE_RECORD.md`, workspace `AGENTS.md`. Verification: checked that both files contain the new rule; no client behavior changed.

### Local changes already present when this record was created

These items were present in the working tree before this record was added. The entries below preserve their status for the next update log; they are not claims of in-game verification.

- Added DiscordPresence in the Client category with the limitless Discord application, logo asset, and an option to hide the server address. Renamed the Hub category heading to Client while retaining compatibility with saved panel positions. Areas: Discord client, module registry, category panel. Verification: Discord IPC was probed outside Minecraft; in-game integration has not been tested.
- Added a Freelook setting to let the third-person camera pass through nearby blocks. Areas: Freelook, camera mixin. Verification: in-game behavior has not been tested.
- Added a Map Scale setting and reduced the default size of the Donut Region Map. Area: Region Map. Verification: in-game appearance has not been tested.
- Reduced the Spawner Notifier popup size. Area: Spawner Notifier. Verification: in-game appearance has not been tested.
- Changed ESP tracer rendering and its Freecam camera origin to address flicker and missing lines. Areas: ESP tracers and render layers, plus affected ESP modules. Verification: in-game behavior has not been tested.
- Changed SusChunk highlight rendering to address highlights appearing over foreground terrain and leaves. Area: SusChunk. Verification: in-game behavior has not been tested.
- Changed Nametags and Spawner Nametags so off-screen targets do not use the on-screen label limit. Areas: Nametags, Spawner Nametags. Verification: in-game behavior has not been tested.
- Expanded `UPDATES.md` with the local work and the November 2–3 beta history. Verification: text reviewed; no client behavior changed.
