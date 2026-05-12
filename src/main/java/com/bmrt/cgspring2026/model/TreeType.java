package com.bmrt.cgspring2026.model;

public final class TreeType {
    public static final byte PLUM   = 0;
    public static final byte LEMON  = 1;
    public static final byte APPLE  = 2;
    public static final byte BANANA = 3;
    public static final int  COUNT  = 4;

    public static byte fromString(String s) {
        return switch (s) {
            case "PLUM"   -> PLUM;
            case "LEMON"  -> LEMON;
            case "APPLE"  -> APPLE;
            case "BANANA" -> BANANA;
            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
        };
    }

    public static String toName(int t) {
        return switch (t) {
            case PLUM   -> "PLUM";
            case LEMON  -> "LEMON";
            case APPLE  -> "APPLE";
            case BANANA -> "BANANA";
            default -> throw new IllegalArgumentException("Unknown tree type: " + t);
        };
    }

    private TreeType() {}
}
