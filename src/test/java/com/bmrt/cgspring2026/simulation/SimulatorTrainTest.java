package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTrainTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 1; GameState.shackMeY = 1;
        GameState.shackOppX = 4; GameState.shackOppY = 4;
        GameState.tiles[1 * 6 + 1] = TileType.SHACK_ME;
        GameState.tiles[4 * 6 + 4] = TileType.SHACK_OPP;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int id) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollId[i] = (byte) id;
        return i;
    }

    @Test void trainSpawnsTrollAtShackWithStatsAndDeductsCost() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0); // existing troll elsewhere (NOT on shack)
        s.shackInventory[ResourceType.PLUM]  = 5; // need n+v^2 = 1+4 = 5
        s.shackInventory[ResourceType.LEMON] = 2; // 1+1 = 2
        s.shackInventory[ResourceType.APPLE] = 2; // 1+1 = 2
        s.shackInventory[ResourceType.IRON]  = 2; // 1+1 = 2
        int[] acts = { Action.train(2, 1, 1, 1) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(2);
        int newIdx = 1;
        assertThat(s.trollPlayer[newIdx]).isEqualTo((byte) 0);
        assertThat(s.trollX[newIdx] & 0xFF).isEqualTo(GameState.shackMeX);
        assertThat(s.trollY[newIdx] & 0xFF).isEqualTo(GameState.shackMeY);
        assertThat(s.trollMS[newIdx]).isEqualTo((byte) 2);
        assertThat(s.trollCC[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollHP[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollCP[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollId[newIdx]).isEqualTo((byte) 1);
        // costs deducted
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.LEMON]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.APPLE]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.IRON]).isEqualTo(0);
    }

    @Test void trainFailsWhenCannotAfford() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0);
        s.shackInventory[ResourceType.PLUM]  = 4;
        s.shackInventory[ResourceType.LEMON] = 2;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;
        int[] acts = { Action.train(2, 1, 1, 1) }; // costs 5 PLUM
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(4);
    }

    @Test void trainFailsWhenTrollAlreadyOnShack() {
        GameState s = new GameState();
        placeTroll(s, 0, GameState.shackMeX, GameState.shackMeY, 0); // blocks shack
        s.shackInventory[ResourceType.PLUM]  = 5;
        s.shackInventory[ResourceType.LEMON] = 2;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;
        int[] acts = { Action.train(2, 1, 1, 1) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(5);
    }

    @Test void secondTrainSameTurnFailsBecauseFirstOccupiesShack() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0);
        s.shackInventory[ResourceType.PLUM]  = 100;
        s.shackInventory[ResourceType.LEMON] = 100;
        s.shackInventory[ResourceType.APPLE] = 100;
        s.shackInventory[ResourceType.IRON]  = 100;
        int[] acts = { Action.train(1, 0, 0, 0), Action.train(1, 0, 0, 0) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollCount).isEqualTo(2);
    }

    @Test void trainForPlayerOneUsesOppInventoryAndShack() {
        GameState s = new GameState();
        placeTroll(s, 1, 3, 3, 0);
        int base = ResourceType.COUNT;
        s.shackInventory[base + ResourceType.PLUM]  = 5;
        s.shackInventory[base + ResourceType.LEMON] = 2;
        s.shackInventory[base + ResourceType.APPLE] = 2;
        s.shackInventory[base + ResourceType.IRON]  = 2;
        // For training player 1, we still emit TRAIN action — but we need a way to know the owning player.
        // Convention: TRAIN action owner is inferred from the next "TRAIN" by ordering — but a single TRAIN action
        // is not tagged with a player. The simulation needs an owner. We adopt: TRAIN affects the player whose
        // resources are sufficient AND who has an empty shack. Tie-break by player 0 first.
        // -> Adapt: in the public API, callers supply TRAIN actions only for their own player. The Simulator
        //          picks player 0 if it can afford & shack free; else player 1.
        int[] acts = { Action.train(1, 0, 0, 0) };
        Simulator.tick(s, acts, 1);
        // Player 0 has 0 PLUM, cannot afford -> player 1's TRAIN runs
        assertThat(s.trollCount).isEqualTo(2);
        assertThat(s.trollPlayer[1]).isEqualTo((byte) 1);
        assertThat(s.trollX[1] & 0xFF).isEqualTo(GameState.shackOppX);
    }
}
