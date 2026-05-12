package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;

public final class TrainPlanner {

    public static final int V_MAX = 4;

    private TrainPlanner() {
    }

    public static Action.Train plan(GameState state) {
        if (state.turn != 1) {
            return null;
        }
        int n = countMyTrolls(state);
        int plums = state.myShackInv[ResourceType.PLUM.ordinal()];
        int lemons = state.myShackInv[ResourceType.LEMON.ordinal()];
        int iron = state.myShackInv[ResourceType.IRON.ordinal()];

        for (int v = V_MAX; v >= 1; v--) {
            int cost = n + v * v;
            if (plums >= cost && lemons >= cost && iron >= cost) {
                return new Action.Train(v, v, 0, v);
            }
        }
        return null;
    }

    private static int countMyTrolls(GameState state) {
        int n = 0;
        for (var t : state.trolls) {
            if (t.player == 0) {
                n++;
            }
        }
        return n;
    }
}
