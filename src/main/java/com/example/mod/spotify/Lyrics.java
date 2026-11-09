
package com.example.mod.spotify;

import java.util.ArrayList;
import java.util.List;
import com.example.mod.spotify.LrclibTrack;
import com.example.mod.spotify.SyncedLyrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record Lyrics(Status status, SyncedLyrics synced, List<String> plainLines, long sourceId) {
    public static final Lyrics LOADING = new Lyrics(Status.LOADING, SyncedLyrics.EMPTY, List.of(), 0L);
    public static final Lyrics NOT_FOUND = new Lyrics(Status.NOT_FOUND, SyncedLyrics.EMPTY, List.of(), 0L);
    public static final Lyrics UNAVAILABLE = new Lyrics(Status.UNAVAILABLE, SyncedLyrics.EMPTY, List.of(), 0L);

    public Lyrics {
        if (status == null) {
            status = Status.NOT_FOUND;
        }
        if (synced == null) {
            synced = SyncedLyrics.EMPTY;
        }
        plainLines = plainLines == null ? List.of() : List.copyOf(plainLines);
    }

    public static Lyrics of(LrclibTrack track) {
        if (track == null) {
            return NOT_FOUND;
        }
        SyncedLyrics synced = SyncedLyrics.parse(track.syncedLyrics());
        List<String> plain = Lyrics.plainLines(track.plainLyrics());
        if (plain.isEmpty() && synced.isEmpty()) {
            plain = Lyrics.plainLines(track.syncedLyrics());
        }
        if (!synced.isEmpty() && !synced.texts().isEmpty()) {
            return new Lyrics(Status.SYNCED, synced, plain.isEmpty() ? synced.texts() : plain, track.id());
        }
        if (!plain.isEmpty()) {
            return new Lyrics(Status.PLAIN, SyncedLyrics.EMPTY, plain, track.id());
        }
        if (track.instrumental() || synced.size() > 0) {
            return new Lyrics(Status.INSTRUMENTAL, SyncedLyrics.EMPTY, List.of(), track.id());
        }
        return NOT_FOUND;
    }

    static List<String> plainLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ArrayList<String> lines = new ArrayList<String>();
        boolean gap = false;
        for (String raw : text.split("\r\n|\r|\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                gap = !lines.isEmpty();
                continue;
            }
            if (gap) {
                lines.add("");
            }
            gap = false;
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    @Environment(value=EnvType.CLIENT)
    public static enum Status {
        LOADING,
        SYNCED,
        PLAIN,
        INSTRUMENTAL,
        NOT_FOUND,
        UNAVAILABLE;

    }
}
