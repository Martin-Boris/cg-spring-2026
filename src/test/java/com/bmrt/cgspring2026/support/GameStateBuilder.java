package com.bmrt.cgspring2026.support;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Resource;
import com.bmrt.cgspring2026.model.TileType;

public final class GameStateBuilder {

    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    private final GameState state = new GameState();

    public GameStateBuilder withGrid(String... rows) {
        state.height = rows.length;
        state.width = rows[0].length();
        state.tileCount = state.width * state.height;
        state.terrain = new byte[state.tileCount];
        state.waterAdj = new boolean[state.tileCount];
        state.treeByTile = new short[state.tileCount];
        state.myTrollByTile = new short[state.tileCount];
        state.oppTrollByTile = new short[state.tileCount];
        for (int i = 0; i < state.tileCount; i++) {
            state.treeByTile[i] = -1;
            state.myTrollByTile[i] = -1;
            state.oppTrollByTile[i] = -1;
        }
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                char c = rows[y].charAt(x);
                byte t = TileType.fromChar(c);
                int tile = y * state.width + x;
                state.terrain[tile] = t;
                if (t == TileType.SHACK_ME)  state.shackMeTile  = tile;
                if (t == TileType.SHACK_OPP) state.shackOppTile = tile;
            }
        }
        computeWaterAdj();
        return this;
    }

    private void computeWaterAdj() {
        for (int y = 0; y < state.height; y++) {
            for (int x = 0; x < state.width; x++) {
                int tile = y * state.width + x;
                boolean near = false;
                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (nx >= 0 && nx < state.width && ny >= 0 && ny < state.height
                            && state.terrain[ny * state.width + nx] == TileType.WATER) {
                        near = true;
                    }
                }
                state.waterAdj[tile] = near;
            }
        }
    }

    public GameStateBuilder withMyTroll(int id, int x, int y, int moveSpeed, int carryCap, int harvest, int chop) {
        return addTroll(id, 0, x, y, moveSpeed, carryCap, harvest, chop, new int[Resource.COUNT]);
    }

    public GameStateBuilder withMyTrollCarrying(int id, int x, int y, int carryCap, int[] carry) {
        return addTroll(id, 0, x, y, 1, carryCap, 1, 1, carry);
    }

    public GameStateBuilder withOppTroll(int id, int x, int y) {
        return addTroll(id, 1, x, y, 1, 1, 1, 1, new int[Resource.COUNT]);
    }

    private GameStateBuilder addTroll(int id, int player, int x, int y,
                                      int moveSpeed, int carryCap, int harvest, int chop,
                                      int[] carry) {
        int i = state.trollCount++;
        state.trollOriginalId[i] = id;
        state.trollPlayer[i] = (byte) player;
        state.trollX[i] = (byte) x;
        state.trollY[i] = (byte) y;
        state.trollMoveSpeed[i]     = (byte) moveSpeed;
        state.trollCarryCapacity[i] = (byte) carryCap;
        state.trollHarvestPower[i]  = (byte) harvest;
        state.trollChopPower[i]     = (byte) chop;
        for (int r = 0; r < Resource.COUNT; r++) {
            state.trollCarry[i * Resource.COUNT + r] = (byte) carry[r];
        }
        int tile = y * state.width + x;
        if (player == 0) state.myTrollByTile[tile]  = (short) i;
        else             state.oppTrollByTile[tile] = (short) i;
        return this;
    }

    public GameStateBuilder withTree(byte type, int x, int y, int size, int health, int fruits, int cooldown) {
        int i = state.treeCount++;
        state.treeType[i] = type;
        state.treeX[i] = (byte) x;
        state.treeY[i] = (byte) y;
        state.treeSize[i]     = (byte) size;
        state.treeHealth[i]   = (byte) health;
        state.treeFruits[i]   = (byte) fruits;
        state.treeCooldown[i] = (byte) cooldown;
        state.treeByTile[y * state.width + x] = (short) i;
        return this;
    }

    public GameState build() {
        return state;
    }
}
