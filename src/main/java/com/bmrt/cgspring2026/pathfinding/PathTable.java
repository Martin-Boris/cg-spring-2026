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

    public static byte[][]   dist;    // [N][N] distance, UNREACHABLE si non connecté
    public static byte[][][] paths;   // [N][N] -> chemin partagé symétriquement

    public static void init() {
        indexCells();
        allocateBfsBuffers();
        dist  = new byte[N][N];
        paths = new byte[N][N][];
        for (int src = 0; src < N; src++) {
            bfs(src);
            dist[src][src]  = 0;
            paths[src][src] = new byte[]{ (byte) src };
            for (int dst = src + 1; dst < N; dst++) {
                if (bfsDist[dst] == UNREACHABLE) {
                    dist[src][dst] = (byte) UNREACHABLE;
                    dist[dst][src] = (byte) UNREACHABLE;
                    continue;
                }
                byte[] p = reconstruct(src, dst);
                paths[src][dst] = p;
                paths[dst][src] = p;             // référence partagée — lecture inversée pour dst→src
                byte d = (byte) (p.length - 1);
                dist[src][dst] = d;
                dist[dst][src] = d;
            }
        }
    }

    private static byte[] reconstruct(int src, int dst) {
        int len = bfsDist[dst] + 1;
        byte[] path = new byte[len];
        int cur = dst;
        for (int i = len - 1; i >= 0; i--) {
            path[i] = (byte) cur;
            cur = bfsPrev[cur];
        }
        return path;
    }

    public  static int[] bfsDist;   // distance depuis la dernière source BFS, UNREACHABLE sinon
    public  static int[] bfsPrev;   // prédecesseur dans l'arbre BFS, -1 si racine ou non atteint
    private static int[] queue;

    private static final int[] DX = { 1, -1, 0,  0 };
    private static final int[] DY = { 0,  0, 1, -1 };

    static void allocateBfsBuffers() {
        queue   = new int[N];
        bfsDist = new int[N];
        bfsPrev = new int[N];
    }

    static void bfs(int src) {
        java.util.Arrays.fill(bfsDist, UNREACHABLE);
        java.util.Arrays.fill(bfsPrev, -1);
        int W = GameState.width;
        int H = GameState.height;
        bfsDist[src] = 0;
        int head = 0, tail = 0;
        queue[tail++] = src;
        while (head < tail) {
            int cur = queue[head++];
            int cx = cellX[cur] & 0xFF;
            int cy = cellY[cur] & 0xFF;
            int d  = bfsDist[cur];
            for (int k = 0; k < 4; k++) {
                int nx = cx + DX[k];
                int ny = cy + DY[k];
                if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                int nid = cellIdAt[ny * W + nx] & 0xFF;
                if (nid == UNREACHABLE) continue;
                if (bfsDist[nid] != UNREACHABLE) continue;
                bfsDist[nid] = d + 1;
                bfsPrev[nid] = cur;
                queue[tail++] = nid;
            }
        }
    }

    private PathTable() {}
}
