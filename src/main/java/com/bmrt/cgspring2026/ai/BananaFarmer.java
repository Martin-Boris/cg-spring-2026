package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Troll;

import java.util.Set;

public final class BananaFarmer {

    private BananaFarmer() {
    }

    public static Action plan(Troll troll,
                              GameState state,
                              int[] bananaBudget,
                              Set<Long> assignedTrees) {
        if (troll.player != 0) {
            return null;
        }
        if (troll.carryCapacity <= 0) {
            return null;
        }
        if (Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) != 1) {
            return null;
        }

        int woodIdx = ResourceType.WOOD.ordinal();
        if (troll.carry[woodIdx] >= 1) {
            return new Action.Drop(troll.id);
        }

        return null;
    }
}
