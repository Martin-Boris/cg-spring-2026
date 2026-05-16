package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomePlantTest {

    @Test void makeTargetIsNotPlant() {
        short g = Genome.makeTarget(5, 7);
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(7);
    }

    @Test void makePlantRoundTripCoords() {
        short g = Genome.makePlant(3, 9, TreeType.APPLE);
        assertThat(Genome.isPlant(g)).isTrue();
        assertThat(Genome.geneX(g)).isEqualTo(3);
        assertThat(Genome.geneY(g)).isEqualTo(9);
    }

    @Test void makePlantRoundTripFruitType() {
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.PLUM))).isEqualTo((int) TreeType.PLUM);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.LEMON))).isEqualTo((int) TreeType.LEMON);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.APPLE))).isEqualTo((int) TreeType.APPLE);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.BANANA))).isEqualTo((int) TreeType.BANANA);
    }

    @Test void legacyEncodeStaysCompatible() {
        short legacy = Genome.encode(7, 4);
        short modern = Genome.makeTarget(7, 4);
        assertThat(legacy).isEqualTo(modern);
        assertThat(Genome.isPlant(legacy)).isFalse();
    }

    @Test void geneXMasksFiveBitsForPlant() {
        short g = Genome.makePlant(21, 10, TreeType.BANANA);
        assertThat(Genome.geneX(g)).isEqualTo(21);
        assertThat(Genome.geneY(g)).isEqualTo(10);
        assertThat(Genome.isPlant(g)).isTrue();
        assertThat(Genome.plantFruitType(g)).isEqualTo((int) TreeType.BANANA);
    }
}
