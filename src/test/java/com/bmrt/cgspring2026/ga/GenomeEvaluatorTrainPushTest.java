package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorTrainPushTest {

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

    private GameState emptyState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        if (s.treeCellIndex == null)
            s.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(s.treeCellIndex, (byte) -1);
        if (s.trollCellIndex == null)
            s.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(s.trollCellIndex, (byte) -1);
        s.trollCellIndex[0] = 0;
        return s;
    }

    private double fitnessOf(GameState source) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();
        return GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
    }

    @Test void ironCarryContributesToFitness() {
        GameState s = emptyState();
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 2;
        s.trollCarryTotal[0] = 2;
        double fit = fitnessOf(s);
        assertThat(fit).isGreaterThanOrEqualTo(2 * GenomeEvaluator.ALPHA_IRON_CARRY - 1e-9);
    }

    @Test void trainPushBoostsShackIronTowardThreshold() {
        GameState withStock = emptyState();
        withStock.shackInventory[ResourceType.IRON] = 1;
        withStock.turn = 10;
        GameState withoutStock = emptyState();
        withoutStock.turn = 10;

        double fitWith    = fitnessOf(withStock);
        double fitWithout = fitnessOf(withoutStock);

        assertThat(fitWith - fitWithout).isGreaterThanOrEqualTo(GenomeEvaluator.ALPHA_TRAIN_PUSH - 1e-9);
    }

    @Test void trainPushDisabledAfterCutoff() {
        GameState withStock = emptyState();
        withStock.shackInventory[ResourceType.IRON] = 1;
        withStock.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        GameState withoutStock = emptyState();
        withoutStock.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        double fitWith    = fitnessOf(withStock);
        double fitWithout = fitnessOf(withoutStock);

        assertThat(fitWith).isCloseTo(fitWithout, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void trainPushDisabledAtTrollCap() {
        GameState s = emptyState();
        s.trollCount = GenomeEvaluator.TRAIN_PUSH_TROLL_CAP;
        for (int i = 0; i < s.trollCount; i++) {
            s.trollPlayer[i] = 0;
            s.trollX[i] = (byte) (i % GameState.width);
            s.trollY[i] = 0;
        }
        s.shackInventory[ResourceType.IRON] = 1;
        s.turn = 10;
        java.util.Arrays.fill(s.trollCellIndex, (byte) -1);
        for (int i = 0; i < s.trollCount; i++) {
            int cell = s.trollY[i] * GameState.width + s.trollX[i];
            if (s.trollCellIndex[cell] == -1) s.trollCellIndex[cell] = (byte) i;
        }

        GameState ref = emptyState();
        ref.trollCount = GenomeEvaluator.TRAIN_PUSH_TROLL_CAP;
        for (int i = 0; i < ref.trollCount; i++) {
            ref.trollPlayer[i] = 0;
            ref.trollX[i] = (byte) (i % GameState.width);
            ref.trollY[i] = 0;
        }
        ref.turn = 10;
        java.util.Arrays.fill(ref.trollCellIndex, (byte) -1);
        for (int i = 0; i < ref.trollCount; i++) {
            int cell = ref.trollY[i] * GameState.width + ref.trollX[i];
            if (ref.trollCellIndex[cell] == -1) ref.trollCellIndex[cell] = (byte) i;
        }

        double fitWith    = fitnessOf(s);
        double fitWithout = fitnessOf(ref);

        assertThat(fitWith).isCloseTo(fitWithout, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void trainPushCapsAtThreshold() {
        GameState big = emptyState();
        big.shackInventory[ResourceType.IRON] = 10;
        big.turn = 10;
        GameState atTarget = emptyState();
        atTarget.shackInventory[ResourceType.IRON] = 2;
        atTarget.turn = 10;

        double fitBig    = fitnessOf(big);
        double fitTarget = fitnessOf(atTarget);

        assertThat(fitBig).isCloseTo(fitTarget, org.assertj.core.data.Offset.offset(1e-9));
    }
}
