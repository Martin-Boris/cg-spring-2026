package com.bmrt.cgspring2026.iaengine;

import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.support.GameStateBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentTest {

    @Test
    void decide_returns_at_least_one_action_for_owned_troll() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 1, 1, 4, 20, 2, 5)
                .build();

        GeneticAgent agent = new GeneticAgent();
        int[] actions = new int[GameState.MAX_TROLLS + 1];
        int n = agent.decide(s, actions, System.currentTimeMillis() + 50);

        assertThat(n).isPositive();
        assertThat(Actions.trollIdx(actions[0])).isZero();
    }

    @Test
    void decide_respects_deadline() {
        GameState s = new GameStateBuilder()
                .withGrid("0........",
                          ".........",
                          ".........",
                          ".........",
                          ".........")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 4, 4, 4, 20, 3, 5)
                .build();

        GeneticAgent agent = new GeneticAgent();
        int[] actions = new int[GameState.MAX_TROLLS + 1];

        long start = System.currentTimeMillis();
        agent.decide(s, actions, start + 40);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(200);
    }

    @Test
    void each_owned_troll_receives_its_own_action_when_interleaved_with_opp() {
        GameState s = new GameStateBuilder()
                .withGrid("0........",
                          ".........",
                          ".........",
                          ".........",
                          "........1")
                .withMyTroll(0, 1, 1, 1, 5, 1, 1)
                .withOppTroll(1, 8, 4)
                .withMyTroll(2, 3, 3, 1, 5, 1, 1)
                .build();

        assertThat(s.myTrollCount).isEqualTo(2);
        assertThat(s.oppTrollCount).isEqualTo(1);
        assertThat(s.myTrollSlot[0]).isZero();
        assertThat(s.myTrollSlot[1]).isEqualTo(2);

        GeneticAgent agent = new GeneticAgent();
        int[] actions = new int[GameState.MAX_TROLLS + 1];
        int n = agent.decide(s, actions, System.currentTimeMillis() + 50);

        int troll0Count = 0, troll2Count = 0;
        for (int i = 0; i < n; i++) {
            if (Actions.type(actions[i]) == ActionType.TRAIN) continue;
            int idx = Actions.trollIdx(actions[i]);
            if (idx == 0) troll0Count++;
            else if (idx == 2) troll2Count++;
        }
        assertThat(troll0Count).isEqualTo(1);
        assertThat(troll2Count).isEqualTo(1);
    }

    @Test
    void all_emitted_troll_actions_carry_correct_troll_idx() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(11, 1, 1, 1, 5, 1, 1)
                .withMyTroll(22, 2, 2, 1, 5, 1, 1)
                .build();

        GeneticAgent agent = new GeneticAgent();
        int[] actions = new int[GameState.MAX_TROLLS + 1];
        int n = agent.decide(s, actions, System.currentTimeMillis() + 50);

        int trollActionsSeen = 0;
        for (int i = 0; i < n; i++) {
            if (Actions.type(actions[i]) == ActionType.TRAIN) continue;
            int idx = Actions.trollIdx(actions[i]);
            assertThat(idx).isBetween(0, s.trollCount - 1);
            trollActionsSeen++;
        }
        assertThat(trollActionsSeen).isEqualTo(2);
    }
}
