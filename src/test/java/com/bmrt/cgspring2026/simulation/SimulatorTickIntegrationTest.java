package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTickIntegrationTest {

    @BeforeEach void grid() {
        String[] rows = {
                "........",
                ".0......",
                "........",
                "....1...",
                "........",
                "........",
                "........",
                "........",
        };
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
        PathTable.init();
    }

    @Test void mixedTurnDropTrainHarvestMove() {
        GameState s = new GameState();
        // Troll 0: on shack-adjacent, has 2 PLUM + 1 WOOD to drop
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollMS[0] = 1; s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollCP[0] = 0;
        s.trollId[0] = 0;
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.PLUM] = 2;
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 1;
        // Troll 1: on tree, harvests
        s.trollPlayer[1] = 0;
        s.trollX[1] = 3; s.trollY[1] = 5;
        s.trollMS[1] = 1; s.trollCC[1] = 5; s.trollHP[1] = 2; s.trollCP[1] = 0;
        s.trollId[1] = 1;
        // Tree under troll 1
        s.treeCount = 1;
        s.treeType[0] = TreeType.APPLE;
        s.treeX[0] = 3; s.treeY[0] = 5;
        s.treeSize[0] = 4; s.treeHealth[0] = 20; s.treeFruits[0] = 3; s.treeCooldown[0] = 5;
        // Shack inventory big enough to TRAIN 1 1 0 0 with n=2 -> needs 3 PLUM, 3 LEMON, 2 APPLE, 2 IRON
        s.shackInventory[ResourceType.PLUM]  = 3;
        s.shackInventory[ResourceType.LEMON] = 3;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;

        int[] acts = {
            Action.harvest(1),
            Action.drop(0),
            Action.train(1, 1, 0, 0),
        };
        Simulator.tick(s, acts, 3);

        // TRAIN runs BEFORE DROP. TRAIN sees PLUM 3, LEMON 3, APPLE 2, IRON 2 -> affordable, spawns troll.
        assertThat(s.trollCount).isEqualTo(3);
        assertThat(s.trollPlayer[2]).isEqualTo((byte) 0);
        assertThat(s.trollX[2] & 0xFF).isEqualTo(GameState.shackMeX);
        // After TRAIN, shack PLUM 0, LEMON 0, APPLE 0, IRON 0.
        // Then DROP adds 2 PLUM + 1 WOOD.
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(2);
        assertThat(s.shackInventory[ResourceType.WOOD]).isEqualTo(1);

        // Troll 1 harvested 2 fruits (hp=2, fruits=3): inventory=2 apples; tree fruits = 1
        assertThat(s.trollInventory[1 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 2);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
        // tree cooldown decremented (no growth, size already 4 fruits<3 after harvest)
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 4);

        // Score check: 2 PLUM + 4 * 1 WOOD = 6 for player 0
        assertThat(s.score(0)).isEqualTo(2 + 4);
    }

    @Test void plantingThenSameTreeStaysIntactPostTick() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 4;
        s.trollMS[0] = 1; s.trollCC[0] = 5;
        s.trollId[0] = 0;
        s.trollInventory[ResourceType.BANANA] = 1;
        int[] acts = { Action.plant(0, TreeType.BANANA) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // BANANA initial health = 6 - 4 = 2; after first tick: size 0->1, health 2+1=3, cooldown set to 6 normal.
        assertThat(s.treeSize[0]).isEqualTo((byte) 1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 3);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 6);
    }

    @Test void chopThenHarvestSameTreeImpossibleSameTurn() {
        // HARVEST (priority 2) happens BEFORE CHOP (priority 4).
        // Troll 0 harvests; troll 1 chops; both on same cell.
        GameState s = new GameState();
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 5;
        s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollId[0] = 0;
        s.trollPlayer[1] = 0;
        s.trollX[1] = 3; s.trollY[1] = 5;
        s.trollCC[1] = 5; s.trollCP[1] = 100; s.trollId[1] = 1;
        s.treeCount = 1;
        s.treeType[0] = TreeType.LEMON;
        s.treeX[0] = 3; s.treeY[0] = 5;
        s.treeSize[0] = 2; s.treeHealth[0] = 8; s.treeFruits[0] = 1; s.treeCooldown[0] = 5;
        int[] acts = { Action.harvest(0), Action.chop(1) };
        Simulator.tick(s, acts, 2);
        // Harvest succeeds first: troll 0 gets 1 LEMON.
        assertThat(s.trollInventory[0 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        // Then chop kills the tree; troll 1 gets 2 wood (size 2).
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[1 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }
}
