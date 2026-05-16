package com.bmrt.cgspring2026.model;

public final class TreeType {

    public static final byte PLUM   = 0;
    public static final byte LEMON  = 1;
    public static final byte APPLE  = 2;
    public static final byte BANANA = 3;

    public static final int COUNT = 4;

    public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
    public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };

    public static final byte[][] HEALTH_BY_SIZE = {
            { 6,  8, 10, 12 },
            { 6,  8, 10, 12 },
            { 11, 14, 17, 20 },
            { 3,  4,  5,  6 },
    };

    public static byte fromString(String s) {
        return switch (s) {
            case "PLUM"   -> PLUM;
            case "LEMON"  -> LEMON;
            case "APPLE"  -> APPLE;
            case "BANANA" -> BANANA;
            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
        };
    }

    private TreeType() {}
}
