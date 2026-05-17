package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyCutFilterTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "......",
            "......"
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
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            TrollPolicy.cursorBuf[j] = 0;
            TrollPolicy.policyPhase[j] = 0;
        }
    }

    @Test void cutGeneSkippedWhenTrollHasNoCp() {
        // Troll cp=0 en (0,0), arbre en (3,2). Gène CUT pointant vers l'arbre.
        // Attendu : le gène CUT est sauté, curseur avance, plus de gènes → WAIT.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollMS[0] = 2; s.trollCC[0] = 2; s.trollHP[0] = 0; s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2)); // gène CUT vers (3,2)
        Genome.setLen(lens, 0, 0, 1);

        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillOwnActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(n).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo(ActionType.WAIT);
    }

    @Test void cutGeneExecutedWhenTrollHasCp() {
        // Troll cp>0 en (0,0), arbre en (3,2). Gène CUT → doit se déplacer vers l'arbre.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollMS[0] = 2; s.trollCC[0] = 2; s.trollHP[0] = 0; s.trollCP[0] = 1;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2));
        Genome.setLen(lens, 0, 0, 1);

        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillOwnActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(Action.type(out[0])).isEqualTo(ActionType.MOVE);
    }
}
