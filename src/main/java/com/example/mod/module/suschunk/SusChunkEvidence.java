package com.example.mod.module.suschunk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Scores observed construction evidence. Scores are support levels, never probabilities. */
public final class SusChunkEvidence {
    private enum Group { STORAGE, UTILITY, BED, REDSTONE, LIGHT, CONSTRUCTION, GEODE,
        KELP, CAVE_VINES, VINES, BAMBOO, COCOA, BEE_NEST }
    public enum Signal {
        PORTABLE_STORAGE("Portable storage", 6, 12, Group.STORAGE),
        SPECIAL_UTILITY("Crafted utilities", 6, 12, Group.UTILITY),
        STORAGE("Storage", 2, 8, Group.STORAGE),
        WORKSTATION("Workstations", 2, 6, Group.UTILITY),
        BED("Beds", 2, 4, Group.BED),
        REDSTONE("Redstone", 2, 8, Group.REDSTONE),
        LIGHT("Placed lighting", 1, 3, Group.LIGHT),
        MANUFACTURED("Manufactured blocks", 1, 6, Group.CONSTRUCTION),
        GEODE_MODIFICATION("Geode construction/change", 4, 8, Group.GEODE),
        MATURE_KELP("Mature kelp", 1, 8, Group.KELP),
        BERRY_VINES("Berry vines", 1, 8, Group.CAVE_VINES),
        DENSE_VINES("Dense vines", 1, 8, Group.VINES),
        BAMBOO_GROWTH("Bamboo growth", 1, 8, Group.BAMBOO),
        RIPE_COCOA("Ripe cocoa", 2, 8, Group.COCOA),
        FULL_BEE_NEST("Full bee nests", 4, 8, Group.BEE_NEST);

        public final String label;
        private final int weight;
        private final int cap;
        private final Group group;

        Signal(String label, int weight, int cap, Group group) {
            this.label = label;
            this.weight = weight;
            this.cap = cap;
            this.group = group;
        }
    }

    public static final class Counts {
        private final int[] counts = new int[Signal.values().length];

        public void add(Signal signal) { add(signal, 1); }
        public void add(Signal signal, int amount) {
            counts[signal.ordinal()] = Math.min(100_000, counts[signal.ordinal()] + Math.max(0, amount));
        }
        public int get(Signal signal) { return counts[signal.ordinal()]; }
        public void set(Signal signal, int amount) { counts[signal.ordinal()] = Math.max(0, amount); }
        public void addAll(Counts other) {
            for (Signal signal : Signal.values()) add(signal, other.get(signal));
        }
    }

    public record Options(int minimumBlocks, int minimumScore, int minimumGroups,
                          boolean ignoreStructures, boolean requireStrongAnchor, EnumSet<Signal> enabled) {
        public Options { enabled = EnumSet.copyOf(enabled); }
        @Override public EnumSet<Signal> enabled() { return EnumSet.copyOf(enabled); }
    }

    public record Result(boolean flagged, int score, int indicatorBlocks, int groups,
                         boolean structureFiltered, List<String> reasons) {
        public Result { reasons = List.copyOf(reasons); }
        public String reasonText() { return String.join(" + ", reasons); }
    }

    public static Result evaluate(Counts evidence, boolean structureContext, Options options) {
        int score = 0, blocks = 0, groups = 0;
        EnumSet<Group> detectedGroups = EnumSet.noneOf(Group.class);
        List<String> reasons = new ArrayList<>();
        for (Signal signal : Signal.values()) {
            int count = enabledCount(evidence, options, signal);
            if (count == 0 || (signal == Signal.MANUFACTURED && count < 4)) continue;
            score += Math.min(signal.cap, count * signal.weight);
            detectedGroups.add(signal.group);
            if (signal != Signal.GEODE_MODIFICATION) blocks += count;
            reasons.add(signal.label + " (" + count + ")");
        }
        groups = detectedGroups.size();

        boolean strongAnchor = enabledCount(evidence, options, Signal.PORTABLE_STORAGE) > 0
                || enabledCount(evidence, options, Signal.SPECIAL_UTILITY) > 0;
        boolean strongGrowth = enabledCount(evidence, options, Signal.MATURE_KELP) >= 48
                || enabledCount(evidence, options, Signal.BERRY_VINES) >= 20
                || enabledCount(evidence, options, Signal.DENSE_VINES) >= 64
                || enabledCount(evidence, options, Signal.BAMBOO_GROWTH) >= 64
                || enabledCount(evidence, options, Signal.RIPE_COCOA) >= 16
                || enabledCount(evidence, options, Signal.FULL_BEE_NEST) >= 4;
        int growthGroups = 0;
        for (Signal signal : List.of(Signal.MATURE_KELP, Signal.BERRY_VINES,
                Signal.DENSE_VINES, Signal.BAMBOO_GROWTH, Signal.RIPE_COCOA, Signal.FULL_BEE_NEST)) {
            if (enabledCount(evidence, options, signal) > 0) growthGroups++;
        }
        boolean builtPattern = strongAnchor
                || enabledCount(evidence, options, Signal.REDSTONE) >= 2
                || enabledCount(evidence, options, Signal.MANUFACTURED) >= 4
                || enabledCount(evidence, options, Signal.GEODE_MODIFICATION) > 0
                || strongGrowth || growthGroups >= 2
                || (!options.requireStrongAnchor && groups >= 3 && blocks >= 6);
        // Natural structures can contain chests, beds, crafting blocks, lights, and redstone.
        // Require a stronger anchor before trusting those combinations in structure context.
        boolean filtered = options.ignoreStructures && structureContext && !strongAnchor;
        boolean flagged = !filtered && builtPattern && blocks >= options.minimumBlocks
                && score >= options.minimumScore && (groups >= options.minimumGroups || strongGrowth);
        return new Result(flagged, score, blocks, groups, filtered, reasons);
    }

    public static List<Signal> detectedSignals(Counts evidence, Options options) {
        List<Signal> found = new ArrayList<>();
        for (Signal signal : Signal.values()) {
            int count = enabledCount(evidence, options, signal);
            if (count > 0 && (signal != Signal.MANUFACTURED || count >= 4)) found.add(signal);
        }
        return found;
    }

    private static int enabledCount(Counts counts, Options options, Signal signal) {
        if (!options.enabled.contains(signal)) return 0;
        int count = counts.get(signal);
        int minimum = switch (signal) {
            case MATURE_KELP -> 24;
            case BERRY_VINES -> 10;
            case DENSE_VINES -> 32;
            case BAMBOO_GROWTH -> 32;
            case RIPE_COCOA -> 8;
            case FULL_BEE_NEST -> 2;
            default -> 1;
        };
        return count >= minimum ? count : 0;
    }

    private SusChunkEvidence() {}
}
