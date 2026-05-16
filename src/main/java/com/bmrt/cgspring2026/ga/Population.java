package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class Population {

    public final short[]  bufA = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    public final short[]  bufB = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    public final byte[]   lenA = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    public final byte[]   lenB = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    public final double[] fitA = new double[Genome.POP_SIZE];
    public final double[] fitB = new double[Genome.POP_SIZE];

    public short[]  cur, nxt;
    public byte[]   curLen, nxtLen;
    public double[] curFit, nxtFit;

    public Population() {
        java.util.Arrays.fill(bufA, Genome.EMPTY_GENE);
        java.util.Arrays.fill(bufB, Genome.EMPTY_GENE);
        cur = bufA; nxt = bufB;
        curLen = lenA; nxtLen = lenB;
        curFit = fitA; nxtFit = fitB;
    }

    public void swap() {
        short[] tmp = cur; cur = nxt; nxt = tmp;
        byte[] tmpLen = curLen; curLen = nxtLen; nxtLen = tmpLen;
        double[] tmpFit = curFit; curFit = nxtFit; nxtFit = tmpFit;
    }

    public void resetCurrent() {
        java.util.Arrays.fill(cur, Genome.EMPTY_GENE);
        java.util.Arrays.fill(curLen, (byte) 0);
        java.util.Arrays.fill(curFit, 0.0);
    }
}
