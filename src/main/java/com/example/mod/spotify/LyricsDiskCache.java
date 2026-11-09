
package com.example.mod.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;
import com.example.mod.spotify.LrclibTrack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class LyricsDiskCache {
    private static final int FORMAT = 1;
    private static final String SUFFIX = ".json";
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{16,64}");
    private static final long MAX_FILE_BYTES = 524288L;
    private final Path dir;
    private final int maxFiles;
    private final long maxBytes;

    LyricsDiskCache(Path dir, int maxFiles, long maxBytes) {
        this.dir = dir;
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
    }

    Entry read(String key) {
        Path file = this.file(key);
        if (file == null) {
            return null;
        }
        try {
            if (Files.size(file) > 524288L) {
                return null;
            }
            JsonElement root = JsonParser.parseString((String)Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            if (LyricsDiskCache.intValue(object, "format") != 1) {
                return null;
            }
            long fetchedAt = LyricsDiskCache.longValue(object, "fetchedAt");
            if (fetchedAt <= 0L) {
                return null;
            }
            LrclibTrack track = object.has("track") ? LrclibTrack.fromJson(object.get("track")) : null;
            LyricsDiskCache.touch(file);
            return new Entry(track, fetchedAt);
        }
        catch (JsonParseException | IOException | IllegalStateException | UnsupportedOperationException missingOrBroken) {
            return null;
        }
    }

    void write(String key, LrclibTrack track, long fetchedAt) {
        Path file = this.file(key);
        if (file == null) {
            return;
        }
        JsonObject object = new JsonObject();
        object.addProperty("format", (Number)1);
        object.addProperty("fetchedAt", (Number)fetchedAt);
        if (track != null) {
            object.add("track", (JsonElement)track.toJson());
        }
        Path tmp = file.resolveSibling(String.valueOf(file.getFileName()) + ".tmp");
        try {
            Files.createDirectories(this.dir, new FileAttribute[0]);
            Files.writeString(tmp, (CharSequence)object.toString(), StandardCharsets.UTF_8, new OpenOption[0]);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException atomicFail) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException failure) {
            LyricsDiskCache.delete(tmp);
        }
    }

    void prune() {
        if (!Files.isDirectory(this.dir, new LinkOption[0])) {
            return;
        }
        ArrayList<Stored> stored = new ArrayList<Stored>();
        long total = 0L;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir);){
            for (Path file : files) {
                BasicFileAttributes attributes;
                String name = file.getFileName().toString();
                if (name.endsWith(".json.tmp")) {
                    LyricsDiskCache.delete(file);
                    continue;
                }
                if (!name.endsWith(SUFFIX) || !KEY.matcher(name.substring(0, name.length() - SUFFIX.length())).matches() || !(attributes = Files.readAttributes(file, BasicFileAttributes.class, new LinkOption[0])).isRegularFile()) continue;
                stored.add(new Stored(file, attributes.size(), attributes.lastModifiedTime().toMillis()));
                total += attributes.size();
            }
        }
        catch (IOException failure) {
            return;
        }
        if (stored.size() <= this.maxFiles && total <= this.maxBytes) {
            return;
        }
        stored.sort(Comparator.comparingLong(Stored::usedAt));
        int count = stored.size();
        for (Stored entry : stored) {
            if (count <= this.maxFiles && total <= this.maxBytes) break;
            if (!LyricsDiskCache.delete(entry.file())) continue;
            --count;
            total -= entry.size();
        }
    }

    private Path file(String key) {
        return key != null && KEY.matcher(key).matches() ? this.dir.resolve(key + SUFFIX) : null;
    }

    private static boolean delete(Path file) {
        try {
            Files.deleteIfExists(file);
            return true;
        }
        catch (IOException inUse) {
            return false;
        }
    }

    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        }
        catch (IOException iOException) {
        }
    }

    private static int intValue(JsonObject object, String name) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive && (primitive = (JsonPrimitive)value).isNumber() ? primitive.getAsInt() : -1;
    }

    private static long longValue(JsonObject object, String name) {
        JsonPrimitive primitive;
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive && (primitive = (JsonPrimitive)value).isNumber() ? primitive.getAsLong() : -1L;
    }

    @Environment(value=EnvType.CLIENT)
    record Entry(LrclibTrack track, long fetchedAt) {
        boolean found() {
            return this.track != null;
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record Stored(Path file, long size, long usedAt) {
    }
}
