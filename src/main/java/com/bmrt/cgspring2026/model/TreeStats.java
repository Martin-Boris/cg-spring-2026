package com.bmrt.cgspring2026.model;

public final class TreeStats {
    public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
    public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };

    public static final byte[][] HEALTH = {
        { 6, 8, 10, 12 },
        { 6, 8, 10, 12 },
        { 11, 14, 17, 20 },
        { 3, 4, 5, 6 }
    };

    public static final int MAX_FRUITS = 3;
    public static final int MAX_SIZE   = 4;

    public static int cooldown(int type, boolean nearWater) {
        return nearWater ? COOLDOWN_WATER[type] : COOLDOWN_NORMAL[type];
    }

    public static int healthAt(int type, int size) {
        return HEALTH[type][size - 1];
    }

    private TreeStats() {}
}
