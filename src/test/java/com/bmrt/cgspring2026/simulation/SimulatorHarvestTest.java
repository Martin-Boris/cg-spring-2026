package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorHarvestTest {

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

    private static int placeTree(GameState s, int x, int y, int type, int size, int fruits, int cooldown) {
        int i = s.treeCount++;
        s.treeType[i] = (byte) type;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeSize[i] = (byte) size;
        s.treeHealth[i] = TreeType.HEALTH_BY_SIZE[type][size - 1];
        s.treeFruits[i] = (byte) fruits;
        s.treeCooldown[i] = (byte) cooldown;
        return i;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int hp, int cc) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollHP[i] = (byte) hp;
        s.trollCC[i] = (byte) cc;
        return i;
    }

    @Test void harvestSingleTrollOneFruit() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 2, 9);
        int t = placeTroll(s, 0, 3, 3, 1, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
    }

    @Test void harvestSingleTrollMatchesHarvestPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 3, 9);
        int t = placeTroll(s, 0, 3, 3, 2, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 2);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
    }

    @Test void harvestSharedTreeLastFruitDuplicated() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.LEMON, 4, 1, 9);
        int t1 = placeTroll(s, 0, 3, 3, 1, 5);
        int t2 = placeTroll(s, 1, 3, 3, 1, 5);
        int[] acts = { Action.harvest(t1), Action.harvest(t2) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 0);
    }

    @Test void harvestFailsWhenNoTreeOnCell() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 2, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void harvestStopsAtCapacity() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 3, 9);
        int t = placeTroll(s, 0, 3, 3, 3, 1);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
    }

    @Test void harvestFailsWithZeroHarvestPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 2, 9);
        int t = placeTroll(s, 0, 3, 3, 0, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
    }
}
