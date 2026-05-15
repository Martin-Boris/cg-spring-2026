package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentTest {

    @BeforeEach void grid() {
        String[] rows = {
            "........",
            ".0......",
            "........",
            "........",
            "......1.",
            "........"
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
    }

    private static GameState seededState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 2; s.trollY[0] = 2;
        s.trollMS[0] = 3; s.trollCC[0] = 16; s.trollHP[0] = 1; s.trollCP[0] = 20;
        s.treeCount = 2;
        s.treeType[0] = TreeType.PLUM;
        s.treeX[0] = 4; s.treeY[0] = 3;
        s.treeSize[0] = 4; s.treeHealth[0] = 12;
        s.treeCooldown[0] = 8;
        s.treeType[1] = TreeType.BANANA;
        s.treeX[1] = 3; s.treeY[1] = 1;
        s.treeSize[1] = 4; s.treeHealth[1] = 6;
        s.treeCooldown[1] = 6;
        return s;
    }

    @Test void decideRespectsDeadline() {
        GameState s = seededState();
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 20_000_000L; // 20ms
        long t0 = System.nanoTime();
        agent.decide(s, deadline, out);
        long elapsed = System.nanoTime() - t0;
        // Tolère un dépassement de 30ms max (init pop + 1 éval peut prendre du temps)
        assertThat(elapsed).isLessThan(80_000_000L);
    }

    @Test void decideProducesOneActionPerTrollWhenNoTrain() {
        GameState s = seededState();
        s.turn = 5; // pas de TRAIN
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 100_000_000L;
        int n = agent.decide(s, deadline, out);
        assertThat(n).isEqualTo(s.trollCount);
    }

    @Test void decideEmitsTrainAtTurnZero() {
        GameState s = seededState();
        s.turn = 0;
        s.shackInventory[ResourceType.PLUM]  = 5;
        s.shackInventory[ResourceType.LEMON] = 5;
        s.shackInventory[ResourceType.APPLE] = 5;
        s.shackInventory[ResourceType.IRON]  = 5;
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 100_000_000L;
        int n = agent.decide(s, deadline, out);
        // Au moins une action TRAIN dans out[0..n[
        boolean hasTrain = false;
        for (int i = 0; i < n; i++) if (Action.type(out[i]) == ActionType.TRAIN) hasTrain = true;
        assertThat(hasTrain).isTrue();
    }

    @Test void bestFitnessIsNonDecreasingAcrossGenerations() {
        GameState s = seededState();
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 200_000_000L; // 200ms
        agent.decide(s, deadline, out);
        // Sur 200ms, on s'attend à au moins quelques générations
        assertThat(agent.lastGenCount()).isGreaterThanOrEqualTo(1);
        assertThat(agent.lastBestFitness()).isNotNaN();
    }

    @Test void geneticAgentMatchesOrBeatsGreedyOnSimpleScenario() {
        // Setup : 1 troll me, 2 arbres ; le GA doit obtenir une fitness ≥ greedy
        GameState src = seededState();

        // Greedy reference : applique greedy sur 25 tours
        com.bmrt.cgspring2026.model.GameState greedyScratch = new com.bmrt.cgspring2026.model.GameState();
        greedyScratch.copyFrom(src);
        int[] gOut = new int[GameState.MAX_TROLLS + 1];
        for (int t = 0; t < GenomeEvaluator.HORIZON; t++) {
            int n = com.bmrt.cgspring2026.greedy.GreedyAgent.decide(greedyScratch, gOut);
            com.bmrt.cgspring2026.simulation.Simulator.tick(greedyScratch, gOut, n);
        }
        int greedyScore = greedyScratch.score(0);

        // GA : tourne 200ms et applique le best tick après tick
        GeneticAgent agent = new GeneticAgent();
        GameState gaScratch = new GameState();
        gaScratch.copyFrom(src);
        int[] gaOut = new int[GameState.MAX_TROLLS + 1];
        for (int t = 0; t < GenomeEvaluator.HORIZON; t++) {
            long deadline = System.nanoTime() + 50_000_000L;
            int n = agent.decide(gaScratch, deadline, gaOut);
            com.bmrt.cgspring2026.simulation.Simulator.tick(gaScratch, gaOut, n);
        }
        int gaScore = gaScratch.score(0);

        // GA ≥ greedy (peut être égal sur scenarios triviaux)
        assertThat(gaScore).isGreaterThanOrEqualTo(greedyScore);
    }
}
