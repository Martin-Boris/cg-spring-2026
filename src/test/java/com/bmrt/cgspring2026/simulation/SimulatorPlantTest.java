package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPlantTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    private static int placeTroll(GameState s, int player, int x, int y) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollCC[i] = 5;
        return i;
    }

    @Test void plantCreatesTreeAndGrowsToSizeOneAfterTick() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // New tree: starts with size=0, health=4 (PLUM final 12 - delta 2 * 4). Tick post-action grows it to size=1, health=6.
        assertThat(s.treeX[0]).isEqualTo((byte) 3);
        assertThat(s.treeY[0]).isEqualTo((byte) 3);
        assertThat(s.treeType[0]).isEqualTo(TreeType.PLUM);
        assertThat(s.treeSize[0]).isEqualTo((byte) 1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 6);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void plantFailsWithoutSeed() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3);
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
    }

    @Test void plantFailsOnNonGrass() {
        GameState s = new GameState();
        GameState.tiles[3 * 6 + 3] = TileType.ROCK;
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void plantFailsOnOccupiedCell() {
        GameState s = new GameState();
        // existing tree at (3,3)
        s.treeCount = 1;
        s.treeType[0] = TreeType.LEMON;
        s.treeX[0] = 3; s.treeY[0] = 3;
        s.treeSize[0] = 2;
        s.treeHealth[0] = 8;
        s.treeFruits[0] = 0;
        s.treeCooldown[0] = 9;
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeType[0]).isEqualTo(TreeType.LEMON);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void twoTrollsSameCellSameTypeOnePlantBothLoseSeed() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 3, 3);
        int t2 = placeTroll(s, 1, 3, 3);
        s.trollInventory[t1 * ResourceType.COUNT + ResourceType.APPLE] = 1;
        s.trollInventory[t2 * ResourceType.COUNT + ResourceType.APPLE] = 1;
        int[] acts = { Action.plant(t1, TreeType.APPLE), Action.plant(t2, TreeType.APPLE) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeType[0]).isEqualTo(TreeType.APPLE);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 0);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 0);
    }

    @Test void twoTrollsSameCellDifferentTypesNoPlantNoSeedLoss() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 3, 3);
        int t2 = placeTroll(s, 1, 3, 3);
        s.trollInventory[t1 * ResourceType.COUNT + ResourceType.PLUM]  = 1;
        s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON] = 1;
        int[] acts = { Action.plant(t1, TreeType.PLUM), Action.plant(t2, TreeType.LEMON) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
    }
}
