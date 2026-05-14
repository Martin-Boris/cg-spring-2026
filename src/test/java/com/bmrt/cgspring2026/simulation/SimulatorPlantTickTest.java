package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPlantTickTest {

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

    @Test void tickIncrementsTurnCounter() {
        GameState s = new GameState();
        s.turn = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.turn).isEqualTo(6);
    }
}
