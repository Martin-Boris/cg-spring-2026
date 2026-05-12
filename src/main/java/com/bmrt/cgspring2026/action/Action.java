package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TreeType;

public sealed interface Action
        permits Action.Move, Action.Harvest, Action.Plant, Action.Chop,
                Action.Pick, Action.Drop, Action.Mine, Action.Train,
                Action.Wait, Action.Msg {

    String toCommand();

    record Move(int trollId, int x, int y) implements Action {
        @Override
        public String toCommand() {
            return "MOVE " + trollId + " " + x + " " + y;
        }
    }

    record Harvest(int trollId) implements Action {
        @Override
        public String toCommand() {
            return "HARVEST " + trollId;
        }
    }

    record Plant(int trollId, TreeType type) implements Action {
        @Override
        public String toCommand() {
            return "PLANT " + trollId + " " + type.name();
        }
    }

    record Chop(int trollId) implements Action {
        @Override
        public String toCommand() {
            return "CHOP " + trollId;
        }
    }

    record Pick(int trollId, ResourceType type) implements Action {
        @Override
        public String toCommand() {
            return "PICK " + trollId + " " + type.name();
        }
    }

    record Drop(int trollId) implements Action {
        @Override
        public String toCommand() {
            return "DROP " + trollId;
        }
    }

    record Mine(int trollId) implements Action {
        @Override
        public String toCommand() {
            return "MINE " + trollId;
        }
    }

    record Train(int moveSpeed, int carryCapacity, int harvestPower, int chopPower) implements Action {
        @Override
        public String toCommand() {
            return "TRAIN " + moveSpeed + " " + carryCapacity + " " + harvestPower + " " + chopPower;
        }
    }

    record Wait(int trollId) implements Action {
        @Override
        public String toCommand() {
            return "WAIT " + trollId;
        }
    }

    record Msg(String text) implements Action {
        @Override
        public String toCommand() {
            return "MSG " + text;
        }
    }
}
