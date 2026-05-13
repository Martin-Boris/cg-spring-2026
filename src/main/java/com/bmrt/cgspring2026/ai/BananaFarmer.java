package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
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

        for (Tree tree : state.trees) {
            if (tree.x == troll.x && tree.y == troll.y
                    && tree.type == TreeType.BANANA
                    && tree.size == 0) {
                assignedTrees.add((long) tree.y * state.width + tree.x);
                return new Action.Chop(troll.id);
            }
        }

        int bananaIdx = ResourceType.BANANA.ordinal();
        if (troll.carry[bananaIdx] == 1 && troll.carryTotal() == 1) {
            boolean tileFree = true;
            for (Tree tree : state.trees) {
                if (tree.x == troll.x && tree.y == troll.y) {
                    tileFree = false;
                    break;
                }
            }
            if (tileFree) {
                return new Action.Plant(troll.id, TreeType.BANANA);
            }
        }

        if (troll.carryTotal() == 0 && bananaBudget[0] >= 1 && isSafeToFarm(troll.x, troll.y, state)) {
            bananaBudget[0]--;
            return new Action.Pick(troll.id, ResourceType.BANANA);
        }

        return null;
    }

    static boolean isSafeToFarm(int tileX, int tileY, GameState state) {
        for (Troll e : state.trolls) {
            if (e.player == 0) {
                continue;
            }
            if (e.movementSpeed <= 0) {
                continue;
            }
            int d = Math.abs(e.x - tileX) + Math.abs(e.y - tileY);
            int reach = (d + e.movementSpeed - 1) / e.movementSpeed;
            if (reach <= 3) {
                return false;
            }
        }
        return true;
    }
}
