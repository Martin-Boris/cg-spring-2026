package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TreeType;

public final class Actions {

    // Layout: [ type:4 | trollIdx:6 | p1:8 | p2:8 | p3:6 ]
    private static final int TYPE_SHIFT  = 0;
    private static final int TROLL_SHIFT = 4;
    private static final int P1_SHIFT    = 10;
    private static final int P2_SHIFT    = 18;
    private static final int P3_SHIFT    = 26;

    private static final int TYPE_MASK  = 0xF;
    private static final int TROLL_MASK = 0x3F;
    private static final int P1_MASK    = 0xFF;
    private static final int P2_MASK    = 0xFF;
    private static final int P3_MASK    = 0x3F;

    public static int encode(int type, int trollIdx, int p1, int p2, int p3) {
        return ((type & TYPE_MASK) << TYPE_SHIFT)
             | ((trollIdx & TROLL_MASK) << TROLL_SHIFT)
             | ((p1 & P1_MASK) << P1_SHIFT)
             | ((p2 & P2_MASK) << P2_SHIFT)
             | ((p3 & P3_MASK) << P3_SHIFT);
    }

    public static int type(int packed)     { return (packed >>> TYPE_SHIFT)  & TYPE_MASK; }
    public static int trollIdx(int packed) { return (packed >>> TROLL_SHIFT) & TROLL_MASK; }
    public static int p1(int packed)       { return (packed >>> P1_SHIFT)    & P1_MASK; }
    public static int p2(int packed)       { return (packed >>> P2_SHIFT)    & P2_MASK; }
    public static int p3(int packed)       { return (packed >>> P3_SHIFT)    & P3_MASK; }

    public static int waitAction()                     { return encode(ActionType.WAIT, 0, 0, 0, 0); }
    public static int move(int trollIdx, int tile)     { return encode(ActionType.MOVE, trollIdx, tile, 0, 0); }
    public static int harvest(int trollIdx)            { return encode(ActionType.HARVEST, trollIdx, 0, 0, 0); }
    public static int plant(int trollIdx, int fruit)   { return encode(ActionType.PLANT, trollIdx, fruit, 0, 0); }
    public static int chop(int trollIdx)               { return encode(ActionType.CHOP, trollIdx, 0, 0, 0); }
    public static int pick(int trollIdx, int fruit)    { return encode(ActionType.PICK, trollIdx, fruit, 0, 0); }
    public static int drop(int trollIdx)               { return encode(ActionType.DROP, trollIdx, 0, 0, 0); }
    public static int mine(int trollIdx)               { return encode(ActionType.MINE, trollIdx, 0, 0, 0); }
    public static int train(int ms, int cc, int hp, int cp) {
        return encode(ActionType.TRAIN, 0, ms, cc, (hp & 0x7) | ((cp & 0x7) << 3));
    }

    public static String format(int packed, GameState state) {
        int type     = type(packed);
        int idx      = trollIdx(packed);
        int troll    = (idx < state.trollCount) ? state.trollOriginalId[idx] : -1;
        return switch (type) {
            case ActionType.WAIT    -> "WAIT";
            case ActionType.MOVE    -> {
                int tile = p1(packed);
                int x = tile % state.width;
                int y = tile / state.width;
                yield "MOVE " + troll + " " + x + " " + y;
            }
            case ActionType.HARVEST -> "HARVEST " + troll;
            case ActionType.PLANT   -> "PLANT "   + troll + " " + TreeType.toName(p1(packed));
            case ActionType.CHOP    -> "CHOP "    + troll;
            case ActionType.PICK    -> "PICK "    + troll + " " + TreeType.toName(p1(packed));
            case ActionType.DROP    -> "DROP "    + troll;
            case ActionType.MINE    -> "MINE "    + troll;
            case ActionType.TRAIN   -> {
                int ms = p1(packed);
                int cc = p2(packed);
                int p3 = p3(packed);
                int hp = p3 & 0x7;
                int cp = (p3 >>> 3) & 0x7;
                yield "TRAIN " + ms + " " + cc + " " + hp + " " + cp;
            }
            case ActionType.MSG -> "MSG";
            default -> "WAIT";
        };
    }

    private Actions() {}
}
