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

    @Test void initFillsDistMatrixSymmetrically() {
        loadTestGrid();
        PathTable.init();
        int dAB = PathTable.dist[0][9] & 0xFF;
        int dBA = PathTable.dist[9][0] & 0xFF;
        assertThat(dAB).isEqualTo(5);
        assertThat(dBA).isEqualTo(5);
    }

    @Test void initSelfDistanceIsZero() {
        loadTestGrid();
        PathTable.init();
        for (int i = 0; i < PathTable.N; i++) {
            assertThat(PathTable.dist[i][i] & 0xFF).isEqualTo(0);
        }
    }

    @Test void initSharesPathReferenceForReversePair() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.paths[0][9]).isSameAs(PathTable.paths[9][0]);
        assertThat(PathTable.paths[3][7]).isSameAs(PathTable.paths[7][3]);
    }

    @Test void initStoresPathFromMinToMax() {
        loadTestGrid();
        PathTable.init();
        byte[] p = PathTable.paths[0][9];
        assertThat(p.length).isEqualTo(6); // dist 5 + 1
        assertThat(p[0] & 0xFF).isEqualTo(0); // commence par min(0,9)=0
        assertThat(p[p.length - 1] & 0xFF).isEqualTo(9); // finit par max(0,9)=9
    }

    @Test void initPathIsContiguousNeighbours() {
        loadTestGrid();
        PathTable.init();
        byte[] p = PathTable.paths[0][9];
        for (int i = 1; i < p.length; i++) {
            int a = p[i - 1] & 0xFF;
            int b = p[i] & 0xFF;
            int dx = Math.abs((PathTable.cellX[a] & 0xFF) - (PathTable.cellX[b] & 0xFF));
            int dy = Math.abs((PathTable.cellY[a] & 0xFF) - (PathTable.cellY[b] & 0xFF));
            assertThat(dx + dy).isEqualTo(1); // 4-connexite
        }
    }

    @Test void initSelfPathIsSingleton() {
        loadTestGrid();
        PathTable.init();
        byte[] p = PathTable.paths[3][3];
        assertThat(p.length).isEqualTo(1);
        assertThat(p[0] & 0xFF).isEqualTo(3);
    }

    @Test void distanceById() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.distance(0, 9)).isEqualTo(5);
        assertThat(PathTable.distance(9, 0)).isEqualTo(5);
        assertThat(PathTable.distance(3, 3)).isEqualTo(0);
    }

    @Test void distanceByCoord() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.distance(0, 0, 3, 2)).isEqualTo(5);
        assertThat(PathTable.distance(3, 2, 0, 0)).isEqualTo(5);
    }

    @Test void stepAlongForwardReadsPathDirectly() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.stepAlong(0, 9, 0)).isEqualTo(0); // etape 0 = source
        assertThat(PathTable.stepAlong(0, 9, 5)).isEqualTo(9); // etape 5 = destination
    }

    @Test void stepAlongReverseReadsPathBackward() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.stepAlong(9, 0, 0)).isEqualTo(9); // etape 0 = source = 9
        assertThat(PathTable.stepAlong(9, 0, 5)).isEqualTo(0); // etape 5 = destination = 0
        int forwardStep1 = PathTable.stepAlong(0, 9, 1);
        int reverseStep4 = PathTable.stepAlong(9, 0, 4);
        assertThat(forwardStep1).isEqualTo(reverseStep4);
    }

    @Test void stepAlongClampsBeyondDistance() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.stepAlong(0, 9, 99)).isEqualTo(9);
        assertThat(PathTable.stepAlong(9, 0, 99)).isEqualTo(0);
    }

    @Test void stepAlongSelfReturnsSelf() {
        loadTestGrid();
        PathTable.init();
        assertThat(PathTable.stepAlong(7, 7, 0)).isEqualTo(7);
        assertThat(PathTable.stepAlong(7, 7, 3)).isEqualTo(7);
    }

    @Test void stepAlongByCoordReturnsCoord() {
        loadTestGrid();
        PathTable.init();
        int after = PathTable.stepAlong(0, 0, 3, 2, 2);
        int x = after % GameState.width;
        int y = after / GameState.width;
        int dx = Math.abs(x - 0);
        int dy = Math.abs(y - 0);
        assertThat(dx + dy).isEqualTo(2);
    }
}
