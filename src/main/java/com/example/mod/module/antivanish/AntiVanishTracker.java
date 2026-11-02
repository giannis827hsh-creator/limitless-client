package com.example.mod.module.antivanish;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-thread evidence tracker. Scores express support, never probability or a person's intent. */
public final class AntiVanishTracker {
    public enum Signal {
        TAB_ABSENT(25), BODY_PRESENT(40), INVISIBLE(20), COMPLETION(35), SPECTATOR(10), SOUND(8), PARTICLE(6);
        final int weight;
        Signal(int weight) { this.weight = weight; }
    }
    public enum Level { NOTICE, CORROBORATED }
    public enum ChangeType { VISIBLE_AGAIN, CONFIRMED_LEFT }
    public record Change(UUID id, String name, ChangeType type, long at) { }
    public record Policy(boolean tab, boolean invisible, boolean spectator, int threshold, int range,
                         long warmup, long disappearanceGrace, long evidenceWindow) { }
    public record Observation(UUID id, String name, boolean staff, boolean listed, boolean entryPresent,
                              boolean body, boolean invisible, boolean potion, boolean spectator, double distance) { }
    public record Finding(UUID id, String name, boolean staff, int score, Level level, boolean nearby, double distance,
                          String reason, Set<Signal> signals, long episode) {
        public Finding { signals = Set.copyOf(signals); }
    }
    private static final int MAX_TRACKED = 4_096;
    private static final long TRUST_MS = 2_000, HISTORY_TTL = 600_000, LEAVE_GRACE = 7_000;
    private final Map<UUID, Person> people = new LinkedHashMap<>();
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private long sessionSince;

    public void reset(long now) { people.clear(); changes.clear(); sessionSince = now; }
    public List<Change> drainChanges() {
        List<Change> result = List.copyOf(changes); changes.clear(); return result;
    }
    private void changed(Person p, ChangeType type, long now) {
        changes.addLast(new Change(p.id, p.name, type, now));
        while (changes.size() > MAX_TRACKED) changes.removeFirst();
    }
    public Set<UUID> nearbyIds(Policy policy, long now) {
        Set<UUID> result = new HashSet<>();
        for (Person p : people.values()) if (now >= p.departureUntil && nearby(p, policy)) result.add(p.id);
        return Set.copyOf(result);
    }
    public boolean isTracked(UUID id) { return people.containsKey(id); }
    public boolean isMarkedStaff(UUID id) { Person p = people.get(id); return p != null && p.staff; }
    public void removeUnmarked() { people.values().removeIf(p -> !p.staff); }
    public int size() { return people.size(); }
    public Set<UUID> ids() { return Set.copyOf(people.keySet()); }
    public String name(UUID id) { Person p = people.get(id); return p == null ? "" : p.name; }
    public void sample(Collection<Observation> observations, Policy policy, long now, boolean rebase) {
        Set<UUID> seen = new HashSet<>();
        for (Observation observation : observations) {
            if (observation.id == null || observation.name == null || observation.name.isBlank()) continue;
            Person p = people.get(observation.id);
            if (p == null) {
                if (people.size() >= MAX_TRACKED) continue;
                p = new Person(observation.id, observation.name, now);
                people.put(p.id, p);
            }
            seen.add(p.id);
            p.name = observation.name;
            p.staff |= observation.staff;
            update(p, observation, policy, now, rebase);
        }
        for (Person p : people.values()) if (!seen.contains(p.id)) {
            update(p, new Observation(p.id, p.name, true, false, false, false, false, false, false, -1), policy, now, rebase);
        }
        people.values().removeIf(p -> !p.listed && !p.body && now - p.lastSeen > HISTORY_TTL);
    }
    private void update(Person p, Observation o, Policy policy, long now, boolean rebase) {
        boolean previouslyListed = p.listed;
        boolean eligible = previouslyListed && p.listedSince >= 0 && now - p.listedSince >= TRUST_MS;
        if (o.listed) {
            if (!previouslyListed) p.listedSince = now;
            // Becoming visible again ends the previous hidden episode immediately.
            if (!previouslyListed) {
                if (p.missingSince >= 0 && now >= p.departureUntil && !rebase) changed(p, ChangeType.VISIBLE_AGAIN, now);
                p.missingSince = -1; p.signals.clear();
            }
        } else {
            if (previouslyListed && eligible && policy.tab && now - sessionSince >= policy.warmup
                    && now >= p.departureUntil && !rebase) { p.missingSince = now; p.signals.clear(); }
            p.listedSince = -1;
        }
        p.listed = o.listed; p.entry = o.entryPresent; p.body = o.body;
        p.distance = o.distance; p.invisible = o.invisible && !o.potion;
        p.spectator = o.spectator;
        if (o.listed || o.entryPresent || o.body) p.lastSeen = now;
        if (rebase) { p.missingSince = -1; p.signals.clear(); return; }
        p.signals.entrySet().removeIf(e -> now - e.getValue() > policy.evidenceWindow);
        if (now - sessionSince < policy.warmup || now < p.departureUntil) { p.signals.clear(); return; }
        if (!policy.tab) {
            p.missingSince = -1;
            p.signals.remove(Signal.TAB_ABSENT); p.signals.remove(Signal.BODY_PRESENT); p.signals.remove(Signal.COMPLETION);
        }
        boolean mature = missingMature(p, policy, now);
        if (mature && (now - p.missingSince <= policy.evidenceWindow || o.entryPresent || o.body
                || p.signals.containsKey(Signal.COMPLETION))) put(p, Signal.TAB_ABSENT, now);
        else p.signals.remove(Signal.TAB_ABSENT);
        if (mature && nearby(p, policy)) put(p, Signal.BODY_PRESENT, now);
        else p.signals.remove(Signal.BODY_PRESENT);
        if (policy.invisible && p.invisible && nearby(p, policy)) put(p, Signal.INVISIBLE, now);
        else p.signals.remove(Signal.INVISIBLE);
        if (policy.spectator && p.spectator && (p.listed || p.entry || p.body)) put(p, Signal.SPECTATOR, now);
        else p.signals.remove(Signal.SPECTATOR);
        if (p.listed) p.signals.remove(Signal.COMPLETION);
    }
    private static void put(Person p, Signal signal, long now) {
        if (p.signals.isEmpty()) p.incidentSince = now;
        p.signals.put(signal, now);
    }
    private boolean missingMature(Person p, Policy policy, long now) {
        return policy.tab && !p.listed && p.missingSince >= 0 && now >= p.departureUntil
                && now - p.missingSince >= policy.disappearanceGrace;
    }
    private static boolean nearby(Person p, Policy policy) {
        return p.body && Double.isFinite(p.distance) && p.distance >= 0 && p.distance <= policy.range;
    }
    public boolean hiddenBody(UUID id, Policy policy, long now) {
        Person p = people.get(id);
        return p != null && now - sessionSince >= policy.warmup && now >= p.departureUntil
                && nearby(p, policy) && (policy.invisible && p.invisible || missingMature(p, policy, now));
    }
    /** Positional events may be attached only to one current, known hidden staff entity by the adapter. */
    public void addLinkedSignal(UUID id, Signal signal, Policy policy, long now) {
        if (signal != Signal.SOUND && signal != Signal.PARTICLE) return;
        Person p = people.get(id);
        if (p != null && hiddenBody(id, policy, now)) {
            long previous = p.signals.getOrDefault(signal, Long.MIN_VALUE / 2);
            if (now - previous >= 3_000) put(p, signal, now);
        }
    }
    public void confirmDeparture(String name, long now) {
        String key = StaffText.normalize(name);
        for (Person p : people.values()) if (StaffText.normalize(p.name).equals(key)) {
            if (now >= p.departureUntil) changed(p, ChangeType.CONFIRMED_LEFT, now);
            p.departureUntil = now + LEAVE_GRACE;
            p.missingSince = -1; p.signals.clear();
        }
    }
    public void removeSignal(Signal signal) { for (Person p : people.values()) p.signals.remove(signal); }
    public Map<String, UUID> completionCandidates(Policy policy, long now) {
        Map<String, UUID> result = new LinkedHashMap<>();
        if (now - sessionSince < policy.warmup) return result;
        for (Person p : people.values()) if (missingMature(p, policy, now)
                && now - p.missingSince <= 60_000 && result.size() < 128)
            result.put(StaffText.normalize(p.name), p.id);
        return result;
    }
    public void confirmCompletion(UUID id, String name, Policy policy, long now) {
        Person p = people.get(id);
        if (p == null || !StaffText.normalize(p.name).equals(StaffText.normalize(name)) || !missingMature(p, policy, now)) return;
        put(p, Signal.COMPLETION, now); put(p, Signal.TAB_ABSENT, now);
    }
    public List<Finding> findings(Policy policy, long now) {
        List<Finding> result = new ArrayList<>();
        if (now - sessionSince < policy.warmup) return List.of();
        for (Person p : people.values()) {
            p.signals.entrySet().removeIf(e -> now - e.getValue() > policy.evidenceWindow);
            if (p.signals.isEmpty() || now < p.departureUntil) continue;
            Set<Signal> kinds = p.signals.keySet();
            boolean tab = kinds.contains(Signal.TAB_ABSENT), body = kinds.contains(Signal.BODY_PRESENT);
            boolean completion = kinds.contains(Signal.COMPLETION);
            if (!tab && !kinds.contains(Signal.INVISIBLE) && !kinds.contains(Signal.SPECTATOR)) continue;
            int score = kinds.stream().mapToInt(s -> s.weight).sum();
            boolean strong = score >= policy.threshold && kinds.size() >= 2 && tab && (body || completion);
            // An unmarked player needs direct evidence. A Tab disappearance by itself
            // is a normal disconnect, and a sound or particle cannot identify a player.
            if (!p.staff && !strong && !(kinds.contains(Signal.INVISIBLE) && nearby(p, policy))) continue;
            String reason = tab && body ? "Off Tab; player entity still nearby"
                    : tab && completion ? "Off Tab; name in command suggestions"
                    : tab ? "Left Tab; departure unconfirmed"
                    : kinds.contains(Signal.INVISIBLE) ? p.staff ? "Invisible staff; vanish unconfirmed"
                            : "Invisible player nearby; staff identity unknown"
                    : "Spectator mode; vanish unconfirmed";
            result.add(new Finding(p.id, p.name, p.staff, score, strong ? Level.CORROBORATED : Level.NOTICE,
                    nearby(p, policy), nearby(p, policy) ? p.distance : -1, reason, kinds,
                    p.missingSince >= 0 ? p.missingSince : p.incidentSince));
        }
        result.sort(Comparator.<Finding>comparingInt(f -> f.level.ordinal()).reversed()
                .thenComparing(Comparator.comparingInt(Finding::score).reversed()).thenComparing(Finding::name));
        return List.copyOf(result);
    }
    private static final class Person {
        final UUID id;
        String name;
        boolean staff, listed, entry, body, invisible, spectator;
        long listedSince = -1, missingSince = -1, departureUntil, lastSeen, incidentSince;
        double distance = -1;
        final EnumMap<Signal, Long> signals = new EnumMap<>(Signal.class);
        Person(UUID id, String name, long now) { this.id = id; this.name = name; lastSeen = now; }
    }
}
