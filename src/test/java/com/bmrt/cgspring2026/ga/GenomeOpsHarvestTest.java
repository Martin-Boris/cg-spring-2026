package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsHarvestTest {

    private GameState state;

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
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

        state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 0; state.trollY[0] = 0;
        state.trollMS[0] = 2; state.trollCC[0] = 3; state.trollHP[0] = 2; state.trollCP[0] = 1;
        // Un arbre de taille 4 vivant
        state.treeCount = 1;
        state.treeX[0] = 4; state.treeY[0] = 3;
        state.treeSize[0] = 4; state.treeHealth[0] = 6;
        Genome.initHarvestCandidates(state);
    }

    // --- initRandom seeds harvest ---

    @Test void initRandom_seedsHarvestGeneWhenTrollHasHP() {
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
                    if (Genome.isHarvest((short) Genome.gene(buf, 0, j, k))) found = true;
                }
            }
        }
        assertThat(found).isTrue();
    }

    @Test void initRandom_noHarvestGeneWhenTrollHasNoHP() {
        state.trollHP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noHarvestWhenNoCandidates() {
        Genome.harvestCandidateCount = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(3);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    // --- mutateInsertHarvest ---

    @Test void mutateInsertHarvest_insertsGeneForTrollWithHP() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(13);
        boolean inserted = false;
        for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
            GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isHarvest((short) Genome.gene(buf, 0, j, k))) inserted = true;
                }
            }
        }
        assertThat(inserted).isTrue();
    }

    @Test void mutateInsertHarvest_noopWhenNoCandidates() {
        int saved = Genome.harvestCandidateCount;
        Genome.harvestCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(5);
            for (int i = 0; i < 30; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
                }
            }
        } finally {
            Genome.harvestCandidateCount = saved;
        }
    }

    @Test void mutateInsertHarvest_noopWhenTrollHasNoHP() {
        state.trollHP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(9);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void mutateInsertHarvest_noDuplicateInIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // Pré-remplir avec le seul candidat existant
        short c = Genome.harvestCandidates[0];
        Genome.setGene(buf, 0, 0, 0, Genome.makeHarvest(Genome.candX(c), Genome.candY(c)));
        Genome.setLen(lens, 0, 0, 1);
        SplittableRandom rng = new SplittableRandom(17);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
        // Il n'y a qu'un seul candidat → pas d'insertion possible → toujours 1 gène
        int total = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) total += Genome.len(lens, 0, j);
        assertThat(total).isEqualTo(1);
    }
}
