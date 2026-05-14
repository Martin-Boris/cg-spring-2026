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
        for (int i = 0; i < treeCount; i++) {
            if (treeX[i] == (byte) x && treeY[i] == (byte) y) return i;
        }
        return -1;
    }

    public static byte tileAt(int x, int y) {
        return tiles[y * width + x];
    }
}
