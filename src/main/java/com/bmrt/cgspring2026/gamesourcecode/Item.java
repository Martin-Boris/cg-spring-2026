package com.bmrt.cgspring2026.gamesourcecode;

public enum Item {
    PLUM,
    LEMON,
    APPLE,
    BANANA,
    IRON,
    WOOD;

    public boolean isPlant() {
        return this.ordinal() <= BANANA.ordinal();
    }
}
