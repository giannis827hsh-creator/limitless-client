package com.example.mod.module;

import com.example.mod.LimitlessClient;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;

/** Turns the camera without changing the player's movement or server-facing rotation. */
public final class Freelook extends Module {
    private final BooleanSetting thirdPerson = addSetting(new BooleanSetting("Third person view", true));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through walls", false));
    private final NumberSetting sensitivity = addSetting(new NumberSetting("Camera sensitivity", 1.0, 0.2, 2.0, 0.1));
    private ClientWorld attachedWorld;
    private ClientPlayerEntity attachedPlayer;
    private Perspective previousPerspective;
    private boolean changedPerspective;
    private float yaw, pitch;

    public Freelook() {
        super("Freelook", false, Category.MISC);
        thirdPerson.describe("Switch to rear third person while Freelook is on, then restore your previous view.");
        throughWalls.describe("Let the third-person camera pass through nearby blocks while Freelook is on.");
        sensitivity.describe("How quickly mouse movement turns the detached camera.");
    }

    public boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return isEnabled() && attachedPlayer != null && attachedWorld != null
                && client.player == attachedPlayer && client.world == attachedWorld
                && client.getCameraEntity() == attachedPlayer && attachedPlayer.isAlive()
                && (LimitlessClient.FREECAM == null || !LimitlessClient.FREECAM.isActive());
    }

    @Override protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !client.player.isAlive()
                || client.getCameraEntity() != client.player) {
            setEnabled(false);
            return;
        }
        if (LimitlessClient.FREECAM != null && LimitlessClient.FREECAM.isEnabled())
            LimitlessClient.FREECAM.setEnabled(false);
        attachedPlayer = client.player;
        attachedWorld = client.world;
        yaw = attachedPlayer.getYaw();
        pitch = attachedPlayer.getPitch();
        previousPerspective = client.options.getPerspective();
        changedPerspective = thirdPerson.get() && previousPerspective != Perspective.THIRD_PERSON_BACK;
        if (changedPerspective) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (changedPerspective && previousPerspective != null
                && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK)
            client.options.setPerspective(previousPerspective);
        attachedPlayer = null;
        attachedWorld = null;
        previousPerspective = null;
        changedPerspective = false;
    }

    @Override public void onWorldLeave() { setEnabled(false); }

    public void tick(MinecraftClient client) {
        if (isEnabled() && !isActive()) setEnabled(false);
    }

    /** Mouse deltas have already passed through vanilla's sensitivity settings. */
    public void look(double dx, double dy) {
        if (!isActive() || !Double.isFinite(dx) || !Double.isFinite(dy)) return;
        yaw = wrap(yaw + (float) (dx * 0.15 * sensitivity.get()));
        pitch = Math.max(-90, Math.min(90, pitch + (float) (dy * 0.15 * sensitivity.get())));
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean throughWalls() { return throughWalls.get(); }

    private static float wrap(float angle) {
        float wrapped = angle % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }
}
