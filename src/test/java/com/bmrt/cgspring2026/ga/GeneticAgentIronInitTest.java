package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentIronInitTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
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
        // Volontairement NE PAS appeler Genome.initIronCandidates ici
        Genome.ironCandidateCount = 0;
    }

    @Test void decide_initializesIronCandidates() {
        GameState state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 0; state.trollY[0] = 0; // case GRASS (shack en (1,1))
        state.trollMS[0] = 1; state.trollCC[0] = 3;
        state.trollHP[0] = 1; state.trollCP[0] = 1;
        state.turn = 0;
        if (state.treeCellIndex == null)
            state.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(state.treeCellIndex, (byte) -1);
        if (state.trollCellIndex == null)
            state.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(state.trollCellIndex, (byte) -1);
        state.trollCellIndex[0] = 0;

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 200_000_000L; // 200ms
        agent.decide(state, deadline, out);

        assertThat(Genome.ironCandidateCount).isEqualTo(1);
    }
}
