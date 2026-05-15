package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class Genome {

    public static final int POP_SIZE = 30;
    public static final int MAX_TARGETS_PER_TROLL = 15;
    public static final int SLOTS_PER_GENOME = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL;
    public static final short EMPTY_GENE = -1;

    private Genome() {
    }

    public static short encode(int x, int y) {
        return (short) (((x & 0xFF) << 8) | (y & 0xFF));
    }

    public static int geneX(short g) {
        return (g >>> 8) & 0xFF;
    }

    public static int geneY(short g) {
        return g & 0xFF;
    }

    public static int offset(int individuIdx, int trollIdx) {
        return individuIdx * SLOTS_PER_GENOME + trollIdx * MAX_TARGETS_PER_TROLL;
    }

    public static int lenOffset(int individuIdx, int trollIdx) {
        return individuIdx * GameState.MAX_TROLLS + trollIdx;
    }

    public static int len(byte[] lenBuf, int individuIdx, int trollIdx) {
        return lenBuf[lenOffset(individuIdx, trollIdx)] & 0xFF;
    }

    public static void setLen(byte[] lenBuf, int individuIdx, int trollIdx, int value) {
        lenBuf[lenOffset(individuIdx, trollIdx)] = (byte) value;
    }

    public static int gene(short[] buf, int individuIdx, int trollIdx, int k) {
        return buf[offset(individuIdx, trollIdx) + k];
    }

    public static void setGene(short[] buf, int individuIdx, int trollIdx, int k, short value) {
        buf[offset(individuIdx, trollIdx) + k] = value;
    }
}
