package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class ShackAdjacency {

    public static final byte[] x = new byte[4];
    public static final byte[] y = new byte[4];
    public static int count;

    private static final int[] DX = { 1, -1, 0,  0 };
    private static final int[] DY = { 0,  0, 1, -1 };

    public static void init() {
        count = 0;
        int sx = GameState.shackMeX;
        int sy = GameState.shackMeY;
        for (int k = 0; k < 4; k++) {
            int nx = sx + DX[k];
            int ny = sy + DY[k];
            if (nx < 0 || nx >= GameState.width)  continue;
            if (ny < 0 || ny >= GameState.height) continue;
            if (GameState.tileAt(nx, ny) != TileType.GRASS) continue;
            x[count] = (byte) nx;
            y[count] = (byte) ny;
            count++;
        }
    }

    private ShackAdjacency() {}
}
