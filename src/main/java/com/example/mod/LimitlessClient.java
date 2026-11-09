package com.example.mod;

import com.example.mod.module.DonutOnlineAdmins;
import com.example.mod.module.CustomSky;
import com.example.mod.module.DonutStaffDetector;
import com.example.mod.module.Freecam;
import com.example.mod.module.Freelook;
import com.example.mod.module.Fullbright;
import com.example.mod.module.ModuleManager;
import com.example.mod.module.SkinOverride;
import com.example.mod.module.SusChunk;
import com.example.mod.module.WatermarkHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

/** Client entry point for the free limitless build. */
public final class LimitlessClient implements ClientModInitializer {
    public static final String MOD_ID = "limitless";
    public static volatile ModuleManager MODULES;
    public static volatile Freecam FREECAM;
    public static volatile Freelook FREELOOK;
    public static volatile Fullbright FULLBRIGHT;
    public static volatile CustomSky CUSTOM_SKY;
    public static volatile DonutStaffDetector DONUT_DETECTOR;
    public static volatile DonutOnlineAdmins ONLINE_ADMINS;
    public static volatile SkinOverride SKIN_OVERRIDE;
    public static volatile SusChunk SUS_CHUNK;
    public static volatile WatermarkHUD WATERMARK;
    private static volatile boolean runtimeReady;
    private static volatile Supplier<Screen> guiFactory;
    private static Screen activeGui;
    private static KeyBinding openGuiKey;
    private int titleRefreshTicks;

    public static boolean isRuntimeReady() { return runtimeReady; }

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.limitless.open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));
        ensureRuntime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++titleRefreshTicks >= 40) {
                titleRefreshTicks = 0;
                client.getWindow().setTitle(Brand.NAME);
            }
            if (!runtimeReady) ensureRuntime();
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen != null && client.currentScreen == activeGui) {
                    client.currentScreen.close();
                } else if (client.currentScreen == null && guiFactory != null) {
                    activeGui = guiFactory.get();
                    client.setScreen(activeGui);
                }
            }
        });
    }

    /** Initializes the full client once; safe to call repeatedly. */
    public static synchronized void ensureRuntime() {
        if (runtimeReady) return;
        try {
            Class.forName("com.example.mod.runtime.LimitlessRuntime")
                    .getMethod("initialize").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException error) {
            throw new IllegalStateException("limitless features could not load", error);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("limitless features could not initialize", error.getCause());
        }
        guiFactory = () -> {
            try {
                return (Screen) Class.forName("com.example.mod.gui.LimitlessClickGUI")
                        .getConstructor().newInstance();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("limitless GUI could not load", error);
            }
        };
        runtimeReady = true;
    }
}
