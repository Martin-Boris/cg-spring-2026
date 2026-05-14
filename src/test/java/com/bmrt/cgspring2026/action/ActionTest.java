package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTest {

    private static void loadGridForOutput() {
        GameState.height = 3;
        GameState.width  = 4;
        GameState.tiles  = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
    }

    @Test void waitEncodesTypeAndTrollIdx() {
        int a = Action.wait(5);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.trollIdx(a)).isEqualTo(5);
    }

    @Test void moveEncodesXAndY() {
        int a = Action.move(3, 17, 9);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(3);
        assertThat(Action.arg1(a)).isEqualTo(17);
        assertThat(Action.arg2(a)).isEqualTo(9);
    }

    @Test void harvestEncodesTypeAndTrollIdx() {
        int a = Action.harvest(2);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.HARVEST);
        assertThat(Action.trollIdx(a)).isEqualTo(2);
    }

    @Test void plantEncodesTreeType() {
        int a = Action.plant(0, TreeType.APPLE);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.PLANT);
        assertThat(Action.arg1(a)).isEqualTo((int) TreeType.APPLE);
    }

    @Test void chopEncodesTypeAndTrollIdx() {
        int a = Action.chop(4);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.CHOP);
        assertThat(Action.trollIdx(a)).isEqualTo(4);
    }

    @Test void pickEncodesResourceType() {
        int a = Action.pick(1, ResourceType.LEMON);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.PICK);
        assertThat(Action.arg1(a)).isEqualTo((int) ResourceType.LEMON);
    }

    @Test void dropEncodesTypeAndTrollIdx() {
        int a = Action.drop(0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.DROP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void mineEncodesTypeAndTrollIdx() {
        int a = Action.mine(7);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MINE);
        assertThat(Action.trollIdx(a)).isEqualTo(7);
    }

    @Test void trainEncodesFourStatsOnSixBits() {
        int a = Action.train(3, 5, 0, 4);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.TRAIN);
        assertThat(Action.trainMS(a)).isEqualTo(3);
        assertThat(Action.trainCC(a)).isEqualTo(5);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(4);
    }

    @Test void trainPreservesMaxSixBitValue() {
        int a = Action.train(63, 63, 63, 63);
        assertThat(Action.trainMS(a)).isEqualTo(63);
        assertThat(Action.trainCC(a)).isEqualTo(63);
        assertThat(Action.trainHP(a)).isEqualTo(63);
        assertThat(Action.trainCP(a)).isEqualTo(63);
    }

    private static GameState stateWithTrolls() {
        GameState s = new GameState();
        s.trollCount = 2;
        s.trollId[0] = (byte) 7;
        s.trollId[1] = (byte) 12;
        return s;
    }

    @Test void toCommandWaitUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.wait(0), stateWithTrolls())).isEqualTo("WAIT ");
    }

    @Test void toCommandMoveUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.move(1, 3, 2), stateWithTrolls())).isEqualTo("MOVE 12 3 2");
    }

    @Test void toCommandChopUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.chop(1), stateWithTrolls())).isEqualTo("CHOP 12");
    }

    @Test void toCommandDropUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.drop(0), stateWithTrolls())).isEqualTo("DROP 7");
    }

    @Test void toCommandHarvestUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.harvest(0), stateWithTrolls())).isEqualTo("HARVEST 7");
    }

    @Test void toCommandMineUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.mine(1), stateWithTrolls())).isEqualTo("MINE 12");
    }

    @Test void toCommandPlantUsesTreeName() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.plant(0, TreeType.BANANA), stateWithTrolls())).isEqualTo("PLANT 7 BANANA");
    }

    @Test void toCommandPickUsesResourceName() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.pick(0, ResourceType.APPLE), stateWithTrolls())).isEqualTo("PICK 7 APPLE");
    }

    @Test void toCommandTrainEmitsFourStats() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.train(3, 2, 0, 1), stateWithTrolls())).isEqualTo("TRAIN 3 2 0 1");
    }
}
