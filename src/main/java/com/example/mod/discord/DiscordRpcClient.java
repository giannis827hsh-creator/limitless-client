package com.example.mod.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Locale;

public final class DiscordRpcClient {
    private static final int FRAME = 1;
    private static final int CLOSE = 2;
    private static final int PING = 3;
    private static final int PONG = 4;
    private static final int MAX_FRAME = 262_144;

    private volatile boolean running;
    private volatile String applicationId = "";
    private volatile String state = "In menus";
    private volatile String image = "limitless";
    private volatile Connection connection;
    private volatile boolean active;
    private volatile String lastError;
    private Thread worker;

    public synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::run, "limitless-discord-presence");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        active = false;
        if (worker != null) worker.interrupt();
        closeConnection();
        worker = null;
    }

    public void update(String applicationId, String state, String image) {
        this.applicationId = applicationId == null ? "" : applicationId.trim();
        this.state = state == null ? "In menus" : state;
        this.image = image == null ? "" : image.trim();
    }

    public boolean isActive() { return active; }
    public String lastError() { return lastError; }

    private void run() {
        while (running && worker == Thread.currentThread()) {
            String id = applicationId;
            if (!id.matches("[0-9]{17,20}")) {
                pause(1000);
                continue;
            }
            try (Connection pipe = connect()) {
                connection = pipe;
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", id);
                pipe.write(0, hello.toString());
                JsonObject ready = pipe.read();
                if (!"READY".equals(ready.has("evt") && !ready.get("evt").isJsonNull()
                        ? ready.get("evt").getAsString() : ""))
                    throw new IOException("Discord handshake failed");
                String lastActivity = "";
                long lastSent = 0;
                boolean useImage = true;
                while (running && worker == Thread.currentThread() && id.equals(applicationId)) {
                    JsonObject activity = new JsonObject();
                    activity.addProperty("details", "limitless client");
                    activity.addProperty("state", state);
                    if (useImage && !image.isBlank()) {
                        JsonObject assets = new JsonObject();
                        assets.addProperty("large_image", image);
                        assets.addProperty("large_text", "limitless client");
                        activity.add("assets", assets);
                    }
                    String payload = activity.toString();
                    if (!payload.equals(lastActivity) || System.nanoTime() - lastSent > 15_000_000_000L) {
                        JsonObject args = new JsonObject();
                        args.addProperty("pid", ProcessHandle.current().pid());
                        args.add("activity", activity);
                        JsonObject request = new JsonObject();
                        request.addProperty("cmd", "SET_ACTIVITY");
                        request.add("args", args);
                        request.addProperty("nonce", Long.toString(System.nanoTime()));
                        pipe.write(FRAME, request.toString());
                        JsonObject reply = pipe.read();
                        if (reply.has("evt") && !reply.get("evt").isJsonNull()
                                && "ERROR".equals(reply.get("evt").getAsString())) {
                            if (useImage && !image.isBlank()) {
                                useImage = false;
                                continue;
                            }
                            throw new IOException("Discord rejected presence");
                        }
                        lastActivity = payload;
                        lastSent = System.nanoTime();
                        active = true;
                        lastError = null;
                    }
                    pause(1000);
                }
            } catch (IOException | RuntimeException error) {
                lastError = error.toString();
                pause(5000);
            } finally {
                active = false;
                if (worker == Thread.currentThread()) connection = null;
            }
        }
    }

    private static Connection connect() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            IOException failure = null;
            for (int i = 0; i < 10; i++) {
                try {
                    RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Connection(pipe, pipe, pipe);
                } catch (IOException e) {
                    failure = e;
                }
            }
            throw failure == null ? new IOException("Discord IPC unavailable") : failure;
        }
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime == null || runtime.isBlank()) runtime = System.getenv("TMPDIR");
        if (runtime == null || runtime.isBlank()) runtime = "/tmp";
        IOException failure = null;
        for (int i = 0; i < 10; i++) {
            try {
                SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                channel.connect(UnixDomainSocketAddress.of(Path.of(runtime, "discord-ipc-" + i)));
                return new Connection(new java.io.DataInputStream(Channels.newInputStream(channel)),
                        new java.io.DataOutputStream(Channels.newOutputStream(channel)), channel);
            } catch (IOException e) {
                failure = e;
            }
        }
        throw failure == null ? new IOException("Discord IPC unavailable") : failure;
    }

    private void closeConnection() {
        Connection pipe = connection;
        if (pipe != null) {
            try { pipe.close(); } catch (IOException ignored) { }
        }
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private record Connection(DataInput input, DataOutput output, Closeable handle) implements Closeable {
        void write(int opcode, String json) throws IOException {
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.writeInt(Integer.reverseBytes(opcode));
            output.writeInt(Integer.reverseBytes(bytes.length));
            output.write(bytes);
        }

        JsonObject read() throws IOException {
            while (true) {
                int opcode = Integer.reverseBytes(input.readInt());
                int length = Integer.reverseBytes(input.readInt());
                if (length < 0 || length > MAX_FRAME) throw new IOException("Invalid Discord frame");
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                String message = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (opcode == CLOSE) throw new IOException("Discord closed IPC");
                if (opcode == PING) {
                    write(PONG, message);
                    continue;
                }
                if (opcode != FRAME) throw new IOException("Invalid Discord opcode");
                return JsonParser.parseString(message).getAsJsonObject();
            }
        }

        @Override public void close() throws IOException { handle.close(); }
    }
}
