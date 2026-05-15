package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAgentDecideTest {

    @BeforeEach void setUpGrid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "...1..",
            "......"
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
        ShackAdjacency.init();
    }

    private static GameState stateWithTroll(int x, int y, int wood) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x;
        s.trollY[0] = (byte) y;
        s.trollMS[0] = 2;
        s.trollCC[0] = 4;
        s.trollHP[0] = 1;
        s.trollCP[0] = 1;
        s.trollInventory[ResourceType.WOOD] = (byte) wood;
        return s;
    }

    @Test void dropWhenStandingOnShackAdjacent() {
        GameState s = stateWithTroll(0, 1, 3);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.DROP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void moveTowardsClosestShackAdjacentWhenCarryingWood() {
        // troll a (5,4). Cases adj du shack (1,1) : (2,1)(0,1)(1,2)(1,0).
        // Distances : (2,1)=6, (0,1)=8, (1,2)=6, (1,0)=8.
        // (2,1) et (1,2) sont a egalite ; le tie-break "premier rencontre"
        // selectionne (2,1) selon l'ordre DX={1,-1,0,0}/DY={0,0,1,-1} de ShackAdjacency.
        GameState s = stateWithTroll(5, 4, 2);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
        assertThat(Action.arg1(a)).isEqualTo(2);
        assertThat(Action.arg2(a)).isEqualTo(1);
    }

    @Test void waitWhenNoTreeAvailable() {
        GameState s = stateWithTroll(2, 2, 0);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void chopWhenStandingOnTargetTree() {
        GameState s = stateWithTroll(3, 2, 0);
        s.treeCount = 1;
        s.treeX[0] = 3;
        s.treeY[0] = 2;
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.CHOP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void moveTowardsTargetTreeWhenNotOnIt() {
        GameState s = stateWithTroll(0, 0, 0);
        s.treeCount = 1;
        s.treeX[0] = 5;
        s.treeY[0] = 4;
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
        assertThat(Action.arg1(a)).isEqualTo(5);
        assertThat(Action.arg2(a)).isEqualTo(4);
    }

    @Test void carryingWoodPriorityOverTreeAssignment() {
        GameState s = stateWithTroll(5, 4, 1);
        s.treeCount = 1;
        s.treeX[0] = 5;
        s.treeY[0] = 4;
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
    }

    // --- decide() top-level ---

    @Test void decideEmptyMapReturnsWaitOnly() {
        GameState s = stateWithTroll(0, 0, 0);
        s.turn = 5;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        assertThat(n).isEqualTo(1);
        assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void decideAssignsClosestFreeTreePerTroll() {
        GameState s = new GameState();
        s.turn = 5;
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollPlayer[1] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollX[1] = 5; s.trollY[1] = 4;
        s.treeCount = 2;
        s.treeX[0] = 0; s.treeY[0] = 4;
        s.treeX[1] = 5; s.treeY[1] = 0;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        assertThat(n).isEqualTo(2);
        assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(buf[0])).isEqualTo(0);
        assertThat(Action.arg2(buf[0])).isEqualTo(4);
        assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(buf[1])).isEqualTo(5);
        assertThat(Action.arg2(buf[1])).isEqualTo(0);
    }

    @Test void decideDoesNotReassignSameTreeTwice() {
        GameState s = new GameState();
        s.turn = 5;
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollPlayer[1] = 0;
        s.trollX[0] = 0; s.trollY[0] = 2;
        s.trollX[1] = 4; s.trollY[1] = 2;
        s.treeCount = 1;
        s.treeX[0] = 2; s.treeY[0] = 2;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        GreedyAgent.decide(s, buf);
        assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(buf[0])).isEqualTo(2);
        assertThat(Action.arg2(buf[0])).isEqualTo(2);
        assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void decideIgnoresEnemyTrolls() {
        GameState s = new GameState();
        s.turn = 5;
        s.trollCount = 2;
        s.trollPlayer[0] = 1;
        s.trollPlayer[1] = 0;
        s.trollX[1] = 0; s.trollY[1] = 0;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        assertThat(n).isEqualTo(1);
        assertThat(Action.trollIdx(buf[0])).isEqualTo(1);
    }

    @Test void decideEmitsTrainAtTurn0WhenAffordable() {
        GameState s = new GameState();
        s.turn = 0;
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.shackInventory[ResourceType.PLUM]  = 2;
        s.shackInventory[ResourceType.LEMON] = 1;
        s.shackInventory[ResourceType.APPLE] = 1;
        s.shackInventory[ResourceType.IRON]  = 1;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        assertThat(n).isEqualTo(2);
        assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.TRAIN);
        assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void decideTrollOnShackMePicksClosestTreeWithoutCrashing() {
        GameState s = new GameState();
        s.turn = 0;
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) GameState.shackMeX; // troll sur le shack (turn 0)
        s.trollY[0] = (byte) GameState.shackMeY;
        s.treeCount = 1;
        s.treeX[0] = 3;
        s.treeY[0] = 2;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        // Au moins une action emise (move vers arbre, eventuellement precedee de TRAIN si payable).
        assertThat(n).isGreaterThanOrEqualTo(1);
        int last = buf[n - 1];
        assertThat(Action.type(last)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(last)).isEqualTo(3);
        assertThat(Action.arg2(last)).isEqualTo(2);
    }

    @Test void decideSkipsTrainAfterTurn0() {
        GameState s = new GameState();
        s.turn = 1;
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.shackInventory[ResourceType.PLUM]  = 10;
        s.shackInventory[ResourceType.LEMON] = 10;
        s.shackInventory[ResourceType.APPLE] = 10;
        s.shackInventory[ResourceType.IRON]  = 10;
        int[] buf = new int[GameState.MAX_TROLLS + 1];
        int n = GreedyAgent.decide(s, buf);
        assertThat(n).isEqualTo(1);
        assertThat(Action.type(buf[0])).isNotEqualTo((int) ActionType.TRAIN);
    }

    @Test void decideForOpponentReturnsMoveTowardsClosestFreeTreeForOppTroll() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1; // adversaire
        s.trollX[0] = 5; s.trollY[0] = 4;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        boolean[] taken = new boolean[GameState.MAX_TREES];
        int a = GreedyAgent.decideForOpponent(s, 0, taken);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(a)).isEqualTo(5);
        assertThat(Action.arg2(a)).isEqualTo(0);
        assertThat(taken[0]).isTrue();
    }

    @Test void decideForOpponentRespectsTreeTakenBuf() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1;
        s.trollX[0] = 5; s.trollY[0] = 4;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        boolean[] taken = new boolean[GameState.MAX_TREES];
        taken[0] = true;
        int a = GreedyAgent.decideForOpponent(s, 0, taken);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
    }
}
