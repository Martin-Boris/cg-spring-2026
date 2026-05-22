package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsCutFilterTest {

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
    }

    private static short[] newBuf() {
        short[] b = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(b, Genome.EMPTY_GENE);
        return b;
    }
    private static byte[] newLen() { return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS]; }

    private static boolean hasCutGene(short[] buf, byte[] lens, int individu) {
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, individu, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, individu, j, k);
                if (!Genome.isPlant(g) && !Genome.isHarvest(g)) return true;
            }
        }
        return false;
    }

    // ── initRandom ──────────────────────────────────────────────────────────

    @Test void initRandom_noCutGeneWhenTrollHasNoCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 0; s.trollHP[0] = 0; // cp=0 : doit pas recevoir CUT
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        Genome.initHarvestCandidates(s);

        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(42);
        for (int i = 0; i < 50; i++) GenomeOps.initRandom(s, buf, lens, 0, rng);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }

    @Test void initRandom_canAssignCutGeneWhenTrollHasCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 1; s.trollHP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        Genome.initHarvestCandidates(s);

        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(7);
        boolean found = false;
        for (int i = 0; i < 50 && !found; i++) {
            GenomeOps.initRandom(s, buf, lens, 0, rng);
            found = hasCutGene(buf, lens, 0);
        }
        assertThat(found).isTrue();
    }

    @Test void initWarm_noCutGeneWhenTrollHasNoCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }

    @Test void mutateInsertCut_noopWhenTrollHasNoCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(99);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }
}
