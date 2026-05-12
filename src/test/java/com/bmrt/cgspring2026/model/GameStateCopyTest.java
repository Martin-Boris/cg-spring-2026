package com.bmrt.cgspring2026.model;

import com.bmrt.cgspring2026.support.GameStateBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateCopyTest {

    @Test
    void copyFrom_roundtrips_scalars_and_arrays() {
        GameState src = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 1, 1, 3, 17, 1, 5)
                .withMyInventory(1, 2, 3, 4, 5, 6)
                .build();

        GameState dst = new GameState();
        dst.copyFrom(src);

        assertThat(dst.width).isEqualTo(src.width);
        assertThat(dst.height).isEqualTo(src.height);
        assertThat(dst.tileCount).isEqualTo(src.tileCount);
        assertThat(dst.shackMeTile).isEqualTo(src.shackMeTile);
        assertThat(dst.trollCount).isEqualTo(1);
        assertThat(dst.treeCount).isEqualTo(1);
        assertThat(dst.invMe).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(dst.trollX[0]).isEqualTo((byte) 2);
        assertThat(dst.treeFruits[0]).isEqualTo((byte) 1);
    }

    @Test
    void modifying_copy_does_not_affect_source() {
        GameState src = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(7, 2, 2, 1, 5, 1, 1)
                .withTree(TreeType.APPLE, 1, 1, 3, 17, 1, 5)
                .build();

        GameState dst = new GameState();
        dst.copyFrom(src);

        dst.trollX[0] = 0;
        dst.treeFruits[0] = 0;
        dst.invMe[Resource.WOOD] = 42;

        assertThat(src.trollX[0]).isEqualTo((byte) 2);
        assertThat(src.treeFruits[0]).isEqualTo((byte) 1);
        assertThat(src.invMe[Resource.WOOD]).isZero();
    }
}
