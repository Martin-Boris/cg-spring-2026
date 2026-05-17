package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeHarvestTest {

    @BeforeEach void grid() {
        GameState.width = 6; GameState.height = 5;
        GameState.tiles = new byte[6 * 5];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 6 + 1] = TileType.SHACK_ME;
        GameState.shackMeX = 1; GameState.shackMeY = 1;
    }

    @Test void makeHarvestRoundTrip() {
        short g = Genome.makeHarvest(5, 12);
        assertThat(Genome.isHarvest(g)).isTrue();
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(12);
    }

    @Test void isHarvestFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isHarvest(g)).isFalse();
    }

    @Test void isHarvestFalseForCutGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isHarvest(g)).isFalse();
    }

    @Test void isHarvestFalseForEmptyGene() {
        assertThat(Genome.isHarvest(Genome.EMPTY_GENE)).isFalse();
    }

    @Test void initHarvestCandidates_picksSize4Trees() {
        GameState s = new GameState();
        s.treeCount = 3;
        s.treeX[0] = 2; s.treeY[0] = 2; s.treeSize[0] = 4; s.treeHealth[0] = 6;
        s.treeX[1] = 3; s.treeY[1] = 3; s.treeSize[1] = 2; s.treeHealth[1] = 8; // trop petit
        s.treeX[2] = 4; s.treeY[2] = 1; s.treeSize[2] = 4; s.treeHealth[2] = 0; // mort
        Genome.initHarvestCandidates(s);
        assertThat(Genome.harvestCandidateCount).isEqualTo(1);
        short c = Genome.harvestCandidates[0];
        assertThat(Genome.candX(c)).isEqualTo(2);
        assertThat(Genome.candY(c)).isEqualTo(2);
    }

    @Test void initHarvestCandidates_emptyWhenNoMatureTree() {
        GameState s = new GameState();
        s.treeCount = 2;
        s.treeX[0] = 1; s.treeY[0] = 3; s.treeSize[0] = 3; s.treeHealth[0] = 5;
        s.treeX[1] = 2; s.treeY[1] = 2; s.treeSize[1] = 1; s.treeHealth[1] = 3;
        Genome.initHarvestCandidates(s);
        assertThat(Genome.harvestCandidateCount).isEqualTo(0);
    }
}
