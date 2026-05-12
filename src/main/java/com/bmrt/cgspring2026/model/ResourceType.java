package com.bmrt.cgspring2026.model;

public enum ResourceType {
    PLUM,
    LEMON,
    APPLE,
    BANANA,
    IRON,
    WOOD;

    private static final ResourceType[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public static ResourceType byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public int scorePerUnit() {
        return switch (this) {
            case PLUM, LEMON, APPLE, BANANA -> 1;
            case WOOD -> 4;
            case IRON -> 0;
        };
    }
}
