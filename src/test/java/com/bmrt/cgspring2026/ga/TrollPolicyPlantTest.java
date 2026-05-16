package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyPlantTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "......",
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
        Genome.initPlantCandidates();
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            TrollPolicy.cursorBuf[j] = 0;
            TrollPolicy.policyPhase[j] = 0;
        }
    }

    private GameState trollAt(int x, int y) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
        s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
        return s;
    }

    private static short[] popBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] lenBuf() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    @Test void phase0_pickWhenAdjacentToShackWithoutFruit() {
        GameState s = trollAt(0, 1);
        s.shackInventory[ResourceType.LEMON] = 3;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PICK);
        assertThat(Action.arg1(out[0])).isEqualTo((int) ResourceType.LEMON);
    }

    @Test void phase0_moveToShackWhenFarAndWithoutFruit() {
        GameState s = trollAt(4, 4);
        s.shackInventory[ResourceType.APPLE] = 2;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.APPLE));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dist = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dist).isEqualTo(1);
    }

    @Test void phase0_abortsWhenShackHasNoFruit() {
        GameState s = trollAt(0, 1);
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2));
        Genome.setLen(lens, 0, 0, 2);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(2);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void phase0_plantsWhenOnTargetWithFruit() {
        GameState s = trollAt(0, 2);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.BANANA));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PLANT);
        assertThat(Action.arg1(out[0])).isEqualTo((int) TreeType.BANANA);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 1);
    }
}
