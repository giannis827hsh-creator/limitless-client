
package com.example.mod.spotify;

import java.io.IOException;
import java.util.Optional;
import com.example.mod.spotify.MediaSession;
import com.example.mod.spotify.ScriptedPlayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class MacSpotify
extends ScriptedPlayer {
    private static final long TIMEOUT_MS = 2000L;
    private static final String APP_BINARY = "/Contents/MacOS/Spotify";
    private static final String NOT_RUNNING = "not running";
    private static final String STOPPED = "stopped";
    private static final char FIELD = '\u001f';
    private static final int FIELDS = 7;
    private static final String[] READ = new String[]{"osascript", "-e", "if application \"Spotify\" is not running then return \"not running\"", "-e", "tell application \"Spotify\"", "-e", "set s to (player state) as text", "-e", "if s is \"stopped\" then return \"stopped\"", "-e", "set t to current track", "-e", "set d to character id 31", "-e", "return s & d & (name of t) & d & (artist of t) & d & (album of t) & d & (duration of t) & d & (player position) & d & (artwork url of t)", "-e", "end tell"};

    MacSpotify() {
    }

    @Override
    protected ScriptedPlayer.Reading read() throws IOException, InterruptedException {
        if (!MacSpotify.appRunning()) {
            return null;
        }
        String reply = MacSpotify.run(2000L, READ).strip();
        if (reply.equals(NOT_RUNNING)) {
            return null;
        }
        if (reply.equals(STOPPED)) {
            return new ScriptedPlayer.Reading(MediaSession.PlaybackState.STOPPED, "", "", "", 0L, 0L, "");
        }
        String[] parts = reply.split(String.valueOf('\u001f'), -1);
        if (parts.length < 7) {
            throw new IOException("unexpected Spotify reply: " + reply);
        }
        MediaSession.PlaybackState state = parts[0].equals("playing") ? MediaSession.PlaybackState.PLAYING : MediaSession.PlaybackState.PAUSED;
        return new ScriptedPlayer.Reading(state, parts[1].strip(), parts[2].strip(), parts[3].strip(), Math.max(0L, (long)MacSpotify.number(parts[4])), Math.max(0L, Math.round(MacSpotify.number(parts[5]) * 1000.0)), parts[6].strip());
    }

    @Override
    protected boolean send(ScriptedPlayer.Command command) throws IOException, InterruptedException {
        if (!MacSpotify.appRunning()) {
            return false;
        }
        String verb = switch (command) {
            default -> throw new MatchException(null, null);
            case ScriptedPlayer.Command.PLAY_PAUSE -> "playpause";
            case ScriptedPlayer.Command.NEXT -> "next track";
            case ScriptedPlayer.Command.PREVIOUS -> "previous track";
        };
        MacSpotify.run(2000L, "osascript", "-e", "if application \"Spotify\" is running then tell application \"Spotify\" to " + verb);
        return true;
    }

    private static boolean appRunning() {
        return ProcessHandle.allProcesses().anyMatch(process -> {
            Optional<String> command = process.info().command();
            return command.isPresent() && command.get().endsWith(APP_BINARY);
        });
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.strip().replace(',', '.'));
        }
        catch (NumberFormatException unreadable) {
            return 0.0;
        }
    }
}
