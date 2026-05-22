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

    @Test void firstTurnSlotZeroEqualsInitWarm() {
        GameState s = simpleState();

        short[] refBuf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(refBuf, Genome.EMPTY_GENE);
        byte[]  refLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GenomeOps.initWarm(s, refBuf, refLen, 0);

        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(agent.pop.curLen, 0, j))
                .as("len troll %d", j)
                .isEqualTo(Genome.len(refLen, 0, j));
            int lenJ = Genome.len(refLen, 0, j);
            for (int k = 0; k < lenJ; k++) {
                assertThat((short) Genome.gene(agent.pop.cur, 0, j, k))
                    .as("gene troll %d k %d", j, k)
                    .isEqualTo((short) Genome.gene(refBuf, 0, j, k));
            }
        }
    }

    @Test void secondTurnSlotZeroSeededFromPrevBest() {
        // Forcer un stash que initWarm ne produirait pas (far tree en premier)
        // pour discriminer la branche hasPrevBest=true du fallback.
        GameState s = simpleState();
        // Test cible la propagation du stash vers slot 0 ; runner au cutoff pour
        // bypasser le gate CUT pré-cutoff (couvert séparément par GenomeOpsCutCutoffTest).
        s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];

        // Tour 1 — peuple le stash naturellement
        agent.decide(s, deadline, out);

        // Réécriture du stash : (5,5) avant (1,0). initWarm placerait (1,0) en premier (closest).
        java.util.Arrays.fill(agent.prevBestBuf, Genome.EMPTY_GENE);
        java.util.Arrays.fill(agent.prevBestLen, (byte) 0);
        agent.prevBestBuf[0] = Genome.encode(5, 5);
        agent.prevBestBuf[1] = Genome.encode(1, 0);
        agent.prevBestLen[0] = 2;

        short[] stashBuf = java.util.Arrays.copyOf(agent.prevBestBuf, Genome.SLOTS_PER_GENOME);
        byte[]  stashLen = java.util.Arrays.copyOf(agent.prevBestLen, GameState.MAX_TROLLS);

        // Tour 2 — initPopulation doit consommer le stash (compactage identité ici)
        agent.decide(s, deadline, out);

        // Slot 0 == stash (et donc (5,5) en gène 0, distinct de ce que initWarm aurait fait)
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(agent.pop.curLen, 0, j))
                .as("len troll %d", j)
                .isEqualTo(stashLen[j] & 0xFF);
            int lenJ = stashLen[j] & 0xFF;
            for (int k = 0; k < lenJ; k++) {
                assertThat((short) Genome.gene(agent.pop.cur, 0, j, k))
                    .as("gene troll %d k %d", j, k)
                    .isEqualTo(stashBuf[j * Genome.MAX_TARGETS_PER_TROLL + k]);
            }
        }
    }
}
