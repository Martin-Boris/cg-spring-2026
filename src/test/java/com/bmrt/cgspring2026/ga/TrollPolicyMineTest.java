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

class TrollPolicyMineTest {

    // Grille 6x5, shack (1,1), IRON (4,2)
    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
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

    private GameState stateWithMineGene(int trollX, int trollY, int cp, int cc) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) trollX; s.trollY[0] = (byte) trollY;
        s.trollMS[0] = 2; s.trollCC[0] = (byte) cc; s.trollHP[0] = 0; s.trollCP[0] = (byte) cp;
        return s;
    }

    private static short[] popWithMineGene(int gx, int gy) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        Genome.setGene(buf, 0, 0, 0, Genome.makeMine(gx, gy));
        return buf;
    }

    private static byte[] lenBufOf1() {
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lens, 0, 0, 1);
        return lens;
    }

    @Test void movesToGrassAdjacentToIronWhenFar() {
        GameState s = stateWithMineGene(0, 0, 2, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dIron = Math.abs(tx - 4) + Math.abs(ty - 2);
        assertThat(dIron).isEqualTo(1);
    }

    @Test void minesWhenAdjacentToIron() {
        GameState s = stateWithMineGene(3, 2, 2, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
    }

    @Test void advancesCursorWhenMineFillsCarry() {
        // cp=2, cc=2 → gain min(cp, cc-carryTotal) = 2 → carry plein après MINE
        GameState s = stateWithMineGene(3, 2, 2, 2);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
    }

    @Test void doesNotAdvanceCursorWhenMineDoesNotFillCarry() {
        // cp=1, cc=5 → gain=1, carry restera 1 < 5 → cursor inchangé
        GameState s = stateWithMineGene(3, 2, 1, 5);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0);
    }

    @Test void dropsWhenCarryFull() {
        // Troll plein d'IRON, adjacent au shack
        GameState s = stateWithMineGene(1, 2, 2, 3);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 3;
        s.trollCarryTotal[0] = 3;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // ne pas consumer le gène : on revient miner
    }

    @Test void movesToShackWhenCarryFullAndFar() {
        GameState s = stateWithMineGene(4, 4, 2, 3);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 3;
        s.trollCarryTotal[0] = 3;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dShack = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dShack).isEqualTo(1);
    }

    @Test void skipsMineGeneWhenCpZero() {
        GameState s = stateWithMineGene(3, 2, 0, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodDropTakesPriorityOverMine() {
        GameState s = stateWithMineGene(1, 2, 2, 3); // adjacent shack
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 2;
        s.trollCarryTotal[0] = 2;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // wood-drop, pas mine-drop
    }
}
