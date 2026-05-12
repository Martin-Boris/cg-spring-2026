package com.bmrt.cgspring2026.model;

public final class TileType {
    public static final byte GRASS     = 0;
    public static final byte WATER     = 1;
    public static final byte ROCK      = 2;
    public static final byte IRON      = 3;
    public static final byte SHACK_ME  = 4;
    public static final byte SHACK_OPP = 5;

    public static byte fromChar(char c) {
        return switch (c) {
            case '.' -> GRASS;
            case '~' -> WATER;
            case '#' -> ROCK;
            case '+' -> IRON;
            case '0' -> SHACK_ME;
            case '1' -> SHACK_OPP;
            default  -> throw new IllegalArgumentException("Unknown terrain char: " + c);
        };
    }

    public static boolean isWalkable(byte t) {
        return t == GRASS;
    }

    private TileType() {}
}
