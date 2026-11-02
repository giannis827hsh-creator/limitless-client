package com.example.mod.module.antivanish;

import com.example.mod.module.antivanish.AntiVanishTracker.Change;
import com.example.mod.module.antivanish.AntiVanishTracker.ChangeType;
import com.example.mod.module.antivanish.AntiVanishTracker.Finding;
import com.example.mod.module.antivanish.AntiVanishTracker.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure presentation timing. Losing contact and becoming visible never mean confirmed departure. */
public final class StaffAlerts {
    public enum Kind { WARNING, FOLLOWUP, LEFT, VISIBLE_AGAIN, LOST_CONTACT }
    public record Notice(UUID id, String name, Kind kind, long at) { }
    public record Frame(Kind kind, String names, float alpha) { }
    public record Options(boolean screenWarnings, boolean departures, long warningMs, long followupMs, int perMinute) { }
    private final Map<UUID, Watch> watched = new HashMap<>();
    private final ArrayDeque<Long> warningTimes = new ArrayDeque<>();
    private long warningAt = Long.MIN_VALUE;
    private String warningNames = "";
    private final Set<UUID> warningPeople = new HashSet<>();
    private Notice toast;

    public void clear() { watched.clear(); warningTimes.clear(); warningPeople.clear(); warningAt = Long.MIN_VALUE; warningNames = ""; toast = null; }
    public List<Notice> update(List<Finding> findings, Set<UUID> nearby, List<Change> changes, Options options, long now) {
        List<Notice> notices = new ArrayList<>();
        while (!warningTimes.isEmpty() && now - warningTimes.peekFirst() >= 60_000) warningTimes.removeFirst();
        if (!options.screenWarnings) warningAt = Long.MIN_VALUE;
        if (!options.departures) toast = null;
        boolean confirmedLeft = false;
        for (Change change : changes) {
            Watch previous = watched.remove(change.id());
            if (change.type() == ChangeType.CONFIRMED_LEFT && warningPeople.remove(change.id()) && warningPeople.isEmpty()) confirmedLeft = true;
            if (!options.departures || change.type() == ChangeType.VISIBLE_AGAIN && previous == null) continue;
            Notice notice = new Notice(change.id(), change.name(), change.type() == ChangeType.CONFIRMED_LEFT ? Kind.LEFT : Kind.VISIBLE_AGAIN, now);
            notices.add(notice); toast = notice;
        }
        List<Watch> warningCandidates = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.level() != Level.CORROBORATED || !finding.nearby()) continue;
            Watch watch = watched.get(finding.id());
            if (watch == null && watched.size() < 256) {
                watch = new Watch(finding.id(), finding.name(), now); watched.put(finding.id(), watch);
            }
            if (watch == null) continue;
            boolean returning = watch.lost;
            if (returning) watch.rearm = true;
            if (options.screenWarnings && (watch.warnedEpisode != finding.episode() || watch.rearm)
                    && (watch.lastWarning < 0 || now - watch.lastWarning >= 20_000)) {
                watch.pendingEpisode = finding.episode(); warningCandidates.add(watch);
            }
            watch.name = finding.name(); watch.lastNear = now; watch.lost = false;
        }
        // One banner can name multiple newly detected people, keeping simultaneous warnings readable.
        if (!warningCandidates.isEmpty() && warningTimes.size() < options.perMinute) {
            warningNames = warningCandidates.stream().limit(2).map(w -> w.name).collect(java.util.stream.Collectors.joining(", "));
            if (warningCandidates.size() > 2) warningNames += " +" + (warningCandidates.size() - 2);
            warningAt = now; warningTimes.addLast(now);
            warningPeople.clear();
            for (Watch watch : warningCandidates) warningPeople.add(watch.id);
            for (Watch watch : warningCandidates) { watch.warnedEpisode = watch.pendingEpisode; watch.lastWarning = now; watch.rearm = false; }
        }
        for (Map.Entry<UUID, Watch> entry : watched.entrySet()) {
            Watch watch = entry.getValue();
            if (nearby.contains(entry.getKey())) {
                if (watch.lost) watch.rearm = true;
                watch.lastNear = now; watch.lost = false;
            }
            else if (!watch.lost && now - watch.lastNear >= 5_000) {
                watch.lost = true;
                if (options.departures) {
                    Notice notice = new Notice(entry.getKey(), watch.name, Kind.LOST_CONTACT, now);
                    notices.add(notice); toast = notice;
                }
            }
        }
        Frame current = warningFrame(options, now);
        if (confirmedLeft && current != null && current.kind == Kind.WARNING
                && watched.keySet().stream().noneMatch(nearby::contains)) {
            // A confirmed exit can finish the warning early, while retaining the requested follow-up.
            warningAt = now - options.warningMs;
        }
        watched.values().removeIf(w -> now - w.lastNear > 600_000);
        return List.copyOf(notices);
    }
    public Frame warningFrame(Options options, long now) {
        if (!options.screenWarnings || warningAt == Long.MIN_VALUE) return null;
        long age = now - warningAt;
        if (age < 0 || age >= options.warningMs + options.followupMs) return null;
        boolean followup = age >= options.warningMs;
        long phaseAge = followup ? age - options.warningMs : age;
        long phaseLength = followup ? options.followupMs : options.warningMs;
        return new Frame(followup ? Kind.FOLLOWUP : Kind.WARNING, warningNames, fade(phaseAge, phaseLength));
    }
    public Frame departureFrame(Options options, long now) {
        if (!options.departures || toast == null || now < toast.at || now - toast.at >= 5_000) return null;
        return new Frame(toast.kind, toast.name, fade(now - toast.at, 5_000));
    }
    private static float fade(long age, long duration) {
        return Math.max(0, Math.min(1f, Math.min(age / 150f, (duration - age) / 250f)));
    }
    private static final class Watch {
        final UUID id;
        String name;
        long lastNear, lastWarning = -1, warnedEpisode = Long.MIN_VALUE, pendingEpisode;
        boolean lost, rearm;
        Watch(UUID id, String name, long now) { this.id = id; this.name = name; lastNear = now; }
    }
}
