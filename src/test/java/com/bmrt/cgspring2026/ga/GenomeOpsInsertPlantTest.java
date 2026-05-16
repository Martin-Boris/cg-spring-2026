package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsInsertPlantTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
    }

    @Test void insertsPlantGeneOnFreshIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(7);
        boolean inserted = false;
        for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
            GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
            int totalPlant = 0;
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    short g = (short) Genome.gene(buf, 0, j, k);
                    if (Genome.isPlant(g)) totalPlant++;
                }
            }
            if (totalPlant > 0) inserted = true;
        }
        assertThat(inserted).isTrue();
    }

    @Test void noopWhenNoCandidates() {
        int saved = Genome.plantCandidateCount;
        Genome.plantCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(3);
            for (int i = 0; i < 50; i++) GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    short g = (short) Genome.gene(buf, 0, j, k);
                    assertThat(Genome.isPlant(g)).isFalse();
                }
            }
        } finally {
            Genome.plantCandidateCount = saved;
        }
    }

    @Test void skipsCellAlreadyPlantInIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int cnt = Math.min(Genome.MAX_TARGETS_PER_TROLL, Genome.plantCandidateCount);
        for (int p = 0; p < cnt; p++) {
            short c = Genome.plantCandidates[p];
            Genome.setGene(buf, 0, 0, p, Genome.makePlant(Genome.candX(c), Genome.candY(c), 0));
        }
        Genome.setLen(lens, 0, 0, cnt);
        SplittableRandom rng = new SplittableRandom(11);
        int initialPlants = countPlants(buf, lens, 0);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
        int finalPlants = countPlants(buf, lens, 0);
        assertThat(finalPlants).isLessThanOrEqualTo(Genome.plantCandidateCount);
        assertThat(finalPlants).isGreaterThanOrEqualTo(initialPlants);
    }

    private static int countPlants(short[] buf, byte[] lens, int idx) {
        int c = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, idx, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, idx, j, k);
                if (Genome.isPlant(g)) c++;
            }
        }
        return c;
    }
}
