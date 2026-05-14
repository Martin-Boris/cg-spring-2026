package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.pathfinding.PathTable;

import java.util.Scanner;

public class Player {

    private static final long FIRST_TURN_BUDGET_MS = 900;
    private static final long TURN_BUDGET_MS = 45;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        GameState state = new GameState();

        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);

            // placeholder — emit WAIT for each own troll
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < state.trollCount; i++) {
                if (state.trollPlayer[i] != 0) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append("WAIT ").append(state.trollId[i] & 0xFF);
            }
            System.out.println(sb);

            System.err.println("turn=" + state.turn + " elapsed=" + (System.nanoTime() - start) + "ns");
            state.turn++;
        }
    }
}
