package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainPlannerTest {

    private GameState stateWithTurn(int turn, int plums, int lemons, int iron) {
        GameState s = new GameState();
        s.width = 1;
        s.height = 1;
        s.grid = new byte[]{0};
        s.turn = turn;
        s.myShackInv[ResourceType.PLUM.ordinal()] = plums;
        s.myShackInv[ResourceType.LEMON.ordinal()] = lemons;
        s.myShackInv[ResourceType.IRON.ordinal()] = iron;
        Troll t = new Troll();
        t.id = 0;
        t.player = 0;
        s.trolls.add(t);
        return s;
    }

    @Test
    void emits_max_affordable_train_at_turn_1() {
        GameState s = stateWithTurn(1, 5, 5, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 2, 0, 2));
    }

    @Test
    void returns_null_when_chop_power_zero() {
        GameState s = stateWithTurn(1, 5, 5, 0);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }

    @Test
    void returns_null_when_resources_insufficient_at_turn_1() {
        GameState s = stateWithTurn(1, 1, 1, 1);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }

    @Test
    void returns_null_after_turn_1_even_with_resources() {
        GameState s = stateWithTurn(2, 100, 100, 100);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }

    @Test
    void decouples_stats_with_asymmetric_budgets() {
        // PLUM=20, LEMON=20, IRON=2 avec n=1
        //   maxAffordable(20, 1) = 4 (1 + 16 = 17 <= 20)
        //   maxAffordable(2,  1) = 1 (1 + 1  = 2  <= 2)
        GameState s = stateWithTurn(1, 20, 20, 2);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(4, 4, 0, 1));
    }

    @Test
    void picks_different_v_per_stat() {
        // n=1, PLUM=5 → v=2 (1+4=5), LEMON=11 → v=3 (1+9=10), IRON=2 → v=1 (1+1=2)
        GameState s = stateWithTurn(1, 5, 11, 2);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 3, 0, 1));
    }

    @Test
    void returns_null_when_carry_capacity_zero() {
        // PLUM=5, LEMON=0 (insuffisant pour v=1), IRON=5
        //   vSpeed=2, vCarry=0, vChop=2 → troll incapable de porter le wood
        GameState s = stateWithTurn(1, 5, 0, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }

    @Test
    void returns_null_when_move_speed_zero() {
        // PLUM=0, LEMON=5, IRON=5
        //   vSpeed=0, vCarry=2, vChop=2 → troll immobile
        GameState s = stateWithTurn(1, 0, 5, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
}
