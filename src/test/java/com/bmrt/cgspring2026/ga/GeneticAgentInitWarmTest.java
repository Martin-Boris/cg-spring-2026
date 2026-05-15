package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentInitWarmTest {

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

    @Test void individual0MatchesWarmStartOutput() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.treeCount = 2;
        s.treeX[0] = 5; s.treeY[0] = 5; s.treeHealth[0] = 5;
        s.treeX[1] = 1; s.treeY[1] = 0; s.treeHealth[1] = 5;

        // Référence : ce que initWarm doit produire
        short[] refBuf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(refBuf, Genome.EMPTY_GENE);
        byte[]  refLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GenomeOps.initWarm(s, refBuf, refLen, 0);

        // Exécution réelle via l'agent — deadline DÉJÀ PASSÉE
        // pour qu'aucune génération ne s'exécute (sinon l'élitisme peut écraser pop.cur[0]).
        // initPopulation + evaluatePopulation s'exécutent, puis la boucle while sort
        // immédiatement, et pop.cur[0] reste l'individu warm-start initial.
        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        // Sanity : initWarm produit bien (1,0) en premier
        assertThat(Genome.len(refLen, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(refBuf, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(1);
        assertThat(Genome.geneY(g0)).isEqualTo(0);

        // Câblage : l'individu 0 de la population doit être identique au warm-start de référence
        // (puisque deadline est dépassée, aucune génération n'a été exécutée).
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
}
