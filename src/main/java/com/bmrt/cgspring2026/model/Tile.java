package com.bmrt.cgspring2026.model;

public enum Tile {
    GRASS,
    WATER,
    ROCK,
    IRON,
    SHACK_ME,
    SHACK_OPP;

    private static final Tile[] VALUES = values();

    public static Tile from(char c) {
        return switch (c) {
            case '.' -> GRASS;
            case '~' -> WATER;
            case '#' -> ROCK;
            case '+' -> IRON;
            case '0' -> SHACK_ME;
            case '1' -> SHACK_OPP;
            default -> throw new IllegalArgumentException("Unknown tile char: " + c);
        };
    }

    public static Tile byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public boolean isWalkable() {
        return this == GRASS;
    }
}
