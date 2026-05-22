package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsCutCutoffTest {

    @BeforeEach void grid() {
        String[] rows = { "......", ".0....", "......", "......", "......" };
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
                if (Genome.isCut(g)) return true;
            }
        }
        return false;
    }

    private static GameState stateWithTree(int turn) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 1; s.trollHP[0] = 1;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        s.turn = turn;
        return s;
    }

    @Test void mutateInsertCut_noopBeforeCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);
        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(42);
        for (int i = 0; i < 100; i++) GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }

    @Test void mutateInsertCut_worksAtCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);
        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(7);
        boolean found = false;
        for (int i = 0; i < 50 && !found; i++) {
            GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
            found = hasCutGene(buf, lens, 0);
        }
        assertThat(found).isTrue();
    }

    @Test void initFromPrevBest_dropsCutGenesBeforeCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);

        short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
        byte[] prevLen = new byte[GameState.MAX_TROLLS];
        // Place a CUT gene on troll 0 targeting tree (3,2)
        prevBuf[0] = Genome.encode(3, 2);
        prevLen[0] = 1;

        short[] dstBuf = newBuf();
        byte[]  dstLen = newLen();
        GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

        assertThat(hasCutGene(dstBuf, dstLen, 0)).isFalse();
        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(0);
    }

    @Test void initFromPrevBest_keepsCutGenesAtCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);

        short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
        byte[] prevLen = new byte[GameState.MAX_TROLLS];
        prevBuf[0] = Genome.encode(3, 2);
        prevLen[0] = 1;

        short[] dstBuf = newBuf();
        byte[]  dstLen = newLen();
        GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

        assertThat(hasCutGene(dstBuf, dstLen, 0)).isTrue();
        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
    }

    @Test void initFromPrevBest_keepsMineGenesBeforeCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);

        short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
        byte[] prevLen = new byte[GameState.MAX_TROLLS];
        // MINE gene n'a pas besoin d'arbre, juste d'une cellule IRON dans la carte.
        // On verifie qu'il survit independamment du gate CUT.
        prevBuf[0] = Genome.makeMine(2, 2);
        prevLen[0] = 1;

        short[] dstBuf = newBuf();
        byte[]  dstLen = newLen();
        GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        short g = (short) Genome.gene(dstBuf, 0, 0, 0);
        assertThat(Genome.isMine(g)).isTrue();
    }
}
