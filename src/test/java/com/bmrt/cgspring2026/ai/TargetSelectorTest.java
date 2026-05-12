package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSelectorTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        return s;
    }

    private Tree tree(int x, int y, int size) {
        Tree t = new Tree();
        t.type = TreeType.PLUM;
        t.x = x;
        t.y = y;
        t.size = size;
        return t;
    }

    private Troll troll(int id, int x, int y) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        return t;
    }

    @Test
    void local_picks_closest_mature_tree() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(3, 1, 4)); // mature, dist 2
        s.trees.add(tree(2, 1, 4)); // mature, dist 1
        s.trees.add(tree(2, 2, 4)); // mature, dist 2 (tie with first)
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
        assertThat(picked.y).isEqualTo(1);
    }

    @Test
    void local_falls_back_to_immature_when_no_mature() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(3, 1, 2));
        s.trees.add(tree(2, 1, 1));
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
        assertThat(picked.y).isEqualTo(1);
    }

    @Test
    void assigned_trees_are_skipped() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(2, 1, 4));
        s.trees.add(tree(4, 1, 4));
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);
        var assigned = new HashSet<Long>();
        assigned.add((long) 1 * 10 + 2);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, assigned, zoning);

        assertThat(picked.x).isEqualTo(4);
        assertThat(picked.y).isEqualTo(1);
    }

    @Test
    void last_remaining_tree_can_be_shared() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(2, 1, 4));
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);
        var assigned = new HashSet<Long>();
        assigned.add((long) 1 * 10 + 2);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, assigned, zoning);

        assertThat(picked).isNotNull();
        assertThat(picked.x).isEqualTo(2);
    }

    @Test
    void leader_targets_opp_zone_first() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(2, 1, 4)); // MINE zone
        s.trees.add(tree(7, 2, 4)); // OPP zone
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LEADER, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(7);
        assertThat(picked.y).isEqualTo(2);
    }

    @Test
    void leader_falls_back_when_no_opp_trees() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(2, 1, 4)); // only MINE zone
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LEADER, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
    }
}
