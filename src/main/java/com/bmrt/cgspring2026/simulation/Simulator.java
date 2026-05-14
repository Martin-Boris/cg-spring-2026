package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;

public final class Simulator {

    private Simulator() {}

    public static void tick(GameState s, int[] actions, int n) {
        s.turn++;
    }
}
