import java.util.Scanner;
class Player {
	private static class TreeType {
	    public static final byte PLUM   = 0;
	    public static final byte LEMON  = 1;
	    public static final byte APPLE  = 2;
	    public static final byte BANANA = 3;
	    public static final int  COUNT  = 4;
	    public static byte fromString(String s) {
	        return switch (s) {
	            case "PLUM"   -> PLUM;
	            case "LEMON"  -> LEMON;
	            case "APPLE"  -> APPLE;
	            case "BANANA" -> BANANA;
	            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
	        };
	    }
	    public static String toName(int t) {
	        return switch (t) {
	            case PLUM   -> "PLUM";
	            case LEMON  -> "LEMON";
	            case APPLE  -> "APPLE";
	            case BANANA -> "BANANA";
	            default -> throw new IllegalArgumentException("Unknown tree type: " + t);
	        };
	    }
	    private TreeType() {}
	}
	private static class TreeStats {
	    public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
	    public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };
	    public static final byte[][] HEALTH = {
	        { 6, 8, 10, 12 },
	        { 6, 8, 10, 12 },
	        { 11, 14, 17, 20 },
	        { 3, 4, 5, 6 }
	    };
	    public static final int MAX_FRUITS = 3;
	    public static final int MAX_SIZE   = 4;
	    public static int cooldown(int type, boolean nearWater) {
	        return nearWater ? COOLDOWN_WATER[type] : COOLDOWN_NORMAL[type];
	    }
	    public static int healthAt(int type, int size) {
	        return HEALTH[type][size - 1];
	    }
	    private TreeStats() {}
	}
	private static class TileType {
	    public static final byte GRASS     = 0;
	    public static final byte WATER     = 1;
	    public static final byte ROCK      = 2;
	    public static final byte IRON      = 3;
	    public static final byte SHACK_ME  = 4;
	    public static final byte SHACK_OPP = 5;
	    public static byte fromChar(char c) {
	        return switch (c) {
	            case '.' -> GRASS;
	            case '~' -> WATER;
	            case '#' -> ROCK;
	            case '+' -> IRON;
	            case '0' -> SHACK_ME;
	            case '1' -> SHACK_OPP;
	            default  -> throw new IllegalArgumentException("Unknown terrain char: " + c);
	        };
	    }
	    public static boolean isWalkable(byte t) {
	        return t == GRASS;
	    }
	    private TileType() {}
	}
	private static class Resource {
	    public static final byte PLUM   = 0;
	    public static final byte LEMON  = 1;
	    public static final byte APPLE  = 2;
	    public static final byte BANANA = 3;
	    public static final byte IRON   = 4;
	    public static final byte WOOD   = 5;
	    public static final int  COUNT  = 6;
	    public static final int POINTS_FRUIT = 1;
	    public static final int POINTS_WOOD  = 4;
	    public static int points(int resource) {
	        if (resource == WOOD) return POINTS_WOOD;
	        if (resource == IRON) return 0;
	        return POINTS_FRUIT;
	    }
	    private Resource() {}
	}
	private static class GameState {
	    public static final int MAX_TILES  = 256;
	    public static final int MAX_TREES  = 128;
	    public static final int MAX_TROLLS = 32;
	    public int width;
	    public int height;
	    public int tileCount;
	    public byte[] terrain;
	    public boolean[] waterAdj;
	    public int shackMeTile;
	    public int shackOppTile;
	    public short[] treeByTile;
	    public short[] myTrollByTile;
	    public short[] oppTrollByTile;
	    public int treeCount;
	    public byte[] treeType    = new byte[MAX_TREES];
	    public byte[] treeX       = new byte[MAX_TREES];
	    public byte[] treeY       = new byte[MAX_TREES];
	    public byte[] treeSize    = new byte[MAX_TREES];
	    public byte[] treeHealth  = new byte[MAX_TREES];
	    public byte[] treeFruits  = new byte[MAX_TREES];
	    public byte[] treeCooldown = new byte[MAX_TREES];
	    public int trollCount;
	    public byte[] trollPlayer         = new byte[MAX_TROLLS];
	    public byte[] trollX              = new byte[MAX_TROLLS];
	    public byte[] trollY              = new byte[MAX_TROLLS];
	    public byte[] trollMoveSpeed      = new byte[MAX_TROLLS];
	    public byte[] trollCarryCapacity  = new byte[MAX_TROLLS];
	    public byte[] trollHarvestPower   = new byte[MAX_TROLLS];
	    public byte[] trollChopPower      = new byte[MAX_TROLLS];
	    public byte[] trollCarry          = new byte[MAX_TROLLS * Resource.COUNT];
	    public int[]  trollOriginalId     = new int[MAX_TROLLS];
	    public int[] invMe  = new int[Resource.COUNT];
	    public int[] invOpp = new int[Resource.COUNT];
	    public int turn;
	    public GameState() {}
	    public int tile(int x, int y) {
	        return y * width + x;
	    }
	    public int x(int tile) {
	        return tile % width;
	    }
	    public int y(int tile) {
	        return tile / width;
	    }
	    public boolean isWalkable(int tile) {
	        return terrain[tile] == TileType.GRASS;
	    }
	    public boolean isAdjacent(int tileA, int tileB) {
	        int ax = x(tileA), ay = y(tileA);
	        int bx = x(tileB), by = y(tileB);
	        int dx = Math.abs(ax - bx);
	        int dy = Math.abs(ay - by);
	        return dx + dy == 1;
	    }
	    public int score(int player) {
	        int[] inv = (player == 0) ? invMe : invOpp;
	        int s = 0;
	        s += inv[Resource.PLUM]   * Resource.POINTS_FRUIT;
	        s += inv[Resource.LEMON]  * Resource.POINTS_FRUIT;
	        s += inv[Resource.APPLE]  * Resource.POINTS_FRUIT;
	        s += inv[Resource.BANANA] * Resource.POINTS_FRUIT;
	        s += inv[Resource.WOOD]   * Resource.POINTS_WOOD;
	        return s;
	    }
	    public void copyFrom(GameState src) {
	        throw new UnsupportedOperationException("not yet implemented");
	    }
	    public void applyTurn(int[] myActions, int[] oppActions) {
	        throw new UnsupportedOperationException("not yet implemented");
	    }
	    public static void readInit(Scanner in, GameState dst) {
	        dst.width  = in.nextInt();
	        dst.height = in.nextInt();
	        if (in.hasNextLine()) in.nextLine();
	        dst.tileCount = dst.width * dst.height;
	        dst.terrain        = new byte[dst.tileCount];
	        dst.waterAdj       = new boolean[dst.tileCount];
	        dst.treeByTile     = new short[dst.tileCount];
	        dst.myTrollByTile  = new short[dst.tileCount];
	        dst.oppTrollByTile = new short[dst.tileCount];
	        for (int y = 0; y < dst.height; y++) {
	            String line = in.nextLine();
	            for (int x = 0; x < dst.width; x++) {
	                char c = line.charAt(x);
	                byte t = TileType.fromChar(c);
	                int tile = y * dst.width + x;
	                dst.terrain[tile] = t;
	                if (t == TileType.SHACK_ME)  dst.shackMeTile  = tile;
	                if (t == TileType.SHACK_OPP) dst.shackOppTile = tile;
	            }
	        }
	        computeWaterAdj(dst);
	        dst.turn = 0;
	    }
	    private static void computeWaterAdj(GameState s) {
	        for (int y = 0; y < s.height; y++) {
	            for (int x = 0; x < s.width; x++) {
	                int tile = y * s.width + x;
	                boolean near = false;
	                if (x > 0           && s.terrain[tile - 1]       == TileType.WATER) near = true;
	                if (x < s.width - 1 && s.terrain[tile + 1]       == TileType.WATER) near = true;
	                if (y > 0           && s.terrain[tile - s.width] == TileType.WATER) near = true;
	                if (y < s.height-1  && s.terrain[tile + s.width] == TileType.WATER) near = true;
	                s.waterAdj[tile] = near;
	            }
	        }
	    }
	    public static void readTurn(Scanner in, GameState dst) {
	        for (int i = 0; i < dst.tileCount; i++) {
	            dst.treeByTile[i]     = -1;
	            dst.myTrollByTile[i]  = -1;
	            dst.oppTrollByTile[i] = -1;
	        }
	        for (int p = 0; p < 2; p++) {
	            int[] inv = (p == 0) ? dst.invMe : dst.invOpp;
	            inv[Resource.PLUM]   = in.nextInt();
	            inv[Resource.LEMON]  = in.nextInt();
	            inv[Resource.APPLE]  = in.nextInt();
	            inv[Resource.BANANA] = in.nextInt();
	            inv[Resource.IRON]   = in.nextInt();
	            inv[Resource.WOOD]   = in.nextInt();
	        }
	        dst.treeCount = in.nextInt();
	        for (int i = 0; i < dst.treeCount; i++) {
	            dst.treeType[i]     = TreeType.fromString(in.next());
	            dst.treeX[i]        = (byte) in.nextInt();
	            dst.treeY[i]        = (byte) in.nextInt();
	            dst.treeSize[i]     = (byte) in.nextInt();
	            dst.treeHealth[i]   = (byte) in.nextInt();
	            dst.treeFruits[i]   = (byte) in.nextInt();
	            dst.treeCooldown[i] = (byte) in.nextInt();
	            int tile = dst.treeY[i] * dst.width + dst.treeX[i];
	            dst.treeByTile[tile] = (short) i;
	        }
	        dst.trollCount = in.nextInt();
	        for (int i = 0; i < dst.trollCount; i++) {
	            dst.trollOriginalId[i]    = in.nextInt();
	            dst.trollPlayer[i]        = (byte) in.nextInt();
	            dst.trollX[i]             = (byte) in.nextInt();
	            dst.trollY[i]             = (byte) in.nextInt();
	            dst.trollMoveSpeed[i]     = (byte) in.nextInt();
	            dst.trollCarryCapacity[i] = (byte) in.nextInt();
	            dst.trollHarvestPower[i]  = (byte) in.nextInt();
	            dst.trollChopPower[i]     = (byte) in.nextInt();
	            int base = i * Resource.COUNT;
	            dst.trollCarry[base + Resource.PLUM]   = (byte) in.nextInt();
	            dst.trollCarry[base + Resource.LEMON]  = (byte) in.nextInt();
	            dst.trollCarry[base + Resource.APPLE]  = (byte) in.nextInt();
	            dst.trollCarry[base + Resource.BANANA] = (byte) in.nextInt();
	            dst.trollCarry[base + Resource.IRON]   = (byte) in.nextInt();
	            dst.trollCarry[base + Resource.WOOD]   = (byte) in.nextInt();
	            int tile = dst.trollY[i] * dst.width + dst.trollX[i];
	            if (dst.trollPlayer[i] == 0) dst.myTrollByTile[tile]  = (short) i;
	            else                         dst.oppTrollByTile[tile] = (short) i;
	        }
	        dst.turn++;
	    }
	}
	private static class ActionType {
	    public static final byte WAIT    = 0;
	    public static final byte MOVE    = 1;
	    public static final byte HARVEST = 2;
	    public static final byte PLANT   = 3;
	    public static final byte CHOP    = 4;
	    public static final byte PICK    = 5;
	    public static final byte DROP    = 6;
	    public static final byte MINE    = 7;
	    public static final byte TRAIN   = 8;
	    public static final byte MSG     = 9;
	    private ActionType() {}
	}
	private static class Actions {
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
	private static class GreedyAgent {
	    private static final int UNREACHABLE = Integer.MAX_VALUE;
	    private static final int[] DX = { 1, -1, 0, 0 };
	    private static final int[] DY = { 0, 0, 1, -1 };
	    private final int[] bfsQueue;
	    private final int[] bfsDist;
	    private final boolean[] reservedTile;
	    public GreedyAgent(int maxTiles) {
	        this.bfsQueue   = new int[maxTiles];
	        this.bfsDist    = new int[maxTiles];
	        this.reservedTile = new boolean[maxTiles];
	    }
	    public int decide(GameState state, int[] out) {
	        java.util.Arrays.fill(reservedTile, 0, state.tileCount, false);
	        int n = 0;
	        for (int i = 0; i < state.trollCount; i++) {
	            if (state.trollPlayer[i] != 0) continue;
	            out[n++] = decideForTroll(state, i);
	        }
	        return n;
	    }
	    private int decideForTroll(GameState s, int idx) {
	        int x = s.trollX[idx] & 0xFF;
	        int y = s.trollY[idx] & 0xFF;
	        int tile = y * s.width + x;
	        int carry = totalCarry(s, idx);
	        int cap   = s.trollCarryCapacity[idx] & 0xFF;
	        if (carry > 0 && s.isAdjacent(tile, s.shackMeTile)) {
	            return Actions.drop(idx);
	        }
	        int treeIdx = s.treeByTile[tile];
	        if (treeIdx >= 0 && carry < cap) {
	            if (s.treeFruits[treeIdx] > 0) {
	                return Actions.harvest(idx);
	            }
	            if (s.treeSize[treeIdx] >= 3 && (cap - carry) >= 1) {
	                return Actions.chop(idx);
	            }
	        }
	        computeBFS(s, tile);
	        if (carry >= cap) {
	            int dest = closestAdjacentToShack(s);
	            if (dest >= 0) return Actions.move(idx, dest);
	            return Actions.waitAction();
	        }
	        int bestTile = -1;
	        double bestScore = Double.NEGATIVE_INFINITY;
	        for (int t = 0; t < s.treeCount; t++) {
	            int tx = s.treeX[t] & 0xFF;
	            int ty = s.treeY[t] & 0xFF;
	            int tt = ty * s.width + tx;
	            if (reservedTile[tt]) continue;
	            int d = bfsDist[tt];
	            if (d == UNREACHABLE) continue;
	            double score = evaluateTree(s, t, d, cap - carry);
	            if (score > bestScore) {
	                bestScore = score;
	                bestTile = tt;
	            }
	        }
	        if (bestTile >= 0) {
	            reservedTile[bestTile] = true;
	            return Actions.move(idx, bestTile);
	        }
	        if (carry > 0) {
	            int dest = closestAdjacentToShack(s);
	            if (dest >= 0) return Actions.move(idx, dest);
	        }
	        return Actions.waitAction();
	    }
	    private double evaluateTree(GameState s, int t, int dist, int freeCap) {
	        int fruits = s.treeFruits[t] & 0xFF;
	        int size   = s.treeSize[t]   & 0xFF;
	        int cd     = s.treeCooldown[t] & 0xFF;
	        if (fruits > 0) {
	            return ((double) Resource.POINTS_FRUIT * fruits) / (dist + 1);
	        }
	        if (size >= 3) {
	            int health = s.treeHealth[t] & 0xFF;
	            int woodOut = Math.min(size, freeCap);
	            double tours = dist + Math.max(1, health);
	            return ((double) Resource.POINTS_WOOD * woodOut) / tours;
	        }
	        if (size == TreeStats.MAX_SIZE && cd <= 2) {
	            return 0.5 / (dist + 1);
	        }
	        return Double.NEGATIVE_INFINITY;
	    }
	    private int closestAdjacentToShack(GameState s) {
	        int sx = s.x(s.shackMeTile);
	        int sy = s.y(s.shackMeTile);
	        int best = -1;
	        int bestDist = UNREACHABLE;
	        for (int d = 0; d < 4; d++) {
	            int nx = sx + DX[d], ny = sy + DY[d];
	            if (nx < 0 || nx >= s.width || ny < 0 || ny >= s.height) continue;
	            int nt = ny * s.width + nx;
	            if (s.terrain[nt] != TileType.GRASS) continue;
	            if (bfsDist[nt] < bestDist) {
	                bestDist = bfsDist[nt];
	                best = nt;
	            }
	        }
	        return best;
	    }
	    private int totalCarry(GameState s, int idx) {
	        int base = idx * Resource.COUNT;
	        int sum = 0;
	        for (int r = 0; r < Resource.COUNT; r++) {
	            sum += s.trollCarry[base + r] & 0xFF;
	        }
	        return sum;
	    }
	    private void computeBFS(GameState s, int src) {
	        for (int i = 0; i < s.tileCount; i++) bfsDist[i] = UNREACHABLE;
	        bfsDist[src] = 0;
	        int head = 0, tail = 0;
	        bfsQueue[tail++] = src;
	        while (head < tail) {
	            int t = bfsQueue[head++];
	            int x = t % s.width, y = t / s.width;
	            for (int d = 0; d < 4; d++) {
	                int nx = x + DX[d], ny = y + DY[d];
	                if (nx < 0 || nx >= s.width || ny < 0 || ny >= s.height) continue;
	                int nt = ny * s.width + nx;
	                if (bfsDist[nt] != UNREACHABLE) continue;
	                if (s.terrain[nt] != TileType.GRASS) continue;
	                bfsDist[nt] = bfsDist[t] + 1;
	                bfsQueue[tail++] = nt;
	            }
	        }
	    }
	}
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);
        GreedyAgent agent = new GreedyAgent(state.tileCount);
        int[] actions = new int[GameState.MAX_TROLLS];
        StringBuilder out = new StringBuilder(256);
        while (true) {
            GameState.readTurn(in, state);
            int n = agent.decide(state, actions);
            out.setLength(0);
            if (n == 0) {
                out.append("WAIT");
            } else {
                for (int i = 0; i < n; i++) {
                    if (i > 0) out.append(';');
                    out.append(Actions.format(actions[i], state));
                }
            }
            System.out.println(out);
        }
    }
}
