package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsPrevBestTest {

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

    private static short[] newPopBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newPopLen() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private static short[] newPrevBuf() {
        short[] buf = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newPrevLen() {
        return new byte[GameState.MAX_TROLLS];
    }

    private static void putPrev(short[] prevBuf, byte[] prevLenBuf, int trollIdx, int[][] cells) {
        int off = trollIdx * Genome.MAX_TARGETS_PER_TROLL;
        for (int k = 0; k < cells.length; k++) {
            prevBuf[off + k] = Genome.encode(cells[k][0], cells[k][1]);
        }
        prevLenBuf[trollIdx] = (byte) cells.length;
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

    @Test void dropsFirstGeneWhenTargetChopped() {
        // Trolls (0,0). Trees : (1,0) MORT, (2,0) vivant. Prev-best troll 0 : [(1,0), (2,0)].
        // Attendu : (1,0) retiré, len=1, gène [(2,0)].
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 0}, {2, 0, 5} }   // (1,0) health=0 → mort
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {2, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        short g0 = (short) Genome.gene(dst, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(2);
        assertThat(Genome.geneY(g0)).isEqualTo(0);
    }

    @Test void compactsDeadTreeInMiddle() {
        // Trolls (0,0). Prev-best troll 0 : [(1,0), (3,0)_MORT, (2,0)]. Attendu : [(1,0), (2,0)].
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 5}, {3, 0, 0}, {2, 0, 5} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {3, 0}, {2, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(2);
        short g0 = (short) Genome.gene(dst, 0, 0, 0);
        short g1 = (short) Genome.gene(dst, 0, 0, 1);
        assertThat(Genome.geneX(g0)).isEqualTo(1); assertThat(Genome.geneY(g0)).isEqualTo(0);
        assertThat(Genome.geneX(g1)).isEqualTo(2); assertThat(Genome.geneY(g1)).isEqualTo(0);
    }

    @Test void emptyPrevLenProducesEmptySegment() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{ {3, 3, 5} });
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(dstLen, 0, j)).isEqualTo(0);
        }
        int base = Genome.offset(0, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) {
            assertThat(dst[base + k]).isEqualTo(Genome.EMPTY_GENE);
        }
    }
}
