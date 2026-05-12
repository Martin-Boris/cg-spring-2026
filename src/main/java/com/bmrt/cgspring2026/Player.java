package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.ai.GreedyAi;
import com.bmrt.cgspring2026.model.GameState;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);
        GreedyAi ai = new GreedyAi();

        while (true) {
            GameState.readTurn(in, state);
            List<Action> actions = ai.decide(state);
            String output;
            if (actions.isEmpty()) {
                output = new Action.Msg("idle").toCommand();
            } else {
                output = actions.stream()
                        .map(Action::toCommand)
                        .collect(Collectors.joining(";"));
            }
            System.out.println(output);
        }
    }
}
