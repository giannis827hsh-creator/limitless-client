package com.example.mod.module.antivanish;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Only responses to our own fresh request can contribute identity evidence. */
public final class CompletionProbe {
    public record Ticket(int id, Map<String, UUID> candidates, long sentAt) {
        public Ticket { candidates = Map.copyOf(candidates); }
    }
    public record Reply(boolean owned, Map<String, UUID> matches) {
        public Reply { matches = Map.copyOf(matches); }
    }
    private int nextId = 1_600_000_000;
    private long nextAllowed;
    private Ticket active;
    private final ArrayDeque<Integer> retired = new ArrayDeque<>();
    public boolean owns(int id) { return active != null && active.id == id || retired.contains(id); }
    public Ticket begin(Map<String, UUID> candidates, long now, long interval) {
        if (active != null && now - active.sentAt <= 3_000) return null;
        retire();
        if (candidates.isEmpty() || now < nextAllowed) return null;
        if (nextId == Integer.MAX_VALUE) nextId = 1_600_000_000;
        active = new Ticket(nextId++, candidates, now);
        nextAllowed = now + Math.max(5_000, interval);
        return active;
    }
    public Reply accept(int id, Collection<String> names, long now) {
        if (active == null || active.id != id) return new Reply(retired.contains(id), Map.of());
        Ticket ticket = active;
        retire();
        Map<String, UUID> found = new LinkedHashMap<>();
        if (now - ticket.sentAt >= 0 && now - ticket.sentAt <= 3_000) {
            for (String name : names) {
                String normalized = StaffText.normalize(name);
                UUID uuid = ticket.candidates.get(normalized);
                if (uuid != null) found.put(normalized, uuid);
            }
        }
        return new Reply(true, found);
    }
    public void reset(long now) { retire(); nextAllowed = now + 5_000; }
    private void retire() {
        if (active == null) return;
        retired.addLast(active.id); active = null;
        while (retired.size() > 16) retired.removeFirst();
    }
}
