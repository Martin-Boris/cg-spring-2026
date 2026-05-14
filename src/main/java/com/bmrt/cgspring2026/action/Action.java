package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;

public final class Action {

    private static int pack(int type, int trollIdx, int arg1, int arg2) {
        return (type & 0xFF)
             | ((trollIdx & 0xFF) << 8)
             | ((arg1     & 0xFF) << 16)
             | ((arg2     & 0xFF) << 24);
    }

    public static int wait(int trollIdx)                       { return pack(ActionType.WAIT,    trollIdx, 0, 0); }
    public static int move(int trollIdx, int x, int y)         { return pack(ActionType.MOVE,    trollIdx, x, y); }
    public static int harvest(int trollIdx)                    { return pack(ActionType.HARVEST, trollIdx, 0, 0); }
    public static int plant(int trollIdx, int treeType)        { return pack(ActionType.PLANT,   trollIdx, treeType, 0); }
    public static int chop(int trollIdx)                       { return pack(ActionType.CHOP,    trollIdx, 0, 0); }
    public static int pick(int trollIdx, int resourceType)     { return pack(ActionType.PICK,    trollIdx, resourceType, 0); }
    public static int drop(int trollIdx)                       { return pack(ActionType.DROP,    trollIdx, 0, 0); }
    public static int mine(int trollIdx)                       { return pack(ActionType.MINE,    trollIdx, 0, 0); }

    public static int train(int ms, int cc, int hp, int cp) {
        return (ActionType.TRAIN & 0xFF)
             | ((ms & 0x3F) << 8)
             | ((cc & 0x3F) << 14)
             | ((hp & 0x3F) << 20)
             | ((cp & 0x3F) << 26);
    }

    public static int type(int action)     { return action & 0xFF; }
    public static int trollIdx(int action) { return (action >>> 8)  & 0xFF; }
    public static int arg1(int action)     { return (action >>> 16) & 0xFF; }
    public static int arg2(int action)     { return (action >>> 24) & 0xFF; }

    public static int trainMS(int action) { return (action >>> 8)  & 0x3F; }
    public static int trainCC(int action) { return (action >>> 14) & 0x3F; }
    public static int trainHP(int action) { return (action >>> 20) & 0x3F; }
    public static int trainCP(int action) { return (action >>> 26) & 0x3F; }

    private static final String[] TREE_NAMES     = { "PLUM", "LEMON", "APPLE", "BANANA" };
    private static final String[] RESOURCE_NAMES = { "PLUM", "LEMON", "APPLE", "BANANA", "IRON", "WOOD" };

    public static String toCommand(int action, GameState state) {
        int t = type(action);
        if (t == ActionType.TRAIN) {
            return "TRAIN " + trainMS(action) + " " + trainCC(action) + " " + trainHP(action) + " " + trainCP(action);
        }
        int idx = trollIdx(action);
        int externalId = state.trollId[idx] & 0xFF;
        return switch (t) {
            case ActionType.WAIT    -> "WAIT ";
            case ActionType.MOVE    -> "MOVE "    + externalId + " " + arg1(action) + " " + arg2(action);
            case ActionType.HARVEST -> "HARVEST " + externalId;
            case ActionType.PLANT   -> "PLANT "   + externalId + " " + TREE_NAMES[arg1(action)];
            case ActionType.CHOP    -> "CHOP "    + externalId;
            case ActionType.PICK    -> "PICK "    + externalId + " " + RESOURCE_NAMES[arg1(action)];
            case ActionType.DROP    -> "DROP "    + externalId;
            case ActionType.MINE    -> "MINE "    + externalId;
            default -> throw new IllegalStateException("unknown action type " + t);
        };
    }

    private Action() {}
}
