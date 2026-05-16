package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentPlantSmokeTest {

    @BeforeEach void grid() {
        String[] rows = {
            "..........",
            "..........",
            "....0.....",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
    }

    @Test void emitsAtLeastOnePlantOverASingleDecide() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX - 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollMS[0] = 2; s.trollCC[0] = 1; s.trollHP[0] = 1; s.trollCP[0] = 3;
        s.shackInventory[ResourceType.LEMON] = 5;

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, System.nanoTime() + 200_000_000L, out);
        boolean planted = false;
        for (int j = 0; j < GameState.MAX_TROLLS && !planted; j++) {
            int len = Genome.len(agent.pop.curLen, agent.lastBestIdx, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(agent.pop.cur, agent.lastBestIdx, j, k);
                if (Genome.isPlant(g)) { planted = true; break; }
            }
        }
        assertThat(planted)
            .as("Best genome contains a PLANT gene when only PLANT-cycle yields score")
            .isTrue();
    }
}
