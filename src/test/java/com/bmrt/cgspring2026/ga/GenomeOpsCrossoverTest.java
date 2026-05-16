package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsCrossoverTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private static GameState makeStateWithTrees(int trees, int ownTrolls) {
        GameState s = new GameState();
        for (int i = 0; i < trees; i++) {
            s.treeCount++;
            s.treeX[i] = (byte) (i % GameState.width);
            s.treeY[i] = (byte) (i / GameState.width);
            s.treeHealth[i] = 5;
        }
        for (int i = 0; i < ownTrolls; i++) { s.trollCount++; s.trollPlayer[i] = 0; }
        return s;
    }

    @Test void crossoverProducesValidOffspring() {
        short[] src = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(src, Genome.EMPTY_GENE);
        byte[] srcLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        GameState s = makeStateWithTrees(15, 3);
        SplittableRandom rng = new SplittableRandom(1);
        GenomeOps.initRandom(s, src, srcLen, 0, rng); // parent 1 dans slot 0
        GenomeOps.initRandom(s, src, srcLen, 1, rng); // parent 2 dans slot 1

        GenomeOps.crossover(src, srcLen, 0, src, srcLen, 1, dst, dstLen, 0, rng);
        assertThat(GenomeInvariants.check(dst, dstLen, 0)).isTrue();
    }

    @Test void crossoverInheritsFromBothParents() {
        // Parent 1 troll 0 : [(1,1),(2,2),(3,3)]
        // Parent 2 troll 0 : [(3,3),(4,4),(5,5)]
        // L'offspring doit contenir des éléments des deux (selon point de coupure)
        short[] src = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(src, Genome.EMPTY_GENE);
        byte[] srcLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(src, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(src, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(src, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(srcLen, 0, 0, 3);
        Genome.setGene(src, 1, 0, 0, Genome.encode(3, 3));
        Genome.setGene(src, 1, 0, 1, Genome.encode(4, 4));
        Genome.setGene(src, 1, 0, 2, Genome.encode(5, 5));
        Genome.setLen(srcLen, 1, 0, 3);

        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        // Plusieurs seeds pour couvrir les cas
        for (int seed = 0; seed < 20; seed++) {
            java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
            java.util.Arrays.fill(dstLen, (byte) 0);
            GenomeOps.crossover(src, srcLen, 0, src, srcLen, 1, dst, dstLen, 0, new SplittableRandom(seed));
            assertThat(GenomeInvariants.check(dst, dstLen, 0)).isTrue();
            int len = Genome.len(dstLen, 0, 0);
            assertThat(len).isBetween(0, 5); // au pire 5 arbres distincts disponibles
        }
    }

    @Test void crossoverPreservesTargetAndPlantOnSameCell() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.makeTarget(3, 4));
        Genome.setGene(buf, 0, 0, 1, Genome.makePlant(3, 4, com.bmrt.cgspring2026.model.TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 2);
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        GenomeOps.crossover(buf, lens, 0, buf, lens, 0, buf, lens, 1, rng);
        assertThat(Genome.len(lens, 1, 0)).isEqualTo(2);
    }
}
