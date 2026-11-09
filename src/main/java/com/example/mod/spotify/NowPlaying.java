
package com.example.mod.spotify;

import com.example.mod.spotify.AlbumArt;
import com.example.mod.spotify.MediaSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record NowPlaying(Status status, String title, String artist, String album, Source source, long durationMs, long positionMs, long sampledAtMs, double rate, AlbumArt art) {
    private static final String SEPARATOR = " - ";
    static final long LYRICS_MIN_LENGTH_MS = 30000L;
    static final long LYRICS_MAX_LENGTH_MS = 3600000L;
    public static final NowPlaying UNSUPPORTED = NowPlaying.idle(Status.UNSUPPORTED);
    public static final NowPlaying WAITING = NowPlaying.idle(Status.WAITING);
    public static final NowPlaying NOT_RUNNING = NowPlaying.idle(Status.NOT_RUNNING);

    public NowPlaying {
        if (status == null) {
            status = Status.NOT_RUNNING;
        }
        if (source == null) {
            source = Source.NONE;
        }
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        durationMs = Math.max(0L, durationMs);
        long l = positionMs = durationMs > 0L ? Math.max(0L, Math.min(positionMs, durationMs)) : Math.max(0L, positionMs);
        if (!(rate > 0.0) || rate > 16.0) {
            rate = 1.0;
        }
    }

    public NowPlaying(Status status, String title, String artist) {
        this(status, title, artist, "", Source.WINDOW, 0L, 0L, 0L, 1.0, null);
    }

    private static NowPlaying idle(Status status) {
        return new NowPlaying(status, "", "", "", Source.NONE, 0L, 0L, 0L, 1.0, null);
    }

    public boolean playing() {
        return this.status == Status.PLAYING;
    }

    public boolean hasTrack() {
        return !this.title.isEmpty();
    }

    public boolean active() {
        return this.status == Status.PLAYING || this.status == Status.PAUSED;
    }

    public boolean hasTimeline() {
        return this.source == Source.SESSION && this.durationMs > 0L;
    }

    public boolean lyricsEligible() {
        if (!this.hasTimeline() || !this.hasTrack() || this.artist.isBlank()) {
            return false;
        }
        if (this.durationMs < 30000L || this.durationMs > 3600000L) {
            return false;
        }
        return !this.artist.equalsIgnoreCase("Spotify") && !this.title.equalsIgnoreCase("Advertisement");
    }

    public boolean sameTrack(NowPlaying other) {
        return other != null && this.title.equals(other.title) && this.artist.equals(other.artist) && this.album.equals(other.album);
    }

    public long positionAt(long nowMs) {
        if (!this.hasTimeline()) {
            return 0L;
        }
        if (!this.playing()) {
            return this.positionMs;
        }
        long advanced = this.positionMs + (long)((double)Math.max(0L, nowMs - this.sampledAtMs) * this.rate);
        return Math.min(advanced, this.durationMs);
    }

    public NowPlaying toggled() {
        long now = System.currentTimeMillis();
        return switch (this.status.ordinal()) {
            case 4 -> new NowPlaying(Status.PAUSED, this.title, this.artist, this.album, this.source, this.durationMs, this.positionAt(now), now, this.rate, this.art);
            case 3 -> {
                if (this.hasTrack()) {
                    yield new NowPlaying(Status.PLAYING, this.title, this.artist, this.album, this.source, this.durationMs, this.positionMs, now, this.rate, this.art);
                }
                yield this;
            }
            default -> this;
        };
    }

    public static NowPlaying fromSession(MediaSession.Snapshot snapshot, AlbumArt art, NowPlaying previous) {
        Status status = switch (snapshot.state()) {
            case PLAYING -> Status.PLAYING;
            case CLOSED -> Status.NOT_RUNNING;
            case CHANGING -> previous != null && previous.source == Source.SESSION && previous.playing()
                    ? Status.PLAYING : Status.PAUSED;
            default -> Status.PAUSED;
        };
        if (status == Status.NOT_RUNNING) {
            return NOT_RUNNING;
        }
        return new NowPlaying(status, snapshot.title(), snapshot.artist(), snapshot.album(), Source.SESSION, snapshot.durationMs(), snapshot.positionMs(), snapshot.sampledAtMs(), snapshot.rate(), art);
    }

    public static NowPlaying fromWindowTitle(String windowTitle, NowPlaying previous) {
        if (windowTitle == null) {
            return NOT_RUNNING;
        }
        String title = windowTitle.strip();
        if (title.isEmpty()) {
            return NOT_RUNNING;
        }
        int split = title.indexOf(SEPARATOR);
        if (split < 0 && (title.equals("Spotify") || title.startsWith("Spotify "))) {
            return previous != null && previous.hasTrack() ? new NowPlaying(Status.PAUSED, previous.title, previous.artist) : new NowPlaying(Status.PAUSED, "", "");
        }
        if (split > 0) {
            return new NowPlaying(Status.PLAYING, title.substring(split + SEPARATOR.length()).strip(), title.substring(0, split).strip());
        }
        return new NowPlaying(Status.PLAYING, title, "");
    }

    @Environment(value=EnvType.CLIENT)
    public static enum Status {
        UNSUPPORTED,
        WAITING,
        NOT_RUNNING,
        PAUSED,
        PLAYING;

    }

    @Environment(value=EnvType.CLIENT)
    public static enum Source {
        NONE,
        WINDOW,
        SESSION;

    }
}
