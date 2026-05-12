package com.bmrt.cgspring2026.model;

import java.util.Scanner;

public final class GameState {

    public static final int MAX_TILES  = 256;
    public static final int MAX_TREES  = 128;
    public static final int MAX_TROLLS = 32;

    public int width;
    public int height;
    public int tileCount;
    public byte[] terrain;
    public boolean[] waterAdj;
    public int shackMeTile;
    public int shackOppTile;

    public short[] treeByTile;
    public short[] myTrollByTile;
    public short[] oppTrollByTile;

    public int treeCount;
    public byte[] treeType    = new byte[MAX_TREES];
    public byte[] treeX       = new byte[MAX_TREES];
    public byte[] treeY       = new byte[MAX_TREES];
    public byte[] treeSize    = new byte[MAX_TREES];
    public byte[] treeHealth  = new byte[MAX_TREES];
    public byte[] treeFruits  = new byte[MAX_TREES];
    public byte[] treeCooldown = new byte[MAX_TREES];

    public int trollCount;
    public byte[] trollPlayer         = new byte[MAX_TROLLS];
    public byte[] trollX              = new byte[MAX_TROLLS];
    public byte[] trollY              = new byte[MAX_TROLLS];
    public byte[] trollMoveSpeed      = new byte[MAX_TROLLS];
    public byte[] trollCarryCapacity  = new byte[MAX_TROLLS];
    public byte[] trollHarvestPower   = new byte[MAX_TROLLS];
    public byte[] trollChopPower      = new byte[MAX_TROLLS];
    public byte[] trollCarry          = new byte[MAX_TROLLS * Resource.COUNT];
    public int[]  trollOriginalId     = new int[MAX_TROLLS];

    public int[] invMe  = new int[Resource.COUNT];
    public int[] invOpp = new int[Resource.COUNT];

    public int turn;

    public GameState() {}

    public int tile(int x, int y) {
        return y * width + x;
    }

    public int x(int tile) {
        return tile % width;
    }

    public int y(int tile) {
        return tile / width;
    }

    public boolean isWalkable(int tile) {
        return terrain[tile] == TileType.GRASS;
    }

    public boolean isAdjacent(int tileA, int tileB) {
        int ax = x(tileA), ay = y(tileA);
        int bx = x(tileB), by = y(tileB);
        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);
        return dx + dy == 1;
    }

    public int score(int player) {
        int[] inv = (player == 0) ? invMe : invOpp;
        int s = 0;
        s += inv[Resource.PLUM]   * Resource.POINTS_FRUIT;
        s += inv[Resource.LEMON]  * Resource.POINTS_FRUIT;
        s += inv[Resource.APPLE]  * Resource.POINTS_FRUIT;
        s += inv[Resource.BANANA] * Resource.POINTS_FRUIT;
        s += inv[Resource.WOOD]   * Resource.POINTS_WOOD;
        return s;
    }

    public void copyFrom(GameState src) {
        // TODO: System.arraycopy de tous les SoA + scalaires
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void applyTurn(int[] myActions, int[] oppActions) {
        // TODO V2 : résolution dans l'ordre MOVE/HARVEST/PLANT/CHOP/PICK/TRAIN/DROP/MINE/Grow
        throw new UnsupportedOperationException("not yet implemented");
    }

    public static void readInit(Scanner in, GameState dst) {
        dst.width  = in.nextInt();
        dst.height = in.nextInt();
        if (in.hasNextLine()) in.nextLine();
        dst.tileCount = dst.width * dst.height;
        dst.terrain        = new byte[dst.tileCount];
        dst.waterAdj       = new boolean[dst.tileCount];
        dst.treeByTile     = new short[dst.tileCount];
        dst.myTrollByTile  = new short[dst.tileCount];
        dst.oppTrollByTile = new short[dst.tileCount];

        for (int y = 0; y < dst.height; y++) {
            String line = in.nextLine();
            for (int x = 0; x < dst.width; x++) {
                char c = line.charAt(x);
                byte t = TileType.fromChar(c);
                int tile = y * dst.width + x;
                dst.terrain[tile] = t;
                if (t == TileType.SHACK_ME)  dst.shackMeTile  = tile;
                if (t == TileType.SHACK_OPP) dst.shackOppTile = tile;
            }
        }
        computeWaterAdj(dst);
        dst.turn = 0;
    }

    private static void computeWaterAdj(GameState s) {
        for (int y = 0; y < s.height; y++) {
            for (int x = 0; x < s.width; x++) {
                int tile = y * s.width + x;
                boolean near = false;
                if (x > 0           && s.terrain[tile - 1]       == TileType.WATER) near = true;
                if (x < s.width - 1 && s.terrain[tile + 1]       == TileType.WATER) near = true;
                if (y > 0           && s.terrain[tile - s.width] == TileType.WATER) near = true;
                if (y < s.height-1  && s.terrain[tile + s.width] == TileType.WATER) near = true;
                s.waterAdj[tile] = near;
            }
        }
    }

    public static void readTurn(Scanner in, GameState dst) {
        for (int i = 0; i < dst.tileCount; i++) {
            dst.treeByTile[i]     = -1;
            dst.myTrollByTile[i]  = -1;
            dst.oppTrollByTile[i] = -1;
        }
        for (int p = 0; p < 2; p++) {
            int[] inv = (p == 0) ? dst.invMe : dst.invOpp;
            inv[Resource.PLUM]   = in.nextInt();
            inv[Resource.LEMON]  = in.nextInt();
            inv[Resource.APPLE]  = in.nextInt();
            inv[Resource.BANANA] = in.nextInt();
            inv[Resource.IRON]   = in.nextInt();
            inv[Resource.WOOD]   = in.nextInt();
        }

        dst.treeCount = in.nextInt();
        for (int i = 0; i < dst.treeCount; i++) {
            dst.treeType[i]     = TreeType.fromString(in.next());
            dst.treeX[i]        = (byte) in.nextInt();
            dst.treeY[i]        = (byte) in.nextInt();
            dst.treeSize[i]     = (byte) in.nextInt();
            dst.treeHealth[i]   = (byte) in.nextInt();
            dst.treeFruits[i]   = (byte) in.nextInt();
            dst.treeCooldown[i] = (byte) in.nextInt();
            int tile = dst.treeY[i] * dst.width + dst.treeX[i];
            dst.treeByTile[tile] = (short) i;
        }

        dst.trollCount = in.nextInt();
        for (int i = 0; i < dst.trollCount; i++) {
            dst.trollOriginalId[i]    = in.nextInt();
            dst.trollPlayer[i]        = (byte) in.nextInt();
            dst.trollX[i]             = (byte) in.nextInt();
            dst.trollY[i]             = (byte) in.nextInt();
            dst.trollMoveSpeed[i]     = (byte) in.nextInt();
            dst.trollCarryCapacity[i] = (byte) in.nextInt();
            dst.trollHarvestPower[i]  = (byte) in.nextInt();
            dst.trollChopPower[i]     = (byte) in.nextInt();
            int base = i * Resource.COUNT;
            dst.trollCarry[base + Resource.PLUM]   = (byte) in.nextInt();
            dst.trollCarry[base + Resource.LEMON]  = (byte) in.nextInt();
            dst.trollCarry[base + Resource.APPLE]  = (byte) in.nextInt();
            dst.trollCarry[base + Resource.BANANA] = (byte) in.nextInt();
            dst.trollCarry[base + Resource.IRON]   = (byte) in.nextInt();
            dst.trollCarry[base + Resource.WOOD]   = (byte) in.nextInt();
            int tile = dst.trollY[i] * dst.width + dst.trollX[i];
            if (dst.trollPlayer[i] == 0) dst.myTrollByTile[tile]  = (short) i;
            else                         dst.oppTrollByTile[tile] = (short) i;
        }
        dst.turn++;
    }
}
