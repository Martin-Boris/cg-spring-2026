package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsMineTest {

    private GameState state;

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
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        Genome.initIronCandidates();

        state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 1; state.trollY[0] = 0;
        state.trollMS[0] = 2; state.trollCC[0] = 3;
        state.trollHP[0] = 0; state.trollCP[0] = 1;
        state.turn = 10;
    }

    @Test void initRandom_seedsMineGeneBeforeCutoff() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(42);
        boolean found = false;
        for (int attempt = 0; attempt < 50 && !found; attempt++) {
            GenomeOps.initRandom(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isMine((short) Genome.gene(buf, 0, j, k))) found = true;
                }
            }
        }
        assertThat(found).isTrue();
    }

    @Test void initRandom_noMineGeneAtCutoff() {
        state.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noMineGeneWhenTrollHasNoCp() {
        state.trollCP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(11);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noMineWhenNoCandidates() {
        int saved = Genome.ironCandidateCount;
        Genome.ironCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(3);
            for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
                }
            }
        } finally {
            Genome.ironCandidateCount = saved;
        }
    }

    @Test void mutateInsertMine_insertsGeneBeforeCutoff() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(13);
        boolean inserted = false;
        for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
            GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isMine((short) Genome.gene(buf, 0, j, k))) inserted = true;
                }
            }
        }
        assertThat(inserted).isTrue();
    }

    @Test void mutateInsertMine_noopAtCutoff() {
        state.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(5);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void mutateInsertMine_noopWhenNoCandidates() {
        int saved = Genome.ironCandidateCount;
        Genome.ironCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(9);
            for (int i = 0; i < 30; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
                }
            }
        } finally {
            Genome.ironCandidateCount = saved;
        }
    }

    @Test void mutateInsertMine_noopWhenTrollHasNoCp() {
        state.trollCP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(15);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void mutateInsertMine_noDuplicateInIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        short c = Genome.ironCandidates[0];
        Genome.setGene(buf, 0, 0, 0, Genome.makeMine(Genome.candX(c), Genome.candY(c)));
        Genome.setLen(lens, 0, 0, 1);
        SplittableRandom rng = new SplittableRandom(21);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
        int total = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) total += Genome.len(lens, 0, j);
        assertThat(total).isEqualTo(1);
    }
}
