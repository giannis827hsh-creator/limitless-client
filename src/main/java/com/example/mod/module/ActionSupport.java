package com.example.mod.module;

import com.example.mod.LimitlessClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Shared guards and input restoration for modules that perform player actions. */
final class ActionSupport {
    private ActionSupport() { }

    static boolean ready(MinecraftClient client) {
        return client.player != null && client.world != null && client.interactionManager != null
                && client.currentScreen == null && !client.isPaused()
                && (LimitlessClient.FREECAM == null || !LimitlessClient.FREECAM.isActive());
    }

    static boolean physicallyDown(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        int code = key.getCode();
        long window = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE)
            return code >= 0 && code <= GLFW.GLFW_MOUSE_BUTTON_LAST
                    && GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.KEYSYM)
            return code >= 0 && code <= GLFW.GLFW_KEY_LAST
                    && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        return binding.isPressed();
    }

    static void restore(MinecraftClient client, KeyBinding binding) {
        binding.setPressed(client.currentScreen == null && physicallyDown(client, binding));
    }
}
