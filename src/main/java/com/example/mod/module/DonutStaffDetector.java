package com.example.mod.module;

import com.example.mod.Theme;
import com.example.mod.LimitlessClient;
import com.example.mod.gui.GuiDraw;
import com.example.mod.license.LicenseManager;
import com.example.mod.module.antivanish.AntiVanishTracker;
import com.example.mod.module.antivanish.AntiVanishTracker.Finding;
import com.example.mod.module.antivanish.AntiVanishTracker.Level;
import com.example.mod.module.antivanish.AntiVanishTracker.Observation;
import com.example.mod.module.antivanish.AntiVanishTracker.Policy;
import com.example.mod.module.antivanish.AntiVanishTracker.Signal;
import com.example.mod.module.antivanish.CompletionProbe;
import com.example.mod.module.antivanish.StaffText;
import com.example.mod.module.antivanish.StaffAlerts;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.IntegerSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.StringSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Tracks staff observations by UUID. */
public final class DonutStaffDetector extends Module {
    private final BooleanSetting tabStars = addSetting(new BooleanSetting("Recognize staff stars", true));
    private final StringSetting extraNames = addSetting(new StringSetting("Extra staff names", "", 512));
    private final BooleanSetting unmarkedPlayers = addSetting(new BooleanSetting("Detect unmarked players", true));
    private final BooleanSetting tabChanges = addSetting(new BooleanSetting("Track Tab changes", true));
    private final BooleanSetting invisibleEntities = addSetting(new BooleanSetting("Check invisible staff", true));
    private final BooleanSetting spectatorChanges = addSetting(new BooleanSetting("Check spectator mode", true));
    private final BooleanSetting completionProbe = addSetting(new BooleanSetting("Command completion probe", false));
    private final IntegerSetting probeInterval = addSetting(new IntegerSetting("Probe interval seconds", 10, 5, 60));
    private final BooleanSetting soundChecks = addSetting(new BooleanSetting("Linked sound evidence", true));
    private final BooleanSetting particleChecks = addSetting(new BooleanSetting("Linked particle evidence", false));
    private final BooleanSetting filterSelf = addSetting(new BooleanSetting("Ignore own actions", true));
    private final BooleanSetting filterMechanisms = addSetting(new BooleanSetting("Filter natural causes", true));
    private final BooleanSetting filterBulk = addSetting(new BooleanSetting("Ignore bulk Tab refresh", true));
    private final IntegerSetting range = addSetting(new IntegerSetting("Sensor range", 64, 8, 128));
    private final IntegerSetting threshold = addSetting(new IntegerSetting("Evidence threshold", 60, 40, 100));
    private final IntegerSetting disappearGrace = addSetting(new IntegerSetting("Tab grace seconds", 2, 1, 10));
    private final BooleanSetting chatNotify = addSetting(new BooleanSetting("Chat notifications", true));
    private final BooleanSetting weakSignals = addSetting(new BooleanSetting("Show unconfirmed signals", false));
    private final IntegerSetting alertsPerMinute = addSetting(new IntegerSetting("Alerts per minute", 4, 1, 10));
    private final BooleanSetting showHud = addSetting(new BooleanSetting("Show HUD", true));
    private final BooleanSetting screenWarnings = addSetting(new BooleanSetting("Screen warning popup", true));
    private final StringSetting warningText = addSetting(new StringSetting("Warning text", "Staff watch! Don't be sus!", 80));
    private final StringSetting followupText = addSetting(new StringSetting("Follow-up text", "Good boy", 80));
    private final IntegerSetting warningSeconds = addSetting(new IntegerSetting("Warning seconds", 4, 2, 10));
    private final IntegerSetting followupSeconds = addSetting(new IntegerSetting("Follow-up seconds", 2, 1, 5));
    private final NumberSetting popupScale = addSetting(new NumberSetting("Popup scale", 1.25, 0.75, 2.0, 0.05));
    private final BooleanSetting departureNotifications = addSetting(new BooleanSetting("Staff leave notifications", true));
    private final IntegerSetting hudX = addSetting(new IntegerSetting("HUD X", 8, 0, 1920));
    private final IntegerSetting hudY = addSetting(new IntegerSetting("HUD Y", 110, 0, 1080));
    private final NumberSetting hudScale = addSetting(new NumberSetting("HUD scale", 0.75, 0.5, 1.25, 0.05));
    private final AntiVanishTracker tracker = new AntiVanishTracker();
    private final CompletionProbe probes = new CompletionProbe();
    private final StaffAlerts staffAlerts = new StaffAlerts();
    private final Map<UUID, Announcement> announcements = new HashMap<>();
    private final ArrayDeque<Long> alertTimes = new ArrayDeque<>();
    private final ArrayDeque<Blast> blasts = new ArrayDeque<>();
    private ClientWorld observedWorld;
    private ClientPlayerEntity observedPlayer;
    private ClientPlayNetworkHandler observedHandler;
    private List<Finding> display = List.of();
    private Set<String> explicitNames = Set.of();
    private String previousNames = "";
    private long nextSnapshot, lastOwnAction = Long.MIN_VALUE / 2;
    private boolean rebasePending;
    private boolean trackingUnmarked;
    private int sensorBudget;

    public DonutStaffDetector() {
        super("Anti Vanish", false, Category.DONUT);
        tabStars.describe("Remember staff identities recognized by green or purple stars in the Tab List.");
        extraNames.describe("Add known staff usernames, separated by spaces or commas. Names alone do not prove someone is watching.");
        unmarkedPlayers.describe("Also detect players without a staff marker who leave Tab while their player entity remains nearby. Report them as possible vanished players, never as confirmed staff.");
        tabChanges.describe("Use a known staff member disappearing from Tab as one clue. Other evidence is still needed.");
        invisibleEntities.describe("Use invisibility on a known staff entity as supporting evidence.");
        spectatorChanges.describe("Use a known staff member switching to spectator mode as supporting evidence.");
        completionProbe.describe("Request name suggestions for known missing staff. Sends completion requests, not commands; suggestions do not prove proximity.");
        probeInterval.describe("Seconds between optional command-completion probes. Higher values send fewer requests.");
        soundChecks.describe("Use nearby sounds only when they can be linked to an already known hidden staff entity.");
        particleChecks.describe("Use nearby particles as support only when linked to a known hidden staff entity.");
        filterSelf.describe("Ignore sounds and particles near your own recent actions to reduce false alarms.");
        filterMechanisms.describe("Filter possible natural, explosion and block-mechanism causes of nearby activity.");
        filterBulk.describe("Treat large Tab List refreshes as a reset rather than many staff disappearances.");
        range.describe("Maximum distance in blocks for nearby entity and activity checks. Hidden server data cannot be recovered.");
        threshold.describe("Evidence score needed for a stronger finding. Higher values require more support; scores are not probabilities.");
        disappearGrace.describe("Wait this many seconds after a staff Tab entry disappears before treating it as a clue.");
        chatNotify.describe("Print staff findings and allowed status notifications in your local chat.");
        weakSignals.describe("Include weaker, unconfirmed findings in the compact HUD. These do not prove staff are nearby.");
        alertsPerMinute.describe("Limit new staff alerts per minute to reduce repeated messages and popups.");
        showHud.describe("Show the compact Anti Vanish findings panel. Screen warning popups have their own toggle.");
        screenWarnings.describe("Show a red screen warning when stronger evidence is linked to a nearby staff entity.");
        warningText.describe("Text shown in the red staff warning popup.");
        followupText.describe("Text shown after the warning, such as Good boy. It does not confirm staff have left.");
        warningSeconds.describe("How many seconds the red warning stays on screen.");
        followupSeconds.describe("How many seconds the follow-up message stays on screen.");
        popupScale.describe("Size of the red warning and follow-up messages. Higher values make them larger.");
        departureNotifications.describe("Show staff status changes. A confirmed departure requires an explicit server announcement.");
        hudX.describe("Horizontal position of the compact findings panel, measured from the left of the screen.");
        hudY.describe("Vertical position of the compact findings panel, measured from the top of the screen.");
        hudScale.describe("Size of the compact findings panel. This does not change the large warning popup size.");
    }
    private static long now() { return System.nanoTime() / 1_000_000; }
    private Policy policy() {
        return new Policy(tabChanges.get(), invisibleEntities.get(), spectatorChanges.get(), threshold.get(),
                range.get(), 5_000, disappearGrace.get() * 1_000L, 15_000);
    }
    private StaffAlerts.Options alertOptions() {
        return new StaffAlerts.Options(screenWarnings.get(), departureNotifications.get(),
                warningSeconds.get() * 1_000L, followupSeconds.get() * 1_000L, alertsPerMinute.get());
    }
    @Override protected void onEnable() { clear(now()); }
    @Override protected void onDisable() { clear(now()); }
    @Override public void onWorldLeave() { clear(now()); }
    private void clear(long time) {
        tracker.reset(time); probes.reset(time); staffAlerts.clear(); display = List.of();
        announcements.clear(); alertTimes.clear(); blasts.clear();
        observedWorld = null; observedPlayer = null; observedHandler = null;
        nextSnapshot = 0; rebasePending = false; trackingUnmarked = false; lastOwnAction = Long.MIN_VALUE / 2;
    }
    private boolean ready() {
        MinecraftClient client = MinecraftClient.getInstance();
        return isEnabled() && LicenseManager.isLicensed() && client.isOnThread()
                && client.world != null && client.world == observedWorld && client.player != null && client.player == observedPlayer
                && client.getNetworkHandler() != null && client.getNetworkHandler() == observedHandler;
    }
    public void tick(MinecraftClient client) {
        if (!isEnabled() || !LicenseManager.isLicensed()) {
            if (observedWorld != null) clear(now());
            return;
        }
        long time = now();
        if (client.world != observedWorld || client.player != observedPlayer || client.getNetworkHandler() != observedHandler) {
            clear(time);
            observedWorld = client.world; observedPlayer = client.player; observedHandler = client.getNetworkHandler();
        }
        if (!ready()) return;
        sensorBudget = 8;
        if (trackingUnmarked != unmarkedPlayers.get()) {
            trackingUnmarked = unmarkedPlayers.get();
            if (!trackingUnmarked) tracker.removeUnmarked();
        }
        if (!extraNames.get().equals(previousNames)) {
            previousNames = extraNames.get(); explicitNames = StaffText.names(previousNames);
        }
        if (!completionProbe.get()) { probes.reset(time); tracker.removeSignal(Signal.COMPLETION); }
        if (!soundChecks.get()) tracker.removeSignal(Signal.SOUND);
        if (!particleChecks.get()) tracker.removeSignal(Signal.PARTICLE);
        if (time >= nextSnapshot) {
            refresh(time); nextSnapshot = time + 250;
            List<Finding> findings = tracker.findings(policy(), time);
            display = findings.stream().filter(f -> weakSignals.get() || f.level() == Level.CORROBORATED
                    || f.signals().contains(Signal.INVISIBLE)
                    || f.staff() && f.signals().contains(Signal.SPECTATOR)).limit(4).toList();
            announce(findings, time);
            List<StaffAlerts.Notice> notices = staffAlerts.update(findings.stream().filter(Finding::staff).toList(),
                    tracker.nearbyIds(policy(), time), tracker.drainChanges().stream()
                            .filter(change -> tracker.isMarkedStaff(change.id())).toList(), alertOptions(), time);
            announceDepartures(notices, time);
        }
        if (completionProbe.get() && client.currentScreen == null && !client.isInSingleplayer()) {
            CompletionProbe.Ticket ticket = probes.begin(tracker.completionCandidates(policy(), time), time, probeInterval.get() * 1_000L);
            if (ticket != null) observedHandler.sendPacket(new RequestCommandCompletionsC2SPacket(ticket.id(), "minecraft:msg "));
        }
        while (!blasts.isEmpty() && time - blasts.peekFirst().at > 4_000) blasts.removeFirst();
    }
    private void refresh(long time) {
        MinecraftClient client = MinecraftClient.getInstance();
        Set<UUID> listed = new HashSet<>();
        for (PlayerListEntry entry : observedHandler.getListedPlayerListEntries()) listed.add(entry.getProfile().id());
        Map<UUID, PlayerEntity> bodies = new HashMap<>();
        for (PlayerEntity player : observedWorld.getPlayers()) if (player != client.player && !player.isRemoved()) bodies.put(player.getUuid(), player);
        Map<UUID, Observation> observations = new HashMap<>();
        int checked = 0;
        for (PlayerListEntry entry : observedHandler.getPlayerList()) {
            if (++checked > 4_096) break;
            UUID id = entry.getProfile().id();
            if (id.equals(client.player.getUuid())) continue;
            String name = entry.getProfile().name();
            boolean staff = explicitNames.contains(StaffText.normalize(name))
                    || tabStars.get() && LimitlessClient.ONLINE_ADMINS.hasAdminStar(entry);
            if (staff || unmarkedPlayers.get() || tracker.isTracked(id)) observations.put(id, observation(id, name, staff,
                    listed.contains(id), true, entry.getGameMode() == GameMode.SPECTATOR, bodies.get(id)));
        }
        for (PlayerEntity player : bodies.values()) {
            UUID id = player.getUuid();
            if (observations.containsKey(id)) continue;
            String name = player.getGameProfile().name();
            boolean staff = explicitNames.contains(StaffText.normalize(name));
            if (staff || unmarkedPlayers.get() || tracker.isTracked(id)) observations.put(id, observation(id, name, staff, listed.contains(id),
                    false, player.isSpectator(), player));
        }
        tracker.sample(observations.values(), policy(), time, rebasePending);
        rebasePending = false;
    }
    private Observation observation(UUID id, String name, boolean staff, boolean listed, boolean entry,
                                    boolean spectator, PlayerEntity body) {
        return new Observation(id, name, staff, listed, entry, body != null, body != null && body.isInvisible(),
                body != null && body.hasStatusEffect(StatusEffects.INVISIBILITY), spectator,
                body == null ? -1 : body.distanceTo(observedPlayer));
    }
    public void onTabChanged(boolean bulk) {
        if (!ready()) return;
        rebasePending |= bulk && filterBulk.get();
    }
    public void onServerTransfer() { if (isEnabled()) clear(now()); }
    public void onServerCorrection() { if (ready()) clear(now()); }
    public void onSystemMessage(Text message) {
        if (!ready() || message == null) return;
        long time = now();
        if (message.getContent() instanceof TranslatableTextContent translated
                && translated.getKey().equals("multiplayer.player.left") && translated.getArgs().length > 0) {
            Object arg = translated.getArgs()[0];
            String decorated = arg instanceof Text text ? text.getString() : String.valueOf(arg);
            for (UUID id : tracker.ids()) if (StaffText.containsName(decorated, tracker.name(id))) tracker.confirmDeparture(tracker.name(id), time);
        } else {
            String departed = StaffText.departed(message.getString());
            if (!departed.isEmpty()) tracker.confirmDeparture(departed, time);
        }
        nextSnapshot = 0;
    }
    public boolean onSuggestions(CommandSuggestionsS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) return false;
        if (!probes.owns(packet.id())) return false;
        long time = now();
        CompletionProbe.Reply reply = probes.accept(packet.id(), packet.getSuggestions().getList().stream().limit(4096).map(s -> s.getText()).toList(), time);
        if (!reply.owned()) return false;
        if (ready() && completionProbe.get()) {
            reply.matches().forEach((name, id) -> tracker.confirmCompletion(id, name, policy(), time)); nextSnapshot = 0;
        }
        return true;
    }
    public void onOutgoing(Packet<?> packet) {
        if (!ready()) return;
        if (packet instanceof PlayerInteractBlockC2SPacket || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerActionC2SPacket
                || packet instanceof HandSwingC2SPacket) lastOwnAction = now();
    }
    public void onExplosion(ExplosionS2CPacket packet) {
        if (!ready()) return;
        blasts.addLast(new Blast(packet.center(), Math.max(6, packet.radius() + 4), now()));
        while (blasts.size() > 16) blasts.removeFirst();
    }
    public void onSound(PlaySoundS2CPacket packet) {
        if (ready() && soundChecks.get() && suspiciousSound(packet.getSound().value().id().getPath()))
            positional(new Vec3d(packet.getX(), packet.getY(), packet.getZ()), Signal.SOUND);
    }
    public void onEntitySound(PlaySoundFromEntityS2CPacket packet) {
        if (!ready() || !soundChecks.get() || !suspiciousSound(packet.getSound().value().id().getPath()) || sensorBudget-- <= 0) return;
        Entity source = observedWorld.getEntityById(packet.getEntityId());
        if (source instanceof PlayerEntity player && tracker.hiddenBody(player.getUuid(), policy(), now())
                && acceptableSource(source.getEntityPos(), player, now()))
            tracker.addLinkedSignal(player.getUuid(), Signal.SOUND, policy(), now());
    }
    public void onParticle(ParticleS2CPacket packet) {
        if (!ready() || !particleChecks.get()) return;
        String id = Registries.PARTICLE_TYPE.getId(packet.getParameters().getType()).getPath();
        if (id.equals("crit") || id.equals("enchanted_hit") || id.equals("damage_indicator") || id.equals("sweep_attack"))
            positional(new Vec3d(packet.getX(), packet.getY(), packet.getZ()), Signal.PARTICLE);
    }
    private void positional(Vec3d source, Signal signal) {
        if (sensorBudget-- <= 0 || source.squaredDistanceTo(observedPlayer.getEntityPos()) > (double) range.get() * range.get()) return;
        long time = now(); PlayerEntity only = null;
        // A weak positional packet cannot name an absent entity or choose between multiple candidates.
        for (PlayerEntity player : observedWorld.getPlayers()) {
            if (!tracker.hiddenBody(player.getUuid(), policy(), time) || source.squaredDistanceTo(player.getEntityPos()) > 9) continue;
            if (only != null) return;
            only = player;
        }
        if (only != null && acceptableSource(source, only, time)) tracker.addLinkedSignal(only.getUuid(), signal, policy(), time);
    }
    private boolean acceptableSource(Vec3d source, PlayerEntity candidate, long time) {
        if (filterSelf.get() && (time - lastOwnAction < 2_500 || source.squaredDistanceTo(observedPlayer.getEntityPos()) < 16)) return false;
        if (!filterMechanisms.get()) return true;
        for (Blast blast : blasts) if (time - blast.at <= 4_000 && source.squaredDistanceTo(blast.position) < blast.radius * blast.radius) return false;
        for (Entity entity : observedWorld.getOtherEntities(candidate, new Box(source.add(-3, -3, -3), source.add(3, 3, 3))))
            if (entity instanceof ProjectileEntity || entity instanceof LivingEntity && entity != observedPlayer && !entity.isInvisible()) return false;
        BlockPos origin = BlockPos.ofFloored(source);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = origin.add(dx, dy, dz);
            String block = Registries.BLOCK.getId(observedWorld.getBlockState(pos).getBlock()).getPath();
            if (block.contains("redstone") || block.contains("piston") || block.equals("observer")
                    || block.equals("repeater") || block.equals("comparator") || block.contains("campfire")
                    || block.contains("spawner") || observedWorld.isReceivingRedstonePower(pos)) return false;
        }
        return true;
    }
    private static boolean suspiciousSound(String id) {
        return id.endsWith("chest.open") || id.endsWith("chest.close") || id.endsWith("barrel.open")
                || id.endsWith("barrel.close") || id.endsWith("shulker_box.open") || id.endsWith("shulker_box.close")
                || id.endsWith("door.open") || id.endsWith("door.close") || id.endsWith("fence_gate.open")
                || id.endsWith("fence_gate.close") || id.endsWith("button.click") || id.endsWith("lever.click");
    }
    private void announce(List<Finding> findings, long time) {
        while (!alertTimes.isEmpty() && time - alertTimes.peekFirst() > 60_000) alertTimes.removeFirst();
        announcements.entrySet().removeIf(e -> time - e.getValue().at > 600_000);
        if (!chatNotify.get()) return;
        for (Finding finding : findings) {
            if (!weakSignals.get() && finding.level() != Level.CORROBORATED
                    && !finding.signals().contains(Signal.INVISIBLE)
                    && !(finding.staff() && finding.signals().contains(Signal.SPECTATOR))) continue;
            String signature = finding.episode() + ":" + finding.level() + ":" + finding.reason();
            Announcement previous = announcements.get(finding.id());
            if (previous != null && (previous.signature.equals(signature)
                    || time - previous.at < 20_000
                    && !(previous.level == Level.NOTICE && finding.level() == Level.CORROBORATED))) continue;
            if (alertTimes.size() >= alertsPerMinute.get()) break;
            announcements.put(finding.id(), new Announcement(signature, time, finding.level())); alertTimes.addLast(time);
            String distance = finding.nearby() ? " (" + Math.round(finding.distance()) + "m)" : " (distance unknown)";
            String label = finding.staff() ? "[limitless Anti Vanish] " : "[limitless Anti Vanish] Possible vanished player: ";
            observedPlayer.sendMessage(Text.literal(label + finding.name() + ": " + finding.reason() + distance)
                    .formatted(finding.staff() && finding.level() == Level.CORROBORATED ? Formatting.GOLD : Formatting.GRAY), false);
        }
    }
    public void renderHud(DrawContext ctx, RenderTickCounter ticks) {
        if (!ready()) return;
        renderPopups(ctx, now());
        if (!showHud.get() || display.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        float scale = (float) hudScale.get();
        int w = 218, h = 23 + display.size() * 25;
        float x = Math.max(0, Math.min(hudX.get(), ctx.getScaledWindowWidth() - w * scale));
        float y = Math.max(0, Math.min(hudY.get(), ctx.getScaledWindowHeight() - h * scale));
        ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(x, y); ctx.getMatrices().scale(scale, scale);
        try {
            GuiDraw.rounded(ctx, 0, 0, w, h, 5, Theme.PANEL_BACKGROUND);
            GuiDraw.outline(ctx, 0, 0, w, h, 5, Theme.BORDER);
            ctx.drawText(client.textRenderer, "ANTI VANISH", 9, 8, Theme.PRIMARY, false);
            String count = Integer.toString(display.size());
            ctx.drawText(client.textRenderer, count, w - 9 - client.textRenderer.getWidth(count), 8, Theme.MUTED_TEXT, false);
            ctx.fill(8, 20, w - 8, 21, Theme.BORDER);
            int row = 26;
            for (Finding finding : display) {
                int color = finding.level() == Level.CORROBORATED ? 0xFFFFBB55 : 0xFFAAAAAA;
                String distance = finding.nearby() ? Math.round(finding.distance()) + "m" : "range unknown";
                String name = GuiDraw.fit(client.textRenderer, finding.name(), w - client.textRenderer.getWidth(distance) - 30);
                ctx.drawText(client.textRenderer, name, 9, row, color, false);
                ctx.drawText(client.textRenderer, distance, w - 9 - client.textRenderer.getWidth(distance), row, Theme.MUTED_TEXT, false);
                ctx.drawText(client.textRenderer, GuiDraw.fit(client.textRenderer, finding.reason(), w - 18), 9, row + 11, Theme.MUTED_TEXT, false);
                row += 25;
            }
        } finally { ctx.getMatrices().popMatrix(); }
    }
    private void announceDepartures(List<StaffAlerts.Notice> notices, long time) {
        if (!chatNotify.get()) return;
        for (StaffAlerts.Notice notice : notices) {
            if (alertTimes.size() >= alertsPerMinute.get()) break;
            String detail = switch (notice.kind()) {
                case LEFT -> "left the server (confirmed departure event).";
                case VISIBLE_AGAIN -> "is visible in Tab again; this does not mean they left.";
                case LOST_CONTACT -> "is no longer detected nearby; departure is not confirmed.";
                default -> "";
            };
            if (!detail.isEmpty()) {
                alertTimes.addLast(time);
                observedPlayer.sendMessage(Text.literal("[limitless Anti Vanish] " + notice.name() + " " + detail)
                        .formatted(notice.kind() == StaffAlerts.Kind.LEFT ? Formatting.GREEN : Formatting.GRAY), false);
            }
        }
    }
    private void renderPopups(DrawContext ctx, long time) {
        StaffAlerts.Options options = alertOptions();
        StaffAlerts.Frame warning = staffAlerts.warningFrame(options, time);
        if (warning != null) {
            boolean followup = warning.kind() == StaffAlerts.Kind.FOLLOWUP;
            String headline = followup ? followupText.get() : warningText.get();
            String detail = followup ? "Stay alert" : "Possible nearby staff: " + warning.names();
            drawPopup(ctx, headline, detail, followup ? Theme.TEXT : 0xFFFF4040,
                    warning.alpha(), (float) popupScale.get(), ctx.getScaledWindowHeight() * 0.32f, false);
        }
        StaffAlerts.Frame departure = staffAlerts.departureFrame(options, time);
        if (departure != null) {
            String headline = switch (departure.kind()) {
                case LEFT -> "STAFF LEFT";
                case VISIBLE_AGAIN -> "STAFF VISIBLE AGAIN";
                default -> "STAFF NO LONGER DETECTED";
            };
            String detail = departure.names() + switch (departure.kind()) {
                case LEFT -> " - confirmed server departure";
                case VISIBLE_AGAIN -> " - back in Tab";
                default -> " - departure not confirmed";
            };
            int color = departure.kind() == StaffAlerts.Kind.LEFT ? 0xFF75FFA6 : 0xFFFFC45C;
            drawPopup(ctx, headline, detail, color, departure.alpha(), 0.85f,
                    ctx.getScaledWindowHeight() - 67, true);
        }
    }
    private void drawPopup(DrawContext ctx, String headline, String detail, int color,
                           float alpha, float requestedScale, float y, boolean small) {
        if (alpha <= 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Text title = Text.literal(headline).formatted(Formatting.BOLD);
        int titleWidth = client.textRenderer.getWidth(title);
        int w = Math.max(small ? 230 : 200, titleWidth + 28), h = 48;
        float scale = Math.min(requestedScale, Math.max(0.1f, (ctx.getScaledWindowWidth() - 24f) / w));
        float x = (ctx.getScaledWindowWidth() - w * scale) / 2;
        y = Math.max(8, Math.min(y, ctx.getScaledWindowHeight() - h * scale - 8));
        ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(x, y); ctx.getMatrices().scale(scale, scale);
        try {
            GuiDraw.rounded(ctx, 0, 0, w, h, 6, GuiDraw.fade(0xDF11151B, alpha));
            GuiDraw.outline(ctx, 0, 0, w, h, 6, GuiDraw.fade(color, alpha * 0.7f));
            ctx.drawTextWithShadow(client.textRenderer, title, (w - titleWidth) / 2, 11, GuiDraw.fade(color, alpha));
            String fitted = GuiDraw.fit(client.textRenderer, detail, w - 20);
            ctx.drawText(client.textRenderer, fitted, (w - client.textRenderer.getWidth(fitted)) / 2, 29,
                    GuiDraw.fade(Theme.MUTED_TEXT, alpha), false);
        } finally { ctx.getMatrices().popMatrix(); }
    }
    private record Announcement(String signature, long at, Level level) { }
    private record Blast(Vec3d position, double radius, long at) { }
}
