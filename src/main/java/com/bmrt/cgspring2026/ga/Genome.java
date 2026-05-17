package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class Genome {

    public static final int POP_SIZE = 20;
    public static final int MAX_TARGETS_PER_TROLL = 10;
    public static final int SLOTS_PER_GENOME = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL;
    public static final short EMPTY_GENE = -1;
    public static final short[] plantCandidates = new short[12];
    // Gene type dispatch (16-bit short):
    //   bit15=1                    → PLANT gene  (bits13-14=fruitType, bits8-12=x, bits0-7=y)
    //   bit15=0, bit14=1           → HARVEST gene (bits8-12=x, bits0-7=y)
    //   bit15=0, bit14=0, bit13=1  → MINE gene    (bits8-12=x, bits0-7=y)
    //   bit15=0, bit14=0, bit13=0  → CUT gene     (bits8-12=x, bits0-7=y; valid only for x<32)
    //   all bits=1                 → EMPTY_GENE
    private static final int PLANT_FLAG_MASK = 0x8000;
    private static final int HARVEST_FLAG_MASK = 0x4000;
    private static final int MINE_FLAG_MASK = 0x2000;  // bit13
    private static final int FRUIT_TYPE_SHIFT = 13;
    private static final int FRUIT_TYPE_MASK = 0x3 << FRUIT_TYPE_SHIFT;
    private static final int X_MASK = 0x1F;
    private static final int X_SHIFT = 8;
    public static int plantCandidateCount = 0;
    public static final short[] harvestCandidates = new short[GameState.MAX_TREES];
    public static int harvestCandidateCount = 0;
    public static final int MAX_IRON_CANDIDATES = 64;
    public static final short[] ironCandidates = new short[MAX_IRON_CANDIDATES];
    public static int ironCandidateCount = 0;

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

    public static short makeMine(int x, int y) {
        return (short) (MINE_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
    }

    public static boolean isMine(short g) {
        if (g == EMPTY_GENE) return false;
        return (g & 0xE000) == MINE_FLAG_MASK;  // bit15=0, bit14=0, bit13=1
    }

    public static boolean isCut(short g) {
        if (g == EMPTY_GENE) return false;
        return (g & 0xE000) == 0;  // bit15=0, bit14=0, bit13=0
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

    public static void initIronCandidates() {
        ironCandidateCount = 0;
        int W = GameState.width, H = GameState.height;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (GameState.tiles[y * W + x] != TileType.IRON) continue;
                if (!hasAdjacentGrass(x, y)) continue;
                if (ironCandidateCount >= MAX_IRON_CANDIDATES) break;
                ironCandidates[ironCandidateCount++] = encode(x, y);
            }
        }
    }

    private static boolean hasAdjacentGrass(int x, int y) {
        int W = GameState.width, H = GameState.height;
        if (x + 1 < W && GameState.tiles[y * W + (x + 1)] == TileType.GRASS) return true;
        if (x - 1 >= 0 && GameState.tiles[y * W + (x - 1)] == TileType.GRASS) return true;
        if (y + 1 < H && GameState.tiles[(y + 1) * W + x] == TileType.GRASS) return true;
        if (y - 1 >= 0 && GameState.tiles[(y - 1) * W + x] == TileType.GRASS) return true;
        return false;
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
        for (int dx = -1; dx <= 1; dx++) {
            int x = sx + dx;
            if (x < 0 || x >= GameState.width) continue;
            int yRange = 1 - Math.abs(dx);
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
