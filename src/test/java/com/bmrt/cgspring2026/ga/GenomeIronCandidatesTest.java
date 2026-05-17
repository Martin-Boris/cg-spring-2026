package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeIronCandidatesTest {

    @Test void initIronCandidates_picksIronCellWithGrassNeighbor() {
        GameState.width = 4; GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 4 + 1] = TileType.IRON;

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(1);
        short c = Genome.ironCandidates[0];
        assertThat(Genome.candX(c)).isEqualTo(1);
        assertThat(Genome.candY(c)).isEqualTo(1);
    }

    @Test void initIronCandidates_emptyWhenNoIron() {
        GameState.width = 4; GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(0);
    }

    @Test void initIronCandidates_excludesIronWithNoGrassNeighbor() {
        GameState.width = 3; GameState.height = 3;
        GameState.tiles = new byte[9];
        java.util.Arrays.fill(GameState.tiles, TileType.ROCK);
        GameState.tiles[1 * 3 + 1] = TileType.IRON;
        // les 4 voisins sont ROCK → cellule IRON non minable

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(0);
    }

    @Test void initIronCandidates_multipleIronCells() {
        GameState.width = 5; GameState.height = 3;
        GameState.tiles = new byte[15];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 5 + 1] = TileType.IRON;
        GameState.tiles[1 * 5 + 3] = TileType.IRON;

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(2);
    }
}
