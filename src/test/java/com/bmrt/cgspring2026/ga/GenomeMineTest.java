package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeMineTest {

    @Test void makeMineRoundTrip() {
        short g = Genome.makeMine(5, 12);
        assertThat(Genome.isMine(g)).isTrue();
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.isHarvest(g)).isFalse();
        assertThat(Genome.isCut(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(12);
    }

    @Test void isMineFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForHarvestGene() {
        short g = Genome.makeHarvest(3, 4);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForCutGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForEmptyGene() {
        assertThat(Genome.isMine(Genome.EMPTY_GENE)).isFalse();
    }

    @Test void isCutTrueForPlainEncodedGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isCut(g)).isTrue();
    }

    @Test void isCutFalseForMineGene() {
        short g = Genome.makeMine(5, 12);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForHarvestGene() {
        short g = Genome.makeHarvest(5, 12);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForEmptyGene() {
        assertThat(Genome.isCut(Genome.EMPTY_GENE)).isFalse();
    }
}
