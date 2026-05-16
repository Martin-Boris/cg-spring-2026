package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorChopTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    private static int placeTree(GameState s, int x, int y, int type, int size, int cooldown) {
        int i = s.treeCount++;
        s.treeType[i] = (byte) type;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeSize[i] = (byte) size;
        s.treeHealth[i] = TreeType.HEALTH_BY_SIZE[type][size - 1];
        s.treeFruits[i] = 0;
        s.treeCooldown[i] = (byte) cooldown;
        return i;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int cp, int cc) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollCP[i] = (byte) cp;
        s.trollCC[i] = (byte) cc;
        return i;
    }

    @Test void chopDamagesTreeWithoutKilling() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 9); // health 20
        int t = placeTroll(s, 0, 3, 3, 5, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // 20 - 5 = 15, then post-tick cooldown 9->8, no growth
        assertThat(s.treeHealth[0]).isEqualTo((byte) 15);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 8);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 0);
    }

    @Test void chopKillsTreeAndYieldsWoodEqualsSize() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 9); // health 12
        int t = placeTroll(s, 0, 3, 3, 20, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 4);
    }

    @Test void chopWoodOverflowIsLost() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 9); // size 4
        int t = placeTroll(s, 0, 3, 3, 20, 2);    // capacity only 2
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }

    @Test void chopSharedDistributesLastWoodDuplicated() {
        // size=3 tree, 2 trolls each chopPower 10, capacity 10 -> 2 wood each = 4 distributed (size 3 -> last duplicated)
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.BANANA, 3, 6); // size 3, health 5
        int t1 = placeTroll(s, 0, 3, 3, 10, 10);
        int t2 = placeTroll(s, 1, 3, 3, 10, 10);
        int[] acts = { Action.chop(t1), Action.chop(t2) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }

    @Test void chopFailsWithoutChopPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 9);
        int t = placeTroll(s, 0, 3, 3, 0, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 12);
    }

    @Test void chopFailsWhenNoTreeOnCell() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 0);
    }
}
