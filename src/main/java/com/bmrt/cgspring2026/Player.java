package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.iaengine.GreedyAgent;
import com.bmrt.cgspring2026.model.GameState;

import java.util.Scanner;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);

        GreedyAgent agent = new GreedyAgent(state.tileCount);
        int[] actions = new int[GameState.MAX_TROLLS];
        StringBuilder out = new StringBuilder(256);

        while (true) {
            GameState.readTurn(in, state);
            int n = agent.decide(state, actions);

            out.setLength(0);
            if (n == 0) {
                out.append("WAIT");
            } else {
                for (int i = 0; i < n; i++) {
                    if (i > 0) out.append(';');
                    out.append(Actions.format(actions[i], state));
                }
            }
            System.out.println(out);
        }
    }
}
