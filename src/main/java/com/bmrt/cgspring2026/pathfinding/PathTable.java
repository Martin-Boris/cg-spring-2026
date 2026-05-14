package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class PathTable {

    public static final int UNREACHABLE = 0xFF;

    public static int N;               // nombre de cases GRASS (traversables)
    public static int Ntot;            // N + nombre de shacks indexes (interrogeables mais non traversables)
    public static int shackMeId  = -1; // id de SHACK_ME si present, -1 sinon
    public static int shackOppId = -1; // id de SHACK_OPP si present, -1 sinon
    public static byte[] cellIdAt;     // [W*H] -> id 0..Ntot-1, 0xFF si non indexe
    public static byte[] cellX;        // [Ntot] x de la case d'id i
    public static byte[] cellY;        // [Ntot] y de la case d'id i

    static void indexCells() {
        int W = GameState.width;
        int H = GameState.height;
        cellIdAt = new byte[W * H];
        java.util.Arrays.fill(cellIdAt, (byte) 0xFF);
        int grassCount = 0;
        int shackMeIdx = -1, shackOppIdx = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                byte t = GameState.tileAt(x, y);
                if      (t == TileType.GRASS)     grassCount++;
                else if (t == TileType.SHACK_ME)  shackMeIdx  = y * W + x;
                else if (t == TileType.SHACK_OPP) shackOppIdx = y * W + x;
            }
        }
        N = grassCount;
        int extra = (shackMeIdx >= 0 ? 1 : 0) + (shackOppIdx >= 0 ? 1 : 0);
        Ntot = N + extra;
        cellX = new byte[Ntot];
        cellY = new byte[Ntot];
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
        shackMeId  = -1;
        shackOppId = -1;
        if (shackMeIdx >= 0) {
            shackMeId = id;
            cellIdAt[shackMeIdx] = (byte) id;
            cellX[id] = (byte) (shackMeIdx % W);
            cellY[id] = (byte) (shackMeIdx / W);
            id++;
        }
        if (shackOppIdx >= 0) {
            shackOppId = id;
            cellIdAt[shackOppIdx] = (byte) id;
            cellX[id] = (byte) (shackOppIdx % W);
            cellY[id] = (byte) (shackOppIdx / W);
            id++;
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
        dist  = new byte[Ntot][Ntot];
        paths = new byte[Ntot][Ntot][];
        for (int src = 0; src < Ntot; src++) {
            bfs(src);
            dist[src][src]  = 0;
            paths[src][src] = new byte[]{ (byte) src };
            for (int dst = src + 1; dst < Ntot; dst++) {
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
        queue   = new int[Ntot];
        bfsDist = new int[Ntot];
        bfsPrev = new int[Ntot];
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
                if (nid >= N) continue; // shack : feuille, on n'y propage pas
                queue[tail++] = nid;
            }
        }
    }

    public static int distance(int fromId, int toId) {
        return dist[fromId][toId] & 0xFF;
    }

    public static int distance(int fx, int fy, int tx, int ty) {
        int W = GameState.width;
        int from = cellIdAt[fy * W + fx] & 0xFF;
        int to   = cellIdAt[ty * W + tx] & 0xFF;
        return dist[from][to] & 0xFF;
    }

    /** Cell id à l'étape {@code k} du chemin {@code from → to} ; {@code k} est clampé à la distance. */
    public static int stepAlong(int fromId, int toId, int k) {
        byte[] p = paths[fromId][toId];
        int last = p.length - 1;
        if (k > last) k = last;
        if (fromId <= toId) {
            return p[k] & 0xFF;
        }
        return p[last - k] & 0xFF;
    }

    /** Surcharge par coords : retourne le raw cell index {@code y*W + x} de la case atteinte. */
    public static int stepAlong(int fx, int fy, int tx, int ty, int k) {
        int W = GameState.width;
        int from = cellIdAt[fy * W + fx] & 0xFF;
        int to   = cellIdAt[ty * W + tx] & 0xFF;
        int destId = stepAlong(from, to, k);
        return (cellY[destId] & 0xFF) * W + (cellX[destId] & 0xFF);
    }

    private PathTable() {}
}
