package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPlantTickTest {

    @BeforeEach void grid() {
        String[] rows = {"........", "...0....", "........", "....1...", "........", "........", "........", "........"};
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

    @Test void tickIncrementsTurnCounter() {
        GameState s = new GameState();
        s.turn = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.turn).isEqualTo(6);
    }

    @Test void plantTickDecrementsCooldown() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 8;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 4);
        assertThat(s.treeSize[0]).isEqualTo((byte) 2);
    }

    @Test void plantTickGrowsWhenCooldownReachesZero() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 8;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        // cooldown 1->0, size 2->3, health 8 + DELTA(2) = 10, cooldown reset to 8 (no water nearby)
        assertThat(s.treeSize[0]).isEqualTo((byte) 3);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 10);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 8);
    }

    @Test void plantTickProducesFruitWhenSizeFour() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 4;
        s.treeHealth[0]   = 20;
        s.treeFruits[0]   = 1;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeSize[0]).isEqualTo((byte) 4);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 9);
    }

    @Test void plantTickCapsFruitsAtThree() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 4;
        s.treeHealth[0]   = 12;
        s.treeFruits[0]   = 3;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 3);
        // No growth, no fruit, cooldown decremented to 0 and stays (no growth branch taken)
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 0);
    }

    @Test void plantTickUsesWaterBoostCooldown() {
        // place water at (5,6) so tree at (5,5) is near water
        GameState.tiles[6 * GameState.width + 5] = TileType.WATER;
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 14;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeSize[0]).isEqualTo((byte) 3);
        // APPLE near water: 9 - 7 = 2
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 2);
    }

    @Test void deadTreesRemovedAfterTick() {
        GameState s = new GameState();
        s.treeCount = 3;
        // tree 0: alive
        s.treeType[0] = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0] = 1; s.treeY[0] = 1;
        s.treeSize[0] = 4; s.treeHealth[0] = 12; s.treeFruits[0] = 0; s.treeCooldown[0] = 5;
        // tree 1: dead
        s.treeType[1] = com.bmrt.cgspring2026.model.TreeType.LEMON;
        s.treeX[1] = 2; s.treeY[1] = 2;
        s.treeSize[1] = 1; s.treeHealth[1] = 0; s.treeFruits[1] = 0; s.treeCooldown[1] = 0;
        // tree 2: alive
        s.treeType[2] = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[2] = 3; s.treeY[2] = 3;
        s.treeSize[2] = 4; s.treeHealth[2] = 20; s.treeFruits[2] = 1; s.treeCooldown[2] = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeCount).isEqualTo(2);
        // The remaining 2 trees are the two alive ones (order may differ due to swap-with-last)
        boolean foundPlum = false, foundApple = false;
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeType[i] == com.bmrt.cgspring2026.model.TreeType.PLUM)  foundPlum  = true;
            if (s.treeType[i] == com.bmrt.cgspring2026.model.TreeType.APPLE) foundApple = true;
        }
        assertThat(foundPlum).isTrue();
        assertThat(foundApple).isTrue();
    }

    @Test void deadTreeDoesNotTickInSameTurn() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0] = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0] = 1; s.treeY[0] = 1;
        s.treeSize[0] = 2; s.treeHealth[0] = 0; s.treeFruits[0] = 0; s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        // Tree compacted away; count becomes 0.
        assertThat(s.treeCount).isEqualTo(0);
    }
}
