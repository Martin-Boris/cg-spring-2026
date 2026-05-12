package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.Troll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GreedyAi {

    private TreeZoning zoning;

    public List<Action> decide(GameState state) {
        if (zoning == null) {
            zoning = TreeZoning.precompute(state);
        }

        List<Troll> myTrolls = new ArrayList<>();
        for (Troll t : state.trolls) {
            if (t.player == 0) {
                myTrolls.add(t);
            }
        }

        Action.Train trainAction = TrainPlanner.plan(state);
        Map<Integer, Role> roles = RoleAssigner.assign(myTrolls);

        myTrolls.sort(Comparator.<Troll>comparingInt(t -> roles.get(t.id) == Role.LEADER ? 0 : 1)
                .thenComparingInt(t -> t.id));

        Set<Long> assignedTrees = new HashSet<>();
        List<Action> actions = new ArrayList<>(myTrolls.size() + 1);
        for (Troll t : myTrolls) {
            actions.add(decideForTroll(t, roles.get(t.id), state, assignedTrees));
        }
        if (trainAction != null) {
            actions.add(trainAction);
        }
        return actions;
    }

    private Action decideForTroll(Troll troll, Role role, GameState state, Set<Long> assignedTrees) {
        if (troll.carryTotal() >= troll.carryCapacity && troll.carryCapacity > 0) {
            if (adjacentToMyShack(troll, state)) {
                return new Action.Drop(troll.id);
            }
            return moveTowardShack(troll, state);
        }
        Tree target = TargetSelector.pickTree(troll, role, state, assignedTrees, zoning);
        if (target == null) {
            return RandomWalk.pick(troll, state);
        }
        if (troll.x == target.x && troll.y == target.y) {
            return new Action.Chop(troll.id);
        }
        return new Action.Move(troll.id, target.x, target.y);
    }

    private static boolean adjacentToMyShack(Troll troll, GameState state) {
        return Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) == 1;
    }

    private static Action.Move moveTowardShack(Troll troll, GameState state) {
        int sx = state.myShackX;
        int sy = state.myShackY;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        int bestX = troll.x;
        int bestY = troll.y;
        int bestDist = Integer.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            int nx = sx + dx[k];
            int ny = sy + dy[k];
            if (!state.walkable(nx, ny)) {
                continue;
            }
            int d = Math.abs(nx - troll.x) + Math.abs(ny - troll.y);
            if (d < bestDist || (d == bestDist && ny < bestY) || (d == bestDist && ny == bestY && nx < bestX)) {
                bestDist = d;
                bestX = nx;
                bestY = ny;
            }
        }
        return new Action.Move(troll.id, bestX, bestY);
    }
}
