package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.pathfinding.PathTable;

import java.util.Scanner;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        ShackAdjacency.init();

        GameState state = new GameState();
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb = new StringBuilder();

        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);

            int n = GreedyAgent.decide(state, actionBuf);
            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            System.out.println(sb);

            System.err.println("turn=" + state.turn + " elapsed=" + (System.nanoTime() - start) + "ns");
            state.turn++;
        }
    }
}
