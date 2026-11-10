package com.example.mod.runtime;

import com.example.mod.LimitlessClient;
import com.example.mod.api.AddonModule;
import com.example.mod.api.AddonRegistry;
import com.example.mod.api.AddonHudElement;
import com.example.mod.api.LimitlessAddon;
import com.example.mod.gui.GuiState;
import com.example.mod.license.LicenseManager;
import com.example.mod.module.ArrayListModule;
import com.example.mod.module.AutoEat;
import com.example.mod.module.AutoReconnect;
import com.example.mod.module.AutoSell;
import com.example.mod.module.AutoTool;
import com.example.mod.module.AutoWalk;
import com.example.mod.module.BlockESP;
import com.example.mod.module.CustomSky;
import com.example.mod.module.DonutOnlineAdmins;
import com.example.mod.module.DonutStaffDetector;
import com.example.mod.module.DiscordPresence;
import com.example.mod.module.EspModule;
import com.example.mod.module.ElytraSwap;
import com.example.mod.module.Freecam;
import com.example.mod.module.Freelook;
import com.example.mod.module.Fullbright;
import com.example.mod.module.GlowESP;
import com.example.mod.module.ArmorHUD;
import com.example.mod.module.HoleESP;
import com.example.mod.module.ItemESP;
import com.example.mod.module.LightESP;
import com.example.mod.module.ModuleKeybinds;
import com.example.mod.module.ModuleManager;
import com.example.mod.module.Nametags;
import com.example.mod.module.PlayerDetection;
import com.example.mod.module.PearlTrajectory;
import com.example.mod.module.RegionMap;
import com.example.mod.module.ShieldBreaker;
import com.example.mod.module.SkinOverride;
import com.example.mod.module.SpawnerNotifier;
import com.example.mod.module.SpawnerNametags;
import com.example.mod.module.SpotifyHub;
import com.example.mod.module.StorageESP;
import com.example.mod.module.Sprint;
import com.example.mod.module.SusChunk;
import com.example.mod.module.TargetHUD;
import com.example.mod.module.TargetESP;
import com.example.mod.module.TpaMacro;
import com.example.mod.module.TriggerBot;
import com.example.mod.module.AutoTotem;
import com.example.mod.module.AutoInventoryTotem;
import com.example.mod.module.WatermarkHUD;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers modules and client callbacks. */
public final class LimitlessRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("limitless/addons");
    private LimitlessRuntime() { }

    private static void runAddon(AddonModule module, Consumer<AddonModule> callback) {
        if (!module.isEnabled()) return;
        try {
            callback.accept(module);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.error("Addon module {} failed and was disabled", module.name(), error);
            try {
                module.setEnabled(false);
            } catch (RuntimeException | LinkageError disableError) {
                LOGGER.error("Could not disable addon module {}", module.name(), disableError);
            }
        }
    }

    private static <T> void runListener(String event, Consumer<T> listener, T value) {
        try {
            listener.accept(value);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.error("Addon {} listener failed", event, error);
        }
    }

    public static void initialize() {
        ModuleManager modules = new ModuleManager();
        EspModule esp = new EspModule();
        PlayerDetection playerDetection = new PlayerDetection();
        Nametags nametags = new Nametags();
        RegionMap regionMap = new RegionMap();
        BlockESP blockEsp = new BlockESP();
        StorageESP storageEsp = new StorageESP();
        HoleESP holeEsp = new HoleESP();
        ItemESP itemEsp = new ItemESP();
        LightESP lightEsp = new LightESP();
        GlowESP glowEsp = new GlowESP();
        TargetESP targetEsp = new TargetESP();
        ArrayListModule arrayList = new ArrayListModule();
        TargetHUD targetHud = new TargetHUD();
        ArmorHUD armorHud = new ArmorHUD();
        PearlTrajectory pearlTrajectory = new PearlTrajectory();
        SpawnerNotifier spawnerNotifier = new SpawnerNotifier();
        SpawnerNametags spawnerNametags = new SpawnerNametags();
        AutoEat autoEat = new AutoEat();
        AutoTool autoTool = new AutoTool();
        AutoTotem autoTotem = new AutoTotem();
        AutoInventoryTotem autoInventoryTotem = new AutoInventoryTotem();
        AutoReconnect autoReconnect = new AutoReconnect();
        AutoWalk autoWalk = new AutoWalk();
        Sprint sprint = new Sprint();
        ElytraSwap elytraSwap = new ElytraSwap();
        AutoSell autoSell = new AutoSell();
        TpaMacro tpaMacro = new TpaMacro();
        DiscordPresence discordPresence = new DiscordPresence();
        SpotifyHub spotifyHub = new SpotifyHub();
        ShieldBreaker shieldBreaker = new ShieldBreaker();
        TriggerBot triggerBot = new TriggerBot();
        LimitlessClient.MODULES = modules;
        LimitlessClient.FREECAM = new Freecam();
        LimitlessClient.FREELOOK = new Freelook();
        LimitlessClient.FULLBRIGHT = new Fullbright();
        LimitlessClient.CUSTOM_SKY = new CustomSky();
        LimitlessClient.SKIN_OVERRIDE = new SkinOverride();
        LimitlessClient.SUS_CHUNK = new SusChunk();
        LimitlessClient.WATERMARK = new WatermarkHUD();
        LimitlessClient.DONUT_DETECTOR = new DonutStaffDetector();
        LimitlessClient.ONLINE_ADMINS = new DonutOnlineAdmins();
        modules.add(esp);
        modules.add(playerDetection);
        modules.add(nametags);
        modules.add(regionMap);
        modules.add(LimitlessClient.FREECAM);
        modules.add(LimitlessClient.FREELOOK);
        modules.add(LimitlessClient.FULLBRIGHT);
        modules.add(LimitlessClient.CUSTOM_SKY);
        modules.add(LimitlessClient.SKIN_OVERRIDE);
        modules.add(blockEsp);
        modules.add(storageEsp);
        modules.add(holeEsp);
        modules.add(itemEsp);
        modules.add(lightEsp);
        modules.add(glowEsp);
        modules.add(targetEsp);
        modules.add(LimitlessClient.SUS_CHUNK);
        modules.add(arrayList);
        modules.add(LimitlessClient.WATERMARK);
        modules.add(targetHud);
        modules.add(armorHud);
        modules.add(pearlTrajectory);
        modules.add(LimitlessClient.DONUT_DETECTOR);
        modules.add(LimitlessClient.ONLINE_ADMINS);
        modules.add(spawnerNotifier);
        modules.add(spawnerNametags);
        modules.add(autoEat);
        modules.add(autoTool);
        modules.add(autoTotem);
        modules.add(autoInventoryTotem);
        modules.add(autoReconnect);
        modules.add(autoWalk);
        modules.add(sprint);
        modules.add(elytraSwap);
        modules.add(autoSell);
        modules.add(tpaMacro);
        modules.add(discordPresence);
        modules.add(spotifyHub);
        modules.add(shieldBreaker);
        modules.add(triggerBot);
        AddonRegistry addons = new AddonRegistry(modules);
        for (var entry : FabricLoader.getInstance().getEntrypointContainers("limitless:addon", LimitlessAddon.class)) {
            try {
                entry.getEntrypoint().onInitialize(addons);
            } catch (RuntimeException | LinkageError error) {
                LOGGER.error("Could not initialize addon from {}", entry.getProvider().getMetadata().getId(), error);
            }
        }
        addons.close();
        LimitlessClient.ADDONS = addons;
        List<AddonModule> addonModules = addons.modules();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            if (!LicenseManager.isLicensed()) return;
            for (var command : addons.commands()) runListener("command registration", command, dispatcher);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (LicenseManager.isLicensed())
                for (var listener : addons.joins()) runListener("join", listener, client);
        });
        GuiState.INSTANCE.loadModules(modules);
        modules.disableAll();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GuiState.INSTANCE.saveModules(modules);
            GuiState.INSTANCE.restoreOnNextJoin();
            LimitlessClient.FREECAM.setEnabled(false);
            modules.clearWorldState();
            ModuleKeybinds.reset();
            for (var listener : addons.disconnects()) runListener("disconnect", listener, client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            GuiState.INSTANCE.saveModules(modules);
            modules.disableAll();
            modules.clearWorldState();
            ModuleKeybinds.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!LicenseManager.isLicensed()) {
                modules.disableAll();
                return;
            }
            GuiState.INSTANCE.restoreEnabledModules(modules, client);
            ModuleKeybinds.tick(client, modules);
            blockEsp.tick(client);
            holeEsp.tick(client);
            storageEsp.tick(client);
            lightEsp.tick(client);
            glowEsp.tick(client);
            targetEsp.tick(client);
            LimitlessClient.SUS_CHUNK.tick(client);
            LimitlessClient.FREECAM.tick(client);
            LimitlessClient.FREELOOK.tick(client);
            LimitlessClient.FULLBRIGHT.tick(client);
            LimitlessClient.SKIN_OVERRIDE.tick(client);
            LimitlessClient.DONUT_DETECTOR.tick(client);
            playerDetection.tick(client);
            spawnerNotifier.tick(client);
            spawnerNametags.tick(client);
            autoEat.tick(client);
            autoTool.tick(client);
            autoTotem.tick(client);
            autoInventoryTotem.tick(client);
            autoReconnect.tick(client);
            autoWalk.tick(client);
            sprint.tick(client);
            elytraSwap.tick(client);
            autoSell.tick(client);
            tpaMacro.tick(client);
            discordPresence.tick(client);
            spotifyHub.tick(client);
            shieldBreaker.tick(client);
            if (!shieldBreaker.actedThisTick()) triggerBot.tick(client);
            LimitlessClient.WATERMARK.tick(client);
            for (AddonModule module : addonModules) {
                runAddon(module, addon -> addon.onClientTick(client));
            }
            for (var listener : addons.ticks()) runListener("tick", listener, client);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) esp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) pearlTrajectory.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) LimitlessClient.CUSTOM_SKY.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) blockEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) holeEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) LimitlessClient.SUS_CHUNK.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) storageEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) itemEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) lightEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) targetEsp.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) spawnerNotifier.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) spawnerNametags.renderWorld(context);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!LicenseManager.isLicensed()) return;
            for (AddonModule module : addonModules) {
                runAddon(module, addon -> addon.onWorldRender(context));
            }
            for (var listener : addons.worldRenders()) runListener("world render", listener, context);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "array_list"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) arrayList.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "entity_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) esp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "item_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) itemEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "block_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) blockEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "storage_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) storageEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "hole_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) holeEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "light_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) lightEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "glow_tracers"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) glowEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "target_tracer"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) targetEsp.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "player_detection"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) playerDetection.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "nametags"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) nametags.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "region_map"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) regionMap.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "limitless_watermark"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) LimitlessClient.WATERMARK.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "target_hud"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) targetHud.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "armor_hud"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) armorHud.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "donut_online_admins"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) LimitlessClient.ONLINE_ADMINS.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "anti_vanish"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) LimitlessClient.DONUT_DETECTOR.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "spawner_notifier"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) spawnerNotifier.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "spawner_nametags"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) spawnerNametags.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "spotify_hub"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) spotifyHub.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "addon_hud"), (context, ticks) -> {
            if (!LicenseManager.isLicensed()) return;
            for (AddonModule module : addonModules) {
                runAddon(module, addon -> addon.onHudRender(context, ticks));
            }
            var event = new AddonRegistry.HudRender(context, ticks);
            for (var listener : addons.hudRenders()) runListener("HUD render", listener, event);
        });
        for (AddonHudElement element : addons.hudElements()) {
            HudElementRegistry.addLast(element.id(), (context, ticks) -> {
                if (!LicenseManager.isLicensed()) return;
                try {
                    element.render(context, ticks);
                } catch (RuntimeException | LinkageError error) {
                    LOGGER.error("Addon HUD {} failed", element.id(), error);
                }
            });
        }
    }
}
