package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class TrollPolicy {

    public static final int[]     cursorBuf      = new int[GameState.MAX_TROLLS];
    public static final byte[]    policyPhase    = new byte[GameState.MAX_TROLLS];
    public static final boolean[] oppTreeTakenBuf = new boolean[GameState.MAX_TREES];

    private TrollPolicy() {}

    public static int fillActions(GameState s, short[] popBuf, byte[] popLen, int idx,
                                  int[] cursor, int[] outActions) {
        // Reset adversaire scratch
        for (int t = 0; t < s.treeCount; t++) oppTreeTakenBuf[t] = false;

        int count = 0;
        for (int trollIdx = 0; trollIdx < s.trollCount; trollIdx++) {
            int player = s.trollPlayer[trollIdx] & 0xFF;
            if (player == 0) {
                outActions[count++] = decideForOwnTroll(s, popBuf, popLen, idx, cursor, trollIdx);
            } else {
                outActions[count++] = GreedyAgent.decideForOpponent(s, trollIdx, oppTreeTakenBuf);
            }
        }
        return count;
    }

    public static int fillOwnActions(GameState s, short[] popBuf, byte[] popLen, int idx,
                                      int[] cursor, int[] outActions) {
        int count = 0;
        for (int trollIdx = 0; trollIdx < s.trollCount; trollIdx++) {
            if ((s.trollPlayer[trollIdx] & 0xFF) == 0) {
                outActions[count++] = decideForOwnTroll(s, popBuf, popLen, idx, cursor, trollIdx);
            }
        }
        return count;
    }

    private static int decideForOwnTroll(GameState s, short[] popBuf, byte[] popLen, int idx,
                                         int[] cursor, int trollIdx) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;

        if (wood > 0) {
            if (isShackAdjacent(tx, ty)) return Action.drop(trollIdx);
            return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
        }

        int len = Genome.len(popLen, idx, trollIdx);
        while (cursor[trollIdx] < len) {
            short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
            if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            int gx = Genome.geneX(g), gy = Genome.geneY(g);

            if (!Genome.isPlant(g)) {
                if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
                if (tx == gx && ty == gy) return Action.chop(trollIdx);
                return Action.move(trollIdx, gx, gy);
            }

            int fruit = Genome.plantFruitType(g);
            int t = s.treeIndexAt(gx, gy);

            if (policyPhase[trollIdx] == 0 && t >= 0) policyPhase[trollIdx] = 1;

            if (policyPhase[trollIdx] == 0) {
                int carryFruit = s.trollInventory[trollIdx * ResourceType.COUNT + fruit] & 0xFF;
                if (carryFruit == 0) {
                    int shackStock = s.shackInventory[fruit];
                    if (shackStock <= 0) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
                        continue;
                    }
                    if (isShackAdjacent(tx, ty)) return Action.pick(trollIdx, fruit);
                    return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
                }
                if (tx == gx && ty == gy) {
                    policyPhase[trollIdx] = 1;
                    return Action.plant(trollIdx, fruit);
                }
                return Action.move(trollIdx, gx, gy);
            }

            if (t < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            if (tx == gx && ty == gy) return Action.chop(trollIdx);
            return Action.move(trollIdx, gx, gy);
        }
        return Action.wait(trollIdx);
    }

    private static boolean isShackAdjacent(int x, int y) {
        for (int i = 0; i < ShackAdjacency.count; i++) {
            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
        }
        return false;
    }

    private static int closestShackAdjX(int x, int y) { return closestShackAdj(x, y, true); }
    private static int closestShackAdjY(int x, int y) { return closestShackAdj(x, y, false); }

    private static int closestShackAdj(int x, int y, boolean returnX) {
        int bestX = ShackAdjacency.x[0] & 0xFF;
        int bestY = ShackAdjacency.y[0] & 0xFF;
        int bestDist = PathTable.distance(x, y, bestX, bestY);
        for (int i = 1; i < ShackAdjacency.count; i++) {
            int cx = ShackAdjacency.x[i] & 0xFF;
            int cy = ShackAdjacency.y[i] & 0xFF;
            int d = PathTable.distance(x, y, cx, cy);
            if (d < bestDist) { bestDist = d; bestX = cx; bestY = cy; }
        }
        return returnX ? bestX : bestY;
    }
}
