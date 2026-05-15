package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateTreeIndexTest {

    @BeforeEach void initGrid() {
        GameState.width  = 8;
        GameState.height = 8;
        GameState.tiles  = new byte[64];
    }

    @Test void addTreeAtRegistersInIndex() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 3, 4, 0, 8);
        assertThat(s.treeIndexAt(3, 4)).isEqualTo(0);
        assertThat(s.treeIndexAt(0, 0)).isEqualTo(-1);
    }

    @Test void treeIndexAtIgnoresDeadTrees() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 3, 4, 0, 8);
        s.killTreeAt(0);
        assertThat(s.treeIndexAt(3, 4)).isEqualTo(-1);
    }

    @Test void compactDeadTreesKeepsIndexConsistent() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 1, 1, 0, 8);   // idx 0
        s.addTree(TreeType.PLUM,  2, 2, 0, 4);   // idx 1
        s.addTree(TreeType.LEMON, 3, 3, 0, 4);   // idx 2
        s.killTreeAt(1);                          // kill middle
        s.compactDeadTrees();
        assertThat(s.treeCount).isEqualTo(2);
        assertThat(s.treeIndexAt(1, 1)).isEqualTo(0);
        assertThat(s.treeIndexAt(3, 3)).isEqualTo(1);
        assertThat(s.treeIndexAt(2, 2)).isEqualTo(-1);
    }

    @Test void copyFromCopiesTreeCellIndex() {
        GameState src = new GameState();
        src.addTree(TreeType.APPLE, 3, 4, 0, 8);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.treeIndexAt(3, 4)).isEqualTo(0);
    }
}
