package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateTrollIndexTest {

    @BeforeEach void initGrid() {
        GameState.width = 8;
        GameState.height = 8;
        GameState.tiles = new byte[64];
    }

    @Test void addTrollRegistersInIndexAndAssignsId() {
        GameState s = new GameState();
        int i = s.addTroll(0, 3, 4, 1, 5, 2, 1);
        assertThat(i).isEqualTo(0);
        assertThat(s.trollIndexAtCell(3, 4)).isEqualTo(0);
        assertThat(s.trollId[i] & 0xFF).isEqualTo(0);
        int j = s.addTroll(1, 5, 5, 1, 5, 1, 0);
        assertThat(s.trollId[j] & 0xFF).isEqualTo(1);
        assertThat(s.nextTrollId).isEqualTo(2);
    }

    @Test void moveTrollUpdatesIndex() {
        GameState s = new GameState();
        int i = s.addTroll(0, 3, 4, 1, 5, 1, 0);
        s.moveTroll(i, 5, 6);
        assertThat(s.trollIndexAtCell(3, 4)).isEqualTo(-1);
        assertThat(s.trollIndexAtCell(5, 6)).isEqualTo(i);
    }

    @Test void copyFromCopiesTrollIndexAndNextId() {
        GameState src = new GameState();
        src.addTroll(0, 3, 4, 1, 5, 1, 0);
        src.addTroll(1, 5, 5, 1, 5, 1, 0);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.trollIndexAtCell(3, 4)).isEqualTo(0);
        assertThat(dst.trollIndexAtCell(5, 5)).isEqualTo(1);
        assertThat(dst.nextTrollId).isEqualTo(2);
    }
}
