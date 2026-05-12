package com.bmrt.cgspring2026.model;

public enum TreeType {
    PLUM,
    LEMON,
    APPLE,
    BANANA;

    private static final TreeType[] VALUES = values();

    public static TreeType parse(String s) {
        return switch (s) {
            case "PLUM" -> PLUM;
            case "LEMON" -> LEMON;
            case "APPLE" -> APPLE;
            case "BANANA" -> BANANA;
            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
        };
    }

    public static TreeType byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public int normalCooldown() {
        return switch (this) {
            case PLUM, LEMON -> 8;
            case APPLE -> 9;
            case BANANA -> 6;
        };
    }

    public int waterCooldown() {
        return switch (this) {
            case PLUM, LEMON -> 3;
            case APPLE -> 2;
            case BANANA -> 4;
        };
    }

    public int healthForSize(int size) {
        return switch (this) {
            case PLUM, LEMON -> switch (size) {
                case 1 -> 6;
                case 2 -> 8;
                case 3 -> 10;
                case 4 -> 12;
                default -> throw new IllegalArgumentException("Invalid size: " + size);
            };
            case APPLE -> switch (size) {
                case 1 -> 11;
                case 2 -> 14;
                case 3 -> 17;
                case 4 -> 20;
                default -> throw new IllegalArgumentException("Invalid size: " + size);
            };
            case BANANA -> switch (size) {
                case 1 -> 3;
                case 2 -> 4;
                case 3 -> 5;
                case 4 -> 6;
                default -> throw new IllegalArgumentException("Invalid size: " + size);
            };
        };
    }

    public ResourceType fruit() {
        return switch (this) {
            case PLUM -> ResourceType.PLUM;
            case LEMON -> ResourceType.LEMON;
            case APPLE -> ResourceType.APPLE;
            case BANANA -> ResourceType.BANANA;
        };
    }
}
