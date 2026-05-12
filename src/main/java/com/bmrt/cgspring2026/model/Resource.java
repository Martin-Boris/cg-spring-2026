package com.bmrt.cgspring2026.model;

public final class Resource {
    public static final byte PLUM   = 0;
    public static final byte LEMON  = 1;
    public static final byte APPLE  = 2;
    public static final byte BANANA = 3;
    public static final byte IRON   = 4;
    public static final byte WOOD   = 5;
    public static final int  COUNT  = 6;

    public static final int POINTS_FRUIT = 1;
    public static final int POINTS_WOOD  = 4;

    public static int points(int resource) {
        if (resource == WOOD) return POINTS_WOOD;
        if (resource == IRON) return 0;
        return POINTS_FRUIT;
    }

    private Resource() {}
}
