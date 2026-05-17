package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorHarvestTest {

    @BeforeEach void grid() {
        String[] rows = { "......", ".0....", "......", "......", "......" };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
    }

    @Test void fruitCarryContributesToFitness() {
        // État final : troll player=0 porte 2 bananes, 0 wood, score identique
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        source.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 2;
        source.trollCarryTotal[0] = 2;
        if (source.treeCellIndex == null)
            source.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.treeCellIndex, (byte) -1);
        if (source.trollCellIndex == null)
            source.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.trollCellIndex, (byte) -1);
        source.trollCellIndex[source.trollY[0] * GameState.width + source.trollX[0]] = 0;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();

        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
        // 2 bananes en transit × ALPHA_FRUIT_CARRY = minimum attendu
        assertThat(fit).isGreaterThanOrEqualTo(2 * GenomeEvaluator.ALPHA_FRUIT_CARRY - 1e-9);
    }

    @Test void fruitCarryIgnoresOpponent() {
        // Troll player=1 porte des fruits → ne contribue pas à la fitness
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 1; // adversaire
        source.trollInventory[0 * ResourceType.COUNT + ResourceType.PLUM] = 3;
        source.trollCarryTotal[0] = 3;
        if (source.treeCellIndex == null)
            source.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.treeCellIndex, (byte) -1);
        if (source.trollCellIndex == null)
            source.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.trollCellIndex, (byte) -1);
        source.trollCellIndex[source.trollY[0] * GameState.width + source.trollX[0]] = 0;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();

        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
        assertThat(fit).isLessThanOrEqualTo(1e-9); // 0.0, les fruits adversaire ne comptent pas
    }
}
