package com.example.mod.module;

import com.example.mod.Brand;
import com.example.mod.Theme;
import com.example.mod.gui.GuiDraw;
import com.example.mod.setting.BooleanSetting;
import com.example.mod.setting.ColorSetting;
import com.example.mod.setting.ModeSetting;
import com.example.mod.setting.NumberSetting;
import com.example.mod.setting.StringSetting;
import com.example.mod.spotify.AlbumArt;
import com.example.mod.spotify.Lyrics;
import com.example.mod.spotify.LyricsQuery;
import com.example.mod.spotify.LyricsService;
import com.example.mod.spotify.MediaSession;
import com.example.mod.spotify.NowPlaying;
import com.example.mod.spotify.PlayerSource;
import com.example.mod.spotify.SpotifyWindow;
import com.example.mod.spotify.SyncedLyrics;
import com.sun.jna.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SpotifyHub extends Module {
    private static final Identifier ART_ID = Identifier.of("limitless", "spotify_album_art");

    private final BooleanSetting showArtist = addSetting(new BooleanSetting("Show artist", true));
    private final BooleanSetting showArt = addSetting(new BooleanSetting("Show album art", true));
    private final BooleanSetting showProgress = addSetting(new BooleanSetting("Show progress", true));
    private final BooleanSetting scrollTitles = addSetting(new BooleanSetting("Scroll long titles", true));
    private final BooleanSetting showLyrics = addSetting(new BooleanSetting("Show lyrics", true));
    private final ModeSetting lyricLines = addSetting(new ModeSetting("Lyric lines", "2", "1", "2", "3"));
    private final ModeSetting lyricMotion = addSetting(new ModeSetting("Lyric motion", "Wave", "Wave", "Glide", "Fade"));
    private final ModeSetting lyricHighlight = addSetting(new ModeSetting("Lyric highlight", "Karaoke", "Karaoke", "Line", "Off"));
    private final BooleanSetting onlineLyrics = addSetting(new BooleanSetting("Lyrics online", true));
    private final NumberSetting cardWidth = addSetting(new NumberSetting("Card width", 240, 200, 560, 1));
    private final NumberSetting cardSize = addSetting(new NumberSetting("Card size", 0.65, 0.35, 1.2, 0.05));
    private final NumberSetting cardX = addSetting(new NumberSetting("Card X", 8, 0, 1500, 1));
    private final NumberSetting cardY = addSetting(new NumberSetting("Card Y", 54, 0, 900, 1));
    private final ColorSetting titleColor = addSetting(new ColorSetting("Title color", Theme.TEXT));
    private final ColorSetting artistColor = addSetting(new ColorSetting("Artist color", Theme.MUTED_TEXT));
    private final ColorSetting lyricsColor = addSetting(new ColorSetting("Lyrics color", Theme.TEXT));
    private final ColorSetting upcomingColor = addSetting(new ColorSetting("Upcoming lyrics color", Theme.MUTED_TEXT));
    private final ColorSetting highlightColor = addSetting(new ColorSetting("Highlight color", Theme.PRIMARY));
    private final BooleanSetting showIdle = addSetting(new BooleanSetting("Show when idle", false));
    private final BooleanSetting mediaKeys = addSetting(new BooleanSetting("Media keys", false));
    private final StringSetting playKey = addSetting(new StringSetting("Play / Pause key", "F7", 20));
    private final StringSetting nextKey = addSetting(new StringSetting("Next track key", "F8", 20));
    private final StringSetting previousKey = addSetting(new StringSetting("Previous track key", "F9", 20));

    private volatile NowPlaying playing = NowPlaying.WAITING;
    private ScheduledThreadPoolExecutor poller;
    private LyricsService lyrics;
    private volatile Worker worker;
    private NativeImageBackedTexture artTexture;
    private AlbumArt displayedArt;
    private int cardLyricIndex = -1;
    private long cardLyricChangedAt;
    private final boolean[] wasPressed = new boolean[3];

    public SpotifyHub() {
        super("Spotify Hub", false, Category.MISC);
        showLyrics.describe("Display synced lyrics from LRCLIB below the song card.");
        onlineLyrics.describe("Fetch lyrics from LRCLIB; cached lyrics remain available offline.");
        mediaKeys.describe("Enable the configured keyboard shortcuts while playing.");
        cardSize.describe("Resize the Spotify card, including its text and album art.");
        for (var setting : new com.example.mod.setting.Setting[]{lyricLines, lyricMotion, lyricHighlight, onlineLyrics,
                lyricsColor, upcomingColor, highlightColor}) setting.visibleWhen(showLyrics::get);
        playKey.visibleWhen(mediaKeys::get);
        nextKey.visibleWhen(mediaKeys::get);
        previousKey.visibleWhen(mediaKeys::get);
    }

    @Override protected void onEnable() {
        playing = NowPlaying.WAITING;
        cardLyricIndex = -1;
        for (int i = 0; i < wasPressed.length; i++) wasPressed[i] = true;
        lyrics = new LyricsService();
        Worker started = new Worker();
        worker = started;
        poller = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "limitless-spotify");
            thread.setDaemon(true);
            return thread;
        });
        poller.setRemoveOnCancelPolicy(true);
        poller.scheduleWithFixedDelay(started::poll, 0, 500, TimeUnit.MILLISECONDS);
    }

    @Override protected void onDisable() {
        ScheduledThreadPoolExecutor executor = poller;
        poller = null;
        Worker stopped = worker;
        worker = null;
        if (executor != null) {
            executor.execute(stopped::close);
            executor.shutdown();
        }
        LyricsService oldLyrics = lyrics;
        lyrics = null;
        if (oldLyrics != null) oldLyrics.close();
        playing = NowPlaying.WAITING;
        cardLyricIndex = -1;
        releaseArt(MinecraftClient.getInstance());
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled() || !mediaKeys.get() || client.getWindow() == null) return;
        int[] keys = {keyCode(playKey.get()), keyCode(nextKey.get()), keyCode(previousKey.get())};
        for (int i = 0; i < keys.length; i++) {
            boolean down = client.currentScreen == null && keys[i] != GLFW.GLFW_KEY_UNKNOWN
                    && GLFW.glfwGetKey(client.getWindow().getHandle(), keys[i]) == GLFW.GLFW_PRESS;
            if (down && !wasPressed[i]) control(i);
            wasPressed[i] = down;
        }
    }

    private static int keyCode(String raw) {
        String key = raw.strip().toUpperCase(Locale.ROOT).replace("GLFW_KEY_", "");
        if (key.matches("F([1-9]|1[0-9]|2[0-5])")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(key.substring(1)) - 1;
        if (key.length() == 1 && key.charAt(0) >= 'A' && key.charAt(0) <= 'Z') return GLFW.GLFW_KEY_A + key.charAt(0) - 'A';
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '9') return GLFW.GLFW_KEY_0 + key.charAt(0) - '0';
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    private void control(int action) {
        ScheduledThreadPoolExecutor executor = poller;
        Worker active = worker;
        if (executor == null || executor.isShutdown() || active == null) return;
        executor.execute(() -> {
            if (worker == active) active.control(action);
        });
    }

    public void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;
        NowPlaying song = playing;
        if (!song.hasTrack() && !showIdle.get()) return;
        Lyrics current = lyricsFor(song);
        int width = (int) cardWidth.get();
        int artSize = showArt.get() && song.art() != null ? 36 : 0;
        int lines = showLyrics.get() && current != null
                && (current.status() == Lyrics.Status.SYNCED || current.status() == Lyrics.Status.PLAIN)
                ? Integer.parseInt(lyricLines.get()) : 0;
        int progressY = showArtist.get() ? 33 : 23;
        boolean hasProgress = showProgress.get() && song.hasTimeline();
        int lyricsY = progressY + (hasProgress ? 8 : 1);
        int cardHeight = Math.max(54, lyricsY + lines * 11 + 6);
        float drawScale = (float) Math.min(cardSize.get(),
                2.5 * (cardSize.get() / 0.65) / client.getWindow().getScaleFactor());
        int x = Math.min((int) cardX.get(), Math.max(0,
                (int) (context.getScaledWindowWidth() - width * drawScale - 4)));
        int y = Math.min((int) cardY.get(), Math.max(0,
                (int) (context.getScaledWindowHeight() - cardHeight * drawScale - 4)));
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(drawScale, drawScale);
            GuiDraw.surface(context, 0, 0, width, cardHeight, 6, Theme.PRIMARY, 1);
            context.fill(1, 8, 3, cardHeight - 8, Theme.PRIMARY);
            Brand.drawIcon(context, 8, 8, 13);
            int textX = 27;
            int contentRight = artSize > 0 ? width - 49 : width - 9;
            if (artSize > 0) {
                updateArt(client, song.art());
                if (artTexture != null) {
                    GuiDraw.rounded(context, width - 45, 6, 40, 40, 4, Theme.SURFACE);
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, ART_ID, width - 43, 8, 0, 0,
                            artSize, artSize, song.art().size(), song.art().size(), song.art().size(), song.art().size());
                }
            }
            String title = song.hasTrack() ? song.title() : "Spotify is idle";
            int available = contentRight - textX;
            if (scrollTitles.get() && client.textRenderer.getWidth(title) > available) {
                int shift = (int) ((System.currentTimeMillis() / 180) % Math.max(1, title.length()));
                title = title.substring(shift) + "  •  " + title.substring(0, shift);
            }
            context.drawTextWithShadow(client.textRenderer, GuiDraw.fit(client.textRenderer, title, available),
                    textX, 9, titleColor.get());
            if (showArtist.get() && song.hasTrack()) context.drawText(client.textRenderer,
                    GuiDraw.fit(client.textRenderer, song.artist(), available), textX, 20, artistColor.get(), false);
            if (hasProgress) {
                int barWidth = contentRight - 9;
                context.fill(9, progressY, 9 + barWidth, progressY + 2, Theme.TRACK);
                long position = song.positionAt(System.currentTimeMillis());
                int filled = (int) (barWidth * Math.min(1d, (double) position / song.durationMs()));
                if (filled > 0) {
                    context.fill(9, progressY, 9 + filled, progressY + 2, highlightColor.get());
                    context.fill(8 + filled, progressY - 1, 10 + filled, progressY + 3, highlightColor.get());
                }
            }
            int lyricX = 15;
            int lyricWidth = contentRight - lyricX;
            if (lines > 0) {
                context.fill(9, lyricsY + 1, 11, lyricsY + 9, highlightColor.get());
                context.fill(9, lyricsY - 3, contentRight, lyricsY - 2,
                        Theme.withAlpha(Theme.BORDER, 110));
            }
            if (lines > 0 && current.status() == Lyrics.Status.SYNCED)
                drawLyrics(context, client, current.synced(), song, lyricX, lyricsY, lyricWidth, lines);
            else if (lines > 0) {
                for (int i = 0; i < lines && i < current.plainLines().size(); i++)
                    context.drawTextWithShadow(client.textRenderer,
                            GuiDraw.fit(client.textRenderer, current.plainLines().get(i), lyricWidth),
                            lyricX, lyricsY + i * 11, i == 0 ? lyricsColor.get() : upcomingColor.get());
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private Lyrics lyricsFor(NowPlaying song) {
        LyricsService service = lyrics;
        if (!showLyrics.get() || service == null || !song.lyricsEligible()) return null;
        service.setOnline(onlineLyrics.get());
        return service.lookup(new LyricsQuery(song.title(), song.artist(), song.album(), 0).withDurationMillis(song.durationMs()));
    }

    private void drawLyrics(DrawContext context, MinecraftClient client, SyncedLyrics lyrics,
                            NowPlaying song, int x, int y, int width, int count) {
        int index = lyrics.indexAt(song.positionAt(System.currentTimeMillis()));
        if (index < 0) index = 0;
        long now = System.currentTimeMillis();
        if (index != cardLyricIndex) {
            cardLyricIndex = index;
            cardLyricChangedAt = now;
        }
        float entrance = Math.min(1f, (now - cardLyricChangedAt) / 280f);
        for (int i = 0; i < count && index + i < lyrics.size(); i++) {
            SyncedLyrics.Line line = lyrics.line(index + i);
            String text = GuiDraw.fit(client.textRenderer, line.text(), width);
            int color = i == 0 ? lyricsColor.get() : upcomingColor.get();
            if (i == 0 && lyricHighlight.get().equals("Line")) color = highlightColor.get();
            int dy = lyricMotion.get().equals("Wave") ? (int) Math.round(Math.sin(now / 250d + i) * 1.5)
                    : lyricMotion.get().equals("Glide") && i == 0 ? Math.round((1 - entrance) * 4) : 0;
            if (lyricMotion.get().equals("Fade") && i == 0)
                color = Theme.withAlpha(color, Math.round(((color >>> 24) & 255) * entrance));
            context.drawTextWithShadow(client.textRenderer, text, x, y + i * 12 + dy, color);
            if (i == 0 && lyricHighlight.get().equals("Karaoke")) {
                float progress = lyricProgress(lyrics, index, song.positionAt(System.currentTimeMillis()));
                if (progress > 0) {
                    context.enableScissor(x, y + i * 12 - 2,
                            x + Math.max(1, Math.round(client.textRenderer.getWidth(text) * progress)), y + i * 12 + 11);
                    context.drawTextWithShadow(client.textRenderer, text, x, y + i * 12 + dy, highlightColor.get());
                    context.disableScissor();
                }
            }
        }
    }

    private static float lyricProgress(SyncedLyrics lyrics, int index, long position) {
        SyncedLyrics.Line line = lyrics.line(index);
        if (!line.wordTimes().isEmpty()) {
            int active = 0;
            for (long time : line.wordTimes()) if (position >= time) active++;
            return Math.min(1, (float) active / line.wordTimes().size());
        }
        long end = index + 1 < lyrics.size() ? lyrics.line(index + 1).timeMs() : line.timeMs() + 3000;
        return Math.max(0, Math.min(1, (float) (position - line.timeMs()) / Math.max(1, end - line.timeMs())));
    }

    private void updateArt(MinecraftClient client, AlbumArt art) {
        if (art == displayedArt) return;
        releaseArt(client);
        if (art == null) return;
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Spotify album art", art.size(), art.size(), false);
        NativeImage image = texture.getImage();
        if (image == null) { texture.close(); return; }
        ByteBuffer pixels = ByteBuffer.allocate(art.byteSize());
        art.copyTo(pixels);
        byte[] rgba = pixels.array();
        for (int y = 0; y < art.size(); y++) for (int x = 0; x < art.size(); x++) {
            int at = (y * art.size() + x) * 4;
            int color = ((rgba[at + 3] & 255) << 24) | ((rgba[at] & 255) << 16)
                    | ((rgba[at + 1] & 255) << 8) | (rgba[at + 2] & 255);
            image.setColorArgb(x, y, color);
        }
        texture.upload();
        client.getTextureManager().registerTexture(ART_ID, texture);
        artTexture = texture;
        displayedArt = art;
    }

    private void releaseArt(MinecraftClient client) {
        if (artTexture != null) client.getTextureManager().destroyTexture(ART_ID);
        artTexture = null;
        displayedArt = null;
    }

    private final class Worker {
        private PlayerSource source;
        private SpotifyWindow window;
        private long lastWindowPoll;
        private int artVersion = -1;

        private void poll() {
            if (worker != this || !isEnabled()) return;
            try {
                if (source == null) source = PlayerSource.spotify();
                MediaSession.Snapshot snapshot = source.poll();
                if (snapshot != null && !snapshot.title().isBlank()) {
                    NowPlaying old = playing;
                    boolean sameTrack = old.title().equals(snapshot.title())
                            && old.artist().equals(snapshot.artist()) && old.album().equals(snapshot.album());
                    AlbumArt art = sameTrack ? old.art() : null;
                    if (snapshot.hasThumbnail() && (artVersion != snapshot.thumbnailVersion() || art == null)) {
                        art = AlbumArt.decode(snapshot.thumbnail(), 128);
                        artVersion = snapshot.thumbnailVersion();
                    }
                    if (worker == this) playing = NowPlaying.fromSession(snapshot, art, old);
                    return;
                }
                if (Platform.isWindows()) {
                    long now = System.currentTimeMillis();
                    if (now - lastWindowPoll >= 1000) {
                        if (window == null) window = new SpotifyWindow();
                        lastWindowPoll = now;
                        if (worker == this) playing = NowPlaying.fromWindowTitle(window.readTitle(), playing);
                    }
                } else if (snapshot == null && worker == this) {
                    playing = NowPlaying.NOT_RUNNING;
                }
            } catch (LinkageError | RuntimeException failure) {
                if (worker == this) playing = NowPlaying.UNSUPPORTED;
            }
        }

        private void control(int action) {
            boolean accepted = source != null && switch (action) {
                case 0 -> source.togglePlayPause();
                case 1 -> source.skipNext();
                default -> source.skipPrevious();
            };
            if (!accepted && Platform.isWindows()) SpotifyWindow.tap(switch (action) {
                case 0 -> SpotifyWindow.MediaKey.PLAY_PAUSE;
                case 1 -> SpotifyWindow.MediaKey.NEXT;
                default -> SpotifyWindow.MediaKey.PREVIOUS;
            });
        }

        private void close() {
            if (source != null) source.close();
            source = null;
        }
    }
}
