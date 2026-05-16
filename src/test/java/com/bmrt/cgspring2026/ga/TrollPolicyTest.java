package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "...1..",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    private static GameState stateOneTroll(int x, int y, int wood) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
        s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
        s.trollInventory[ResourceType.WOOD] = (byte) wood;
        s.trollCarryTotal[0] = wood;
        return s;
    }

    @Test void emptyListEmitsWait() {
        GameState s = stateOneTroll(3, 3, 0);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(n).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodCarriedTriggersMoveTowardsShackAdjacent() {
        GameState s = stateOneTroll(5, 4, 2);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // Une target lointaine — on doit quand même drop d'abord
        Genome.setGene(buf, 0, 0, 0, Genome.encode(4, 4));
        Genome.setLen(lenBuf, 0, 0, 1);
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
    }

    @Test void dropWhenAdjacentToShack() {
        GameState s = stateOneTroll(0, 1, 2); // (0,1) is shack-adjacent
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
    }

    @Test void chopWhenStandingOnTargetTree() {
        GameState s = stateOneTroll(3, 2, 0);
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2));
        Genome.setLen(lenBuf, 0, 0, 1);
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.CHOP);
    }

    @Test void skipsDeadTreeAndAdvancesCursor() {
        GameState s = stateOneTroll(3, 2, 0);
        // Pas d'arbre vivant à (3,2) — gène ciblé pointe vers du vide
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2)); // mort/disparu
        Genome.setGene(buf, 0, 0, 1, Genome.encode(4, 2));
        Genome.setLen(lenBuf, 0, 0, 2);
        s.treeCount = 1;
        s.treeX[0] = 4; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        // Le cursor doit avoir avancé à 1, et l'action est un MOVE vers (4,2)
        assertThat(cursor[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(out[0])).isEqualTo(4);
        assertThat(Action.arg2(out[0])).isEqualTo(2);
    }

    @Test void opponentTrollDecidedViaGreedy() {
        GameState s = stateOneTroll(3, 2, 0);
        s.trollCount = 2;
        s.trollPlayer[1] = 1; // opp
        s.trollX[1] = 5; s.trollY[1] = 4;
        s.trollMS[1] = 2;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(n).isEqualTo(2);
        // Action 0 = mon troll (WAIT, pas de target), Action 1 = opp (MOVE vers arbre)
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.type(out[1])).isEqualTo((int) ActionType.MOVE);
    }
}
