package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateReadTurnTest {

    // shack inventories: mine=1 2 3 4 5 6 / opp=0 0 0 0 0 0
    // 2 trees: PLUM 3 4 2 8 1 7 / APPLE 5 6 3 14 0 9
    // 2 trolls: id=0 player=0 x=1 y=2 ms=2 cc=3 hp=1 cp=1 carry=0 0 0 0 0 0
    //           id=1 player=1 x=7 y=3 ms=1 cc=2 hp=2 cp=0 carry=2 0 1 0 0 0
    private static final String TURN_INPUT =
            "1 2 3 4 5 6\n" +
            "0 0 0 0 0 0\n" +
            "2\n" +
            "PLUM 3 4 2 8 1 7\n" +
            "APPLE 5 6 3 14 0 9\n" +
            "2\n" +
            "0 0 1 2 2 3 1 1 0 0 0 0 0 0\n" +
            "1 1 7 3 1 2 2 0 2 0 1 0 0 0\n";

    private GameState state;

    @BeforeEach void setUp() {
        state = new GameState();
    }

    // --- shack inventory ---

    @Test void shackInventoryMine() {
        state.readTurn(new Scanner(TURN_INPUT));
        // player 0: indices 0..5
        assertThat(state.shackInventory[ResourceType.PLUM]).isEqualTo(1);
        assertThat(state.shackInventory[ResourceType.LEMON]).isEqualTo(2);
        assertThat(state.shackInventory[ResourceType.APPLE]).isEqualTo(3);
        assertThat(state.shackInventory[ResourceType.BANANA]).isEqualTo(4);
        assertThat(state.shackInventory[ResourceType.IRON]).isEqualTo(5);
        assertThat(state.shackInventory[ResourceType.WOOD]).isEqualTo(6);
    }

    @Test void shackInventoryOpp() {
        state.readTurn(new Scanner(TURN_INPUT));
        // player 1: indices 6..11
        int base = ResourceType.COUNT;
        assertThat(state.shackInventory[base + ResourceType.PLUM]).isEqualTo(0);
        assertThat(state.shackInventory[base + ResourceType.WOOD]).isEqualTo(0);
    }

    // --- trees ---

    @Test void treeCountIsTwo() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.treeCount).isEqualTo(2);
    }

    @Test void firstTreeFields() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.treeType[0]).isEqualTo(TreeType.PLUM);
        assertThat(state.treeX[0]).isEqualTo((byte) 3);
        assertThat(state.treeY[0]).isEqualTo((byte) 4);
        assertThat(state.treeSize[0]).isEqualTo((byte) 2);
        assertThat(state.treeHealth[0]).isEqualTo((byte) 8);
        assertThat(state.treeFruits[0]).isEqualTo((byte) 1);
        assertThat(state.treeCooldown[0]).isEqualTo((byte) 7);
    }

    @Test void secondTreeType() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.treeType[1]).isEqualTo(TreeType.APPLE);
        assertThat(state.treeSize[1]).isEqualTo((byte) 3);
        assertThat(state.treeHealth[1]).isEqualTo((byte) 14);
    }

    @Test void treeIndexAtFindsTree() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.treeIndexAt(3, 4)).isEqualTo(0);
        assertThat(state.treeIndexAt(5, 6)).isEqualTo(1);
        assertThat(state.treeIndexAt(0, 0)).isEqualTo(-1);
    }

    // --- trolls ---

    @Test void trollCountIsTwo() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.trollCount).isEqualTo(2);
    }

    @Test void firstTrollFields() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.trollId[0]).isEqualTo((byte) 0);
        assertThat(state.trollPlayer[0]).isEqualTo((byte) 0);
        assertThat(state.trollX[0]).isEqualTo((byte) 1);
        assertThat(state.trollY[0]).isEqualTo((byte) 2);
        assertThat(state.trollMS[0]).isEqualTo((byte) 2);
        assertThat(state.trollCC[0]).isEqualTo((byte) 3);
        assertThat(state.trollHP[0]).isEqualTo((byte) 1);
        assertThat(state.trollCP[0]).isEqualTo((byte) 1);
    }

    @Test void firstTrollInventoryAllZero() {
        state.readTurn(new Scanner(TURN_INPUT));
        for (int r = 0; r < ResourceType.COUNT; r++) {
            assertThat(state.trollInventory[r]).isEqualTo((byte) 0);
        }
    }

    @Test void secondTrollInventory() {
        state.readTurn(new Scanner(TURN_INPUT));
        int base = 1 * ResourceType.COUNT;
        assertThat(state.trollInventory[base + ResourceType.PLUM]).isEqualTo((byte) 2);
        assertThat(state.trollInventory[base + ResourceType.APPLE]).isEqualTo((byte) 1);
        assertThat(state.trollInventory[base + ResourceType.LEMON]).isEqualTo((byte) 0);
    }

    @Test void trollIndexByIdFindsIndex() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.trollIndexById(0)).isEqualTo(0);
        assertThat(state.trollIndexById(1)).isEqualTo(1);
        assertThat(state.trollIndexById(99)).isEqualTo(-1);
    }

    // --- score ---

    @Test void scorePlayerZero() {
        state.readTurn(new Scanner(TURN_INPUT));
        // plum+lemon+apple+banana + 4*wood = 1+2+3+4 + 4*6 = 10+24 = 34
        assertThat(state.score(0)).isEqualTo(34);
    }

    @Test void scorePlayerOneIsZero() {
        state.readTurn(new Scanner(TURN_INPUT));
        assertThat(state.score(1)).isEqualTo(0);
    }
}
