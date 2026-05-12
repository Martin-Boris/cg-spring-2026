package com.bmrt.cgspring2026.iaengine;

import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Resource;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeStats;

public final class GreedyAgent {

    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    private final int[] bfsQueue;
    private final int[] bfsDist;
    private final boolean[] reservedTile;

    public GreedyAgent(int maxTiles) {
        this.bfsQueue   = new int[maxTiles];
        this.bfsDist    = new int[maxTiles];
        this.reservedTile = new boolean[maxTiles];
    }

    public int decide(GameState state, int[] out) {
        java.util.Arrays.fill(reservedTile, 0, state.tileCount, false);
        int n = 0;
        for (int i = 0; i < state.trollCount; i++) {
            if (state.trollPlayer[i] != 0) continue;
            out[n++] = decideForTroll(state, i);
        }
        return n;
    }

    private int decideForTroll(GameState s, int idx) {
        int x = s.trollX[idx] & 0xFF;
        int y = s.trollY[idx] & 0xFF;
        int tile = y * s.width + x;
        int carry = totalCarry(s, idx);
        int cap   = s.trollCarryCapacity[idx] & 0xFF;

        if (carry > 0 && s.isAdjacent(tile, s.shackMeTile)) {
            return Actions.drop(idx);
        }

        int treeIdx = s.treeByTile[tile];
        if (treeIdx >= 0 && carry < cap) {
            if (s.treeFruits[treeIdx] > 0) {
                return Actions.harvest(idx);
            }
            if (s.treeSize[treeIdx] >= 3 && (cap - carry) >= 1) {
                return Actions.chop(idx);
            }
        }

        computeBFS(s, tile);

        if (carry >= cap) {
            int dest = closestAdjacentToShack(s);
            if (dest >= 0) return Actions.move(idx, dest);
            return Actions.waitAction();
        }

        int bestTile = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < s.treeCount; t++) {
            int tx = s.treeX[t] & 0xFF;
            int ty = s.treeY[t] & 0xFF;
            int tt = ty * s.width + tx;
            if (reservedTile[tt]) continue;
            int d = bfsDist[tt];
            if (d == UNREACHABLE) continue;

            double score = evaluateTree(s, t, d, cap - carry);
            if (score > bestScore) {
                bestScore = score;
                bestTile = tt;
            }
        }

        if (bestTile >= 0) {
            reservedTile[bestTile] = true;
            return Actions.move(idx, bestTile);
        }

        if (carry > 0) {
            int dest = closestAdjacentToShack(s);
            if (dest >= 0) return Actions.move(idx, dest);
        }
        return Actions.waitAction();
    }

    private double evaluateTree(GameState s, int t, int dist, int freeCap) {
        int fruits = s.treeFruits[t] & 0xFF;
        int size   = s.treeSize[t]   & 0xFF;
        int cd     = s.treeCooldown[t] & 0xFF;

        if (fruits > 0) {
            return ((double) Resource.POINTS_FRUIT * fruits) / (dist + 1);
        }
        if (size >= 3) {
            int health = s.treeHealth[t] & 0xFF;
            int woodOut = Math.min(size, freeCap);
            double tours = dist + Math.max(1, health);
            return ((double) Resource.POINTS_WOOD * woodOut) / tours;
        }
        if (size == TreeStats.MAX_SIZE && cd <= 2) {
            return 0.5 / (dist + 1);
        }
        return Double.NEGATIVE_INFINITY;
    }

    private int closestAdjacentToShack(GameState s) {
        int sx = s.x(s.shackMeTile);
        int sy = s.y(s.shackMeTile);
        int best = -1;
        int bestDist = UNREACHABLE;
        for (int d = 0; d < 4; d++) {
            int nx = sx + DX[d], ny = sy + DY[d];
            if (nx < 0 || nx >= s.width || ny < 0 || ny >= s.height) continue;
            int nt = ny * s.width + nx;
            if (s.terrain[nt] != TileType.GRASS) continue;
            if (bfsDist[nt] < bestDist) {
                bestDist = bfsDist[nt];
                best = nt;
            }
        }
        return best;
    }

    private int totalCarry(GameState s, int idx) {
        int base = idx * Resource.COUNT;
        int sum = 0;
        for (int r = 0; r < Resource.COUNT; r++) {
            sum += s.trollCarry[base + r] & 0xFF;
        }
        return sum;
    }

    private void computeBFS(GameState s, int src) {
        for (int i = 0; i < s.tileCount; i++) bfsDist[i] = UNREACHABLE;
        bfsDist[src] = 0;
        int head = 0, tail = 0;
        bfsQueue[tail++] = src;
        while (head < tail) {
            int t = bfsQueue[head++];
            int x = t % s.width, y = t / s.width;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx < 0 || nx >= s.width || ny < 0 || ny >= s.height) continue;
                int nt = ny * s.width + nx;
                if (bfsDist[nt] != UNREACHABLE) continue;
                if (s.terrain[nt] != TileType.GRASS) continue;
                bfsDist[nt] = bfsDist[t] + 1;
                bfsQueue[tail++] = nt;
            }
        }
    }
}
