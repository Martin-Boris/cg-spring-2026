package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.simulation.Simulator;

public final class GenomeEvaluator {

    public static final int HORIZON = 25;
    public static final double ALPHA_WOOD_CARRY = 2.0;

    private GenomeEvaluator() {
    }

    public static double evaluate(GameState scratch, GameState source,
                                  short[] popBuf, byte[] popLen, int idx,
                                  int[] actionBuf) {
        scratch.copyFrom(source);
        int[] cursor = TrollPolicy.cursorBuf;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
        for (int t = 0; t < HORIZON; t++) {
            int n = TrollPolicy.fillActions(scratch, popBuf, popLen, idx, cursor, actionBuf);
            Simulator.tick(scratch, actionBuf, n);
        }
        return fitness(scratch);
    }

    private static double fitness(GameState finalState) {
        int scoreMe = finalState.score(0);
        int scoreOpp = finalState.score(1);
        int woodCarryMe = 0;
        for (int i = 0; i < finalState.trollCount; i++) {
            if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
            woodCarryMe += finalState.trollInventory[i * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
        }
        return (scoreMe - scoreOpp) + ALPHA_WOOD_CARRY * woodCarryMe;
    }
}
