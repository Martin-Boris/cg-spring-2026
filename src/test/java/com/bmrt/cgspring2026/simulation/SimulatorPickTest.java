package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPickTest {

    @BeforeEach void grid() {
        String[] rows = {"........", "...0....", "........", "....1...", "........", "........", "........", "........"};
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
    }

    @Test void pickMovesOnePlumFromShackToTroll() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 5;
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(2);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void pickFailsWhenStockEmpty() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 5;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void pickFailsWhenNotNearShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 6; s.trollY[0] = 6;
        s.trollCC[0] = 5;
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
    }

    @Test void pickFailsWhenCarryFull() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 1;
        s.trollInventory[ResourceType.WOOD] = 1; // full
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
    }

    @Test void twoPicksDepleteStock() {
        GameState s = new GameState();
        s.trollCount = 2;
        for (int t = 0; t < 2; t++) {
            s.trollPlayer[t] = 0;
            s.trollX[t] = (byte) (GameState.shackMeX + (t == 0 ? 1 : -1));
            s.trollY[t] = (byte) GameState.shackMeY;
            s.trollCC[t] = 5;
        }
        s.shackInventory[ResourceType.LEMON] = 1;
        int[] acts = { Action.pick(0, ResourceType.LEMON), Action.pick(1, ResourceType.LEMON) };
        Simulator.tick(s, acts, 2);
        assertThat(s.shackInventory[ResourceType.LEMON]).isEqualTo(0);
        // first picker gets the lemon, second is a no-op
        assertThat(s.trollInventory[ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 0);
    }
}
