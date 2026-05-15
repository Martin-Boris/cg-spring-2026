package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class GenomeInvariants {

    private GenomeInvariants() {}

    public static boolean check(short[] buf, byte[] lenBuf, int individuIdx) {
        int W = GameState.width;
        int H = GameState.height;
        boolean[] seen = new boolean[W * H];
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lenBuf, individuIdx, j);
            if (len < 0 || len > Genome.MAX_TARGETS_PER_TROLL) {
                throw new AssertionError("len out of range troll=" + j + " len=" + len);
            }
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, individuIdx, j, k);
                if (g == Genome.EMPTY_GENE) {
                    throw new AssertionError("active slot is EMPTY troll=" + j + " k=" + k);
                }
                int x = Genome.geneX(g);
                int y = Genome.geneY(g);
                if (x < 0 || x >= W || y < 0 || y >= H) {
                    throw new AssertionError("gene out of bounds (" + x + "," + y + ")");
                }
                int idx = y * W + x;
                if (seen[idx]) {
                    throw new AssertionError("duplicate gene (" + x + "," + y + ")");
                }
                seen[idx] = true;
            }
            for (int k = len; k < Genome.MAX_TARGETS_PER_TROLL; k++) {
                if (Genome.gene(buf, individuIdx, j, k) != Genome.EMPTY_GENE) {
                    throw new AssertionError("dirty slot after len troll=" + j + " k=" + k);
                }
            }
        }
        return true;
    }
}
