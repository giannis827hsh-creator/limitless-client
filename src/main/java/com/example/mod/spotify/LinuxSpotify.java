
package com.example.mod.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.mod.spotify.MediaSession;
import com.example.mod.spotify.ScriptedPlayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class LinuxSpotify
extends ScriptedPlayer {
    private static final long TIMEOUT_MS = 2000L;
    private static final long LOOKUP_GAP_MS = 2000L;
    private static final String BUS_PREFIX = "org.mpris.MediaPlayer2.spotify";
    private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER = "org.mpris.MediaPlayer2.Player";
    private String busName = "org.mpris.MediaPlayer2.spotify";
    private long nextLookupAt;

    LinuxSpotify() {
    }

    @Override
    protected ScriptedPlayer.Reading read() throws IOException, InterruptedException {
        String status;
        Map map;
        String reply;
        String name = this.busName(false);
        if (name == null) {
            return null;
        }
        try {
            reply = LinuxSpotify.run(2000L, "dbus-send", "--session", "--print-reply", "--dest=" + name, OBJECT_PATH, "org.freedesktop.DBus.Properties.GetAll", "string:org.mpris.MediaPlayer2.Player");
        }
        catch (ScriptedPlayer.CommandFailed failure) {
            if (!LinuxSpotify.gone(failure)) {
                throw failure;
            }
            this.busName = null;
            return null;
        }
        Object object = DbusReply.parse(reply);
        if (!(object instanceof Map)) {
            throw new IOException("unexpected MPRIS reply");
        }
        Map properties = (Map)object;
        Object v = properties.get("Metadata");
        Map metadata = v instanceof Map ? (map = (Map)v) : Map.of();
        MediaSession.PlaybackState state = switch (status = LinuxSpotify.text(properties.get("PlaybackStatus"))) {
            case "Playing" -> MediaSession.PlaybackState.PLAYING;
            case "Paused" -> MediaSession.PlaybackState.PAUSED;
            default -> MediaSession.PlaybackState.STOPPED;
        };
        return new ScriptedPlayer.Reading(state, LinuxSpotify.text(metadata.get("xesam:title")), LinuxSpotify.text(metadata.get("xesam:artist")), LinuxSpotify.text(metadata.get("xesam:album")), LinuxSpotify.micros(metadata.get("mpris:length")), LinuxSpotify.micros(properties.get("Position")), LinuxSpotify.text(metadata.get("mpris:artUrl")));
    }

    @Override
    protected boolean send(ScriptedPlayer.Command command) throws IOException, InterruptedException {
        String name = this.busName(true);
        if (name == null) {
            return false;
        }
        String method = switch (command) {
            default -> throw new MatchException(null, null);
            case ScriptedPlayer.Command.PLAY_PAUSE -> "PlayPause";
            case ScriptedPlayer.Command.NEXT -> "Next";
            case ScriptedPlayer.Command.PREVIOUS -> "Previous";
        };
        try {
            LinuxSpotify.run(2000L, "dbus-send", "--session", "--print-reply", "--dest=" + name, OBJECT_PATH, "org.mpris.MediaPlayer2.Player." + method);
        }
        catch (ScriptedPlayer.CommandFailed failure) {
            if (!LinuxSpotify.gone(failure)) {
                throw failure;
            }
            this.busName = null;
            return false;
        }
        return true;
    }

    private String busName(boolean now) throws IOException, InterruptedException {
        if (this.busName != null) {
            return this.busName;
        }
        long time = System.currentTimeMillis();
        if (!now && time < this.nextLookupAt) {
            return null;
        }
        this.nextLookupAt = time + 2000L;
        String reply = LinuxSpotify.run(2000L, "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
        Object parsed = DbusReply.parse(reply);
        if (parsed instanceof List) {
            List names = (List) parsed;
            for (Object entry : names) {
                String candidate;
                if (!(entry instanceof String) || !(candidate = (String)entry).startsWith(BUS_PREFIX)) continue;
                this.busName = candidate;
                return candidate;
            }
        }
        return null;
    }

    private static boolean gone(ScriptedPlayer.CommandFailed failure) {
        String message = failure.getMessage();
        return message != null && (message.contains("ServiceUnknown") || message.contains("NameHasNoOwner"));
    }

    private static String text(Object value) {
        if (value instanceof String) {
            String string = (String)value;
            return string.strip();
        }
        if (value instanceof List) {
            List list = (List)value;
            ArrayList<String> parts = new ArrayList<String>();
            for (Object entry : list) {
                String part = LinuxSpotify.text(entry);
                if (part.isEmpty()) continue;
                parts.add(part);
            }
            return String.join((CharSequence)", ", parts);
        }
        return "";
    }

    private static long micros(Object value) {
        long l;
        if (value instanceof Number) {
            Number number = (Number)value;
            l = Math.max(0L, number.longValue() / 1000L);
        } else {
            l = 0L;
        }
        return l;
    }

    @Environment(value=EnvType.CLIENT)
    static final class DbusReply {
        private final String[] lines;
        private int at;

        private DbusReply(String[] lines) {
            this.lines = lines;
        }

        static Object parse(String output) {
            String[] lines = output.strip().split("\\R");
            DbusReply reply = new DbusReply(lines);
            if (lines.length > 0 && lines[0].startsWith("method return")) {
                reply.at = 1;
            }
            return reply.at < lines.length ? reply.value(reply.next()) : null;
        }

        private String next() {
            return this.at < this.lines.length ? this.lines[this.at++].strip() : "]";
        }

        private Object value(String line) {
            if (line.startsWith("variant")) {
                line = line.substring("variant".length()).strip();
            }
            if (line.equals("array [")) {
                return this.array();
            }
            if (line.startsWith("string ") || line.startsWith("object path ")) {
                return DbusReply.unquote(line);
            }
            int space = line.lastIndexOf(32);
            String type = space < 0 ? line : line.substring(0, space);
            String raw = space < 0 ? "" : line.substring(space + 1);
            try {
                return switch (type) {
                    case "int64", "uint64", "int32", "uint32", "int16", "uint16", "byte" -> Long.parseLong(raw);
                    case "double" -> Double.parseDouble(raw);
                    case "boolean" -> Boolean.parseBoolean(raw);
                    default -> line;
                };
            }
            catch (NumberFormatException unreadable) {
                return line;
            }
        }

        private Object array() {
            String line;
            ArrayList<Object> items = new ArrayList<Object>();
            LinkedHashMap<String, Object> entries = new LinkedHashMap<String, Object>();
            while (this.at < this.lines.length && !(line = this.next()).equals("]")) {
                if (line.equals("dict entry(")) {
                    Object key = this.value(this.next());
                    Object value = this.value(this.next());
                    this.next();
                    if (!(key instanceof String)) continue;
                    String name = (String)key;
                    entries.put(name, value);
                    continue;
                }
                items.add(this.value(line));
            }
            return entries.isEmpty() ? items : entries;
        }

        private static String unquote(String line) {
            int first = line.indexOf(34);
            int last = line.lastIndexOf(34);
            return first >= 0 && last > first ? line.substring(first + 1, last).replace("\\\"", "\"") : "";
        }
    }
}
