package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class PathTable {

    public static final int UNREACHABLE = 0xFF;

    public static int N;
    public static byte[] cellIdAt;     // [W*H] -> id 0..N-1, 0xFF si non walkable
    public static byte[] cellX;        // [N] x de la case d'id i
    public static byte[] cellY;        // [N] y de la case d'id i

    static void indexCells() {
        int W = GameState.width;
        int H = GameState.height;
        cellIdAt = new byte[W * H];
        java.util.Arrays.fill(cellIdAt, (byte) 0xFF);
        N = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (GameState.tileAt(x, y) == TileType.GRASS) N++;
            }
        }
        cellX = new byte[N];
        cellY = new byte[N];
        int id = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (GameState.tileAt(x, y) == TileType.GRASS) {
                    cellIdAt[y * W + x] = (byte) id;
                    cellX[id] = (byte) x;
                    cellY[id] = (byte) y;
                    id++;
                }
            }
        }
    }

    public static int cellId(int x, int y) {
        return cellIdAt[y * GameState.width + x] & 0xFF;
    }

    private PathTable() {}
}
