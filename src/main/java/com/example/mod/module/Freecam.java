package com.example.mod.module;

import com.example.mod.module.freecam.FreecamMotion;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Camera coordinates and mouse look are detached; the real player retains vanilla physics and packets. */
public final class Freecam extends Module {
    private static final PlayerInput SNEAK_ONLY = new PlayerInput(false, false, false, false, false, true, false);
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 0.20, 0.05, 1.5, 0.05));
    private final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical speed", 1.0, 0.2, 5.0, 0.1));
    private final NumberSetting sprintMultiplier = addSetting(new NumberSetting("Sprint multiplier", 2.0, 1.0, 5.0, 0.25));
    private final BooleanSetting showPlayer = addSetting(new BooleanSetting("Show player", true));
    private final BooleanSetting keepSneaking = addSetting(new BooleanSetting("Keep initial sneak", true));
    private final BooleanSetting scrollSpeed = addSetting(new BooleanSetting("Scroll adjusts speed", true));
    private final FreecamMotion motion = new FreecamMotion();
    private ClientWorld attachedWorld;
    private ClientPlayerEntity attachedPlayer;
    private Perspective originalPerspective;
    private KeyBinding[] suppressedActions = new KeyBinding[0];
    private boolean initiallySneaking, originalCulling, originalBobbing;
    private double originalFovEffect;
    private long lastFrame;

    public Freecam() {
        super("Freecam", false, Category.MISC);
        speed.describe("Set how fast the detached camera moves. Your player body stays under normal world physics.");
        verticalSpeed.describe("Multiply upward and downward camera movement without changing horizontal speed.");
        sprintMultiplier.describe("Multiply camera speed while you hold the sprint key.");
        showPlayer.describe("Show your own player body while the camera is detached.");
        keepSneaking.describe("If you were sneaking when Freecam started, keep the body sneaking until Freecam ends.");
        scrollSpeed.describe("Use the mouse wheel to raise or lower camera speed while Freecam is active.");
    }
    public NumberSetting speed() { return speed; }
    public BooleanSetting showPlayer() { return showPlayer; }

    /** Also guards the short interval between a world change and the next client tick. */
    public boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return isEnabled() && attachedPlayer != null && attachedWorld != null
                && client.player == attachedPlayer && client.world == attachedWorld
                && client.getCameraEntity() == attachedPlayer && attachedPlayer.isAlive() && !attachedPlayer.isRemoved();
    }
    @Override protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !client.player.isAlive()
                || client.getCameraEntity() != client.player) {
            setEnabled(false); return;
        }
        if (com.example.mod.LimitlessClient.FREELOOK != null
                && com.example.mod.LimitlessClient.FREELOOK.isEnabled())
            com.example.mod.LimitlessClient.FREELOOK.setEnabled(false);
        attachedPlayer = client.player; attachedWorld = client.world;
        initiallySneaking = attachedPlayer.isSneaking();
        originalPerspective = client.options.getPerspective();
        originalBobbing = client.options.getBobView().getValue();
        originalFovEffect = client.options.getFovEffectScale().getValue();
        originalCulling = client.chunkCullingEnabled;
        Camera camera = client.gameRenderer.getCamera();
        boolean ready = camera.isReady() && camera.getFocusedEntity() == attachedPlayer;
        Vec3d eye = ready ? camera.getCameraPos() : attachedPlayer.getEyePos();
        motion.reset(eye.x, eye.y, eye.z, ready ? camera.getYaw() : attachedPlayer.getYaw(),
                ready ? camera.getPitch() : attachedPlayer.getPitch());
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.options.getBobView().setValue(false);
        client.options.getFovEffectScale().setValue(0.0);
        client.chunkCullingEnabled = false;
        suppressedActions = new KeyBinding[] {client.options.attackKey, client.options.useKey,
                client.options.pickItemKey, client.options.dropKey, client.options.swapHandsKey,
                client.options.inventoryKey, client.options.chatKey, client.options.commandKey,
                client.options.socialInteractionsKey, client.options.advancementsKey,
                client.options.togglePerspectiveKey, client.options.smoothCameraKey,
                client.options.saveToolbarActivatorKey, client.options.loadToolbarActivatorKey,
                client.options.quickActionsKey};
        attachedPlayer.input.playerInput = playerInput();
        attachedPlayer.setSprinting(false);
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
            if (attachedPlayer.isUsingItem()) client.interactionManager.stopUsingItem(attachedPlayer);
        }
        suppressActions();
        client.worldRenderer.scheduleTerrainUpdate();
        lastFrame = System.nanoTime();
    }
    @Override protected void onDisable() {
        if (attachedPlayer == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        // Drain presses before resuming normal handling, so actions don't burst out on exit.
        suppressActions();
        for (KeyBinding key : suppressedActions) key.setPressed(false);
        client.options.setPerspective(originalPerspective);
        client.options.getBobView().setValue(originalBobbing);
        client.options.getFovEffectScale().setValue(originalFovEffect);
        client.chunkCullingEnabled = originalCulling;
        if (client.player == attachedPlayer) attachedPlayer.input.playerInput = PlayerInput.DEFAULT;
        client.worldRenderer.scheduleTerrainUpdate();
        suppressedActions = new KeyBinding[0];
        attachedPlayer = null; attachedWorld = null; originalPerspective = null;
    }
    public void tick(MinecraftClient client) {
        if (!isEnabled()) return;
        if (!isActive()) { setEnabled(false); return; }
        // The body stays in its own world state. Never change its position, flight abilities or velocity.
        suppressActions();
        if (client.options.getPerspective() != Perspective.FIRST_PERSON)
            client.options.setPerspective(Perspective.FIRST_PERSON);
    }
    public PlayerInput playerInput() {
        return initiallySneaking && keepSneaking.get() ? SNEAK_ONLY : PlayerInput.DEFAULT;
    }
    public void suppressActions() {
        for (KeyBinding key : suppressedActions) while (key.wasPressed()) { /* Consume queued actions. */ }
        MinecraftClient client = MinecraftClient.getInstance();
        for (KeyBinding key : client.options.hotbarKeys) while (key.wasPressed()) { }
    }
    /** Receives the exact sensitivity-adjusted mouse deltas that vanilla would apply to the body. */
    public void look(double dx, double dy) { if (isActive()) motion.look(dx, dy); }
    public void applyCamera(Camera camera) {
        if (!isActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        double seconds = (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        if (client.currentScreen == null && client.isWindowFocused() && !client.isPaused()) {
            double forward = axis(client.options.forwardKey, client.options.backKey);
            double right = axis(client.options.rightKey, client.options.leftKey);
            double vertical = axis(client.options.jumpKey, client.options.sneakKey);
            double boost = down(client.options.sprintKey) ? sprintMultiplier.get() : 1.0;
            motion.move(forward, right, vertical, speed.get() * 20 * boost, verticalSpeed.get(), seconds);
        }
        camera.setPos(motion.x(), motion.y(), motion.z());
        camera.setRotation(motion.yaw(), motion.pitch());
    }
    private double axis(KeyBinding positive, KeyBinding negative) { return (down(positive) ? 1 : 0) - (down(negative) ? 1 : 0); }
    private boolean down(KeyBinding binding) {
        MinecraftClient client = MinecraftClient.getInstance();
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        int code = key.getCode();
        long window = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE)
            return code >= 0 && code <= GLFW.GLFW_MOUSE_BUTTON_LAST && GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.KEYSYM)
            return code >= GLFW.GLFW_KEY_SPACE && code <= GLFW.GLFW_KEY_LAST && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        return binding.isPressed(); // Preserve bindings supplied using native scan codes.
    }
    /** Consume the wheel even when speed adjustment is disabled, keeping it off the body's hotbar. */
    public void scroll(double amount) {
        if (scrollSpeed.get() && Double.isFinite(amount) && amount != 0)
            speed.set(speed.get() + Math.signum(amount) * 0.05);
    }
    public boolean shouldRenderPlayer() { return isActive() && showPlayer.get(); }
    public void stopForServerCorrection() {
        if (!isEnabled()) return;
        setEnabled(false);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("Freecam closed: server corrected your position."), false);
    }
}
