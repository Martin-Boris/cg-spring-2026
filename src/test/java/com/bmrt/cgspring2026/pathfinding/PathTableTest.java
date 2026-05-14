package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableTest {

    private static void loadTestGrid() {
        GameState.width = 4;
        GameState.height = 3;
        GameState.tiles = new byte[]{
            TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
            TileType.GRASS, TileType.ROCK,  TileType.ROCK,  TileType.GRASS,
            TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
        };
    }

    @Test void indexCellsAssigns10WalkableCells() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.N).isEqualTo(10);
    }

    @Test void indexCellsAssignsRowMajorIds() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellId(0, 0)).isEqualTo(0);
        assertThat(PathTable.cellId(3, 0)).isEqualTo(3);
        assertThat(PathTable.cellId(0, 1)).isEqualTo(4);
        assertThat(PathTable.cellId(3, 1)).isEqualTo(5);
        assertThat(PathTable.cellId(3, 2)).isEqualTo(9);
    }

    @Test void indexCellsMarksObstaclesUnreachable() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellId(1, 1)).isEqualTo(0xFF);
        assertThat(PathTable.cellId(2, 1)).isEqualTo(0xFF);
    }

    @Test void cellXYReverseLookup() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellX[5] & 0xFF).isEqualTo(3);
        assertThat(PathTable.cellY[5] & 0xFF).isEqualTo(1);
    }
}
