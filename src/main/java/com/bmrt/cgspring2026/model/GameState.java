package com.bmrt.cgspring2026.model;

import java.util.Scanner;

public final class GameState {

    public static final int MAX_TREES  = 128;
    public static final int MAX_TROLLS = 32;

    public static int width;
    public static int height;
    public static byte[] tiles;
    public static int shackMeX;
    public static int shackMeY;
    public static int shackOppX;
    public static int shackOppY;

    public int turn;

    public final int[] shackInventory = new int[2 * ResourceType.COUNT];

    public int treeCount;
    public final byte[] treeType     = new byte[MAX_TREES];
    public final byte[] treeX        = new byte[MAX_TREES];
    public final byte[] treeY        = new byte[MAX_TREES];
    public final byte[] treeSize     = new byte[MAX_TREES];
    public final byte[] treeHealth   = new byte[MAX_TREES];
    public final byte[] treeFruits   = new byte[MAX_TREES];
    public final byte[] treeCooldown = new byte[MAX_TREES];

    /** Cell -> live tree index, -1 if none. byte suffices because MAX_TREES <= 127. Size W*H. */
    public byte[] treeCellIndex;

    public int trollCount;
    public final byte[] trollId     = new byte[MAX_TROLLS];
    public final byte[] trollPlayer = new byte[MAX_TROLLS];
    public final byte[] trollX      = new byte[MAX_TROLLS];
    public final byte[] trollY      = new byte[MAX_TROLLS];
    public final byte[] trollMS     = new byte[MAX_TROLLS];
    public final byte[] trollCC     = new byte[MAX_TROLLS];
    public final byte[] trollHP     = new byte[MAX_TROLLS];
    public final byte[] trollCP     = new byte[MAX_TROLLS];

    public final byte[] trollInventory = new byte[MAX_TROLLS * ResourceType.COUNT];

    public GameState() {
    }

    public static void readInit(Scanner in) {
        width  = in.nextInt();
        height = in.nextInt();
        in.nextLine();
        tiles = new byte[width * height];
        for (int y = 0; y < height; y++) {
            String line = in.nextLine();
            for (int x = 0; x < width; x++) {
                byte t = TileType.fromChar(line.charAt(x));
                tiles[y * width + x] = t;
                if (t == TileType.SHACK_ME)  { shackMeX  = x; shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { shackOppX = x; shackOppY = y; }
            }
        }
    }

    public void readTurn(Scanner in) {
        for (int p = 0; p < 2; p++) {
            for (int r = 0; r < ResourceType.COUNT; r++) {
                shackInventory[p * ResourceType.COUNT + r] = in.nextInt();
            }
        }
        treeCount = in.nextInt();
        for (int i = 0; i < treeCount; i++) {
            treeType[i]     = TreeType.fromString(in.next());
            treeX[i]        = (byte) in.nextInt();
            treeY[i]        = (byte) in.nextInt();
            treeSize[i]     = (byte) in.nextInt();
            treeHealth[i]   = (byte) in.nextInt();
            treeFruits[i]   = (byte) in.nextInt();
            treeCooldown[i] = (byte) in.nextInt();
        }
        treeCellIndex = null; // invalidate; will be lazily rebuilt on first use
        trollCount = in.nextInt();
        for (int i = 0; i < trollCount; i++) {
            trollId[i]     = (byte) in.nextInt();
            trollPlayer[i] = (byte) in.nextInt();
            trollX[i]      = (byte) in.nextInt();
            trollY[i]      = (byte) in.nextInt();
            trollMS[i]     = (byte) in.nextInt();
            trollCC[i]     = (byte) in.nextInt();
            trollHP[i]     = (byte) in.nextInt();
            trollCP[i]     = (byte) in.nextInt();
            for (int r = 0; r < ResourceType.COUNT; r++) {
                trollInventory[i * ResourceType.COUNT + r] = (byte) in.nextInt();
            }
        }
    }

    public void copyFrom(GameState src) {
        turn = src.turn;
        System.arraycopy(src.shackInventory, 0, shackInventory, 0, shackInventory.length);
        treeCount = src.treeCount;
        System.arraycopy(src.treeType,     0, treeType,     0, MAX_TREES);
        System.arraycopy(src.treeX,        0, treeX,        0, MAX_TREES);
        System.arraycopy(src.treeY,        0, treeY,        0, MAX_TREES);
        System.arraycopy(src.treeSize,     0, treeSize,     0, MAX_TREES);
        System.arraycopy(src.treeHealth,   0, treeHealth,   0, MAX_TREES);
        System.arraycopy(src.treeFruits,   0, treeFruits,   0, MAX_TREES);
        System.arraycopy(src.treeCooldown, 0, treeCooldown, 0, MAX_TREES);
        if (treeCellIndex == null || treeCellIndex.length < width * height) {
            treeCellIndex = new byte[width * height];
        }
        if (src.treeCellIndex != null) {
            System.arraycopy(src.treeCellIndex, 0, treeCellIndex, 0, width * height);
        } else {
            java.util.Arrays.fill(treeCellIndex, (byte) -1);
            for (int i = 0; i < treeCount; i++) {
                if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
            }
        }
        trollCount = src.trollCount;
        System.arraycopy(src.trollId,        0, trollId,        0, MAX_TROLLS);
        System.arraycopy(src.trollPlayer,    0, trollPlayer,    0, MAX_TROLLS);
        System.arraycopy(src.trollX,         0, trollX,         0, MAX_TROLLS);
        System.arraycopy(src.trollY,         0, trollY,         0, MAX_TROLLS);
        System.arraycopy(src.trollMS,        0, trollMS,        0, MAX_TROLLS);
        System.arraycopy(src.trollCC,        0, trollCC,        0, MAX_TROLLS);
        System.arraycopy(src.trollHP,        0, trollHP,        0, MAX_TROLLS);
        System.arraycopy(src.trollCP,        0, trollCP,        0, MAX_TROLLS);
        System.arraycopy(src.trollInventory, 0, trollInventory, 0, MAX_TROLLS * ResourceType.COUNT);
    }

    public void apply(int action) {
        throw new UnsupportedOperationException("TODO");
    }

    public int score(int player) {
        int base = player * ResourceType.COUNT;
        return shackInventory[base + ResourceType.PLUM]
             + shackInventory[base + ResourceType.LEMON]
             + shackInventory[base + ResourceType.APPLE]
             + shackInventory[base + ResourceType.BANANA]
             + 4 * shackInventory[base + ResourceType.WOOD];
    }

    public int trollIndexById(int id) {
        for (int i = 0; i < trollCount; i++) {
            if ((trollId[i] & 0xFF) == id) return i;
        }
        return -1;
    }

    public int treeIndexAt(int x, int y) {
        if (treeCellIndex == null) return slowTreeIndexAt(x, y);
        int v = treeCellIndex[y * width + x];
        return (v == -1) ? -1 : (v & 0xFF);
    }

    private int slowTreeIndexAt(int x, int y) {
        for (int i = 0; i < treeCount; i++) {
            if (treeX[i] == (byte) x && treeY[i] == (byte) y && treeHealth[i] > 0) return i;
        }
        return -1;
    }

    private void ensureTreeCellIndex() {
        if (treeCellIndex == null || treeCellIndex.length < width * height) {
            treeCellIndex = new byte[width * height];
            java.util.Arrays.fill(treeCellIndex, (byte) -1);
            for (int i = 0; i < treeCount; i++) {
                if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
            }
        }
    }

    /** Adds a tree, returns its index. The tree must be alive (health > 0). */
    public int addTree(byte type, int x, int y, int size, int health) {
        ensureTreeCellIndex();
        int idx = treeCount++;
        treeType[idx]     = type;
        treeX[idx]        = (byte) x;
        treeY[idx]        = (byte) y;
        treeSize[idx]     = (byte) size;
        treeHealth[idx]   = (byte) health;
        treeFruits[idx]   = 0;
        treeCooldown[idx] = 0;
        treeCellIndex[y * width + x] = (byte) idx;
        return idx;
    }

    /** Marks tree as dead (health=0) and removes it from the index. */
    public void killTreeAt(int idx) {
        ensureTreeCellIndex();
        treeHealth[idx] = 0;
        treeCellIndex[(treeY[idx] & 0xFF) * width + (treeX[idx] & 0xFF)] = -1;
    }

    /** Compacts dead trees by swap-last. Maintains treeCellIndex for moved live trees. */
    public void compactDeadTrees() {
        ensureTreeCellIndex();
        int i = 0;
        while (i < treeCount) {
            if (treeHealth[i] <= 0) {
                int last = treeCount - 1;
                if (i != last) {
                    treeType[i]     = treeType[last];
                    treeX[i]        = treeX[last];
                    treeY[i]        = treeY[last];
                    treeSize[i]     = treeSize[last];
                    treeHealth[i]   = treeHealth[last];
                    treeFruits[i]   = treeFruits[last];
                    treeCooldown[i] = treeCooldown[last];
                    if (treeHealth[i] > 0) {
                        treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
                    }
                }
                treeCount--;
            } else {
                i++;
            }
        }
    }

    public static byte tileAt(int x, int y) {
        return tiles[y * width + x];
    }
}
