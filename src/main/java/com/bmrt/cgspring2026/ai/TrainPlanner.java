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
        int vSpeed = maxAffordable(state.myShackInv[ResourceType.PLUM.ordinal()], n);
        int vCarry = maxAffordable(state.myShackInv[ResourceType.LEMON.ordinal()], n);
        int vChop = maxAffordable(state.myShackInv[ResourceType.IRON.ordinal()], n);

        if (vChop == 0) {
            return null;
        }
        return new Action.Train(vSpeed, vCarry, 0, vChop);
    }

    private static int maxAffordable(int stock, int n) {
        for (int v = V_MAX; v >= 1; v--) {
            if (stock >= n + v * v) {
                return v;
            }
        }
        return 0;
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
