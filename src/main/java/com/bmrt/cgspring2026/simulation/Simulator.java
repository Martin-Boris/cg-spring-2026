package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;

public final class Simulator {

    private static final byte[] WATER_BOOST = { 5, 5, 7, 2 };

    private Simulator() {}

    public static void tick(GameState s, int[] actions, int n) {
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyDrops(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.DROP) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            if (!trollNearOwnShack(s, idx)) continue;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            if (total == 0) continue;
            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
            for (int r = 0; r < ResourceType.COUNT; r++) {
                s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
                s.trollInventory[invBase + r] = 0;
            }
        }
    }

    static boolean trollNearOwnShack(GameState s, int trollIdx) {
        int player = s.trollPlayer[trollIdx] & 0xFF;
        int sx = (player == 0) ? GameState.shackMeX  : GameState.shackOppX;
        int sy = (player == 0) ? GameState.shackMeY  : GameState.shackOppY;
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int d  = Math.abs(tx - sx) + Math.abs(ty - sy);
        return d <= 1;
    }

    static void applyMines(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.MINE) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int cp = s.trollCP[idx] & 0xFF;
            if (cp == 0) continue;
            if (!adjacentToIron(s, idx)) continue;
            int cc = s.trollCC[idx] & 0xFF;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            int free = cc - total;
            int gain = Math.min(cp, free);
            if (gain <= 0) continue;
            s.trollInventory[invBase + ResourceType.IRON] += gain;
        }
    }

    static boolean adjacentToIron(GameState s, int trollIdx) {
        int x = s.trollX[trollIdx] & 0xFF;
        int y = s.trollY[trollIdx] & 0xFF;
        return tileAtOrNone(x + 1, y) == TileType.IRON
            || tileAtOrNone(x - 1, y) == TileType.IRON
            || tileAtOrNone(x, y + 1) == TileType.IRON
            || tileAtOrNone(x, y - 1) == TileType.IRON;
    }

    static byte tileAtOrNone(int x, int y) {
        if (x < 0 || x >= GameState.width || y < 0 || y >= GameState.height) return -1;
        return GameState.tiles[y * GameState.width + x];
    }

    static void compactDeadTrees(GameState s) {
        int i = 0;
        while (i < s.treeCount) {
            if (s.treeHealth[i] <= 0) {
                int last = s.treeCount - 1;
                if (i != last) {
                    s.treeType[i]     = s.treeType[last];
                    s.treeX[i]        = s.treeX[last];
                    s.treeY[i]        = s.treeY[last];
                    s.treeSize[i]     = s.treeSize[last];
                    s.treeHealth[i]   = s.treeHealth[last];
                    s.treeFruits[i]   = s.treeFruits[last];
                    s.treeCooldown[i] = s.treeCooldown[last];
                }
                s.treeCount--;
            } else {
                i++;
            }
        }
    }

    static void plantTick(GameState s) {
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeCooldown[i] > 0) s.treeCooldown[i]--;
            if (s.treeCooldown[i] == 0 && s.treeHealth[i] > 0) {
                int type = s.treeType[i] & 0xFF;
                if (s.treeSize[i] < 4) {
                    s.treeSize[i]++;
                    s.treeHealth[i] += deltaHealth(type);
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                } else if (s.treeFruits[i] < 3) {
                    s.treeFruits[i]++;
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                }
            }
        }
    }

    private static byte deltaHealth(int treeType) {
        // PLUM=2, LEMON=2, APPLE=3, BANANA=1 (mirrors Constants.PLANT_DELTA_HEALTH)
        switch (treeType) {
            case TreeType.PLUM:   return 2;
            case TreeType.LEMON:  return 2;
            case TreeType.APPLE:  return 3;
            case TreeType.BANANA: return 1;
            default: throw new IllegalStateException();
        }
    }

    private static byte growthCooldown(GameState s, int treeIdx, int treeType) {
        int base = TreeType.COOLDOWN_NORMAL[treeType] & 0xFF;
        int x = s.treeX[treeIdx] & 0xFF;
        int y = s.treeY[treeIdx] & 0xFF;
        if (nearWater(x, y)) base -= WATER_BOOST[treeType] & 0xFF;
        return (byte) base;
    }

    private static boolean nearWater(int x, int y) {
        return tileEquals(x + 1, y, TileType.WATER)
            || tileEquals(x - 1, y, TileType.WATER)
            || tileEquals(x, y + 1, TileType.WATER)
            || tileEquals(x, y - 1, TileType.WATER);
    }

    private static boolean tileEquals(int x, int y, byte type) {
        if (x < 0 || x >= GameState.width || y < 0 || y >= GameState.height) return false;
        return GameState.tiles[y * GameState.width + x] == type;
    }
}
