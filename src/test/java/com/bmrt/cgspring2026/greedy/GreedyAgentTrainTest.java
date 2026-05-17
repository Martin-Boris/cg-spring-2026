package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAgentTrainTest {

    private static GameState stateWithSingleTroll(int plum, int lemon, int apple, int iron) {
        GameState.width = 4;
        GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.shackInventory[ResourceType.PLUM] = plum;
        s.shackInventory[ResourceType.LEMON] = lemon;
        s.shackInventory[ResourceType.APPLE] = apple;
        s.shackInventory[ResourceType.IRON] = iron;
        return s;
    }

    @Test
    void noTrainWhenPlumBelowMinForMs1() {
        GameState s = stateWithSingleTroll(1, 5, 5, 5);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test
    void noTrainWhenLemonInsufficient() {
        GameState s = stateWithSingleTroll(5, 0, 5, 5);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test
    void trainWhenAppleZeroButIronSufficient() {
        // n=1 : iron=5 → cp=maxV(5,1,0)=2 ; apple=0 → hp=0 ; cp≠0 → train
        GameState s = stateWithSingleTroll(5, 5, 0, 5);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.type(a)).isEqualTo(ActionType.TRAIN);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(2);
    }

    @Test
    void trainWhenIronZeroButAppleSufficient() {
        // n=1 : apple=5 → hp=maxV(5,1,0)=2 ; iron=0 → cp=0 ; hp≠0 → train
        GameState s = stateWithSingleTroll(5, 5, 5, 0);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.type(a)).isEqualTo(ActionType.TRAIN);
        assertThat(Action.trainCP(a)).isEqualTo(0);
        assertThat(Action.trainHP(a)).isEqualTo(2);
    }

    @Test
    void minimalTrainWhenJustAffordable() {
        GameState s = stateWithSingleTroll(2, 2, 1, 2);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.type(a)).isEqualTo(ActionType.TRAIN);
        assertThat(Action.trainMS(a)).isEqualTo(1);
        assertThat(Action.trainCC(a)).isEqualTo(1);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(1);
    }

    @Test
    void maximumIndependentlyPerStat() {
        // n=1. PLUM=10 -> ms=3 (1+9=10). LEMON=5 -> cc=2 (1+4=5). APPLE=1 -> hp=0. IRON=17 -> cp=4 (1+16=17).
        GameState s = stateWithSingleTroll(10, 5, 9, 17);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.trainMS(a)).isEqualTo(3);
        assertThat(Action.trainCC(a)).isEqualTo(2);
        assertThat(Action.trainHP(a)).isEqualTo(2);
        assertThat(Action.trainCP(a)).isEqualTo(4);
    }

    @Test
    void costAccountsForCurrentTrollCount() {
        // n=2 alliés + 1 adversaire. PLUM=10 -> ms=2 (2+4=6).
        GameState.width = 4;
        GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState s = new GameState();
        s.trollCount = 3;
        s.trollPlayer[0] = 0;
        s.trollPlayer[1] = 0;
        s.trollPlayer[2] = 1;
        s.shackInventory[ResourceType.PLUM] = 10;
        s.shackInventory[ResourceType.LEMON] = 10;
        s.shackInventory[ResourceType.APPLE] = 10;
        s.shackInventory[ResourceType.IRON] = 10;
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.trainMS(a)).isEqualTo(2);
    }

    @Test
    void noTrainWhenBothHpAndCpImpossible() {
        // n=1 : apple=0 → hp=0, iron=0 → cp=0, les deux nuls → -1
        GameState s = stateWithSingleTroll(5, 5, 0, 0);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test
    void noTrainWhenTurnIsAtOrAbove200() {
        GameState s = stateWithSingleTroll(5, 5, 5, 5);
        s.turn = 200;
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test
    void trainStillPossibleAtTurn199() {
        GameState s = stateWithSingleTroll(5, 5, 5, 5);
        s.turn = 199;
        assertThat(Action.type(GreedyAgent.maybeTrain(s))).isEqualTo(ActionType.TRAIN);
    }

    @Test
    void noTrainWhenFiveTrolls() {
        GameState.width = 4; GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState s = new GameState();
        s.trollCount = 5;
        for (int i = 0; i < 5; i++) s.trollPlayer[i] = 0;
        s.shackInventory[ResourceType.PLUM]  = 99;
        s.shackInventory[ResourceType.LEMON] = 99;
        s.shackInventory[ResourceType.APPLE] = 99;
        s.shackInventory[ResourceType.IRON]  = 99;
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }
}
