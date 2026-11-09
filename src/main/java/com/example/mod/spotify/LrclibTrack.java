
package com.example.mod.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record LrclibTrack(long id, String trackName, String artistName, String albumName, double duration, boolean instrumental, String plainLyrics, String syncedLyrics) {
    public LrclibTrack {
        trackName = LrclibTrack.nonNull(trackName);
        artistName = LrclibTrack.nonNull(artistName);
        albumName = LrclibTrack.nonNull(albumName);
        plainLyrics = LrclibTrack.nonNull(plainLyrics);
        syncedLyrics = LrclibTrack.nonNull(syncedLyrics);
        if (!Double.isFinite(duration) || duration < 0.0) {
            duration = 0.0;
        }
    }

    public boolean hasSynced() {
        return !this.syncedLyrics.isBlank();
    }

    public boolean hasPlain() {
        return !this.plainLyrics.isBlank();
    }

    public boolean usable() {
        return this.instrumental || this.hasSynced() || this.hasPlain();
    }

    public static LrclibTrack fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        return new LrclibTrack(LrclibTrack.number(object, "id", 0L), LrclibTrack.text(object, "trackName"), LrclibTrack.text(object, "artistName"), LrclibTrack.text(object, "albumName"), LrclibTrack.number(object, "duration", 0.0), LrclibTrack.bool(object, "instrumental"), LrclibTrack.text(object, "plainLyrics"), LrclibTrack.text(object, "syncedLyrics"));
    }

    public static List<LrclibTrack> listFromJson(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        ArrayList<LrclibTrack> tracks = new ArrayList<LrclibTrack>(array.size());
        for (JsonElement item : array) {
            LrclibTrack track = LrclibTrack.fromJson(item);
            if (track == null) continue;
            tracks.add(track);
        }
        return List.copyOf(tracks);
    }

    public static JsonElement parse(String json) {
        return JsonParser.parseString((String)json);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", (Number)this.id);
        object.addProperty("trackName", this.trackName);
        object.addProperty("artistName", this.artistName);
        object.addProperty("albumName", this.albumName);
        object.addProperty("duration", (Number)this.duration);
        object.addProperty("instrumental", Boolean.valueOf(this.instrumental));
        object.addProperty("plainLyrics", this.plainLyrics);
        object.addProperty("syncedLyrics", this.syncedLyrics);
        return object;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static String text(JsonObject object, String name) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive && (primitive = (JsonPrimitive)value).isString() ? primitive.getAsString() : "";
    }

    private static long number(JsonObject object, String name, long fallback) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        if (!(value instanceof JsonPrimitive) || !(primitive = (JsonPrimitive)value).isNumber()) {
            return fallback;
        }
        try {
            return primitive.getAsLong();
        }
        catch (NumberFormatException notInteger) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String name, double fallback) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive && (primitive = (JsonPrimitive)value).isNumber() ? primitive.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject object, String name) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive && (primitive = (JsonPrimitive)value).isBoolean() && primitive.getAsBoolean();
    }
}
