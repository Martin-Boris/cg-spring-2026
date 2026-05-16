// src/test/java/com/bmrt/cgspring2026/ga/PlantCandidatesTest.java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantCandidatesTest {

    private static void grid(String[] rows) {
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
    }

    private static boolean contains(int x, int y) {
        short packed = (short) ((x << 8) | y);
        for (int i = 0; i < Genome.plantCandidateCount; i++) {
            if (Genome.plantCandidates[i] == packed) return true;
        }
        return false;
    }

    @Test void manhattanDiamondAroundShack() {
        grid(new String[]{
            ".........",
            ".........",
            "....0....",
            ".........",
            "........."
        });
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isEqualTo(12);
        assertThat(contains(4, 2)).isFalse();
        assertThat(contains(2, 2)).isTrue();
        assertThat(contains(6, 2)).isTrue();
        assertThat(contains(4, 0)).isTrue();
        assertThat(contains(4, 4)).isTrue();
        assertThat(contains(3, 1)).isTrue();
        assertThat(contains(1, 2)).isFalse();
    }

    @Test void excludesNonGrassTiles() {
        grid(new String[]{
            ".........",
            "...~~~...",
            "...~0~...",
            "...~~~...",
            "........."
        });
        Genome.initPlantCandidates();
        assertThat(contains(3, 2)).isFalse();
        assertThat(contains(2, 2)).isTrue();
        assertThat(contains(6, 2)).isTrue();
        assertThat(contains(4, 0)).isTrue();
        assertThat(contains(4, 4)).isTrue();
    }

    @Test void emptyWhenShackIsolated() {
        // Shack at (2,2) surrounded entirely by rocks within Manhattan distance 2
        grid(new String[]{
            "#####",
            "#####",
            "##0##",
            "#####",
            "#####"
        });
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isEqualTo(0);
    }

    @Test void respectsGridBoundary() {
        grid(new String[]{
            "0........",
            ".........",
            ".........",
            "........."
        });
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isEqualTo(5);
        assertThat(contains(0, 0)).isFalse();
        assertThat(contains(1, 1)).isTrue();
        assertThat(contains(2, 0)).isTrue();
    }
}
