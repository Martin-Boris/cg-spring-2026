package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentPrevBestTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            "......",
            "......",
            "......",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                GameState.tiles[y * GameState.width + x] = TileType.fromChar(rows[y].charAt(x));
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    private static GameState simpleState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.treeCount = 2;
        s.treeX[0] = 5; s.treeY[0] = 5; s.treeHealth[0] = 5;
        s.treeX[1] = 1; s.treeY[1] = 0; s.treeHealth[1] = 5;
        return s;
    }

    @Test void stashesBestGenomeBetweenTurns() {
        GameState s = simpleState();
        GeneticAgent agent = new GeneticAgent();
        // Avant : pas de stash
        assertThat(agent.hasPrevBest).isFalse();

        // Deadline déjà passée → aucune génération ne tourne, lastBestIdx est
        // simplement l'argmax des fitness initiales.
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        // Après : stash actif et égal au génome de lastBestIdx
        assertThat(agent.hasPrevBest).isTrue();
        int bestIdx = agent.lastBestIdx;
        int srcBase = Genome.offset(bestIdx, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) {
            assertThat(agent.prevBestBuf[k])
                .as("prevBestBuf[%d] vs pop.cur slot %d", k, bestIdx)
                .isEqualTo(agent.pop.cur[srcBase + k]);
        }
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(agent.prevBestLen[j])
                .as("prevBestLen[%d] vs pop.curLen slot %d", j, bestIdx)
                .isEqualTo(agent.pop.curLen[Genome.lenOffset(bestIdx, j)]);
        }
    }
}
