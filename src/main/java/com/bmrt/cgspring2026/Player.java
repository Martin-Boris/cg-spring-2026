package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.model.GameState;

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
            // V1 (à venir) : invoquer l'agent greedy ici.
            System.out.println(Actions.format(Actions.waitAction(), state));
        }
    }
}
