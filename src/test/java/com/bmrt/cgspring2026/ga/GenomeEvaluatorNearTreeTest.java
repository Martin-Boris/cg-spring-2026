package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorNearTreeTest {

    @BeforeEach void grid() {
        // Shack en (1,1). Carte 10x6 grass.
        String[] rows = {
            "..........",
            ".0........",
            "..........",
            "..........",
            "..........",
            ".........."
        };
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
        s.turn = 10;
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

    private void addTree(GameState s, int x, int y, byte type) {
        int i = s.treeCount++;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeHealth[i] = 5;
        s.treeType[i] = type;
        s.treeCellIndex[y * GameState.width + x] = (byte) i;
    }

    @Test void plumNearShackAddsAlphaNearTree() {
        GameState withPlum = emptyState();
        addTree(withPlum, 3, 1, TreeType.PLUM);   // d=2 du shack (1,1)
        GameState withoutTrees = emptyState();

        double fitWith    = fitnessOf(withPlum);
        double fitWithout = fitnessOf(withoutTrees);

        assertThat(fitWith - fitWithout).isGreaterThanOrEqualTo(GenomeEvaluator.ALPHA_NEAR_TREE - 1e-9);
    }

    @Test void threeTypesNearShackAddsThreeAlpha() {
        GameState s = emptyState();
        addTree(s, 2, 1, TreeType.PLUM);
        addTree(s, 3, 1, TreeType.LEMON);
        addTree(s, 4, 1, TreeType.APPLE);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit - fitRef).isGreaterThanOrEqualTo(3 * GenomeEvaluator.ALPHA_NEAR_TREE - 1e-9);
        assertThat(fit - fitRef).isLessThanOrEqualTo(3 * GenomeEvaluator.ALPHA_NEAR_TREE + 1e-9);
    }

    @Test void duplicateTypeNotDoubleCounted() {
        GameState s = emptyState();
        addTree(s, 2, 1, TreeType.PLUM);
        addTree(s, 3, 1, TreeType.PLUM);  // 2e PLUM proche : pas de bonus additionnel

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit - fitRef).isLessThanOrEqualTo(GenomeEvaluator.ALPHA_NEAR_TREE + 1e-9);
    }

    @Test void treeBeyondDistance5IsIgnored() {
        GameState s = emptyState();
        // shack (1,1), arbre en (8,5) : dist Manhattan = 7+4 = 11. Hors zone.
        addTree(s, 8, 5, TreeType.PLUM);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void bananaNearShackIsIgnored() {
        GameState s = emptyState();
        addTree(s, 3, 1, TreeType.BANANA);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void deadTreeIsIgnored() {
        GameState s = emptyState();
        int i = s.treeCount++;
        s.treeX[i] = 3; s.treeY[i] = 1;
        s.treeHealth[i] = 0;  // mort
        s.treeType[i] = TreeType.PLUM;

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void disabledAfterCutoff() {
        GameState s = emptyState();
        addTree(s, 3, 1, TreeType.PLUM);
        s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        GameState ref = emptyState();
        ref.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(ref);

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }
}
