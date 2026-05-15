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
}
