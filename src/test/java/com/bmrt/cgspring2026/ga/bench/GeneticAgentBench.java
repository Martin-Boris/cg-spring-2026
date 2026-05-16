package com.bmrt.cgspring2026.ga.bench;

import com.bmrt.cgspring2026.ga.GeneticAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("manual bench, run avec -Dtest=GeneticAgentBench")
class GeneticAgentBench {

    @Test void runMidGameBench() {
        // Carte 16×8 réaliste avec eau
        String[] rows = {
            "................",
            ".0..............",
            "....~~~.........",
            "....~...........",
            "........~~......",
            "..........~~....",
            ".............1..",
            "................"
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

        // 8 arbres, 3 trolls par côté
        GameState s = new GameState();
        s.turn = 30;
        int[][] trees = {{3,3,TreeType.PLUM},{6,5,TreeType.LEMON},{8,2,TreeType.APPLE},
                         {10,6,TreeType.BANANA},{12,3,TreeType.PLUM},{4,5,TreeType.LEMON},
                         {7,3,TreeType.APPLE},{11,4,TreeType.BANANA}};
        for (int[] t : trees) {
            int i = s.treeCount++;
            s.treeType[i] = (byte) t[2];
            s.treeX[i] = (byte) t[0]; s.treeY[i] = (byte) t[1];
            s.treeSize[i] = 3; s.treeHealth[i] = 10; s.treeCooldown[i] = 5;
        }
        int[][] trolls = {{0,2,1,2,8,1,5},{0,3,2,2,6,1,4},{0,1,2,2,4,1,3},
                          {1,13,7,2,8,1,5},{1,12,7,2,6,1,4},{1,14,6,2,4,1,3}};
        for (int[] tr : trolls) {
            int i = s.trollCount++;
            s.trollPlayer[i] = (byte) tr[0];
            s.trollX[i] = (byte) tr[1]; s.trollY[i] = (byte) tr[2];
            s.trollMS[i] = (byte) tr[3]; s.trollCC[i] = (byte) tr[4];
            s.trollHP[i] = (byte) tr[5]; s.trollCP[i] = (byte) tr[6];
        }

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];

        // Warmup
        for (int i = 0; i < 5; i++) {
            agent.decide(s, System.nanoTime() + 40_000_000L, out);
        }

        // Bench : 50 décisions, 40ms chacune
        long totalNs = 0;
        int totalGen = 0;
        double bestFit = Double.NEGATIVE_INFINITY;
        int trials = 50;
        for (int i = 0; i < trials; i++) {
            long t0 = System.nanoTime();
            long deadline = t0 + 40_000_000L;
            agent.decide(s, deadline, out);
            totalNs += System.nanoTime() - t0;
            totalGen += agent.lastGenCount();
            if (agent.lastBestFitness() > bestFit) bestFit = agent.lastBestFitness();
        }

        System.out.println("=== GeneticAgent Bench ===");
        System.out.printf("trials=%d  avg=%.2f ms  avg_gen=%.2f  best_fit=%.2f%n",
            trials, totalNs / 1e6 / trials, (double) totalGen / trials, bestFit);
    }
}
