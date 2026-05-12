package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Troll;

import java.util.Random;

public final class RandomWalk {

    private static final int RADIUS = 5;
    private static final int ATTEMPTS = 16;

    private RandomWalk() {
    }

    public static Action.Move pick(Troll troll, GameState state) {
        Random rng = new Random((long) state.turn * 1000L + troll.id);
        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = rng.nextInt(2 * RADIUS + 1) - RADIUS;
            int dy = rng.nextInt(2 * RADIUS + 1) - RADIUS;
            int tx = clamp(troll.x + dx, 0, state.width - 1);
            int ty = clamp(troll.y + dy, 0, state.height - 1);
            if ((tx != troll.x || ty != troll.y) && state.walkable(tx, ty)) {
                return new Action.Move(troll.id, tx, ty);
            }
        }
        return new Action.Move(troll.id, troll.x, troll.y);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
