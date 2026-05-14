package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorMineTest {

    @BeforeEach void grid() {
        // iron at (3,3), grass everywhere else
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.tiles[3 * 6 + 3] = TileType.IRON;
        GameState.shackMeX  = 0; GameState.shackMeY  = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    @Test void mineAdjacentToIronAddsChopPowerIron() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 2; // north of iron
        s.trollCP[0] = 3;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 3);
    }

    @Test void mineRespectsCarryCapacity() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 2; s.trollY[0] = 3;
        s.trollCP[0] = 5;
        s.trollCC[0] = 2;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 2);
    }

    @Test void mineFailsWhenNotAdjacentToIron() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 3;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 0);
    }

    @Test void mineFailsWhenChopPowerZero() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 2;
        s.trollCP[0] = 0;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 0);
    }
}
