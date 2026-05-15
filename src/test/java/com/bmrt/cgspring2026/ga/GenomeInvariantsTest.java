package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenomeInvariantsTest {

    @BeforeEach void setUp() {
        GameState.width  = 10;
        GameState.height = 10;
        GameState.tiles  = new byte[100];
    }

    @Test void passesForEmptyIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void detectsDuplicateAcrossTrolls() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 4));
        Genome.setLen(lenBuf, 0, 0, 1);
        Genome.setGene(buf, 0, 1, 0, Genome.encode(3, 4));
        Genome.setLen(lenBuf, 0, 1, 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }

    @Test void detectsOutOfBoundsCoord() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(15, 4)); // width=10 → 15 invalide
        Genome.setLen(lenBuf, 0, 0, 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }

    @Test void detectsLenExceedsMax() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lenBuf, 0, 0, Genome.MAX_TARGETS_PER_TROLL + 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }
}
