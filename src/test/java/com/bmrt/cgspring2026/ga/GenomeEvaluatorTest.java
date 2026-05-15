package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "...1..",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    @Test void fitnessIsZeroWhenNoTrollsAndNoTrees() {
        GameState source = new GameState();
        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        assertThat(fit).isEqualTo(0.0);
    }

    @Test void fitnessRewardsScoreAccumulation() {
        // Petit setup où un troll peut couper un arbre puis drop
        GameState source = new GameState();
        // Troll player=0 sur (2,1) (shack-adjacent), capable de chop large
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        source.trollX[0] = 2; source.trollY[0] = 1;
        source.trollMS[0] = 4; source.trollCC[0] = 16;
        source.trollHP[0] = 1; source.trollCP[0] = 20; // one-shot
        // Arbre adulte taille 4 à (2,2) (proche)
        source.treeCount = 1;
        source.treeType[0] = TreeType.PLUM;
        source.treeX[0] = 2; source.treeY[0] = 2;
        source.treeSize[0] = 4; source.treeHealth[0] = 12;
        source.treeFruits[0] = 0; source.treeCooldown[0] = 8;

        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(2, 2));
        Genome.setLen(lenBuf, 0, 0, 1);

        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        // Le troll devrait chop (16 pts) puis drop dans l'horizon → fit positive
        assertThat(fit).isGreaterThan(0.0);
    }

    @Test void fitnessFormulaIncludesCarryBonus() {
        // Scénario où le troll récupère du wood mais n'a pas le temps de drop
        // → la fitness doit refléter le wood porté via ALPHA_WOOD_CARRY
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        // Position loin du shack pour empêcher drop
        source.trollX[0] = 4; source.trollY[0] = 5;
        source.trollMS[0] = 1; source.trollCC[0] = 16;
        source.trollHP[0] = 1; source.trollCP[0] = 20;
        source.treeCount = 1;
        source.treeType[0] = TreeType.BANANA; // health très bas
        source.treeX[0] = 4; source.treeY[0] = 5;
        source.treeSize[0] = 4; source.treeHealth[0] = 6;
        source.treeFruits[0] = 0; source.treeCooldown[0] = 6;

        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(4, 5));
        Genome.setLen(lenBuf, 0, 0, 1);

        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        // troll chop banana taille 4 → 4 wood porté. Pas de drop possible (loin du shack avec MS=1)
        // fitness = 0 (score) + 2.0 * 4 = 8.0
        assertThat(fit).isGreaterThanOrEqualTo(8.0);
    }
}
