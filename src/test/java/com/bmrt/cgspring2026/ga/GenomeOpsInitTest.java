package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsInitTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private static GameState makeState(int trees, int ownTrolls, int oppTrolls) {
        GameState s = new GameState();
        for (int i = 0; i < trees; i++) {
            s.treeCount++;
            s.treeX[i] = (byte) (i % GameState.width);
            s.treeY[i] = (byte) (i / GameState.width);
            s.treeHealth[i] = 5;
        }
        for (int i = 0; i < ownTrolls; i++) {
            s.trollCount++;
            s.trollPlayer[i] = 0;
        }
        for (int i = 0; i < oppTrolls; i++) {
            int idx = ownTrolls + i;
            s.trollCount++;
            s.trollPlayer[idx] = 1;
        }
        return s;
    }

    @Test void emptyStateProducesEmptyIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(0, 1, 0);
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(42));
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(lenBuf, 0, j)).isEqualTo(0);
        }
    }

    @Test void unicityGloballyEnforced() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(20, 3, 1);
        GenomeOps.initRandom(s, buf, lenBuf, 5, new SplittableRandom(123));
        assertThat(GenomeInvariants.check(buf, lenBuf, 5)).isTrue();
    }

    @Test void onlyPlayerZeroTrollsReceiveTargets() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(10, 2, 2); // trolls 0,1 = me ; 2,3 = opp
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(7));
        assertThat(Genome.len(lenBuf, 0, 2)).isEqualTo(0);
        assertThat(Genome.len(lenBuf, 0, 3)).isEqualTo(0);
    }

    @Test void totalAssignedRespectsSkipProbability() {
        // Avec P_SKIP_INIT = 0.30, on s'attend ~70% assignés sur grand échantillon
        int trees = 30;
        int totalAssigned = 0;
        int trials = 200;
        SplittableRandom rng = new SplittableRandom(1);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        for (int t = 0; t < trials; t++) {
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            java.util.Arrays.fill(lenBuf, (byte) 0);
            GameState s = makeState(trees, 3, 0);
            GenomeOps.initRandom(s, buf, lenBuf, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                totalAssigned += Genome.len(lenBuf, 0, j);
            }
        }
        double avg = (double) totalAssigned / trials;
        // espérance = trees * (1 - P_SKIP_INIT) = 30 * 0.7 = 21
        assertThat(avg).isBetween(18.0, 24.0);
    }

    @Test void allAssignedCoordsAreFromState() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(8, 2, 0);
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(99));
        Set<Integer> validCells = new HashSet<>();
        for (int i = 0; i < s.treeCount; i++) {
            validCells.add(((s.treeX[i] & 0xFF) << 8) | (s.treeY[i] & 0xFF));
        }
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lenBuf, 0, j);
            for (int k = 0; k < len; k++) {
                int g = Genome.gene(buf, 0, j, k) & 0xFFFF;
                assertThat(validCells).contains(g);
            }
        }
    }
}
