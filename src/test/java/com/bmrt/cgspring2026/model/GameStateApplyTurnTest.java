package com.bmrt.cgspring2026.model;

import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.support.GameStateBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateApplyTurnTest {

    private static int[] slice() {
        return new int[GameState.SLOTS_PER_TURN];
    }

    @Test
    void move_action_moves_troll_one_tile_when_within_speed() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 1, 1, 1, 5, 1, 1)
                .build();

        int[] mine = slice();
        mine[0] = Actions.move(0, 2 * s.width + 1);
        s.applyTurn(mine, null);

        assertThat(s.trollX[0]).isEqualTo((byte) 1);
        assertThat(s.trollY[0]).isEqualTo((byte) 2);
    }

    @Test
    void harvest_takes_fruits_into_carry() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 2, 2, 4, 20, 3, 5)
                .build();

        int[] mine = slice();
        mine[0] = Actions.harvest(0);
        s.applyTurn(mine, null);

        assertThat(s.trollCarry[Resource.APPLE]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
    }

    @Test
    void chop_kills_tree_and_yields_wood() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 4)
                .withTree(TreeType.BANANA, 2, 2, 3, 3, 0, 6)
                .build();

        int[] mine = slice();
        mine[0] = Actions.chop(0);
        s.applyTurn(mine, null);

        assertThat(s.treeCount).isZero();
        assertThat(s.trollCarry[Resource.WOOD]).isEqualTo((byte) 3);
        assertThat(s.treeByTile[2 * s.width + 2]).isEqualTo((short) -1);
    }

    @Test
    void drop_adjacent_to_shack_transfers_carry_to_inventory() {
        int[] carry = new int[Resource.COUNT];
        carry[Resource.APPLE] = 2;
        carry[Resource.WOOD]  = 1;
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTrollCarrying(7, 0, 1, 5, carry)
                .build();

        int[] mine = slice();
        mine[0] = Actions.drop(0);
        s.applyTurn(mine, null);

        assertThat(s.invMe[Resource.APPLE]).isEqualTo(2);
        assertThat(s.invMe[Resource.WOOD]).isEqualTo(1);
        assertThat(s.trollCarry[Resource.APPLE]).isZero();
        assertThat(s.trollCarry[Resource.WOOD]).isZero();
    }

    @Test
    void train_spawns_new_troll_and_debits_shack() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withMyInventory(2, 2, 2, 0, 2, 0)
                .build();

        int[] mine = slice();
        mine[GameState.TRAIN_SLOT] = Actions.train(1, 1, 1, 1);
        s.applyTurn(mine, null);

        assertThat(s.trollCount).isEqualTo(2);
        assertThat(s.trollMoveSpeed[1]).isEqualTo((byte) 1);
        assertThat(s.invMe[Resource.PLUM]).isZero();
        assertThat(s.invMe[Resource.LEMON]).isZero();
        assertThat(s.invMe[Resource.APPLE]).isZero();
        assertThat(s.invMe[Resource.IRON]).isZero();
    }

    @Test
    void grow_phase_decrements_cooldown_and_produces_fruit_when_ready() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withTree(TreeType.APPLE, 2, 2, 4, 20, 0, 1)
                .build();

        s.applyTurn(slice(), null);

        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
        assertThat(s.treeCooldown[0] & 0xFF).isEqualTo(9);
    }

    @Test
    void mine_adjacent_iron_adds_to_carry() {
        GameState s = new GameStateBuilder()
                .withGrid("0..+",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 0, 1, 5, 1, 2)
                .build();

        int[] mine = slice();
        mine[0] = Actions.mine(0);
        s.applyTurn(mine, null);

        assertThat(s.trollCarry[Resource.IRON]).isEqualTo((byte) 2);
    }
}
