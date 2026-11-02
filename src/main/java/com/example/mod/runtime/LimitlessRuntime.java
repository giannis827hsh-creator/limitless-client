package com.example.mod.runtime;

import com.example.mod.LimitlessClient;
import com.example.mod.gui.GuiState;
import com.example.mod.license.LicenseManager;
import com.example.mod.module.ArrayListModule;
import com.example.mod.module.AutoEat;
import com.example.mod.module.AutoReconnect;
import com.example.mod.module.AutoSell;
import com.example.mod.module.AutoTool;
import com.example.mod.module.AutoWalk;
import com.example.mod.module.BlockESP;
import com.example.mod.module.DonutOnlineAdmins;
import com.example.mod.module.DonutStaffDetector;
import com.example.mod.module.EspModule;
import com.example.mod.module.EspTracers;
import com.example.mod.module.ElytraSwap;
import com.example.mod.module.Freecam;
import com.example.mod.module.Freelook;
import com.example.mod.module.Fullbright;
import com.example.mod.module.GlowESP;
import com.example.mod.module.HoleESP;
import com.example.mod.module.ItemESP;
import com.example.mod.module.LightESP;
import com.example.mod.module.ModuleKeybinds;
import com.example.mod.module.ModuleManager;
import com.example.mod.module.PlayerDetection;
import com.example.mod.module.ShieldBreaker;
import com.example.mod.module.SkinOverride;
import com.example.mod.module.SpawnerNotifier;
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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.Identifier;

/** Loaded only after the encrypted feature bundle has been defined. */
public final class LimitlessRuntime {
    private LimitlessRuntime() { }

    public static void initialize() {
        ModuleManager modules = new ModuleManager();
        EspModule esp = new EspModule();
        PlayerDetection playerDetection = new PlayerDetection();
        BlockESP blockEsp = new BlockESP();
        StorageESP storageEsp = new StorageESP();
        HoleESP holeEsp = new HoleESP();
        ItemESP itemEsp = new ItemESP();
        LightESP lightEsp = new LightESP();
        GlowESP glowEsp = new GlowESP();
        TargetESP targetEsp = new TargetESP();
        ArrayListModule arrayList = new ArrayListModule();
        TargetHUD targetHud = new TargetHUD();
        SpawnerNotifier spawnerNotifier = new SpawnerNotifier();
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
        ShieldBreaker shieldBreaker = new ShieldBreaker();
        TriggerBot triggerBot = new TriggerBot();
        LimitlessClient.MODULES = modules;
        LimitlessClient.FREECAM = new Freecam();
        LimitlessClient.FREELOOK = new Freelook();
        LimitlessClient.FULLBRIGHT = new Fullbright();
        LimitlessClient.SKIN_OVERRIDE = new SkinOverride();
        LimitlessClient.SUS_CHUNK = new SusChunk();
        LimitlessClient.WATERMARK = new WatermarkHUD();
        LimitlessClient.DONUT_DETECTOR = new DonutStaffDetector();
        LimitlessClient.ONLINE_ADMINS = new DonutOnlineAdmins();
        modules.add(esp);
        modules.add(playerDetection);
        modules.add(LimitlessClient.FREECAM);
        modules.add(LimitlessClient.FREELOOK);
        modules.add(LimitlessClient.FULLBRIGHT);
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
        modules.add(LimitlessClient.DONUT_DETECTOR);
        modules.add(LimitlessClient.ONLINE_ADMINS);
        modules.add(spawnerNotifier);
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
        modules.add(shieldBreaker);
        modules.add(triggerBot);
        GuiState.INSTANCE.loadModules(modules);
        modules.disableAll();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GuiState.INSTANCE.saveModules(modules);
            LimitlessClient.FREECAM.setEnabled(false);
            modules.clearWorldState();
            ModuleKeybinds.reset();
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
            shieldBreaker.tick(client);
            if (!shieldBreaker.actedThisTick()) triggerBot.tick(client);
            LimitlessClient.WATERMARK.tick(client);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (LicenseManager.isLicensed()) esp.renderWorld(context);
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
            if (LicenseManager.isLicensed()) EspTracers.renderWorld(context);
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
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "limitless_watermark"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) LimitlessClient.WATERMARK.renderHud(context, ticks);
        });
        HudElementRegistry.addLast(Identifier.of(LimitlessClient.MOD_ID, "target_hud"), (context, ticks) -> {
            if (LicenseManager.isLicensed()) targetHud.renderHud(context, ticks);
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
    }
}
