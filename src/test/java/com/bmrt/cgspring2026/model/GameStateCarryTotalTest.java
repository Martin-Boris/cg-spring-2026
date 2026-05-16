package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateCarryTotalTest {

    @BeforeEach void initGrid() {
        GameState.width  = 8;
        GameState.height = 8;
        GameState.tiles  = new byte[64];
    }

    @Test void addToInventoryUpdatesCarryTotal() {
        GameState s = new GameState();
        int i = s.addTroll(0, 1, 1, 1, 5, 1, 0);
        assertThat(s.trollCarryTotal[i]).isEqualTo(0);
        s.addToInventory(i, ResourceType.APPLE, 2);
        assertThat(s.trollCarryTotal[i]).isEqualTo(2);
        s.addToInventory(i, ResourceType.WOOD, 1);
        assertThat(s.trollCarryTotal[i]).isEqualTo(3);
    }

    @Test void clearInventoryResetsCarryTotal() {
        GameState s = new GameState();
        int i = s.addTroll(0, 1, 1, 1, 5, 1, 0);
        s.addToInventory(i, ResourceType.APPLE, 2);
        s.addToInventory(i, ResourceType.WOOD, 1);
        s.clearInventory(i);
        assertThat(s.trollCarryTotal[i]).isEqualTo(0);
        for (int r = 0; r < ResourceType.COUNT; r++) {
            assertThat(s.trollInventory[i * ResourceType.COUNT + r]).isEqualTo((byte) 0);
        }
    }

    @Test void copyFromCopiesCarryTotal() {
        GameState src = new GameState();
        int i = src.addTroll(0, 1, 1, 1, 5, 1, 0);
        src.addToInventory(i, ResourceType.APPLE, 2);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.trollCarryTotal[i]).isEqualTo(2);
    }
}
