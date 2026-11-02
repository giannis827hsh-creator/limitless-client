package com.example.mod.module;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/** Client-tick key polling also works while Freecam cancels vanilla action handling. */
public final class ModuleKeybinds {
    private static final Map<Module, KeyState> PREVIOUS = new IdentityHashMap<>();
    private static boolean previouslyEligible;
    private static Object previousWorld;

    private ModuleKeybinds() {}

    public static void reset() {
        PREVIOUS.clear(); previouslyEligible = false; previousWorld = null;
    }

    /** Call once in END_CLIENT_TICK, even while a screen is open, to consume held keys. */
    public static void tick(MinecraftClient client, ModuleManager modules) {
        boolean eligible = client.player != null && client.world != null
                && client.currentScreen == null && client.getOverlay() == null
                && client.isWindowFocused();
        boolean acceptPress = eligible && previouslyEligible && previousWorld == client.world;
        for (Module module : modules.all()) {
            int code = module.keybind();
            boolean down = Module.isBindableKey(code)
                    && InputUtil.isKeyPressed(client.getWindow(), code);
            KeyState previous = PREVIOUS.put(module, new KeyState(code, down));
            // A newly assigned binding, world change, or GUI/focus transition must first
            // observe key release; closing a drawer while holding its bind cannot toggle it.
            if (acceptPress && previous != null && previous.code == code && down && !previous.down) {
                module.toggle();
            }
        }
        PREVIOUS.keySet().removeIf(module -> !modules.all().contains(module));
        previouslyEligible = eligible;
        previousWorld = client.world;
    }

    private record KeyState(int code, boolean down) {}
}
