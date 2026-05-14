import java.util.Scanner;
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
	        trollCount = src.trollCount;
	        System.arraycopy(src.trollId,        0, trollId,        0, MAX_TROLLS);
	        System.arraycopy(src.trollPlayer,    0, trollPlayer,    0, MAX_TROLLS);
	        System.arraycopy(src.trollX,         0, trollX,         0, MAX_TROLLS);
	        System.arraycopy(src.trollY,         0, trollY,         0, MAX_TROLLS);
	        System.arraycopy(src.trollMS,        0, trollMS,        0, MAX_TROLLS);
	        System.arraycopy(src.trollCC,        0, trollCC,        0, MAX_TROLLS);
	        System.arraycopy(src.trollHP,        0, trollHP,        0, MAX_TROLLS);
	        System.arraycopy(src.trollCP,        0, trollCP,        0, MAX_TROLLS);
	        System.arraycopy(src.trollInventory, 0, trollInventory, 0, MAX_TROLLS * ResourceType.COUNT);
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
	    public int trollIndexById(int id) {
	        for (int i = 0; i < trollCount; i++) {
	            if ((trollId[i] & 0xFF) == id) return i;
	        }
	        return -1;
	    }
	    public int treeIndexAt(int x, int y) {
	        for (int i = 0; i < treeCount; i++) {
	            if (treeX[i] == (byte) x && treeY[i] == (byte) y) return i;
	        }
	        return -1;
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
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        ShackAdjacency.init();
        GameState state = new GameState();
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb = new StringBuilder();
        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);
            int n = GreedyAgent.decide(state, actionBuf);
            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            sb.append(";MSG turn=")
                    .append(state.turn)
                    .append(" elapsed=")
                    .append((System.nanoTime() - start) / 1_000_000)
                    .append("ms");
            System.out.println(sb);
            state.turn++;
        }
    }
}
