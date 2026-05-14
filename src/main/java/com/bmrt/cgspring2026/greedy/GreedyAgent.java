package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;

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

    private GreedyAgent() {}
}
