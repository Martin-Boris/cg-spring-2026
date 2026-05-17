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

class TrollPolicyHarvestTest {

    // Grille 6x5, shack en (1,1)
    // Arbre de taille 4 en (4,2) avec 2 fruits
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

    private GameState stateWithHarvestTree(int trollX, int trollY, int treeFruits) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) trollX; s.trollY[0] = (byte) trollY;
        s.trollMS[0] = 2; s.trollCC[0] = 3; s.trollHP[0] = 2; s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 4; s.treeY[0] = 2;
        s.treeSize[0] = 4; s.treeHealth[0] = 6;
        s.treeFruits[0] = (byte) treeFruits;
        s.treeType[0] = TreeType.BANANA;
        return s;
    }

    private static short[] popWithHarvestGene(int gx, int gy) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        Genome.setGene(buf, 0, 0, 0, Genome.makeHarvest(gx, gy));
        return buf;
    }

    private static byte[] lenBufOf1() {
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lens, 0, 0, 1);
        return lens;
    }

    @Test void movesToTreeWhenNotOnIt() {
        GameState s = stateWithHarvestTree(0, 0, 2);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
    }

    @Test void harvestsWhenOnTreeWithFruits() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.HARVEST);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // cursor ne bouge pas encore
    }

    @Test void skipsGeneWhenOnTreeWithZeroFruits() {
        GameState s = stateWithHarvestTree(4, 2, 0);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1); // gene skippé
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void movesToShackWhenCarryingFruit() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        // Troll porte déjà un fruit (post-harvest)
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        // La destination doit être adjacente au shack (1,1) → (0,1) ou (1,0) ou (2,1) ou (1,2)
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dShack = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dShack).isEqualTo(1);
    }

    @Test void dropsAndAdvancesCursorWhenAdjacentToShackWithFruit() {
        GameState s = stateWithHarvestTree(1, 2, 2); // adjacent shack en (1,1)
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1); // cursor avance
    }

    @Test void skipsGeneWhenTreeDeadOrSmall() {
        GameState s = stateWithHarvestTree(0, 0, 2);
        s.treeHealth[0] = 0; // arbre mort
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void skipsGeneWhenTreeTooSmall() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        s.treeSize[0] = 3; // pas assez grand
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodDropStillTakesPriorityOverHarvest() {
        // Troll porte du bois ET a un gène HARVEST → drop du bois en premier
        GameState s = stateWithHarvestTree(1, 2, 2); // adjacent shack
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 2;
        s.trollCarryTotal[0] = 2;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        // cursor NE doit PAS avancer (c'est le wood-drop, pas le harvest-drop)
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0);
    }
}
