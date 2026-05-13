package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAiTest {

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
        s.turn = 2;
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

    private Troll troll(int id, int x, int y, int cc) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        t.carryCapacity = cc;
        return t;
    }

    @Test
    void chops_when_on_tree_tile() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(3, 1, 4));
        s.trolls.add(troll(7, 3, 1, 10));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(new Action.Chop(7));
    }

    @Test
    void drops_when_carry_full_and_adjacent() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 3);
        t.carry[5] = 3;
        s.trolls.add(t);

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(new Action.Drop(0));
    }

    @Test
    void moves_toward_shack_when_full_and_not_adjacent() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 1, 1, 3);
        t.carry[5] = 3;
        s.trolls.add(t);

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(new Action.Move(0, 4, 1));
    }

    @Test
    void random_walks_when_no_trees() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trolls.add(troll(0, 5, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isInstanceOf(Action.Move.class);
    }

    @Test
    void local_troll_adjacent_picks_banana_when_stock_available() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        s.myShackInv[ResourceType.BANANA.ordinal()] = 1;
        s.trolls.add(troll(0, 6, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(
                new Action.Pick(0, ResourceType.BANANA));
    }

    @Test
    void two_local_trolls_share_one_banana_budget() {
        // Map 10x4, two adjacent trolls at (4,1) and (6,1), shack at (5,1).
        // 1 banana in stock → first by id picks, second falls back.
        // RoleAssigner with 2 trolls picks one LEADER (highest score, tie-break lowest id).
        // Both trolls have identical stats → LEADER = id 0, LOCAL = id 1.
        // LEADER (id 0) goes to TargetSelector → null tree → RandomWalk.Move
        // LOCAL (id 1) is adjacent → PICK BANANA.
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        s.myShackInv[ResourceType.BANANA.ordinal()] = 1;
        s.trolls.add(troll(0, 4, 1, 5));
        s.trolls.add(troll(1, 6, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).hasSize(2);
        assertThat(actions).contains(
                new Action.Pick(1, ResourceType.BANANA));
    }

    @Test
    void no_pick_when_banana_stock_is_zero() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        s.myShackInv[ResourceType.BANANA.ordinal()] = 0;
        s.trolls.add(troll(0, 6, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isNotInstanceOf(Action.Pick.class);
    }

    @Test
    void emits_train_in_addition_to_troll_action_at_turn_1() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.turn = 1;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.PLUM.ordinal()] = 100;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.LEMON.ordinal()] = 100;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.IRON.ordinal()] = 100;
        s.trees.add(tree(3, 1, 4));
        s.trolls.add(troll(0, 1, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).hasSize(2);
        assertThat(actions.get(1)).isInstanceOf(Action.Train.class);
    }
}
