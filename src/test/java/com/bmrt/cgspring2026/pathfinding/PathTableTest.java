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

    @Test void bfsFromCornerComputesDistances() {
        loadTestGrid();
        PathTable.indexCells();
        PathTable.allocateBfsBuffers();
        PathTable.bfs(0); // source = (0,0)
        // (0,0)=0, (1,0)=1, (2,0)=2, (3,0)=3 distances 0,1,2,3
        assertThat(PathTable.bfsDist[0]).isEqualTo(0);
        assertThat(PathTable.bfsDist[1]).isEqualTo(1);
        assertThat(PathTable.bfsDist[2]).isEqualTo(2);
        assertThat(PathTable.bfsDist[3]).isEqualTo(3);
        // (0,1)=4 dist 1, (3,1)=5 dist 4 (contourne via (3,0))
        assertThat(PathTable.bfsDist[4]).isEqualTo(1);
        assertThat(PathTable.bfsDist[5]).isEqualTo(4);
        // (3,2)=9 dist 5
        assertThat(PathTable.bfsDist[9]).isEqualTo(5);
    }

    @Test void bfsRecordsPredecessors() {
        loadTestGrid();
        PathTable.indexCells();
        PathTable.allocateBfsBuffers();
        PathTable.bfs(0);
        assertThat(PathTable.bfsPrev[0]).isEqualTo(-1); // source
        assertThat(PathTable.bfsPrev[1]).isEqualTo(0);  // (1,0) <- (0,0)
        assertThat(PathTable.bfsPrev[4]).isEqualTo(0);  // (0,1) <- (0,0)
    }

    @Test void bfsResetsBetweenCalls() {
        loadTestGrid();
        PathTable.indexCells();
        PathTable.allocateBfsBuffers();
        PathTable.bfs(0);
        PathTable.bfs(9); // source = (3,2)
        assertThat(PathTable.bfsDist[9]).isEqualTo(0);
        assertThat(PathTable.bfsDist[0]).isEqualTo(5);
        assertThat(PathTable.bfsPrev[9]).isEqualTo(-1);
    }
}
