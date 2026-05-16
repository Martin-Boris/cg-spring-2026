package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.Test;

import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateReadInitTest {

    // 4×2 grid:  row0=".~#+"  row1="01.."
    // shack 0 at (0,1), shack 1 at (1,1)
    private static final String GRID_4x2 =
            "4 2\n" +
            ".~#+\n" +
            "01..\n";

    @Test void parsesWidthAndHeight() {
        GameState.readInit(new Scanner(GRID_4x2));
        assertThat(GameState.width).isEqualTo(4);
        assertThat(GameState.height).isEqualTo(2);
    }

    @Test void parsesTilesRow0() {
        GameState.readInit(new Scanner(GRID_4x2));
        // tiles[y*width+x]
        assertThat(GameState.tiles[0]).isEqualTo(TileType.GRASS);
        assertThat(GameState.tiles[1]).isEqualTo(TileType.WATER);
        assertThat(GameState.tiles[2]).isEqualTo(TileType.ROCK);
        assertThat(GameState.tiles[3]).isEqualTo(TileType.IRON);
    }

    @Test void parsesTilesRow1() {
        GameState.readInit(new Scanner(GRID_4x2));
        assertThat(GameState.tiles[4]).isEqualTo(TileType.SHACK_ME);
        assertThat(GameState.tiles[5]).isEqualTo(TileType.SHACK_OPP);
        assertThat(GameState.tiles[6]).isEqualTo(TileType.GRASS);
        assertThat(GameState.tiles[7]).isEqualTo(TileType.GRASS);
    }

    @Test void extractsShackMePosition() {
        GameState.readInit(new Scanner(GRID_4x2));
        assertThat(GameState.shackMeX).isEqualTo(0);
        assertThat(GameState.shackMeY).isEqualTo(1);
    }

    @Test void extractsShackOppPosition() {
        GameState.readInit(new Scanner(GRID_4x2));
        assertThat(GameState.shackOppX).isEqualTo(1);
        assertThat(GameState.shackOppY).isEqualTo(1);
    }

    @Test void tileAtAccessor() {
        GameState.readInit(new Scanner(GRID_4x2));
        assertThat(GameState.tileAt(0, 0)).isEqualTo(TileType.GRASS);
        assertThat(GameState.tileAt(1, 0)).isEqualTo(TileType.WATER);
        assertThat(GameState.tileAt(0, 1)).isEqualTo(TileType.SHACK_ME);
    }
}
