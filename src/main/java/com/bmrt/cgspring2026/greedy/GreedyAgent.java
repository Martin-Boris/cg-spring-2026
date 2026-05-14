package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class GreedyAgent {

    public static int maybeTrain(GameState s) {
        int n = countOwnTrolls(s);
        int plum  = s.shackInventory[ResourceType.PLUM];
        int lemon = s.shackInventory[ResourceType.LEMON];
        int apple = s.shackInventory[ResourceType.APPLE];
        int iron  = s.shackInventory[ResourceType.IRON];

        if (plum  < n + 1) return -1;
        if (lemon < n)     return -1;
        if (apple < n)     return -1;
        if (iron  < n)     return -1;

        int ms = maxV(plum,  n, 1);
        int cc = maxV(lemon, n, 0);
        int cp = maxV(iron,  n, 0);
        return Action.train(ms, cc, 0, cp);
    }

    private static int maxV(int resource, int n, int floor) {
        int v = floor;
        while ((long) (n + (v + 1) * (v + 1)) <= resource) v++;
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
                int d  = PathTable.distance(tx, ty, cx, cy);
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

    private GreedyAgent() {}
}
