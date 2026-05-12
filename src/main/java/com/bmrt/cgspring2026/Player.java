package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Troll;

import java.util.Scanner;

public class Player {

    private static final long FIRST_TURN_BUDGET_MS = 900;
    private static final long TURN_BUDGET_MS = 45;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);

        while (true) {
            GameState.readTurn(in, state);

            StringBuilder out = new StringBuilder();
            boolean first = true;
            for (Troll t : state.trolls) {
                if (t.player != 0) {
                    continue;
                }
                if (!first) {
                    out.append(';');
                }
                first = false;
                out.append(new Action.Move(t.id, state.myShackX, state.myShackY).toCommand());
            }
            if (first) {
                out.append(new Action.Msg("idle").toCommand());
            }
            System.out.println(out);
        }
    }
}
