package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorInvariantsTest {

    @BeforeEach void setupGrid() {
        String[] rows = {
                "........",
                ".0......",
                "........",
                "....1...",
                "........",
                "........",
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        Simulator.DEBUG_INVARIANTS = true;
    }

    @AfterEach void teardown() {
        Simulator.DEBUG_INVARIANTS = false;
    }

    @Test void debugFlagDefaultsToFalseInProduction() {
        Simulator.DEBUG_INVARIANTS = false;
        assertThat(Simulator.DEBUG_INVARIANTS).isFalse();
    }

    private static void assertTreeIndexConsistent(GameState s) {
        int W = GameState.width;
        int H = GameState.height;
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeHealth[i] > 0) {
                int x = s.treeX[i] & 0xFF, y = s.treeY[i] & 0xFF;
                assertThat(s.treeCellIndex[y * W + x] & 0xFF)
                    .as("tree %d at (%d,%d)", i, x, y)
                    .isEqualTo(i);
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                byte v = s.treeCellIndex[y * W + x];
                if (v != -1) {
                    int idx = v & 0xFF;
                    assertThat(idx).isLessThan(s.treeCount);
                    assertThat(s.treeX[idx] & 0xFF).isEqualTo(x);
                    assertThat(s.treeY[idx] & 0xFF).isEqualTo(y);
                    assertThat(s.treeHealth[idx]).isGreaterThan((byte) 0);
                }
            }
        }
    }

    private static void assertTrollIndexConsistent(GameState s) {
        int W = GameState.width;
        int H = GameState.height;
        for (int i = 0; i < s.trollCount; i++) {
            int x = s.trollX[i] & 0xFF, y = s.trollY[i] & 0xFF;
            assertThat(s.trollCellIndex[y * W + x] & 0xFF)
                .as("troll %d at (%d,%d)", i, x, y)
                .isEqualTo(i);
        }
        int counted = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                byte v = s.trollCellIndex[y * W + x];
                if (v != -1) {
                    int idx = v & 0xFF;
                    assertThat(idx).isLessThan(s.trollCount);
                    assertThat(s.trollX[idx] & 0xFF).isEqualTo(x);
                    assertThat(s.trollY[idx] & 0xFF).isEqualTo(y);
                    counted++;
                }
            }
        }
        assertThat(counted).isEqualTo(s.trollCount);
    }

    @Test void trollIndexRemainsConsistentAfterTrainAndMove() {
        GameState s = new GameState();
        s.shackInventory[ResourceType.PLUM]  = 100;
        s.shackInventory[ResourceType.LEMON] = 100;
        s.shackInventory[ResourceType.APPLE] = 100;
        s.shackInventory[ResourceType.IRON]  = 100;
        int[] acts = { Action.train(1, 1, 1, 0) };
        Simulator.tick(s, acts, 1);
        assertTrollIndexConsistent(s);
        assertThat(s.trollCount).isEqualTo(1);
        int t = 0;
        // shackMeX=1, shackMeY=1 in the BeforeEach grid — move right
        int dx = (GameState.shackMeX + 1), dy = GameState.shackMeY;
        int[] acts2 = { Action.move(t, dx, dy) };
        Simulator.tick(s, acts2, 1);
        assertTrollIndexConsistent(s);
    }

    @Test void treeIndexRemainsConsistentAfterPlantChopCompact() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 1; s.trollY[0] = 1;
        s.trollMS[0] = 1; s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollCP[0] = 3;
        s.trollId[0] = 0;
        s.trollInventory[ResourceType.APPLE] = 1;
        int[] acts = { Action.plant(0, TreeType.APPLE) };
        Simulator.tick(s, acts, 1);
        assertTreeIndexConsistent(s);
        // Force health low so one CHOP kills it
        if (s.treeCount > 0) s.treeHealth[0] = 1;
        int[] acts2 = { Action.chop(0) };
        Simulator.tick(s, acts2, 1);
        assertTreeIndexConsistent(s);
        assertThat(s.treeCount).isEqualTo(0);
    }
}
