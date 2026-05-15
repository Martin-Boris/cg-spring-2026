package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsWarmStartTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            "......",
            "......",
            "......",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                GameState.tiles[y * GameState.width + x] = TileType.fromChar(rows[y].charAt(x));
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    private static short[] newBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newLen() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private static GameState stateWith(int[][] trolls, int[][] trees) {
        GameState s = new GameState();
        for (int[] tr : trolls) {
            int i = s.trollCount++;
            s.trollPlayer[i] = (byte) tr[0];
            s.trollX[i]      = (byte) tr[1];
            s.trollY[i]      = (byte) tr[2];
        }
        for (int[] t : trees) {
            int i = s.treeCount++;
            s.treeX[i]      = (byte) t[0];
            s.treeY[i]      = (byte) t[1];
            s.treeHealth[i] = (byte) t[2];
        }
        return s;
    }

    @Test void emptyWhenNoOwnTrolls() {
        GameState s = stateWith(new int[][]{ {1, 0, 0} }, new int[][]{ {3, 3, 5} });
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(len, 0, j)).isEqualTo(0);
        }
    }

    @Test void emptyWhenNoTrees() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{});
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(0);
    }

    @Test void emptyWhenAllTreesDead() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{ {3, 3, 0}, {4, 4, 0} });
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(0);
    }

    @Test void assignsClosestTreeFirst() {
        // troll en (1,1), arbres en (1,2) [d=1] et (5,5) [d=8]
        GameState s = stateWith(
            new int[][]{ {0, 1, 1} },
            new int[][]{ {5, 5, 5}, {1, 2, 5} }   // ordre exprès non trié
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(1);
        assertThat(Genome.geneY(g0)).isEqualTo(2);
    }

    @Test void chainsFromLastAssignedTree() {
        // troll (0,0) ; arbres en (5,5), (1,0), (2,0).
        // Attendu : depuis (0,0) → (1,0) d=1 ; depuis (1,0) → (2,0) d=1 ; depuis (2,0) → (5,5) d=6
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {5, 5, 5}, {1, 0, 5}, {2, 0, 5} }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(3);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        short g1 = (short) Genome.gene(buf, 0, 0, 1);
        short g2 = (short) Genome.gene(buf, 0, 0, 2);
        assertThat(Genome.geneX(g0)).isEqualTo(1); assertThat(Genome.geneY(g0)).isEqualTo(0);
        assertThat(Genome.geneX(g1)).isEqualTo(2); assertThat(Genome.geneY(g1)).isEqualTo(0);
        assertThat(Genome.geneX(g2)).isEqualTo(5); assertThat(Genome.geneY(g2)).isEqualTo(5);
    }

    @Test void roundRobinDistributesBetweenTrolls() {
        // Troll0 (0,0), Troll1 (5,5).
        // Arbres : (1,0) près du T0 ; (5,4) près du T1 ; (0,1) près du T0 ; (4,5) près du T1.
        // Attendu : chaque troll récupère ses arbres voisins (couche 0 : (1,0) et (5,4)).
        GameState s = stateWith(
            new int[][]{ {0, 0, 0}, {0, 5, 5} },
            new int[][]{ {1, 0, 5}, {5, 4, 5}, {0, 1, 5}, {4, 5, 5} }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        // T0 prend (1,0) au k=0
        short t0k0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.geneX(t0k0)).isEqualTo(1);
        assertThat(Genome.geneY(t0k0)).isEqualTo(0);
        // T1 prend (5,4) au k=0
        short t1k0 = (short) Genome.gene(buf, 0, 1, 0);
        assertThat(Genome.geneX(t1k0)).isEqualTo(5);
        assertThat(Genome.geneY(t1k0)).isEqualTo(4);
    }
}
