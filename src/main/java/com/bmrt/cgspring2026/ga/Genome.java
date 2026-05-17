package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class Genome {

    public static final int POP_SIZE = 20;
    public static final int MAX_TARGETS_PER_TROLL = 10;
    public static final int SLOTS_PER_GENOME = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL;
    public static final short EMPTY_GENE = -1;
    public static final short[] plantCandidates = new short[12];
    // Bit layout: bit15=flag(PLANT=1), bits13-14=fruitType, bits8-12=x(5 bits), bits0-7=y(8 bits)
    private static final int PLANT_FLAG_MASK = 0x8000;
    private static final int HARVEST_FLAG_MASK = 0x4000;
    private static final int FRUIT_TYPE_SHIFT = 13;
    private static final int FRUIT_TYPE_MASK = 0x3 << FRUIT_TYPE_SHIFT;
    private static final int X_MASK = 0x1F;
    private static final int X_SHIFT = 8;
    public static int plantCandidateCount = 0;
    public static final short[] harvestCandidates = new short[GameState.MAX_TREES];
    public static int harvestCandidateCount = 0;

    public static short encode(int x, int y) {
        return (short) (((x & 0xFF) << 8) | (y & 0xFF));
    }

    public static short makeTarget(int x, int y) {
        return encode(x, y);
    }

    public static short makePlant(int x, int y, int fruitType) {
        return (short) (PLANT_FLAG_MASK
                | ((fruitType & 0x3) << FRUIT_TYPE_SHIFT)
                | ((x & X_MASK) << X_SHIFT)
                | (y & 0xFF));
    }

    public static boolean isPlant(short g) {
        return (g & PLANT_FLAG_MASK) != 0;
    }

    public static short makeHarvest(int x, int y) {
        return (short) (HARVEST_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
    }

    public static boolean isHarvest(short g) {
        return (g & HARVEST_FLAG_MASK) != 0 && (g & PLANT_FLAG_MASK) == 0;
    }

    public static void initHarvestCandidates(GameState state) {
        harvestCandidateCount = 0;
        for (int t = 0; t < state.treeCount; t++) {
            if ((state.treeSize[t] & 0xFF) == 4 && state.treeHealth[t] > 0) {
                harvestCandidates[harvestCandidateCount++] =
                    encode(state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
            }
        }
    }

    public static int plantFruitType(short g) {
        return (g & FRUIT_TYPE_MASK) >>> FRUIT_TYPE_SHIFT;
    }

    public static int geneX(short g) {
        return (g >>> X_SHIFT) & X_MASK;
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

    public static void initPlantCandidates() {
        plantCandidateCount = 0;
        int sx = GameState.shackMeX;
        int sy = GameState.shackMeY;
        for (int dx = -2; dx <= 2; dx++) {
            int x = sx + dx;
            if (x < 0 || x >= GameState.width) continue;
            int yRange = 2 - Math.abs(dx);
            for (int dy = -yRange; dy <= yRange; dy++) {
                int y = sy + dy;
                if (y < 0 || y >= GameState.height) continue;
                if (dx == 0 && dy == 0) continue;
                if (GameState.tiles[y * GameState.width + x] != TileType.GRASS) continue;
                plantCandidates[plantCandidateCount++] = (short) ((x << 8) | y);
            }
        }
    }

    public static int candX(short c) {
        return (c >>> 8) & 0xFF;
    }

    public static int candY(short c) {
        return c & 0xFF;
    }
}
