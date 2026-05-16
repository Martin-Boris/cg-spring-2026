package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableNearWaterTest {

    private static void setGrid(String[] rows) {
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

    @Test void isNearWaterFlagsAllOrthogonalNeighbours() {
        setGrid(new String[]{
                ".0..",
                ".~..",
                "....",
                "...1",
        });
        PathTable.init();
        // Cells adjacent to water at (1,1): (0,1), (2,1), (1,0), (1,2)
        assertThat(PathTable.isNearWater[1 * GameState.width + 0]).isTrue();
        assertThat(PathTable.isNearWater[1 * GameState.width + 2]).isTrue();
        assertThat(PathTable.isNearWater[0 * GameState.width + 1]).isTrue();
        assertThat(PathTable.isNearWater[2 * GameState.width + 1]).isTrue();
        // Cell far from water (3,3)
        assertThat(PathTable.isNearWater[3 * GameState.width + 3]).isFalse();
    }

    @Test void isNearWaterFalseWhenNoWaterOnGrid() {
        setGrid(new String[]{
                ".0..",
                "....",
                "...1",
        });
        PathTable.init();
        for (int i = 0; i < PathTable.isNearWater.length; i++) {
            assertThat(PathTable.isNearWater[i]).as("cell %d", i).isFalse();
        }
    }
}
