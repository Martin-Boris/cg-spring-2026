package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsPrevBestMineTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        Genome.initIronCandidates();
    }

    @Test void initFromPrevBest_preservesMineGeneEvenWithoutTree() {
        short[] prev = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(prev, Genome.EMPTY_GENE);
        prev[0] = Genome.makeMine(4, 2);
        byte[] prevLen = new byte[GameState.MAX_TROLLS];
        prevLen[0] = 1;

        GameState state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 1; state.trollY[0] = 0;
        state.treeCount = 0;

        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        GenomeOps.initFromPrevBest(state, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        assertThat(Genome.isMine((short) Genome.gene(dst, 0, 0, 0))).isTrue();
        assertThat(Genome.geneX((short) Genome.gene(dst, 0, 0, 0))).isEqualTo(4);
        assertThat(Genome.geneY((short) Genome.gene(dst, 0, 0, 0))).isEqualTo(2);
    }
}
