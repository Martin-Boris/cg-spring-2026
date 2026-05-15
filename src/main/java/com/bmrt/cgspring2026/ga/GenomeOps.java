package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

import java.util.SplittableRandom;

public final class GenomeOps {

    public static final double P_SKIP_INIT = 0.30;

    // Scratch buffers réutilisés (jamais alloués dans le hot path après init)
    private static final short[] shuffleBuf      = new short[GameState.MAX_TREES];
    private static final int[]   ownTrollsBuf    = new int[GameState.MAX_TROLLS];
    private static final int[]   freeTrollsBuf   = new int[GameState.MAX_TROLLS];

    private GenomeOps() {}

    public static void initRandom(GameState state, short[] buf, byte[] lenBuf,
                                  int individuIdx, SplittableRandom rng) {
        // 1. Reset segment de cet individu
        int base = Genome.offset(individuIdx, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) buf[base + k] = Genome.EMPTY_GENE;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(lenBuf, individuIdx, j, 0);

        // 2. Récupérer trolls player=0
        int ownTrollsCount = 0;
        for (int i = 0; i < state.trollCount; i++) {
            if ((state.trollPlayer[i] & 0xFF) == 0) ownTrollsBuf[ownTrollsCount++] = i;
        }
        if (ownTrollsCount == 0) return;

        // 3. Récupérer arbres vivants, shuffle Fisher-Yates
        int treeCount = 0;
        for (int t = 0; t < state.treeCount; t++) {
            if (state.treeHealth[t] > 0) {
                shuffleBuf[treeCount++] = Genome.encode(state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
            }
        }
        for (int i = treeCount - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            short tmp = shuffleBuf[i]; shuffleBuf[i] = shuffleBuf[j]; shuffleBuf[j] = tmp;
        }

        // 4. Distribuer
        for (int i = 0; i < treeCount; i++) {
            if (rng.nextDouble() < P_SKIP_INIT) continue;
            // Liste des trolls non pleins
            int freeCount = 0;
            for (int k = 0; k < ownTrollsCount; k++) {
                int trollIdx = ownTrollsBuf[k];
                if (Genome.len(lenBuf, individuIdx, trollIdx) < Genome.MAX_TARGETS_PER_TROLL) {
                    freeTrollsBuf[freeCount++] = trollIdx;
                }
            }
            if (freeCount == 0) break;
            int chosenTroll = freeTrollsBuf[rng.nextInt(freeCount)];
            int len = Genome.len(lenBuf, individuIdx, chosenTroll);
            Genome.setGene(buf, individuIdx, chosenTroll, len, shuffleBuf[i]);
            Genome.setLen(lenBuf, individuIdx, chosenTroll, len + 1);
        }
    }

    public static final double P_MUT_SWAP_INTRA = 0.40;
    public static final double P_MUT_SWAP_INTER = 0.30;
    public static final double P_MUT_REVERSE    = 0.20;
    public static final double P_MUT_DELETE     = 0.10;

    public static final int MUT_SWAP_INTRA = 0;
    public static final int MUT_SWAP_INTER = 1;
    public static final int MUT_REVERSE    = 2;
    public static final int MUT_DELETE     = 3;

    public static int pickMutationKind(SplittableRandom rng) {
        double r = rng.nextDouble();
        if (r < P_MUT_SWAP_INTRA) return MUT_SWAP_INTRA;
        r -= P_MUT_SWAP_INTRA;
        if (r < P_MUT_SWAP_INTER) return MUT_SWAP_INTER;
        r -= P_MUT_SWAP_INTER;
        if (r < P_MUT_REVERSE) return MUT_REVERSE;
        return MUT_DELETE;
    }

    public static void runMutation(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        switch (pickMutationKind(rng)) {
            case MUT_SWAP_INTRA -> mutateSwapIntra(buf, lenBuf, individuIdx, rng);
            case MUT_SWAP_INTER -> mutateSwapInter(buf, lenBuf, individuIdx, rng);
            case MUT_REVERSE    -> mutateReverse  (buf, lenBuf, individuIdx, rng);
            case MUT_DELETE     -> mutateDelete   (buf, lenBuf, individuIdx, rng);
            default -> throw new IllegalStateException();
        }
    }

    /** Tire un troll avec len >= minLen parmi [0..MAX_TROLLS[. Retourne -1 si aucun. */
    private static int pickTrollWithLen(byte[] lenBuf, int individuIdx, int minLen, SplittableRandom rng) {
        int count = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            if (Genome.len(lenBuf, individuIdx, j) >= minLen) freeTrollsBuf[count++] = j;
        }
        if (count == 0) return -1;
        return freeTrollsBuf[rng.nextInt(count)];
    }

    public static void mutateSwapIntra(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int k1 = rng.nextInt(len);
        int k2 = rng.nextInt(len);
        if (k1 == k2) return;
        int base = Genome.offset(individuIdx, j);
        short tmp = buf[base + k1]; buf[base + k1] = buf[base + k2]; buf[base + k2] = tmp;
    }

    public static void mutateSwapInter(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j1 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j1 < 0) return;
        int j2 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j2 < 0 || j1 == j2) return;
        int len1 = Genome.len(lenBuf, individuIdx, j1);
        int len2 = Genome.len(lenBuf, individuIdx, j2);
        int k1 = rng.nextInt(len1);
        int k2 = rng.nextInt(len2);
        int b1 = Genome.offset(individuIdx, j1);
        int b2 = Genome.offset(individuIdx, j2);
        short tmp = buf[b1 + k1]; buf[b1 + k1] = buf[b2 + k2]; buf[b2 + k2] = tmp;
    }

    public static void mutateReverse(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int a = rng.nextInt(len);
        int b = rng.nextInt(len);
        if (a == b) return;
        if (a > b) { int t = a; a = b; b = t; }
        int base = Genome.offset(individuIdx, j);
        while (a < b) {
            short tmp = buf[base + a]; buf[base + a] = buf[base + b]; buf[base + b] = tmp;
            a++; b--;
        }
    }

    public static void mutateDelete(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int k = rng.nextInt(len);
        int base = Genome.offset(individuIdx, j);
        for (int i = k; i < len - 1; i++) buf[base + i] = buf[base + i + 1];
        buf[base + len - 1] = Genome.EMPTY_GENE;
        Genome.setLen(lenBuf, individuIdx, j, len - 1);
    }

    private static final boolean[] seenBuf = new boolean[256 * 256]; // max grid 256x256

    public static void crossover(short[] srcA, byte[] lenA, int idxA,
                                 short[] srcB, byte[] lenB, int idxB,
                                 short[] dst,  byte[] dstLen, int idxDst,
                                 SplittableRandom rng) {
        int W = GameState.width;
        int H = GameState.height;
        // Reset offspring
        int dstBase = Genome.offset(idxDst, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) dst[dstBase + k] = Genome.EMPTY_GENE;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(dstLen, idxDst, j, 0);
        // Reset seen[] sur la zone utilisée
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) seenBuf[y * W + x] = false;
        }
        // OX par troll
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int la = Genome.len(lenA, idxA, j);
            int lb = Genome.len(lenB, idxB, j);
            if (la == 0 && lb == 0) continue;
            int cut = (la > 0) ? rng.nextInt(la + 1) : 0;
            int dstOff = Genome.offset(idxDst, j);
            int written = 0;
            // Préfixe de P1
            int aBase = Genome.offset(idxA, j);
            for (int k = 0; k < cut; k++) {
                short g = srcA[aBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                int cell = y * W + x;
                if (seenBuf[cell]) continue; // ne devrait pas arriver si parent valide, défensif
                seenBuf[cell] = true;
                dst[dstOff + written++] = g;
            }
            // Compléter avec P2
            int bBase = Genome.offset(idxB, j);
            int target = (la > 0 ? la : lb);
            for (int k = 0; k < lb && written < target && written < Genome.MAX_TARGETS_PER_TROLL; k++) {
                short g = srcB[bBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                int cell = y * W + x;
                if (seenBuf[cell]) continue;
                seenBuf[cell] = true;
                dst[dstOff + written++] = g;
            }
            Genome.setLen(dstLen, idxDst, j, written);
        }
    }
}
