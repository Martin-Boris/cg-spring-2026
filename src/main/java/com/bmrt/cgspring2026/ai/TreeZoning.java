package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TreeZoning {

    private static final int INF = Integer.MAX_VALUE;

    private final int width;
    private final int height;
    private final Zone[] zones;

    private TreeZoning(int width, int height, Zone[] zones) {
        this.width = width;
        this.height = height;
        this.zones = zones;
    }

    public static TreeZoning precompute(GameState state) {
        int w = state.width;
        int h = state.height;
        int[] distMine = bfsFromShack(state, state.myShackX, state.myShackY);
        int[] distOpp = bfsFromShack(state, state.oppShackX, state.oppShackY);
        Zone[] zones = new Zone[w * h];
        for (int i = 0; i < zones.length; i++) {
            int dm = distMine[i];
            int dp = distOpp[i];
            if (dm == INF && dp == INF) {
                zones[i] = Zone.UNREACHABLE;
            } else if (dm < dp) {
                zones[i] = Zone.MINE;
            } else if (dm > dp) {
                zones[i] = Zone.OPP;
            } else {
                zones[i] = Zone.NEUTRAL;
            }
        }
        return new TreeZoning(w, h, zones);
    }

    private static int[] bfsFromShack(GameState state, int shackX, int shackY) {
        int w = state.width;
        int h = state.height;
        int[] dist = new int[w * h];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = INF;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        // Seed BFS from GRASS neighbors of the shack (shack itself isn't walkable).
        for (int k = 0; k < 4; k++) {
            int nx = shackX + dx[k];
            int ny = shackY + dy[k];
            if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
                dist[ny * w + nx] = 1;
                queue.add(new int[]{nx, ny});
            }
        }
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cy = cur[1];
            int cd = dist[cy * w + cx];
            for (int k = 0; k < 4; k++) {
                int nx = cx + dx[k];
                int ny = cy + dy[k];
                if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
                    dist[ny * w + nx] = cd + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return dist;
    }

    public Zone zoneOf(int x, int y) {
        return zones[y * width + x];
    }
}
