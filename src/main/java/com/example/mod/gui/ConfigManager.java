package com.example.mod.gui;

import com.example.mod.LimitlessClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public final class ConfigManager {
    private static final String SHARE_PREFIX = "LIMITLESS-CONFIG-1:";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("limitless/configs");

    private ConfigManager() { }

    public static List<String> names() throws IOException {
        if (!Files.isDirectory(DIRECTORY)) return List.of();
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .map(name -> name.substring(0, name.length() - ".properties".length()))
                    .filter(ConfigManager::validName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    public static void save(String name) throws IOException {
        Path path = path(name);
        Properties config = GuiState.INSTANCE.snapshot(LimitlessClient.MODULES);
        Files.createDirectories(DIRECTORY);
        Path temporary = Files.createTempFile(DIRECTORY, "config-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { config.store(output, "limitless config"); }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void load(String name, MinecraftClient client) throws IOException {
        Path path = path(name);
        if (Files.size(path) > MAX_BYTES) throw new IOException("Config is too large");
        Properties config = new Properties();
        try (var input = Files.newInputStream(path)) { config.load(input); }
        GuiState.INSTANCE.applyConfig(config, LimitlessClient.MODULES, client);
    }

    public static String share(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(path(name));
        if (bytes.length > MAX_BYTES) throw new IOException("Config is too large");
        return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static void importShared(String name, String code) throws IOException {
        Path path = path(name);
        String value = code.strip();
        if (!value.startsWith(SHARE_PREFIX) || value.length() > MAX_BYTES * 2)
            throw new IOException("Paste a limitless config code");
        byte[] bytes;
        try { bytes = Base64.getUrlDecoder().decode(value.substring(SHARE_PREFIX.length())); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid config code", exception); }
        if (bytes.length > MAX_BYTES) throw new IOException("Config is too large");
        Properties config = new Properties();
        try (var input = new ByteArrayInputStream(bytes)) { config.load(input); }
        Properties filtered = new Properties();
        for (String key : config.stringPropertyNames()) {
            if (key.startsWith("bind.") || key.startsWith("enabled.") || key.startsWith("setting."))
                filtered.setProperty(key, config.getProperty(key));
        }
        if (filtered.isEmpty()) throw new IOException("No module settings in this code");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        filtered.store(output, "limitless shared config");
        Files.createDirectories(DIRECTORY);
        Files.write(path, output.toByteArray());
    }

    public static void delete(String name) throws IOException { Files.deleteIfExists(path(name)); }

    private static Path path(String name) throws IOException {
        if (!validName(name)) throw new IOException("Use 1–32 letters, numbers, spaces, _ or - for the name");
        return DIRECTORY.resolve(name + ".properties");
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,31}");
    }
}
