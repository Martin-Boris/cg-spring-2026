package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.Troll;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TargetSelector {

    private TargetSelector() {
    }

    public static Tree pickTree(Troll troll, Role role, GameState state,
                                Set<Long> assignedTrees, TreeZoning zoning) {
        if (state.trees.isEmpty()) {
            return null;
        }

        // 1. Filter out already-assigned trees, unless only one tree remains (sharing allowed).
        List<Tree> candidates = new ArrayList<>(state.trees.size());
        boolean lastTree = state.trees.size() == 1;
        for (Tree tree : state.trees) {
            if (lastTree || !assignedTrees.contains(key(tree, state.width))) {
                candidates.add(tree);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // 2. Zone filtering by role with fallback chain.
        List<Tree> zoned;
        if (role == Role.LEADER) {
            zoned = filterByZones(candidates, zoning, Zone.OPP);
            if (zoned.isEmpty()) {
                zoned = filterByZones(candidates, zoning, Zone.OPP, Zone.NEUTRAL);
            }
            if (zoned.isEmpty()) {
                zoned = candidates;
            }
        } else {
            zoned = filterByZones(candidates, zoning, Zone.MINE, Zone.NEUTRAL);
            if (zoned.isEmpty()) {
                zoned = candidates;
            }
        }
        if (lastTree) {
            zoned = candidates;
        }

        // 3. Mature-first strict.
        List<Tree> matures = new ArrayList<>(zoned.size());
        for (Tree tree : zoned) {
            if (tree.size == 4) {
                matures.add(tree);
            }
        }
        List<Tree> pool = matures.isEmpty() ? zoned : matures;

        // 4. Argmin Manhattan distance from troll ; tie-break (y, x).
        Tree best = pool.get(0);
        int bestDist = manhattan(troll, best);
        for (int i = 1; i < pool.size(); i++) {
            Tree cur = pool.get(i);
            int d = manhattan(troll, cur);
            if (d < bestDist
                    || (d == bestDist && cur.y < best.y)
                    || (d == bestDist && cur.y == best.y && cur.x < best.x)) {
                best = cur;
                bestDist = d;
            }
        }
        assignedTrees.add(key(best, state.width));
        return best;
    }

    private static List<Tree> filterByZones(List<Tree> candidates, TreeZoning zoning, Zone... allowed) {
        List<Tree> out = new ArrayList<>();
        for (Tree tree : candidates) {
            Zone z = zoning.zoneOf(tree.x, tree.y);
            for (Zone a : allowed) {
                if (z == a) {
                    out.add(tree);
                    break;
                }
            }
        }
        return out;
    }

    private static int manhattan(Troll troll, Tree tree) {
        return Math.abs(troll.x - tree.x) + Math.abs(troll.y - tree.y);
    }

    private static long key(Tree tree, int width) {
        return (long) tree.y * width + tree.x;
    }
}
