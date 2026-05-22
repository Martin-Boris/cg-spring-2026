package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.simulation.Simulator;

public final class GenomeEvaluator {

    public static final int HORIZON = 15;
    public static final int TRAIN_PUSH_TURN_CUTOFF = 150;
    public static final int TRAIN_PUSH_TROLL_CAP = 5;
    public static final double ALPHA_WOOD_CARRY = 2.0;
    public static final double ALPHA_FRUIT_CARRY = 0.5;
    public static final double ALPHA_IRON_CARRY = 0.5;
    public static final double ALPHA_TRAIN_PUSH = 1;
    public static final double ALPHA_NEAR_TREE = 2.0;

    private static final int[] TRAIN_RESOURCES = {
            ResourceType.PLUM, ResourceType.LEMON, ResourceType.APPLE, ResourceType.IRON
    };

    // Tampons "zéro" immuables (lus seulement) pour la surcharge cold-start.
    private static final int[] ZERO_CURSOR = new int[GameState.MAX_TROLLS];
    private static final byte[] ZERO_PHASE = new byte[GameState.MAX_TROLLS];

    private GenomeEvaluator() {
    }

    public static double evaluate(GameState scratch, GameState source,
                                  short[] popBuf, byte[] popLen, int idx,
                                  int[] actionBuf) {
        return evaluate(scratch, source, popBuf, popLen, idx, actionBuf, ZERO_CURSOR, ZERO_PHASE);
    }

    public static double evaluate(GameState scratch, GameState source,
                                  short[] popBuf, byte[] popLen, int idx,
                                  int[] actionBuf,
                                  int[] startCursor, byte[] startPhase) {
        scratch.copyFrom(source);
        int[] cursor = TrollPolicy.cursorBuf;
        System.arraycopy(startCursor, 0, cursor, 0, GameState.MAX_TROLLS);
        System.arraycopy(startPhase, 0, TrollPolicy.policyPhase, 0, GameState.MAX_TROLLS);
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
        int fruitCarryMe = 0;
        int ironCarryMe = 0;
        int ownTrolls = 0;
        for (int i = 0; i < finalState.trollCount; i++) {
            if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
            ownTrolls++;
            int base = i * ResourceType.COUNT;
            woodCarryMe += finalState.trollInventory[base + ResourceType.WOOD] & 0xFF;
            ironCarryMe += finalState.trollInventory[base + ResourceType.IRON] & 0xFF;
            for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
                fruitCarryMe += finalState.trollInventory[base + r] & 0xFF;
        }

        double trainPush = 0.0;
        if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF && ownTrolls < TRAIN_PUSH_TROLL_CAP) {
            int target = ownTrolls + 1;
            for (int k = 0; k < TRAIN_RESOURCES.length; k++) {
                int stock = finalState.shackInventory[TRAIN_RESOURCES[k]];
                trainPush += Math.min(stock, target);
            }
        }

        double nearTreeBonus = 0.0;
        if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF) {
            boolean hasPlum = false, hasLemon = false, hasApple = false;
            int sx = GameState.shackMeX, sy = GameState.shackMeY;
            for (int t = 0; t < finalState.treeCount; t++) {
                if (finalState.treeHealth[t] <= 0) continue;
                byte type = finalState.treeType[t];
                if (type == TreeType.BANANA) continue;
                int dx = (finalState.treeX[t] & 0xFF) - sx;
                int dy = (finalState.treeY[t] & 0xFF) - sy;
                if (Math.abs(dx) + Math.abs(dy) > 5) continue;
                if      (type == TreeType.PLUM  && !hasPlum)  hasPlum  = true;
                else if (type == TreeType.LEMON && !hasLemon) hasLemon = true;
                else if (type == TreeType.APPLE && !hasApple) hasApple = true;
                if (hasPlum && hasLemon && hasApple) break;
            }
            int typesCovered = (hasPlum ? 1 : 0) + (hasLemon ? 1 : 0) + (hasApple ? 1 : 0);
            nearTreeBonus = ALPHA_NEAR_TREE * typesCovered;
        }

        return (scoreMe - scoreOpp)
                + ALPHA_WOOD_CARRY * woodCarryMe
                + ALPHA_FRUIT_CARRY * fruitCarryMe
                + ALPHA_IRON_CARRY * ironCarryMe
                + ALPHA_TRAIN_PUSH * trainPush
                + nearTreeBonus;
    }
}
