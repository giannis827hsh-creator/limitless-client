package com.example.mod.module.suschunk;

import com.example.mod.module.suschunk.SusChunkEvidence.Signal;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Incremental analysis of connected, already-observed geode shells, including chunk borders. */
public final class SusChunkGeodes {
    public record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    public enum Material { AMETHYST, BUDDING, CALCITE, BASALT, REMOVED_BUDDING }
    public record Placement(Cell cell, Signal signal) {}

    /** Immutable input; this job never reads Minecraft world objects. */
    public static final class Job {
        private final Map<Cell, Material> cells;
        private final List<Placement> placements;
        private final Iterator<Cell> seeds;
        private final Set<Cell> visited = new HashSet<>();
        private final ArrayDeque<Cell> frontier = new ArrayDeque<>();
        private final int chunkX, chunkZ;
        private int minX, maxX, minY, maxY, minZ, maxZ;
        private int amethyst, removed;
        private boolean intersectsChunk, componentOpen, complete;
        private int modifiedComponents;

        public Job(Map<Cell, Material> cells, List<Placement> placements, int chunkX, int chunkZ) {
            this.cells = Map.copyOf(cells);
            this.placements = List.copyOf(placements);
            seeds = this.cells.keySet().iterator();
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        /** Consume at most this many cells, so work can share the scanner's tick budget. */
        public int advance(int maximumCells) {
            int processed = 0;
            while (!complete && processed < maximumCells) {
                if (frontier.isEmpty()) {
                    if (componentOpen) finishComponent();
                    Cell seed = nextSeed();
                    if (seed == null) { complete = true; break; }
                    beginComponent(seed);
                }
                Cell cell = frontier.removeFirst();
                processed++;
                Material material = cells.get(cell);
                minX = Math.min(minX, cell.x); maxX = Math.max(maxX, cell.x);
                minY = Math.min(minY, cell.y); maxY = Math.max(maxY, cell.y);
                minZ = Math.min(minZ, cell.z); maxZ = Math.max(maxZ, cell.z);
                if (material == Material.AMETHYST || material == Material.BUDDING
                        || material == Material.REMOVED_BUDDING) amethyst++;
                if (material == Material.REMOVED_BUDDING) removed++;
                intersectsChunk |= Math.floorDiv(cell.x, 16) == chunkX && Math.floorDiv(cell.z, 16) == chunkZ;
                visit(cell.offset(1, 0, 0)); visit(cell.offset(-1, 0, 0));
                visit(cell.offset(0, 1, 0)); visit(cell.offset(0, -1, 0));
                visit(cell.offset(0, 0, 1)); visit(cell.offset(0, 0, -1));
            }
            return processed;
        }

        public boolean complete() { return complete; }
        public int modifiedComponents() { return modifiedComponents; }

        private Cell nextSeed() {
            while (seeds.hasNext()) {
                Cell seed = seeds.next();
                if (visited.add(seed)) return seed;
            }
            return null;
        }

        private void beginComponent(Cell seed) {
            minX = maxX = seed.x; minY = maxY = seed.y; minZ = maxZ = seed.z;
            amethyst = removed = 0;
            intersectsChunk = false;
            componentOpen = true;
            frontier.add(seed);
        }

        private void visit(Cell cell) {
            if (cells.containsKey(cell) && visited.add(cell)) frontier.addLast(cell);
        }

        private void finishComponent() {
            componentOpen = false;
            if (!intersectsChunk || amethyst < 8) return;
            int interiorUtilities = 0, interiorSupport = 0, nearbyUtilities = 0, nearbySupport = 0;
            EnumSet<Signal> interiorGroups = EnumSet.noneOf(Signal.class);
            for (Placement placement : placements) {
                Cell cell = placement.cell;
                boolean near = cell.x >= minX - 2 && cell.x <= maxX + 2
                        && cell.y >= minY - 2 && cell.y <= maxY + 2
                        && cell.z >= minZ - 2 && cell.z <= maxZ + 2;
                if (!near) continue;
                boolean support = placement.signal == Signal.LIGHT || placement.signal == Signal.REDSTONE;
                if (support) nearbySupport++; else nearbyUtilities++;
                boolean interior = cell.x > minX && cell.x < maxX && cell.y > minY && cell.y < maxY
                        && cell.z > minZ && cell.z < maxZ;
                if (interior) {
                    interiorGroups.add(placement.signal);
                    if (support) interiorSupport++; else interiorUtilities++;
                }
            }
            // Missing budding blocks by themselves are deliberately never evidence.
            boolean witnessedWithConstruction = removed > 0 && nearbyUtilities > 0 && nearbySupport > 0;
            boolean constructionInside = interiorUtilities >= 2 && interiorSupport > 0 && interiorGroups.size() >= 2;
            if (witnessedWithConstruction || constructionInside) modifiedComponents++;
        }
    }

    private SusChunkGeodes() {}
}
