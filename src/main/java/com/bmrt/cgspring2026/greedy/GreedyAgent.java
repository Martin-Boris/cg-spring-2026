package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class GreedyAgent {

    private static final boolean[] treeTaken = new boolean[GameState.MAX_TREES];

    private GreedyAgent() {
    }

    public static int maybeTrain(GameState s) {
        int n = countOwnTrolls(s);
        int plum = s.shackInventory[ResourceType.PLUM];
        int lemon = s.shackInventory[ResourceType.LEMON];
        int apple = s.shackInventory[ResourceType.APPLE];
        int iron = s.shackInventory[ResourceType.IRON];

        if (plum < n + 1) return -1;
        if (lemon < n + 1) return -1;
        if (apple < 1) return -1;
        if (iron < n + 1) return -1;

        int ms = maxV(plum, n, 1);
        int cc = maxV(lemon, n, 1);
        int cp = maxV(iron, n, 1);
        int hp = maxV(apple, n, 0);
        return Action.train(ms, cc, hp, cp);
    }

    private static int maxV(int resource, int n, int floor) {
        int v = floor;
        while ((n + (long) (v + 1) * (v + 1)) <= resource) v++;
        return v;
    }

    private static int countOwnTrolls(GameState s) {
        int n = 0;
        for (int i = 0; i < s.trollCount; i++) {
            if (s.trollPlayer[i] == 0) n++;
        }
        return n;
    }

    public static int decideForTroll(GameState s, int trollIdx, int treeIdx) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;

        if (wood > 0) {
            if (isShackAdjacent(tx, ty)) {
                return Action.drop(trollIdx);
            }
            int dropX = ShackAdjacency.x[0] & 0xFF;
            int dropY = ShackAdjacency.y[0] & 0xFF;
            int bestDist = PathTable.distance(tx, ty, dropX, dropY);
            for (int i = 1; i < ShackAdjacency.count; i++) {
                int cx = ShackAdjacency.x[i] & 0xFF;
                int cy = ShackAdjacency.y[i] & 0xFF;
                int d = PathTable.distance(tx, ty, cx, cy);
                if (d < bestDist) {
                    bestDist = d;
                    dropX = cx;
                    dropY = cy;
                }
            }
            return Action.move(trollIdx, dropX, dropY);
        }

        if (treeIdx < 0) {
            return Action.wait(trollIdx);
        }
        int treeX = s.treeX[treeIdx] & 0xFF;
        int treeY = s.treeY[treeIdx] & 0xFF;
        if (tx == treeX && ty == treeY) {
            return Action.chop(trollIdx);
        }
        return Action.move(trollIdx, treeX, treeY);
    }

    private static boolean isShackAdjacent(int x, int y) {
        for (int i = 0; i < ShackAdjacency.count; i++) {
            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
        }
        return false;
    }

    public static int decide(GameState s, int[] outActions) {
        int count = 0;
        if (s.turn == 0) {
            int trainAction = maybeTrain(s);
            if (trainAction != -1) {
                outActions[count++] = trainAction;
            }
        }
        java.util.Arrays.fill(treeTaken, 0, s.treeCount, false);
        for (int i = 0; i < s.trollCount; i++) {
            if (s.trollPlayer[i] != 0) continue;
            int treeIdx = pickClosestFreeTree(s, i);
            if (treeIdx >= 0) treeTaken[treeIdx] = true;
            outActions[count++] = decideForTroll(s, i, treeIdx);
        }
        return count;
    }

    public static int decideForOpponent(GameState s, int trollIdx, boolean[] oppTreeTakenBuf) {
        int treeIdx = pickClosestFreeTreeWithBuf(s, trollIdx, oppTreeTakenBuf);
        if (treeIdx >= 0) oppTreeTakenBuf[treeIdx] = true;
        return decideForTroll(s, trollIdx, treeIdx);
    }

    private static int pickClosestFreeTreeWithBuf(GameState s, int trollIdx, boolean[] taken) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int t = 0; t < s.treeCount; t++) {
            if (taken[t]) continue;
            if (s.treeHealth[t] <= 0) continue;
            int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
            if (d == PathTable.UNREACHABLE) continue;
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private static int pickClosestFreeTree(GameState s, int trollIdx) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int t = 0; t < s.treeCount; t++) {
            if (treeTaken[t]) continue;
            int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
            if (d == PathTable.UNREACHABLE) continue;
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }
}
