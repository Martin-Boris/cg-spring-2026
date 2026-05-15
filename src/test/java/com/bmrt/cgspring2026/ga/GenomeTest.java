package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeTest {

    @Test void slotsPerGenomeMatchesMaxTrollsTimesMaxTargets() {
        assertThat(Genome.SLOTS_PER_GENOME)
            .isEqualTo(GameState.MAX_TROLLS * Genome.MAX_TARGETS_PER_TROLL);
    }

    @Test void encodeAndDecodeCoords() {
        short g = Genome.encode(5, 11);
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(11);
    }

    @Test void offsetIsTrollSegmentStart() {
        // troll 0 of individu 0 → 0
        assertThat(Genome.offset(0, 0)).isEqualTo(0);
        // troll 1 of individu 0 → MAX_TARGETS_PER_TROLL
        assertThat(Genome.offset(0, 1)).isEqualTo(Genome.MAX_TARGETS_PER_TROLL);
        // troll 0 of individu 1 → SLOTS_PER_GENOME
        assertThat(Genome.offset(1, 0)).isEqualTo(Genome.SLOTS_PER_GENOME);
    }

    @Test void geneAndSetGeneRoundTrip() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        Genome.setGene(buf, 2, 3, 4, (short) 0x0A0B);
        assertThat(Genome.gene(buf, 2, 3, 4)).isEqualTo(0x0A0B);
    }

    @Test void lenAndSetLenRoundTrip() {
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lenBuf, 5, 7, 12);
        assertThat(Genome.len(lenBuf, 5, 7)).isEqualTo(12);
    }
}
