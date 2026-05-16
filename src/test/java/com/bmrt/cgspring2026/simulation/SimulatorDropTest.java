package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorDropTest {

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

    @Test void dropTransfersAllItemsAdjacentToOwnShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1); // adjacent east
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 10;
        s.trollInventory[ResourceType.PLUM] = 3;
        s.trollInventory[ResourceType.WOOD] = 2;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
        assertThat(s.shackInventory[ResourceType.WOOD]).isEqualTo(2);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.trollInventory[ResourceType.WOOD]).isEqualTo((byte) 0);
    }

    @Test void dropDoesNothingWhenNotNearShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 6; s.trollY[0] = 6; // far from shack
        s.trollInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(0);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 3);
    }

    @Test void dropDoesNothingWhenInventoryEmpty() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        for (int r = 0; r < ResourceType.COUNT; r++) {
            assertThat(s.shackInventory[r]).isEqualTo(0);
        }
    }

    @Test void dropForPlayerOneUsesOppShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1;
        s.trollX[0] = (byte) (GameState.shackOppX + 1);
        s.trollY[0] = (byte) GameState.shackOppY;
        s.trollInventory[ResourceType.APPLE] = 4;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.COUNT + ResourceType.APPLE]).isEqualTo(4);
        assertThat(s.shackInventory[ResourceType.APPLE]).isEqualTo(0);
    }
}
