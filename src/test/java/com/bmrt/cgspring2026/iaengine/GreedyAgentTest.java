package com.bmrt.cgspring2026.iaengine;

import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Resource;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.support.GameStateBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAgentTest {

    @Test
    void troll_adjacent_to_shack_with_carry_does_drop() {
        int[] carry = new int[Resource.COUNT];
        carry[Resource.PLUM] = 1;
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTrollCarrying(7, 0, 1, 5, carry)
                .build();

        int[] actions = decide(s);

        assertThat(Actions.type(actions[0])).isEqualTo((int) ActionType.DROP);
        assertThat(Actions.trollIdx(actions[0])).isZero();
    }

    @Test
    void troll_on_fruited_tree_does_harvest() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 2, 2, 4, 20, 2, 5)
                .build();

        int[] actions = decide(s);

        assertThat(Actions.type(actions[0])).isEqualTo((int) ActionType.HARVEST);
    }

    @Test
    void troll_far_from_tree_moves_toward_it() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 1, 1, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 3, 3, 4, 20, 2, 5)
                .build();

        int[] actions = decide(s);

        assertThat(Actions.type(actions[0])).isEqualTo((int) ActionType.MOVE);
        int expectedTile = 3 * s.width + 3;
        assertThat(Actions.p1(actions[0])).isEqualTo(expectedTile);
    }

    @Test
    void full_troll_moves_back_to_shack() {
        int[] carry = new int[Resource.COUNT];
        carry[Resource.APPLE] = 3;
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTrollCarrying(7, 3, 3, 3, carry)
                .build();

        int[] actions = decide(s);

        assertThat(Actions.type(actions[0])).isEqualTo((int) ActionType.MOVE);
    }

    @Test
    void no_owned_troll_emits_no_action() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withOppTroll(7, 2, 2)
                .build();

        GreedyAgent agent = new GreedyAgent(s.tileCount);
        int[] actions = new int[GameState.MAX_TROLLS];
        int n = agent.decide(s, actions);

        assertThat(n).isZero();
    }

    private int[] decide(GameState s) {
        GreedyAgent agent = new GreedyAgent(s.tileCount);
        int[] actions = new int[GameState.MAX_TROLLS];
        int n = agent.decide(s, actions);
        assertThat(n).isPositive();
        return actions;
    }
}
