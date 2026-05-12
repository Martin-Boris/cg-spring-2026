package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreeZoningTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        return s;
    }

    @Test
    void cell_closer_to_my_shack_is_mine_zone() {
        // 8x4 open map, my shack at (1,1), opp shack at (6,2)
        GameState s = openMap(8, 4, 1, 1, 6, 2);

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(2, 1)).isEqualTo(Zone.MINE);
    }

    @Test
    void cell_closer_to_opp_shack_is_opp_zone() {
        GameState s = openMap(8, 4, 1, 1, 6, 2);

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(5, 2)).isEqualTo(Zone.OPP);
    }

    @Test
    void equidistant_cell_is_neutral_zone() {
        // Symmetric map: shacks at (1,1) and (5,1), middle column equidistant
        GameState s = openMap(7, 3, 1, 1, 5, 1);

        TreeZoning zoning = TreeZoning.precompute(s);

        // (3,1) is at distance 2 from each shack
        assertThat(zoning.zoneOf(3, 1)).isEqualTo(Zone.NEUTRAL);
    }

    @Test
    void cell_walled_off_by_rocks_is_unreachable() {
        GameState s = openMap(5, 5, 1, 1, 3, 3);
        // Wall a 1x1 island at (4,4): surround it with ROCK
        s.grid[3 * 5 + 4] = (byte) Tile.ROCK.ordinal();
        s.grid[4 * 5 + 3] = (byte) Tile.ROCK.ordinal();

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(4, 4)).isEqualTo(Zone.UNREACHABLE);
    }
}
