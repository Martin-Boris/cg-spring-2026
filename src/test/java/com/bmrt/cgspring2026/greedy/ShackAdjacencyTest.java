package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShackAdjacencyTest {

    private static void loadGrid(String... rows) {
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

    @Test void fourNeighboursAllGrass() {
        loadGrid(
            "......",
            ".0....",
            "......",
            "...1..",
            "......"
        );
        ShackAdjacency.init();
        assertThat(ShackAdjacency.count).isEqualTo(4);
        int mask = 0;
        for (int i = 0; i < ShackAdjacency.count; i++) {
            int x = ShackAdjacency.x[i] & 0xFF;
            int y = ShackAdjacency.y[i] & 0xFF;
            if (x == 0 && y == 1) mask |= 1;
            if (x == 2 && y == 1) mask |= 2;
            if (x == 1 && y == 0) mask |= 4;
            if (x == 1 && y == 2) mask |= 8;
        }
        assertThat(mask).isEqualTo(0xF);
    }

    @Test void cornerShackHasFewerNeighbours() {
        loadGrid(
            "0#....",
            "......",
            ".....1"
        );
        ShackAdjacency.init();
        assertThat(ShackAdjacency.count).isEqualTo(1);
        assertThat(ShackAdjacency.x[0] & 0xFF).isEqualTo(0);
        assertThat(ShackAdjacency.y[0] & 0xFF).isEqualTo(1);
    }

    @Test void waterAndShackOppAreExcluded() {
        loadGrid(
            "..~...",
            "..01..",
            "..#..."
        );
        ShackAdjacency.init();
        assertThat(ShackAdjacency.count).isEqualTo(1);
        assertThat(ShackAdjacency.x[0] & 0xFF).isEqualTo(1);
        assertThat(ShackAdjacency.y[0] & 0xFF).isEqualTo(1);
    }
}
