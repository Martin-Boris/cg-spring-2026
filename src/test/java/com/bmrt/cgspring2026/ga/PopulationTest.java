package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationTest {

    @Test void buffersAreSizedCorrectly() {
        Population p = new Population();
        assertThat(p.bufA).hasSize(Genome.POP_SIZE * Genome.SLOTS_PER_GENOME);
        assertThat(p.bufB).hasSize(Genome.POP_SIZE * Genome.SLOTS_PER_GENOME);
        assertThat(p.lenA).hasSize(Genome.POP_SIZE * GameState.MAX_TROLLS);
        assertThat(p.lenB).hasSize(Genome.POP_SIZE * GameState.MAX_TROLLS);
        assertThat(p.fitA).hasSize(Genome.POP_SIZE);
        assertThat(p.fitB).hasSize(Genome.POP_SIZE);
    }

    @Test void initiallyCurPointsToBufA() {
        Population p = new Population();
        assertThat(p.cur).isSameAs(p.bufA);
        assertThat(p.nxt).isSameAs(p.bufB);
        assertThat(p.curLen).isSameAs(p.lenA);
        assertThat(p.nxtLen).isSameAs(p.lenB);
        assertThat(p.curFit).isSameAs(p.fitA);
        assertThat(p.nxtFit).isSameAs(p.fitB);
    }

    @Test void swapExchangesPointers() {
        Population p = new Population();
        p.swap();
        assertThat(p.cur).isSameAs(p.bufB);
        assertThat(p.nxt).isSameAs(p.bufA);
        assertThat(p.curLen).isSameAs(p.lenB);
        assertThat(p.nxtLen).isSameAs(p.lenA);
        assertThat(p.curFit).isSameAs(p.fitB);
        assertThat(p.nxtFit).isSameAs(p.fitA);
    }

    @Test void emptyClearsCurrentGenerationToEmpty() {
        Population p = new Population();
        p.cur[0] = 42;
        p.curLen[0] = 5;
        p.curFit[0] = 100.0;
        p.resetCurrent();
        assertThat(p.cur[0]).isEqualTo(Genome.EMPTY_GENE);
        assertThat(p.curLen[0]).isEqualTo((byte) 0);
        assertThat(p.curFit[0]).isEqualTo(0.0);
    }
}
