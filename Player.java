import java.util.Scanner;
import java.util.SplittableRandom;
class Player {
	private static class TreeType {
	    public static final byte PLUM   = 0;
	    public static final byte LEMON  = 1;
	    public static final byte APPLE  = 2;
	    public static final byte BANANA = 3;
	    public static final int COUNT = 4;
	    public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
	    public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };
	    public static final byte[][] HEALTH_BY_SIZE = {
	            { 6,  8, 10, 12 },
	            { 6,  8, 10, 12 },
	            { 11, 14, 17, 20 },
	            { 3,  4,  5,  6 },
	    };
	    public static byte fromString(String s) {
	        return switch (s) {
	            case "PLUM"   -> PLUM;
	            case "LEMON"  -> LEMON;
	            case "APPLE"  -> APPLE;
	            case "BANANA" -> BANANA;
	            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
	        };
	    }
	    private TreeType() {}
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
	            default  -> throw new IllegalArgumentException("Unknown tile char: " + c);
	        };
	    }
	    private TileType() {}
	}
	private static class ResourceType {
	    public static final byte PLUM   = 0;
	    public static final byte LEMON  = 1;
	    public static final byte APPLE  = 2;
	    public static final byte BANANA = 3;
	    public static final byte IRON   = 4;
	    public static final byte WOOD   = 5;
	    public static final int COUNT = 6;
	    private ResourceType() {}
	}
	private static class GameState {
	    public static final int MAX_TREES  = 128;
	    public static final int MAX_TROLLS = 32;
	    public static int width;
	    public static int height;
	    public static byte[] tiles;
	    public static int shackMeX;
	    public static int shackMeY;
	    public static int shackOppX;
	    public static int shackOppY;
	    public int turn;
	    public final int[] shackInventory = new int[2 * ResourceType.COUNT];
	    public int treeCount;
	    public final byte[] treeType     = new byte[MAX_TREES];
	    public final byte[] treeX        = new byte[MAX_TREES];
	    public final byte[] treeY        = new byte[MAX_TREES];
	    public final byte[] treeSize     = new byte[MAX_TREES];
	    public final byte[] treeHealth   = new byte[MAX_TREES];
	    public final byte[] treeFruits   = new byte[MAX_TREES];
	    public final byte[] treeCooldown = new byte[MAX_TREES];
	    public byte[] treeCellIndex;
	    public int trollCount;
	    public final byte[] trollId     = new byte[MAX_TROLLS];
	    public final byte[] trollPlayer = new byte[MAX_TROLLS];
	    public final byte[] trollX      = new byte[MAX_TROLLS];
	    public final byte[] trollY      = new byte[MAX_TROLLS];
	    public final byte[] trollMS     = new byte[MAX_TROLLS];
	    public final byte[] trollCC     = new byte[MAX_TROLLS];
	    public final byte[] trollHP     = new byte[MAX_TROLLS];
	    public final byte[] trollCP     = new byte[MAX_TROLLS];
	    public final byte[] trollInventory = new byte[MAX_TROLLS * ResourceType.COUNT];
	    public final int[] trollCarryTotal = new int[MAX_TROLLS];
	    public byte[] trollCellIndex;
	    public int nextTrollId;
	    public GameState() {
	    }
	    public static void readInit(Scanner in) {
	        width  = in.nextInt();
	        height = in.nextInt();
	        in.nextLine();
	        tiles = new byte[width * height];
	        for (int y = 0; y < height; y++) {
	            String line = in.nextLine();
	            for (int x = 0; x < width; x++) {
	                byte t = TileType.fromChar(line.charAt(x));
	                tiles[y * width + x] = t;
	                if (t == TileType.SHACK_ME)  { shackMeX  = x; shackMeY  = y; }
	                if (t == TileType.SHACK_OPP) { shackOppX = x; shackOppY = y; }
	            }
	        }
	    }
	    public void readTurn(Scanner in) {
	        for (int p = 0; p < 2; p++) {
	            for (int r = 0; r < ResourceType.COUNT; r++) {
	                shackInventory[p * ResourceType.COUNT + r] = in.nextInt();
	            }
	        }
	        treeCount = in.nextInt();
	        for (int i = 0; i < treeCount; i++) {
	            treeType[i]     = TreeType.fromString(in.next());
	            treeX[i]        = (byte) in.nextInt();
	            treeY[i]        = (byte) in.nextInt();
	            treeSize[i]     = (byte) in.nextInt();
	            treeHealth[i]   = (byte) in.nextInt();
	            treeFruits[i]   = (byte) in.nextInt();
	            treeCooldown[i] = (byte) in.nextInt();
	        }
	        treeCellIndex = null; // invalidate; will be lazily rebuilt on first use
	        trollCount = in.nextInt();
	        for (int i = 0; i < trollCount; i++) {
	            trollId[i]     = (byte) in.nextInt();
	            trollPlayer[i] = (byte) in.nextInt();
	            trollX[i]      = (byte) in.nextInt();
	            trollY[i]      = (byte) in.nextInt();
	            trollMS[i]     = (byte) in.nextInt();
	            trollCC[i]     = (byte) in.nextInt();
	            trollHP[i]     = (byte) in.nextInt();
	            trollCP[i]     = (byte) in.nextInt();
	            for (int r = 0; r < ResourceType.COUNT; r++) {
	                trollInventory[i * ResourceType.COUNT + r] = (byte) in.nextInt();
	            }
	        }
	        trollCellIndex = null; // invalidate; will be lazily rebuilt on first use
	        nextTrollId = 0;
	        for (int i = 0; i < trollCount; i++) {
	            int id = trollId[i] & 0xFF;
	            if (id >= nextTrollId) nextTrollId = id + 1;
	            int base = i * ResourceType.COUNT;
	            int tot = 0;
	            for (int r = 0; r < ResourceType.COUNT; r++) tot += trollInventory[base + r] & 0xFF;
	            trollCarryTotal[i] = tot;
	        }
	    }
	    public void copyFrom(GameState src) {
	        turn = src.turn;
	        System.arraycopy(src.shackInventory, 0, shackInventory, 0, shackInventory.length);
	        treeCount = src.treeCount;
	        System.arraycopy(src.treeType,     0, treeType,     0, MAX_TREES);
	        System.arraycopy(src.treeX,        0, treeX,        0, MAX_TREES);
	        System.arraycopy(src.treeY,        0, treeY,        0, MAX_TREES);
	        System.arraycopy(src.treeSize,     0, treeSize,     0, MAX_TREES);
	        System.arraycopy(src.treeHealth,   0, treeHealth,   0, MAX_TREES);
	        System.arraycopy(src.treeFruits,   0, treeFruits,   0, MAX_TREES);
	        System.arraycopy(src.treeCooldown, 0, treeCooldown, 0, MAX_TREES);
	        if (treeCellIndex == null || treeCellIndex.length < width * height) {
	            treeCellIndex = new byte[width * height];
	        }
	        if (src.treeCellIndex != null) {
	            System.arraycopy(src.treeCellIndex, 0, treeCellIndex, 0, width * height);
	        } else {
	            java.util.Arrays.fill(treeCellIndex, (byte) -1);
	            for (int i = 0; i < treeCount; i++) {
	                if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
	            }
	        }
	        trollCount = src.trollCount;
	        System.arraycopy(src.trollId,        0, trollId,        0, MAX_TROLLS);
	        System.arraycopy(src.trollPlayer,    0, trollPlayer,    0, MAX_TROLLS);
	        System.arraycopy(src.trollX,         0, trollX,         0, MAX_TROLLS);
	        System.arraycopy(src.trollY,         0, trollY,         0, MAX_TROLLS);
	        System.arraycopy(src.trollMS,        0, trollMS,        0, MAX_TROLLS);
	        System.arraycopy(src.trollCC,        0, trollCC,        0, MAX_TROLLS);
	        System.arraycopy(src.trollHP,        0, trollHP,        0, MAX_TROLLS);
	        System.arraycopy(src.trollCP,        0, trollCP,        0, MAX_TROLLS);
	        System.arraycopy(src.trollInventory,  0, trollInventory,  0, MAX_TROLLS * ResourceType.COUNT);
	        System.arraycopy(src.trollCarryTotal, 0, trollCarryTotal, 0, MAX_TROLLS);
	        nextTrollId = src.nextTrollId;
	        if (trollCellIndex == null || trollCellIndex.length < width * height) {
	            trollCellIndex = new byte[width * height];
	        }
	        if (src.trollCellIndex != null) {
	            System.arraycopy(src.trollCellIndex, 0, trollCellIndex, 0, width * height);
	        } else {
	            java.util.Arrays.fill(trollCellIndex, (byte) -1);
	            for (int i = 0; i < trollCount; i++) {
	                trollCellIndex[(trollY[i] & 0xFF) * width + (trollX[i] & 0xFF)] = (byte) i;
	            }
	        }
	    }
	    public void apply(int action) {
	        throw new UnsupportedOperationException("TODO");
	    }
	    public int score(int player) {
	        int base = player * ResourceType.COUNT;
	        return shackInventory[base + ResourceType.PLUM]
	             + shackInventory[base + ResourceType.LEMON]
	             + shackInventory[base + ResourceType.APPLE]
	             + shackInventory[base + ResourceType.BANANA]
	             + 4 * shackInventory[base + ResourceType.WOOD];
	    }
	    private void ensureTrollCellIndex() {
	        if (trollCellIndex == null || trollCellIndex.length < width * height) {
	            trollCellIndex = new byte[width * height];
	            java.util.Arrays.fill(trollCellIndex, (byte) -1);
	            int maxId = -1;
	            for (int i = 0; i < trollCount; i++) {
	                trollCellIndex[(trollY[i] & 0xFF) * width + (trollX[i] & 0xFF)] = (byte) i;
	                int id = trollId[i] & 0xFF;
	                if (id > maxId) maxId = id;
	            }
	            if (nextTrollId <= maxId) nextTrollId = maxId + 1;
	        }
	    }
	    public int trollIndexAtCell(int x, int y) {
	        ensureTrollCellIndex();
	        byte v = trollCellIndex[y * width + x];
	        return (v == -1) ? -1 : (v & 0xFF);
	    }
	    public int addTroll(int player, int x, int y, int ms, int cc, int hp, int cp) {
	        ensureTrollCellIndex();
	        int idx = trollCount++;
	        trollPlayer[idx] = (byte) player;
	        trollId[idx]     = (byte) nextTrollId++;
	        trollX[idx]      = (byte) x;
	        trollY[idx]      = (byte) y;
	        trollMS[idx]     = (byte) ms;
	        trollCC[idx]     = (byte) cc;
	        trollHP[idx]     = (byte) hp;
	        trollCP[idx]     = (byte) cp;
	        int invBase = idx * ResourceType.COUNT;
	        for (int r = 0; r < ResourceType.COUNT; r++) trollInventory[invBase + r] = 0;
	        trollCarryTotal[idx] = 0;
	        trollCellIndex[y * width + x] = (byte) idx;
	        return idx;
	    }
	    public void addToInventory(int idx, int resource, int delta) {
	        trollInventory[idx * ResourceType.COUNT + resource] += (byte) delta;
	        trollCarryTotal[idx] += delta;
	    }
	    public void clearInventory(int idx) {
	        int base = idx * ResourceType.COUNT;
	        for (int r = 0; r < ResourceType.COUNT; r++) trollInventory[base + r] = 0;
	        trollCarryTotal[idx] = 0;
	    }
	    public void moveTroll(int idx, int x, int y) {
	        ensureTrollCellIndex();
	        int W = width;
	        trollCellIndex[(trollY[idx] & 0xFF) * W + (trollX[idx] & 0xFF)] = -1;
	        trollX[idx] = (byte) x;
	        trollY[idx] = (byte) y;
	        trollCellIndex[y * W + x] = (byte) idx;
	    }
	    public int trollIndexById(int id) {
	        for (int i = 0; i < trollCount; i++) {
	            if ((trollId[i] & 0xFF) == id) return i;
	        }
	        return -1;
	    }
	    public int treeIndexAt(int x, int y) {
	        if (treeCellIndex == null) return slowTreeIndexAt(x, y);
	        int v = treeCellIndex[y * width + x];
	        return (v == -1) ? -1 : (v & 0xFF);
	    }
	    private int slowTreeIndexAt(int x, int y) {
	        for (int i = 0; i < treeCount; i++) {
	            if (treeX[i] == (byte) x && treeY[i] == (byte) y && treeHealth[i] > 0) return i;
	        }
	        return -1;
	    }
	    private void ensureTreeCellIndex() {
	        if (treeCellIndex == null || treeCellIndex.length < width * height) {
	            treeCellIndex = new byte[width * height];
	            java.util.Arrays.fill(treeCellIndex, (byte) -1);
	            for (int i = 0; i < treeCount; i++) {
	                if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
	            }
	        }
	    }
	    public int addTree(byte type, int x, int y, int size, int health) {
	        ensureTreeCellIndex();
	        int idx = treeCount++;
	        treeType[idx]     = type;
	        treeX[idx]        = (byte) x;
	        treeY[idx]        = (byte) y;
	        treeSize[idx]     = (byte) size;
	        treeHealth[idx]   = (byte) health;
	        treeFruits[idx]   = 0;
	        treeCooldown[idx] = 0;
	        treeCellIndex[y * width + x] = (byte) idx;
	        return idx;
	    }
	    public void killTreeAt(int idx) {
	        ensureTreeCellIndex();
	        treeHealth[idx] = 0;
	        treeCellIndex[(treeY[idx] & 0xFF) * width + (treeX[idx] & 0xFF)] = -1;
	    }
	    public void compactDeadTrees() {
	        ensureTreeCellIndex();
	        int i = 0;
	        while (i < treeCount) {
	            if (treeHealth[i] <= 0) {
	                int last = treeCount - 1;
	                if (i != last) {
	                    treeType[i]     = treeType[last];
	                    treeX[i]        = treeX[last];
	                    treeY[i]        = treeY[last];
	                    treeSize[i]     = treeSize[last];
	                    treeHealth[i]   = treeHealth[last];
	                    treeFruits[i]   = treeFruits[last];
	                    treeCooldown[i] = treeCooldown[last];
	                    if (treeHealth[i] > 0) {
	                        treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
	                    }
	                }
	                treeCount--;
	            } else {
	                i++;
	            }
	        }
	    }
	    public static byte tileAt(int x, int y) {
	        return tiles[y * width + x];
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
	    private ActionType() {}
	}
	private static class Action {
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
	private static class PathTable {
	    public static final int UNREACHABLE = 0xFF;
	    public static int N;               // nombre de cases GRASS (traversables)
	    public static int Ntot;            // N + nombre de shacks indexes (interrogeables mais non traversables)
	    public static int shackMeId  = -1; // id de SHACK_ME si present, -1 sinon
	    public static int shackOppId = -1; // id de SHACK_OPP si present, -1 sinon
	    public static byte[] cellIdAt;     // [W*H] -> id 0..Ntot-1, 0xFF si non indexe
	    public static byte[] cellX;        // [Ntot] x de la case d'id i
	    public static byte[] cellY;        // [Ntot] y de la case d'id i
	    static void indexCells() {
	        int W = GameState.width;
	        int H = GameState.height;
	        cellIdAt = new byte[W * H];
	        java.util.Arrays.fill(cellIdAt, (byte) 0xFF);
	        int grassCount = 0;
	        int shackMeIdx = -1, shackOppIdx = -1;
	        for (int y = 0; y < H; y++) {
	            for (int x = 0; x < W; x++) {
	                byte t = GameState.tileAt(x, y);
	                if      (t == TileType.GRASS)     grassCount++;
	                else if (t == TileType.SHACK_ME)  shackMeIdx  = y * W + x;
	                else if (t == TileType.SHACK_OPP) shackOppIdx = y * W + x;
	            }
	        }
	        N = grassCount;
	        int extra = (shackMeIdx >= 0 ? 1 : 0) + (shackOppIdx >= 0 ? 1 : 0);
	        Ntot = N + extra;
	        cellX = new byte[Ntot];
	        cellY = new byte[Ntot];
	        int id = 0;
	        for (int y = 0; y < H; y++) {
	            for (int x = 0; x < W; x++) {
	                if (GameState.tileAt(x, y) == TileType.GRASS) {
	                    cellIdAt[y * W + x] = (byte) id;
	                    cellX[id] = (byte) x;
	                    cellY[id] = (byte) y;
	                    id++;
	                }
	            }
	        }
	        shackMeId  = -1;
	        shackOppId = -1;
	        if (shackMeIdx >= 0) {
	            shackMeId = id;
	            cellIdAt[shackMeIdx] = (byte) id;
	            cellX[id] = (byte) (shackMeIdx % W);
	            cellY[id] = (byte) (shackMeIdx / W);
	            id++;
	        }
	        if (shackOppIdx >= 0) {
	            shackOppId = id;
	            cellIdAt[shackOppIdx] = (byte) id;
	            cellX[id] = (byte) (shackOppIdx % W);
	            cellY[id] = (byte) (shackOppIdx / W);
	            id++;
	        }
	    }
	    public static int cellId(int x, int y) {
	        return cellIdAt[y * GameState.width + x] & 0xFF;
	    }
	    public static boolean[] isNearWater;  // [W*H] true si une case orthogonale est de l'eau
	    public static byte[][]   dist;    // [N][N] distance, UNREACHABLE si non connecté
	    public static byte[][][] paths;   // [N][N] -> chemin partagé symétriquement
	    public static void init() {
	        indexCells();
	        allocateBfsBuffers();
	        dist  = new byte[Ntot][Ntot];
	        paths = new byte[Ntot][Ntot][];
	        for (int src = 0; src < Ntot; src++) {
	            bfs(src);
	            dist[src][src]  = 0;
	            paths[src][src] = new byte[]{ (byte) src };
	            for (int dst = src + 1; dst < Ntot; dst++) {
	                if (bfsDist[dst] == UNREACHABLE) {
	                    dist[src][dst] = (byte) UNREACHABLE;
	                    dist[dst][src] = (byte) UNREACHABLE;
	                    continue;
	                }
	                byte[] p = reconstruct(src, dst);
	                paths[src][dst] = p;
	                paths[dst][src] = p;             // référence partagée — lecture inversée pour dst→src
	                byte d = (byte) (p.length - 1);
	                dist[src][dst] = d;
	                dist[dst][src] = d;
	            }
	        }
	        int W = GameState.width;
	        int H = GameState.height;
	        isNearWater = new boolean[W * H];
	        for (int y = 0; y < H; y++) {
	            for (int x = 0; x < W; x++) {
	                boolean near = (x + 1 < W && GameState.tiles[y * W + (x + 1)] == TileType.WATER)
	                            || (x - 1 >= 0 && GameState.tiles[y * W + (x - 1)] == TileType.WATER)
	                            || (y + 1 < H && GameState.tiles[(y + 1) * W + x] == TileType.WATER)
	                            || (y - 1 >= 0 && GameState.tiles[(y - 1) * W + x] == TileType.WATER);
	                isNearWater[y * W + x] = near;
	            }
	        }
	    }
	    private static byte[] reconstruct(int src, int dst) {
	        int len = bfsDist[dst] + 1;
	        byte[] path = new byte[len];
	        int cur = dst;
	        for (int i = len - 1; i >= 0; i--) {
	            path[i] = (byte) cur;
	            cur = bfsPrev[cur];
	        }
	        return path;
	    }
	    public  static int[] bfsDist;   // distance depuis la dernière source BFS, UNREACHABLE sinon
	    public  static int[] bfsPrev;   // prédecesseur dans l'arbre BFS, -1 si racine ou non atteint
	    private static int[] queue;
	    private static final int[] DX = { 1, -1, 0,  0 };
	    private static final int[] DY = { 0,  0, 1, -1 };
	    static void allocateBfsBuffers() {
	        queue   = new int[Ntot];
	        bfsDist = new int[Ntot];
	        bfsPrev = new int[Ntot];
	    }
	    static void bfs(int src) {
	        java.util.Arrays.fill(bfsDist, UNREACHABLE);
	        java.util.Arrays.fill(bfsPrev, -1);
	        int W = GameState.width;
	        int H = GameState.height;
	        bfsDist[src] = 0;
	        int head = 0, tail = 0;
	        queue[tail++] = src;
	        while (head < tail) {
	            int cur = queue[head++];
	            int cx = cellX[cur] & 0xFF;
	            int cy = cellY[cur] & 0xFF;
	            int d  = bfsDist[cur];
	            for (int k = 0; k < 4; k++) {
	                int nx = cx + DX[k];
	                int ny = cy + DY[k];
	                if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
	                int nid = cellIdAt[ny * W + nx] & 0xFF;
	                if (nid == UNREACHABLE) continue;
	                if (bfsDist[nid] != UNREACHABLE) continue;
	                bfsDist[nid] = d + 1;
	                bfsPrev[nid] = cur;
	                if (nid >= N) continue; // shack : feuille, on n'y propage pas
	                queue[tail++] = nid;
	            }
	        }
	    }
	    public static int distance(int fromId, int toId) {
	        return dist[fromId][toId] & 0xFF;
	    }
	    public static int distance(int fx, int fy, int tx, int ty) {
	        int W = GameState.width;
	        int from = cellIdAt[fy * W + fx] & 0xFF;
	        int to   = cellIdAt[ty * W + tx] & 0xFF;
	        return dist[from][to] & 0xFF;
	    }
	    public static int stepAlong(int fromId, int toId, int k) {
	        byte[] p = paths[fromId][toId];
	        int last = p.length - 1;
	        if (k > last) k = last;
	        if (fromId <= toId) {
	            return p[k] & 0xFF;
	        }
	        return p[last - k] & 0xFF;
	    }
	    public static int stepAlong(int fx, int fy, int tx, int ty, int k) {
	        int W = GameState.width;
	        int from = cellIdAt[fy * W + fx] & 0xFF;
	        int to   = cellIdAt[ty * W + tx] & 0xFF;
	        int destId = stepAlong(from, to, k);
	        return (cellY[destId] & 0xFF) * W + (cellX[destId] & 0xFF);
	    }
	    private PathTable() {}
	}
	private static class ShackAdjacency {
	    public static final byte[] x = new byte[4];
	    public static final byte[] y = new byte[4];
	    public static int count;
	    private static final int[] DX = { 1, -1, 0,  0 };
	    private static final int[] DY = { 0,  0, 1, -1 };
	    public static void init() {
	        count = 0;
	        int sx = GameState.shackMeX;
	        int sy = GameState.shackMeY;
	        for (int k = 0; k < 4; k++) {
	            int nx = sx + DX[k];
	            int ny = sy + DY[k];
	            if (nx < 0 || nx >= GameState.width)  continue;
	            if (ny < 0 || ny >= GameState.height) continue;
	            if (GameState.tileAt(nx, ny) != TileType.GRASS) continue;
	            x[count] = (byte) nx;
	            y[count] = (byte) ny;
	            count++;
	        }
	    }
	    private ShackAdjacency() {}
	}
	private static class GreedyAgent {
	    public static int maybeTrain(GameState s) {
	        int n = countOwnTrolls(s);
	        int plum  = s.shackInventory[ResourceType.PLUM];
	        int lemon = s.shackInventory[ResourceType.LEMON];
	        int apple = s.shackInventory[ResourceType.APPLE];
	        int iron  = s.shackInventory[ResourceType.IRON];
	        if (plum  < n + 1) return -1;
	        if (lemon < n)     return -1;
	        if (apple < n)     return -1;
	        if (iron  < n)     return -1;
	        int ms = maxV(plum,  n, 1);
	        int cc = maxV(lemon, n, 0);
	        int cp = maxV(iron,  n, 0);
	        return Action.train(ms, cc, 0, cp);
	    }
	    private static int maxV(int resource, int n, int floor) {
	        int v = floor;
	        while ((long) (n + (v + 1) * (v + 1)) <= resource) v++;
	        return v;
	    }
	    private static int countOwnTrolls(GameState s) {
	        int n = 0;
	        for (int i = 0; i < s.trollCount; i++) {
	            if (s.trollPlayer[i] == 0) n++;
	        }
	        return n;
	    }
	    public static int decideForTroll(GameState s, int trollIdx, int treeIdx) {
	        int tx = s.trollX[trollIdx] & 0xFF;
	        int ty = s.trollY[trollIdx] & 0xFF;
	        int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
	        if (wood > 0) {
	            if (isShackAdjacent(tx, ty)) {
	                return Action.drop(trollIdx);
	            }
	            int dropX = ShackAdjacency.x[0] & 0xFF;
	            int dropY = ShackAdjacency.y[0] & 0xFF;
	            int bestDist = PathTable.distance(tx, ty, dropX, dropY);
	            for (int i = 1; i < ShackAdjacency.count; i++) {
	                int cx = ShackAdjacency.x[i] & 0xFF;
	                int cy = ShackAdjacency.y[i] & 0xFF;
	                int d  = PathTable.distance(tx, ty, cx, cy);
	                if (d < bestDist) {
	                    bestDist = d;
	                    dropX = cx;
	                    dropY = cy;
	                }
	            }
	            return Action.move(trollIdx, dropX, dropY);
	        }
	        if (treeIdx < 0) {
	            return Action.wait(trollIdx);
	        }
	        int treeX = s.treeX[treeIdx] & 0xFF;
	        int treeY = s.treeY[treeIdx] & 0xFF;
	        if (tx == treeX && ty == treeY) {
	            return Action.chop(trollIdx);
	        }
	        return Action.move(trollIdx, treeX, treeY);
	    }
	    private static boolean isShackAdjacent(int x, int y) {
	        for (int i = 0; i < ShackAdjacency.count; i++) {
	            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
	        }
	        return false;
	    }
	    private static final boolean[] treeTaken = new boolean[GameState.MAX_TREES];
	    public static int decide(GameState s, int[] outActions) {
	        int count = 0;
	        if (s.turn == 0) {
	            int trainAction = maybeTrain(s);
	            if (trainAction != -1) {
	                outActions[count++] = trainAction;
	            }
	        }
	        java.util.Arrays.fill(treeTaken, 0, s.treeCount, false);
	        for (int i = 0; i < s.trollCount; i++) {
	            if (s.trollPlayer[i] != 0) continue;
	            int treeIdx = pickClosestFreeTree(s, i);
	            if (treeIdx >= 0) treeTaken[treeIdx] = true;
	            outActions[count++] = decideForTroll(s, i, treeIdx);
	        }
	        return count;
	    }
	    public static int decideForOpponent(GameState s, int trollIdx, boolean[] oppTreeTakenBuf) {
	        int treeIdx = pickClosestFreeTreeWithBuf(s, trollIdx, oppTreeTakenBuf);
	        if (treeIdx >= 0) oppTreeTakenBuf[treeIdx] = true;
	        return decideForTroll(s, trollIdx, treeIdx);
	    }
	    private static int pickClosestFreeTreeWithBuf(GameState s, int trollIdx, boolean[] taken) {
	        int tx = s.trollX[trollIdx] & 0xFF;
	        int ty = s.trollY[trollIdx] & 0xFF;
	        int best = -1;
	        int bestDist = Integer.MAX_VALUE;
	        for (int t = 0; t < s.treeCount; t++) {
	            if (taken[t]) continue;
	            if (s.treeHealth[t] <= 0) continue;
	            int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
	            if (d == PathTable.UNREACHABLE) continue;
	            if (d < bestDist) { bestDist = d; best = t; }
	        }
	        return best;
	    }
	    private static int pickClosestFreeTree(GameState s, int trollIdx) {
	        int tx = s.trollX[trollIdx] & 0xFF;
	        int ty = s.trollY[trollIdx] & 0xFF;
	        int best = -1;
	        int bestDist = Integer.MAX_VALUE;
	        for (int t = 0; t < s.treeCount; t++) {
	            if (treeTaken[t]) continue;
	            int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
	            if (d == PathTable.UNREACHABLE) continue;
	            if (d < bestDist) {
	                bestDist = d;
	                best = t;
	            }
	        }
	        return best;
	    }
	    private GreedyAgent() {}
	}
	private static class Simulator {
	    public static boolean DEBUG_INVARIANTS = false;
	    private static final byte[] WATER_BOOST = { 5, 5, 7, 2 };
	    private static final boolean[] harvestTreeProcessed = new boolean[GameState.MAX_TREES];
	    private static final int[]     harvestSharedTrolls  = new int[GameState.MAX_TROLLS];
	    private static final boolean[] plantCellProcessed = new boolean[GameState.MAX_TROLLS + 8];
	    private static final int[]     plantTypeBuf       = new int[GameState.MAX_TROLLS];
	    private static final boolean[] chopTreeProcessed = new boolean[GameState.MAX_TREES];
	    private Simulator() {}
	    public static void tick(GameState s, int[] actions, int n) {
	        syncCarryTotals(s);
	        applyMoves(s, actions, n);
	        applyHarvests(s, actions, n);
	        applyPlants(s, actions, n);
	        applyChops(s, actions, n);
	        applyPicks(s, actions, n);
	        applyTrains(s, actions, n);
	        applyDrops(s, actions, n);
	        applyMines(s, actions, n);
	        plantTick(s);
	        s.compactDeadTrees();
	        s.turn++;
	        if (DEBUG_INVARIANTS) checkInvariants(s);
	    }
	    private static void syncCarryTotals(GameState s) {
	        for (int i = 0; i < s.trollCount; i++) {
	            int base = i * ResourceType.COUNT;
	            int tot = 0;
	            for (int r = 0; r < ResourceType.COUNT; r++) tot += s.trollInventory[base + r] & 0xFF;
	            s.trollCarryTotal[i] = tot;
	        }
	    }
	    static void checkInvariants(GameState s) {
	        int W = GameState.width;
	        if (s.trollCellIndex != null) {
	            for (int i = 0; i < s.trollCount; i++) {
	                int x = s.trollX[i] & 0xFF, y = s.trollY[i] & 0xFF;
	                if ((s.trollCellIndex[y * W + x] & 0xFF) != i) {
	                    throw new IllegalStateException("trollCellIndex inconsistent at troll " + i);
	                }
	            }
	        }
	        if (s.treeCellIndex != null) {
	            for (int i = 0; i < s.treeCount; i++) {
	                if (s.treeHealth[i] > 0) {
	                    int x = s.treeX[i] & 0xFF, y = s.treeY[i] & 0xFF;
	                    if ((s.treeCellIndex[y * W + x] & 0xFF) != i) {
	                        throw new IllegalStateException("treeCellIndex inconsistent at tree " + i);
	                    }
	                }
	            }
	        }
	        for (int i = 0; i < s.trollCount; i++) {
	            int base = i * ResourceType.COUNT;
	            int sum = 0;
	            for (int r = 0; r < ResourceType.COUNT; r++) sum += s.trollInventory[base + r] & 0xFF;
	            if (s.trollCarryTotal[i] != sum) {
	                throw new IllegalStateException("trollCarryTotal inconsistent at troll " + i
	                    + " (expected " + sum + ", got " + s.trollCarryTotal[i] + ")");
	            }
	        }
	    }
	    private static final int[] moveTargetX = new int[GameState.MAX_TROLLS];
	    private static final int[] moveTargetY = new int[GameState.MAX_TROLLS];
	    private static final boolean[] hasMove = new boolean[GameState.MAX_TROLLS];
	    private static final int[] resolverIdx = new int[GameState.MAX_TROLLS];
	    private static final boolean[] resolverDone = new boolean[GameState.MAX_TROLLS];
	    private static final int[] freqDirty = new int[GameState.MAX_TROLLS];
	    private static final int[] occupiedDirty = new int[GameState.MAX_TROLLS];
	    static void applyMoves(GameState s, int[] actions, int n) {
	        for (int i = 0; i < s.trollCount; i++) {
	            hasMove[i] = false;
	            moveTargetX[i] = s.trollX[i] & 0xFF;
	            moveTargetY[i] = s.trollY[i] & 0xFF;
	        }
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.MOVE) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            int tx = Action.arg1(a), ty = Action.arg2(a);
	            if (tx < 0 || tx >= GameState.width || ty < 0 || ty >= GameState.height) continue;
	            int fromX = s.trollX[idx] & 0xFF, fromY = s.trollY[idx] & 0xFF;
	            int speed = s.trollMS[idx] & 0xFF;
	            int[] dest = preResolveTarget(fromX, fromY, tx, ty, speed);
	            if (dest == null) continue;
	            hasMove[idx] = true;
	            moveTargetX[idx] = dest[0];
	            moveTargetY[idx] = dest[1];
	        }
	        for (int player = 0; player < 2; player++) resolvePlayerMoves(s, player);
	    }
	    private static final int[] preResolveDest = new int[2];
	    private static int[] preResolveTarget(int fromX, int fromY, int toX, int toY, int speed) {
	        if (fromX == toX && fromY == toY) return null;
	        int fromId = PathTable.cellId(fromX, fromY);
	        int toId   = PathTable.cellId(toX, toY);
	        if (fromId == PathTable.UNREACHABLE || toId == PathTable.UNREACHABLE) return null;
	        int dist = PathTable.distance(fromId, toId);
	        if (dist == PathTable.UNREACHABLE) return null;
	        int k = Math.min(speed, dist);
	        if (k == 0) return null;
	        int raw = PathTable.stepAlong(fromX, fromY, toX, toY, k);
	        int x = raw % GameState.width, y = raw / GameState.width;
	        if (GameState.tiles[y * GameState.width + x] != TileType.GRASS) {
	            if (k <= 1) return null;
	            raw = PathTable.stepAlong(fromX, fromY, toX, toY, k - 1);
	            x = raw % GameState.width; y = raw / GameState.width;
	            if (GameState.tiles[y * GameState.width + x] != TileType.GRASS) return null;
	        }
	        preResolveDest[0] = x;
	        preResolveDest[1] = y;
	        return preResolveDest;
	    }
	    private static void resolvePlayerMoves(GameState s, int player) {
	        int count = 0;
	        for (int i = 0; i < s.trollCount; i++) {
	            if ((s.trollPlayer[i] & 0xFF) != player) continue;
	            resolverIdx[count] = i;
	            resolverDone[i] = !hasMove[i] || (moveTargetX[i] == (s.trollX[i] & 0xFF)
	                                            && moveTargetY[i] == (s.trollY[i] & 0xFF));
	            count++;
	        }
	        int W = GameState.width;
	        boolean[] occupied = ensureOccupiedBuffer(W * GameState.height);
	        int occDirty = 0;
	        for (int k = 0; k < count; k++) {
	            int idx = resolverIdx[k];
	            int cell = (s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF);
	            occupied[cell] = true;
	            occupiedDirty[occDirty++] = cell;
	        }
	        boolean progressed = true;
	        boolean allowBlocked = false;
	        int[] freq = ensureFreqBuffer(W * GameState.height);
	        while (progressed) {
	            progressed = false;
	            int dirty = 0;
	            for (int k = 0; k < count; k++) {
	                int idx = resolverIdx[k];
	                if (resolverDone[idx]) continue;
	                int cell = moveTargetY[idx] * W + moveTargetX[idx];
	                if (freq[cell] == 0) freqDirty[dirty++] = cell;
	                freq[cell]++;
	            }
	            for (int k = 0; k < count; k++) {
	                int idx = resolverIdx[k];
	                if (resolverDone[idx]) continue;
	                int destCell = moveTargetY[idx] * W + moveTargetX[idx];
	                if (!occupied[destCell] && (allowBlocked || freq[destCell] == 1)) {
	                    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
	                    s.moveTroll(idx, moveTargetX[idx], moveTargetY[idx]);
	                    occupied[destCell] = true;
	                    resolverDone[idx] = true;
	                    progressed = true;
	                    allowBlocked = false;
	                }
	            }
	            if (!progressed) {
	                for (int startK = 0; startK < count && !progressed; startK++) {
	                    int startIdx = resolverIdx[startK];
	                    if (resolverDone[startIdx]) continue;
	                    int cur = startIdx;
	                    int hops = 0;
	                    int found = -1;
	                    while (hops <= count) {
	                        int next = s.trollIndexAtCell(moveTargetX[cur], moveTargetY[cur]);
	                        if (next < 0 || resolverDone[next] || (s.trollPlayer[next] & 0xFF) != player) break;
	                        if (next == startIdx) { found = hops; break; }
	                        cur = next; hops++;
	                    }
	                    if (found >= 0) {
	                        int cur2 = startIdx;
	                        int[] cycle = cycleBuf;
	                        int len = 0;
	                        cycle[len++] = cur2;
	                        for (int h = 0; h <= found; h++) {
	                            int next = s.trollIndexAtCell(moveTargetX[cur2], moveTargetY[cur2]);
	                            if (next < 0 || next == startIdx) break;
	                            cycle[len++] = next;
	                            cur2 = next;
	                        }
	                        for (int c = 0; c < len; c++) {
	                            int idx = cycle[c];
	                            occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
	                            if (s.trollCellIndex != null)
	                                s.trollCellIndex[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = -1;
	                        }
	                        for (int c = 0; c < len; c++) {
	                            int idx = cycle[c];
	                            s.trollX[idx] = (byte) moveTargetX[idx];
	                            s.trollY[idx] = (byte) moveTargetY[idx];
	                            occupied[moveTargetY[idx] * W + moveTargetX[idx]] = true;
	                            if (s.trollCellIndex != null)
	                                s.trollCellIndex[moveTargetY[idx] * W + moveTargetX[idx]] = (byte) idx;
	                            resolverDone[idx] = true;
	                        }
	                        progressed = true;
	                    }
	                }
	            }
	            for (int d = 0; d < dirty; d++) freq[freqDirty[d]] = 0;
	            if (!progressed && !allowBlocked) {
	                allowBlocked = true;
	                progressed = true; // re-enter loop with relaxed rule
	            }
	        }
	        for (int d = 0; d < occDirty; d++) occupied[occupiedDirty[d]] = false;
	        for (int k = 0; k < count; k++) {
	            int idx = resolverIdx[k];
	            occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
	        }
	    }
	    private static boolean[] occupiedBuf;
	    private static int[]     freqBuf;
	    private static final int[] cycleBuf = new int[GameState.MAX_TROLLS];
	    private static boolean[] ensureOccupiedBuffer(int size) {
	        if (occupiedBuf == null || occupiedBuf.length < size) occupiedBuf = new boolean[size];
	        return occupiedBuf;
	    }
	    private static int[] ensureFreqBuffer(int size) {
	        if (freqBuf == null || freqBuf.length < size) freqBuf = new int[size];
	        return freqBuf;
	    }
	    static void applyHarvests(GameState s, int[] actions, int n) {
	        for (int t = 0; t < s.treeCount; t++) harvestTreeProcessed[t] = false;
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.HARVEST) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
	            int treeIdx = s.treeIndexAt(tx, ty);
	            if (treeIdx < 0) continue;
	            if (harvestTreeProcessed[treeIdx]) continue;
	            int shared = 0;
	            for (int j = i; j < n; j++) {
	                int b = actions[j];
	                if (Action.type(b) != ActionType.HARVEST) continue;
	                int jdx = Action.trollIdx(b);
	                if (jdx >= s.trollCount) continue;
	                int jx = s.trollX[jdx] & 0xFF, jy = s.trollY[jdx] & 0xFF;
	                if (jx == tx && jy == ty) harvestSharedTrolls[shared++] = jdx;
	            }
	            harvestTreeProcessed[treeIdx] = true;
	            int type = s.treeType[treeIdx] & 0xFF;
	            for (int power = 1; power <= 3; power++) {
	                if (s.treeFruits[treeIdx] == 0) break;
	                for (int k = 0; k < shared; k++) {
	                    int trollIdx = harvestSharedTrolls[k];
	                    int hp = s.trollHP[trollIdx] & 0xFF;
	                    if (power > hp) continue;
	                    int cc = s.trollCC[trollIdx] & 0xFF;
	                    if (s.trollCarryTotal[trollIdx] >= cc) continue;
	                    s.addToInventory(trollIdx, type, 1);
	                    if (s.treeFruits[treeIdx] > 0) s.treeFruits[treeIdx]--;
	                }
	            }
	        }
	    }
	    static void applyChops(GameState s, int[] actions, int n) {
	        for (int t = 0; t < s.treeCount; t++) chopTreeProcessed[t] = false;
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.CHOP) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            if ((s.trollCP[idx] & 0xFF) == 0) continue;
	            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
	            int treeIdx = s.treeIndexAt(tx, ty);
	            if (treeIdx < 0) continue;
	            if (chopTreeProcessed[treeIdx]) continue;
	            int shared = 0;
	            for (int j = i; j < n; j++) {
	                int b = actions[j];
	                if (Action.type(b) != ActionType.CHOP) continue;
	                int jdx = Action.trollIdx(b);
	                if (jdx >= s.trollCount) continue;
	                if ((s.trollCP[jdx] & 0xFF) == 0) continue;
	                if ((s.trollX[jdx] & 0xFF) != tx || (s.trollY[jdx] & 0xFF) != ty) continue;
	                harvestSharedTrolls[shared++] = jdx;
	            }
	            chopTreeProcessed[treeIdx] = true;
	            boolean killed = false;
	            for (int k = 0; k < shared; k++) {
	                int dmg = s.trollCP[harvestSharedTrolls[k]] & 0xFF;
	                int h = (s.treeHealth[treeIdx] & 0xFF) - dmg;
	                if (h <= 0) {
	                    if (!killed) { s.killTreeAt(treeIdx); killed = true; }
	                } else {
	                    s.treeHealth[treeIdx] = (byte) h;
	                }
	            }
	            if (!killed) continue;
	            int size = s.treeSize[treeIdx] & 0xFF;
	            int remaining = size;
	            for (int round = 0; round < size && remaining > 0; round++) {
	                for (int k = 0; k < shared; k++) {
	                    int trollIdx = harvestSharedTrolls[k];
	                    int cc = s.trollCC[trollIdx] & 0xFF;
	                    if (s.trollCarryTotal[trollIdx] >= cc) continue;
	                    s.addToInventory(trollIdx, ResourceType.WOOD, 1);
	                    remaining--;
	                }
	            }
	        }
	    }
	    static void applyPicks(GameState s, int[] actions, int n) {
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.PICK) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            int type = Action.arg1(a);
	            if (type < 0 || type >= ResourceType.COUNT) continue;
	            if (!trollNearOwnShack(s, idx)) continue;
	            int cc = s.trollCC[idx] & 0xFF;
	            if (s.trollCarryTotal[idx] >= cc) continue;
	            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
	            if (s.shackInventory[shackBase + type] <= 0) continue;
	            s.shackInventory[shackBase + type]--;
	            s.addToInventory(idx, type, 1);
	        }
	    }
	    static void applyPlants(GameState s, int[] actions, int n) {
	        for (int i = 0; i < n; i++) plantCellProcessed[i] = false;
	        for (int i = 0; i < n; i++) {
	            if (plantCellProcessed[i]) continue;
	            int a = actions[i];
	            if (Action.type(a) != ActionType.PLANT) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
	            if (GameState.tiles[ty * GameState.width + tx] != TileType.GRASS) continue;
	            if (s.treeIndexAt(tx, ty) >= 0) continue;
	            int firstType = Action.arg1(a);
	            boolean contradictory = false;
	            int sharedCount = 0;
	            int[] sharedIdx = harvestSharedTrolls; // reuse buffer
	            int[] sharedType = plantTypeBuf;
	            for (int j = i; j < n; j++) {
	                int b = actions[j];
	                if (Action.type(b) != ActionType.PLANT) continue;
	                int jdx = Action.trollIdx(b);
	                if (jdx >= s.trollCount) continue;
	                int jx = s.trollX[jdx] & 0xFF, jy = s.trollY[jdx] & 0xFF;
	                if (jx != tx || jy != ty) continue;
	                int jtype = Action.arg1(b);
	                if (s.trollInventory[jdx * ResourceType.COUNT + jtype] <= 0) continue;
	                sharedIdx[sharedCount]  = jdx;
	                sharedType[sharedCount] = jtype;
	                if (jtype != firstType) contradictory = true;
	                sharedCount++;
	                plantCellProcessed[j] = true;
	            }
	            if (sharedCount == 0 || contradictory) continue;
	            int treeType = firstType;
	            s.addTree((byte) treeType, tx, ty, 0, initialPlantHealth(treeType));
	            for (int k = 0; k < sharedCount; k++) {
	                int jdx = sharedIdx[k];
	                int jtype = sharedType[k];
	                s.addToInventory(jdx, jtype, -1);
	            }
	        }
	    }
	    private static int initialPlantHealth(int treeType) {
	        switch (treeType) {
	            case TreeType.PLUM:   return 4;
	            case TreeType.LEMON:  return 4;
	            case TreeType.APPLE:  return 8;
	            case TreeType.BANANA: return 2;
	            default: throw new IllegalStateException();
	        }
	    }
	    static void applyTrains(GameState s, int[] actions, int n) {
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.TRAIN) continue;
	            int ms = Action.trainMS(a);
	            int cc = Action.trainCC(a);
	            int hp = Action.trainHP(a);
	            int cp = Action.trainCP(a);
	            int player = -1;
	            int chosenN = 0;
	            for (int p = 0; p < 2; p++) {
	                int sx = (p == 0) ? GameState.shackMeX : GameState.shackOppX;
	                int sy = (p == 0) ? GameState.shackMeY : GameState.shackOppY;
	                if (s.trollIndexAtCell(sx, sy) >= 0) continue;
	                int nUnits = countOwnTrolls(s, p);
	                if (!canAffordTrainWithCount(s, p, ms, cc, hp, cp, nUnits)) continue;
	                player = p;
	                chosenN = nUnits;
	                break;
	            }
	            if (player < 0) continue;
	            int base = player * ResourceType.COUNT;
	            s.shackInventory[base + ResourceType.PLUM]  -= chosenN + ms * ms;
	            s.shackInventory[base + ResourceType.LEMON] -= chosenN + cc * cc;
	            s.shackInventory[base + ResourceType.APPLE] -= chosenN + hp * hp;
	            s.shackInventory[base + ResourceType.IRON]  -= chosenN + cp * cp;
	            int spawnX = (player == 0) ? GameState.shackMeX : GameState.shackOppX;
	            int spawnY = (player == 0) ? GameState.shackMeY : GameState.shackOppY;
	            s.addTroll(player, spawnX, spawnY, ms, cc, hp, cp);
	        }
	    }
	    private static boolean canAffordTrainWithCount(GameState s, int player, int ms, int cc, int hp, int cp, int n) {
	        int base = player * ResourceType.COUNT;
	        return s.shackInventory[base + ResourceType.PLUM]  >= n + ms * ms
	            && s.shackInventory[base + ResourceType.LEMON] >= n + cc * cc
	            && s.shackInventory[base + ResourceType.APPLE] >= n + hp * hp
	            && s.shackInventory[base + ResourceType.IRON]  >= n + cp * cp;
	    }
	    private static int countOwnTrolls(GameState s, int player) {
	        int c = 0;
	        for (int i = 0; i < s.trollCount; i++) if ((s.trollPlayer[i] & 0xFF) == player) c++;
	        return c;
	    }
	    static void applyDrops(GameState s, int[] actions, int n) {
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.DROP) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            if (!trollNearOwnShack(s, idx)) continue;
	            if (s.trollCarryTotal[idx] == 0) continue;
	            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
	            int invBase = idx * ResourceType.COUNT;
	            for (int r = 0; r < ResourceType.COUNT; r++) {
	                s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
	            }
	            s.clearInventory(idx);
	        }
	    }
	    static boolean trollNearOwnShack(GameState s, int trollIdx) {
	        int player = s.trollPlayer[trollIdx] & 0xFF;
	        int sx = (player == 0) ? GameState.shackMeX  : GameState.shackOppX;
	        int sy = (player == 0) ? GameState.shackMeY  : GameState.shackOppY;
	        int tx = s.trollX[trollIdx] & 0xFF;
	        int ty = s.trollY[trollIdx] & 0xFF;
	        int d  = Math.abs(tx - sx) + Math.abs(ty - sy);
	        return d <= 1;
	    }
	    static void applyMines(GameState s, int[] actions, int n) {
	        for (int i = 0; i < n; i++) {
	            int a = actions[i];
	            if (Action.type(a) != ActionType.MINE) continue;
	            int idx = Action.trollIdx(a);
	            if (idx >= s.trollCount) continue;
	            int cp = s.trollCP[idx] & 0xFF;
	            if (cp == 0) continue;
	            if (!adjacentToIron(s, idx)) continue;
	            int cc = s.trollCC[idx] & 0xFF;
	            int free = cc - s.trollCarryTotal[idx];
	            int gain = Math.min(cp, free);
	            if (gain <= 0) continue;
	            s.addToInventory(idx, ResourceType.IRON, gain);
	        }
	    }
	    static boolean adjacentToIron(GameState s, int trollIdx) {
	        int x = s.trollX[trollIdx] & 0xFF;
	        int y = s.trollY[trollIdx] & 0xFF;
	        return tileAtOrNone(x + 1, y) == TileType.IRON
	            || tileAtOrNone(x - 1, y) == TileType.IRON
	            || tileAtOrNone(x, y + 1) == TileType.IRON
	            || tileAtOrNone(x, y - 1) == TileType.IRON;
	    }
	    static byte tileAtOrNone(int x, int y) {
	        if (x < 0 || x >= GameState.width || y < 0 || y >= GameState.height) return -1;
	        return GameState.tiles[y * GameState.width + x];
	    }
	    static void plantTick(GameState s) {
	        for (int i = 0; i < s.treeCount; i++) {
	            if (s.treeCooldown[i] > 0) s.treeCooldown[i]--;
	            if (s.treeCooldown[i] == 0 && s.treeHealth[i] > 0) {
	                int type = s.treeType[i] & 0xFF;
	                if (s.treeSize[i] < 4) {
	                    s.treeSize[i]++;
	                    s.treeHealth[i] += deltaHealth(type);
	                    s.treeCooldown[i] = growthCooldown(s, i, type);
	                } else if (s.treeFruits[i] < 3) {
	                    s.treeFruits[i]++;
	                    s.treeCooldown[i] = growthCooldown(s, i, type);
	                }
	            }
	        }
	    }
	    private static byte deltaHealth(int treeType) {
	        switch (treeType) {
	            case TreeType.PLUM:   return 2;
	            case TreeType.LEMON:  return 2;
	            case TreeType.APPLE:  return 3;
	            case TreeType.BANANA: return 1;
	            default: throw new IllegalStateException();
	        }
	    }
	    private static byte growthCooldown(GameState s, int treeIdx, int treeType) {
	        int base = TreeType.COOLDOWN_NORMAL[treeType] & 0xFF;
	        int x = s.treeX[treeIdx] & 0xFF;
	        int y = s.treeY[treeIdx] & 0xFF;
	        if (PathTable.isNearWater[y * GameState.width + x]) base -= WATER_BOOST[treeType] & 0xFF;
	        return (byte) base;
	    }
	}
	private static class TrollPolicy {
	    public static final int[]     cursorBuf      = new int[GameState.MAX_TROLLS];
	    public static final boolean[] oppTreeTakenBuf = new boolean[GameState.MAX_TREES];
	    private TrollPolicy() {}
	    public static int fillActions(GameState s, short[] popBuf, byte[] popLen, int idx,
	                                  int[] cursor, int[] outActions) {
	        for (int t = 0; t < s.treeCount; t++) oppTreeTakenBuf[t] = false;
	        int count = 0;
	        for (int trollIdx = 0; trollIdx < s.trollCount; trollIdx++) {
	            int player = s.trollPlayer[trollIdx] & 0xFF;
	            if (player == 0) {
	                outActions[count++] = decideForOwnTroll(s, popBuf, popLen, idx, cursor, trollIdx);
	            } else {
	                outActions[count++] = GreedyAgent.decideForOpponent(s, trollIdx, oppTreeTakenBuf);
	            }
	        }
	        return count;
	    }
	    private static int decideForOwnTroll(GameState s, short[] popBuf, byte[] popLen, int idx,
	                                         int[] cursor, int trollIdx) {
	        int tx = s.trollX[trollIdx] & 0xFF;
	        int ty = s.trollY[trollIdx] & 0xFF;
	        int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
	        if (wood > 0) {
	            if (isShackAdjacent(tx, ty)) return Action.drop(trollIdx);
	            return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
	        }
	        int len = Genome.len(popLen, idx, trollIdx);
	        while (cursor[trollIdx] < len) {
	            short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
	            if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; continue; }
	            int gx = Genome.geneX(g), gy = Genome.geneY(g);
	            if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; continue; }
	            if (tx == gx && ty == gy) return Action.chop(trollIdx);
	            return Action.move(trollIdx, gx, gy);
	        }
	        return Action.wait(trollIdx);
	    }
	    private static boolean isShackAdjacent(int x, int y) {
	        for (int i = 0; i < ShackAdjacency.count; i++) {
	            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
	        }
	        return false;
	    }
	    private static int closestShackAdjX(int x, int y) { return closestShackAdj(x, y, true); }
	    private static int closestShackAdjY(int x, int y) { return closestShackAdj(x, y, false); }
	    private static int closestShackAdj(int x, int y, boolean returnX) {
	        int bestX = ShackAdjacency.x[0] & 0xFF;
	        int bestY = ShackAdjacency.y[0] & 0xFF;
	        int bestDist = PathTable.distance(x, y, bestX, bestY);
	        for (int i = 1; i < ShackAdjacency.count; i++) {
	            int cx = ShackAdjacency.x[i] & 0xFF;
	            int cy = ShackAdjacency.y[i] & 0xFF;
	            int d = PathTable.distance(x, y, cx, cy);
	            if (d < bestDist) { bestDist = d; bestX = cx; bestY = cy; }
	        }
	        return returnX ? bestX : bestY;
	    }
	}
	private static class Selection {
	    private Selection() {}
	    public static int tournament(double[] fit, SplittableRandom rng, int popSize) {
	        int a = rng.nextInt(popSize);
	        int b = rng.nextInt(popSize);
	        return (fit[a] >= fit[b]) ? a : b;
	    }
	}
	private static class Population {
	    public final short[]  bufA = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
	    public final short[]  bufB = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
	    public final byte[]   lenA = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
	    public final byte[]   lenB = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
	    public final double[] fitA = new double[Genome.POP_SIZE];
	    public final double[] fitB = new double[Genome.POP_SIZE];
	    public short[]  cur, nxt;
	    public byte[]   curLen, nxtLen;
	    public double[] curFit, nxtFit;
	    public Population() {
	        java.util.Arrays.fill(bufA, Genome.EMPTY_GENE);
	        java.util.Arrays.fill(bufB, Genome.EMPTY_GENE);
	        cur = bufA; nxt = bufB;
	        curLen = lenA; nxtLen = lenB;
	        curFit = fitA; nxtFit = fitB;
	    }
	    public void swap() {
	        short[] tmp = cur; cur = nxt; nxt = tmp;
	        byte[] tmpLen = curLen; curLen = nxtLen; nxtLen = tmpLen;
	        double[] tmpFit = curFit; curFit = nxtFit; nxtFit = tmpFit;
	    }
	    public void resetCurrent() {
	        java.util.Arrays.fill(cur, Genome.EMPTY_GENE);
	        java.util.Arrays.fill(curLen, (byte) 0);
	        java.util.Arrays.fill(curFit, 0.0);
	    }
	}
	private static class GenomeOps {
	    public static final double P_SKIP_INIT = 0.30;
	    private static final short[] shuffleBuf      = new short[GameState.MAX_TREES];
	    private static final int[]   ownTrollsBuf    = new int[GameState.MAX_TROLLS];
	    private static final int[]   freeTrollsBuf   = new int[GameState.MAX_TROLLS];
	    private GenomeOps() {}
	    public static void initRandom(GameState state, short[] buf, byte[] lenBuf,
	                                  int individuIdx, SplittableRandom rng) {
	        int base = Genome.offset(individuIdx, 0);
	        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) buf[base + k] = Genome.EMPTY_GENE;
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(lenBuf, individuIdx, j, 0);
	        int ownTrollsCount = 0;
	        for (int i = 0; i < state.trollCount; i++) {
	            if ((state.trollPlayer[i] & 0xFF) == 0) ownTrollsBuf[ownTrollsCount++] = i;
	        }
	        if (ownTrollsCount == 0) return;
	        int treeCount = 0;
	        for (int t = 0; t < state.treeCount; t++) {
	            if (state.treeHealth[t] > 0) {
	                shuffleBuf[treeCount++] = Genome.encode(state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
	            }
	        }
	        for (int i = treeCount - 1; i > 0; i--) {
	            int j = rng.nextInt(i + 1);
	            short tmp = shuffleBuf[i]; shuffleBuf[i] = shuffleBuf[j]; shuffleBuf[j] = tmp;
	        }
	        for (int i = 0; i < treeCount; i++) {
	            if (rng.nextDouble() < P_SKIP_INIT) continue;
	            int freeCount = 0;
	            for (int k = 0; k < ownTrollsCount; k++) {
	                int trollIdx = ownTrollsBuf[k];
	                if (Genome.len(lenBuf, individuIdx, trollIdx) < Genome.MAX_TARGETS_PER_TROLL) {
	                    freeTrollsBuf[freeCount++] = trollIdx;
	                }
	            }
	            if (freeCount == 0) break;
	            int chosenTroll = freeTrollsBuf[rng.nextInt(freeCount)];
	            int len = Genome.len(lenBuf, individuIdx, chosenTroll);
	            Genome.setGene(buf, individuIdx, chosenTroll, len, shuffleBuf[i]);
	            Genome.setLen(lenBuf, individuIdx, chosenTroll, len + 1);
	        }
	    }
	    public static final double P_MUT_SWAP_INTRA = 0.40;
	    public static final double P_MUT_SWAP_INTER = 0.30;
	    public static final double P_MUT_REVERSE    = 0.20;
	    public static final double P_MUT_DELETE     = 0.10;
	    public static final int MUT_SWAP_INTRA = 0;
	    public static final int MUT_SWAP_INTER = 1;
	    public static final int MUT_REVERSE    = 2;
	    public static final int MUT_DELETE     = 3;
	    public static int pickMutationKind(SplittableRandom rng) {
	        double r = rng.nextDouble();
	        if (r < P_MUT_SWAP_INTRA) return MUT_SWAP_INTRA;
	        r -= P_MUT_SWAP_INTRA;
	        if (r < P_MUT_SWAP_INTER) return MUT_SWAP_INTER;
	        r -= P_MUT_SWAP_INTER;
	        if (r < P_MUT_REVERSE) return MUT_REVERSE;
	        return MUT_DELETE;
	    }
	    public static void runMutation(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
	        switch (pickMutationKind(rng)) {
	            case MUT_SWAP_INTRA -> mutateSwapIntra(buf, lenBuf, individuIdx, rng);
	            case MUT_SWAP_INTER -> mutateSwapInter(buf, lenBuf, individuIdx, rng);
	            case MUT_REVERSE    -> mutateReverse  (buf, lenBuf, individuIdx, rng);
	            case MUT_DELETE     -> mutateDelete   (buf, lenBuf, individuIdx, rng);
	            default -> throw new IllegalStateException();
	        }
	    }
	    private static int pickTrollWithLen(byte[] lenBuf, int individuIdx, int minLen, SplittableRandom rng) {
	        int count = 0;
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
	            if (Genome.len(lenBuf, individuIdx, j) >= minLen) freeTrollsBuf[count++] = j;
	        }
	        if (count == 0) return -1;
	        return freeTrollsBuf[rng.nextInt(count)];
	    }
	    public static void mutateSwapIntra(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
	        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
	        if (j < 0) return;
	        int len = Genome.len(lenBuf, individuIdx, j);
	        int k1 = rng.nextInt(len);
	        int k2 = rng.nextInt(len);
	        if (k1 == k2) return;
	        int base = Genome.offset(individuIdx, j);
	        short tmp = buf[base + k1]; buf[base + k1] = buf[base + k2]; buf[base + k2] = tmp;
	    }
	    public static void mutateSwapInter(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
	        int j1 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
	        if (j1 < 0) return;
	        int j2 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
	        if (j2 < 0 || j1 == j2) return;
	        int len1 = Genome.len(lenBuf, individuIdx, j1);
	        int len2 = Genome.len(lenBuf, individuIdx, j2);
	        int k1 = rng.nextInt(len1);
	        int k2 = rng.nextInt(len2);
	        int b1 = Genome.offset(individuIdx, j1);
	        int b2 = Genome.offset(individuIdx, j2);
	        short tmp = buf[b1 + k1]; buf[b1 + k1] = buf[b2 + k2]; buf[b2 + k2] = tmp;
	    }
	    public static void mutateReverse(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
	        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
	        if (j < 0) return;
	        int len = Genome.len(lenBuf, individuIdx, j);
	        int a = rng.nextInt(len);
	        int b = rng.nextInt(len);
	        if (a == b) return;
	        if (a > b) { int t = a; a = b; b = t; }
	        int base = Genome.offset(individuIdx, j);
	        while (a < b) {
	            short tmp = buf[base + a]; buf[base + a] = buf[base + b]; buf[base + b] = tmp;
	            a++; b--;
	        }
	    }
	    public static void mutateDelete(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
	        int j = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
	        if (j < 0) return;
	        int len = Genome.len(lenBuf, individuIdx, j);
	        int k = rng.nextInt(len);
	        int base = Genome.offset(individuIdx, j);
	        for (int i = k; i < len - 1; i++) buf[base + i] = buf[base + i + 1];
	        buf[base + len - 1] = Genome.EMPTY_GENE;
	        Genome.setLen(lenBuf, individuIdx, j, len - 1);
	    }
	    private static final boolean[] seenBuf = new boolean[256 * 256]; // max grid 256x256
	    public static void crossover(short[] srcA, byte[] lenA, int idxA,
	                                 short[] srcB, byte[] lenB, int idxB,
	                                 short[] dst,  byte[] dstLen, int idxDst,
	                                 SplittableRandom rng) {
	        int W = GameState.width;
	        int H = GameState.height;
	        int dstBase = Genome.offset(idxDst, 0);
	        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) dst[dstBase + k] = Genome.EMPTY_GENE;
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(dstLen, idxDst, j, 0);
	        for (int y = 0; y < H; y++) {
	            for (int x = 0; x < W; x++) seenBuf[y * W + x] = false;
	        }
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
	            int la = Genome.len(lenA, idxA, j);
	            int lb = Genome.len(lenB, idxB, j);
	            if (la == 0 && lb == 0) continue;
	            int cut = (la > 0) ? rng.nextInt(la + 1) : 0;
	            int dstOff = Genome.offset(idxDst, j);
	            int written = 0;
	            int aBase = Genome.offset(idxA, j);
	            for (int k = 0; k < cut; k++) {
	                short g = srcA[aBase + k];
	                int x = Genome.geneX(g), y = Genome.geneY(g);
	                int cell = y * W + x;
	                if (seenBuf[cell]) continue; // ne devrait pas arriver si parent valide, défensif
	                seenBuf[cell] = true;
	                dst[dstOff + written++] = g;
	            }
	            int bBase = Genome.offset(idxB, j);
	            int target = (la > 0 ? la : lb);
	            for (int k = 0; k < lb && written < target && written < Genome.MAX_TARGETS_PER_TROLL; k++) {
	                short g = srcB[bBase + k];
	                int x = Genome.geneX(g), y = Genome.geneY(g);
	                int cell = y * W + x;
	                if (seenBuf[cell]) continue;
	                seenBuf[cell] = true;
	                dst[dstOff + written++] = g;
	            }
	            Genome.setLen(dstLen, idxDst, j, written);
	        }
	    }
	}
	private static class GenomeInvariants {
	    private GenomeInvariants() {}
	    public static boolean check(short[] buf, byte[] lenBuf, int individuIdx) {
	        int W = GameState.width;
	        int H = GameState.height;
	        boolean[] seen = new boolean[W * H];
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
	            int len = Genome.len(lenBuf, individuIdx, j);
	            if (len < 0 || len > Genome.MAX_TARGETS_PER_TROLL) {
	                throw new AssertionError("len out of range troll=" + j + " len=" + len);
	            }
	            for (int k = 0; k < len; k++) {
	                short g = (short) Genome.gene(buf, individuIdx, j, k);
	                if (g == Genome.EMPTY_GENE) {
	                    throw new AssertionError("active slot is EMPTY troll=" + j + " k=" + k);
	                }
	                int x = Genome.geneX(g);
	                int y = Genome.geneY(g);
	                if (x < 0 || x >= W || y < 0 || y >= H) {
	                    throw new AssertionError("gene out of bounds (" + x + "," + y + ")");
	                }
	                int idx = y * W + x;
	                if (seen[idx]) {
	                    throw new AssertionError("duplicate gene (" + x + "," + y + ")");
	                }
	                seen[idx] = true;
	            }
	            for (int k = len; k < Genome.MAX_TARGETS_PER_TROLL; k++) {
	                if (Genome.gene(buf, individuIdx, j, k) != Genome.EMPTY_GENE) {
	                    throw new AssertionError("dirty slot after len troll=" + j + " k=" + k);
	                }
	            }
	        }
	        return true;
	    }
	}
	private static class GenomeEvaluator {
	    public static final int    HORIZON          = 25;
	    public static final double ALPHA_WOOD_CARRY = 2.0;
	    private GenomeEvaluator() {}
	    public static double evaluate(GameState scratch, GameState source,
	                                  short[] popBuf, byte[] popLen, int idx,
	                                  int[] actionBuf) {
	        scratch.copyFrom(source);
	        int[] cursor = TrollPolicy.cursorBuf;
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
	        for (int t = 0; t < HORIZON; t++) {
	            int n = TrollPolicy.fillActions(scratch, popBuf, popLen, idx, cursor, actionBuf);
	            Simulator.tick(scratch, actionBuf, n);
	        }
	        return fitness(scratch);
	    }
	    private static double fitness(GameState finalState) {
	        int scoreMe  = finalState.score(0);
	        int scoreOpp = finalState.score(1);
	        int woodCarryMe = 0;
	        for (int i = 0; i < finalState.trollCount; i++) {
	            if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
	            woodCarryMe += finalState.trollInventory[i * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
	        }
	        return (scoreMe - scoreOpp) + ALPHA_WOOD_CARRY * woodCarryMe;
	    }
	}
	private static class Genome {
	    public static final int POP_SIZE = 15;
	    public static final int MAX_TARGETS_PER_TROLL = 10;
	    public static final int SLOTS_PER_GENOME = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL;
	    public static final short EMPTY_GENE = -1;
	    private Genome() {
	    }
	    public static short encode(int x, int y) {
	        return (short) (((x & 0xFF) << 8) | (y & 0xFF));
	    }
	    public static int geneX(short g) {
	        return (g >>> 8) & 0xFF;
	    }
	    public static int geneY(short g) {
	        return g & 0xFF;
	    }
	    public static int offset(int individuIdx, int trollIdx) {
	        return individuIdx * SLOTS_PER_GENOME + trollIdx * MAX_TARGETS_PER_TROLL;
	    }
	    public static int lenOffset(int individuIdx, int trollIdx) {
	        return individuIdx * GameState.MAX_TROLLS + trollIdx;
	    }
	    public static int len(byte[] lenBuf, int individuIdx, int trollIdx) {
	        return lenBuf[lenOffset(individuIdx, trollIdx)] & 0xFF;
	    }
	    public static void setLen(byte[] lenBuf, int individuIdx, int trollIdx, int value) {
	        lenBuf[lenOffset(individuIdx, trollIdx)] = (byte) value;
	    }
	    public static int gene(short[] buf, int individuIdx, int trollIdx, int k) {
	        return buf[offset(individuIdx, trollIdx) + k];
	    }
	    public static void setGene(short[] buf, int individuIdx, int trollIdx, int k, short value) {
	        buf[offset(individuIdx, trollIdx) + k] = value;
	    }
	}
	private static class GeneticAgent {
	    public static final long TURN_BUDGET_NS = 45_000_000L;
	    public static final long INIT_BUDGET_NS = 900_000_000L;
	    public static final double P_CROSSOVER = 0.70;
	    private final GameState scratch = new GameState();
	    private final Population pop = new Population();
	    private final SplittableRandom rng = new SplittableRandom();
	    private final int[] evalActionBuf = new int[GameState.MAX_TROLLS + 1];
	    private int lastGenCount;
	    private double lastBestFitness;
	    private int lastBestIdx;
	    public GeneticAgent() {
	    }
	    private static void copyIndividu(short[] srcBuf, byte[] srcLen, int srcIdx,
	                                     short[] dstBuf, byte[] dstLen, int dstIdx) {
	        System.arraycopy(srcBuf, Genome.offset(srcIdx, 0),
	                dstBuf, Genome.offset(dstIdx, 0), Genome.SLOTS_PER_GENOME);
	        System.arraycopy(srcLen, Genome.lenOffset(srcIdx, 0),
	                dstLen, Genome.lenOffset(dstIdx, 0), GameState.MAX_TROLLS);
	    }
	    private static int argmax(double[] arr) {
	        int best = 0;
	        double bestV = arr[0];
	        for (int i = 1; i < arr.length; i++) {
	            if (arr[i] > bestV) {
	                bestV = arr[i];
	                best = i;
	            }
	        }
	        return best;
	    }
	    public int decide(GameState state, long deadlineNs, int[] outActions) {
	        initPopulation(state);
	        evaluatePopulation(state);
	        lastGenCount = 0;
	        while (System.nanoTime() < deadlineNs) {
	            stepGeneration(state);
	            lastGenCount++;
	            if (System.nanoTime() >= deadlineNs) break;
	        }
	        lastBestIdx = argmax(pop.curFit);
	        lastBestFitness = pop.curFit[lastBestIdx];
	        int[] cursor = TrollPolicy.cursorBuf;
	        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
	        int n = TrollPolicy.fillActions(state, pop.cur, pop.curLen, lastBestIdx, cursor, outActions);
	        if (state.turn == 0) {
	            int trainAction = GreedyAgent.maybeTrain(state);
	            if (trainAction != -1) {
	                System.arraycopy(outActions, 0, outActions, 1, n);
	                outActions[0] = trainAction;
	                n++;
	            }
	        }
	        return n;
	    }
	    public int lastGenCount() {
	        return lastGenCount;
	    }
	    public double lastBestFitness() {
	        return lastBestFitness;
	    }
	    private void initPopulation(GameState state) {
	        for (int i = 0; i < Genome.POP_SIZE; i++) {
	            GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
	        }
	    }
	    private void evaluatePopulation(GameState state) {
	        for (int i = 0; i < Genome.POP_SIZE; i++) {
	            pop.curFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.cur, pop.curLen, i, evalActionBuf);
	        }
	    }
	    private void stepGeneration(GameState state) {
	        int bestIdx = argmax(pop.curFit);
	        copyIndividu(pop.cur, pop.curLen, bestIdx, pop.nxt, pop.nxtLen, 0);
	        pop.nxtFit[0] = pop.curFit[bestIdx];
	        for (int i = 1; i < Genome.POP_SIZE; i++) {
	            if (rng.nextDouble() < P_CROSSOVER) {
	                int p1 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
	                int p2 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
	                GenomeOps.crossover(pop.cur, pop.curLen, p1, pop.cur, pop.curLen, p2,
	                        pop.nxt, pop.nxtLen, i, rng);
	            } else {
	                int p = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
	                copyIndividu(pop.cur, pop.curLen, p, pop.nxt, pop.nxtLen, i);
	                GenomeOps.runMutation(pop.nxt, pop.nxtLen, i, rng);
	            }
	            pop.nxtFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.nxt, pop.nxtLen, i, evalActionBuf);
	        }
	        pop.swap();
	    }
	}
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        ShackAdjacency.init();
        GameState state    = new GameState();
        GeneticAgent agent = new GeneticAgent();
        int[] actionBuf    = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb   = new StringBuilder();
        boolean firstTurn = true;
        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);
            long deadline = start + (firstTurn ? GeneticAgent.INIT_BUDGET_NS
                                               : GeneticAgent.TURN_BUDGET_NS);
            int n = agent.decide(state, deadline, actionBuf);
            firstTurn = false;
            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            sb.append(";MSG GA gen=").append(agent.lastGenCount())
              .append(" fit=").append((int) agent.lastBestFitness())
              .append(" t=").append((System.nanoTime() - start) / 1_000_000).append("ms");
            System.out.println(sb);
            state.turn++;
        }
    }
}
