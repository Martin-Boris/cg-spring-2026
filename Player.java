import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.Scanner;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Random;
import java.util.HashMap;
class Player {
	private static class Troll {
	    public int id;
	    public int player;
	    public int x;
	    public int y;
	    public int movementSpeed;
	    public int carryCapacity;
	    public int harvestPower;
	    public int chopPower;
	    public int[] carry = new int[ResourceType.COUNT];
	    public Troll() {
	    }
	    public Troll copy() {
	        Troll c = new Troll();
	        c.id = this.id;
	        c.player = this.player;
	        c.x = this.x;
	        c.y = this.y;
	        c.movementSpeed = this.movementSpeed;
	        c.carryCapacity = this.carryCapacity;
	        c.harvestPower = this.harvestPower;
	        c.chopPower = this.chopPower;
	        c.carry = this.carry.clone();
	        return c;
	    }
	    public int carryTotal() {
	        int sum = 0;
	        for (int v : carry) {
	            sum += v;
	        }
	        return sum;
	    }
	}
	private static enum TreeType {
	    PLUM,
	    LEMON,
	    APPLE,
	    BANANA;
	    private static final TreeType[] VALUES = values();
	    public static TreeType parse(String s) {
	        return switch (s) {
	            case "PLUM" -> PLUM;
	            case "LEMON" -> LEMON;
	            case "APPLE" -> APPLE;
	            case "BANANA" -> BANANA;
	            default -> throw new IllegalArgumentException("Unknown tree type: " + s);
	        };
	    }
	    public static TreeType byOrdinal(int ordinal) {
	        return VALUES[ordinal];
	    }
	    public int normalCooldown() {
	        return switch (this) {
	            case PLUM, LEMON -> 8;
	            case APPLE -> 9;
	            case BANANA -> 6;
	        };
	    }
	    public int waterCooldown() {
	        return switch (this) {
	            case PLUM, LEMON -> 3;
	            case APPLE -> 2;
	            case BANANA -> 4;
	        };
	    }
	    public int healthForSize(int size) {
	        return switch (this) {
	            case PLUM, LEMON -> switch (size) {
	                case 1 -> 6;
	                case 2 -> 8;
	                case 3 -> 10;
	                case 4 -> 12;
	                default -> throw new IllegalArgumentException("Invalid size: " + size);
	            };
	            case APPLE -> switch (size) {
	                case 1 -> 11;
	                case 2 -> 14;
	                case 3 -> 17;
	                case 4 -> 20;
	                default -> throw new IllegalArgumentException("Invalid size: " + size);
	            };
	            case BANANA -> switch (size) {
	                case 1 -> 3;
	                case 2 -> 4;
	                case 3 -> 5;
	                case 4 -> 6;
	                default -> throw new IllegalArgumentException("Invalid size: " + size);
	            };
	        };
	    }
	    public ResourceType fruit() {
	        return switch (this) {
	            case PLUM -> ResourceType.PLUM;
	            case LEMON -> ResourceType.LEMON;
	            case APPLE -> ResourceType.APPLE;
	            case BANANA -> ResourceType.BANANA;
	        };
	    }
	}
	private static class Tree {
	    public TreeType type;
	    public int x;
	    public int y;
	    public int size;
	    public int health;
	    public int fruits;
	    public int cooldown;
	    public Tree() {
	    }
	    public Tree copy() {
	        Tree c = new Tree();
	        c.type = this.type;
	        c.x = this.x;
	        c.y = this.y;
	        c.size = this.size;
	        c.health = this.health;
	        c.fruits = this.fruits;
	        c.cooldown = this.cooldown;
	        return c;
	    }
	}
	private static enum Tile {
	    GRASS,
	    WATER,
	    ROCK,
	    IRON,
	    SHACK_ME,
	    SHACK_OPP;
	    private static final Tile[] VALUES = values();
	    public static Tile from(char c) {
	        return switch (c) {
	            case '.' -> GRASS;
	            case '~' -> WATER;
	            case '#' -> ROCK;
	            case '+' -> IRON;
	            case '0' -> SHACK_ME;
	            case '1' -> SHACK_OPP;
	            default -> throw new IllegalArgumentException("Unknown tile char: " + c);
	        };
	    }
	    public static Tile byOrdinal(int ordinal) {
	        return VALUES[ordinal];
	    }
	    public boolean isWalkable() {
	        return this == GRASS;
	    }
	}
	private static class GameState {
	    public int width;
	    public int height;
	    public byte[] grid = new byte[0];
	    public int myShackX;
	    public int myShackY;
	    public int oppShackX;
	    public int oppShackY;
	    public int[] myShackInv = new int[ResourceType.COUNT];
	    public int[] oppShackInv = new int[ResourceType.COUNT];
	    public List<Tree> trees = new ArrayList<>();
	    public List<Troll> trolls = new ArrayList<>();
	    public int turn;
	    public GameState() {
	    }
	    public static void readInit(Scanner in, GameState state) {
	        state.width = in.nextInt();
	        state.height = in.nextInt();
	        if (in.hasNextLine()) {
	            in.nextLine();
	        }
	        state.grid = new byte[state.width * state.height];
	        for (int y = 0; y < state.height; y++) {
	            String line = in.nextLine();
	            for (int x = 0; x < state.width; x++) {
	                Tile t = Tile.from(line.charAt(x));
	                state.grid[y * state.width + x] = (byte) t.ordinal();
	                if (t == Tile.SHACK_ME) {
	                    state.myShackX = x;
	                    state.myShackY = y;
	                } else if (t == Tile.SHACK_OPP) {
	                    state.oppShackX = x;
	                    state.oppShackY = y;
	                }
	            }
	        }
	    }
	    public static void readTurn(Scanner in, GameState state) {
	        state.turn++;
	        for (int i = 0; i < ResourceType.COUNT; i++) {
	            state.myShackInv[i] = in.nextInt();
	        }
	        for (int i = 0; i < ResourceType.COUNT; i++) {
	            state.oppShackInv[i] = in.nextInt();
	        }
	        int treeCount = in.nextInt();
	        state.trees.clear();
	        for (int i = 0; i < treeCount; i++) {
	            Tree t = new Tree();
	            t.type = TreeType.parse(in.next());
	            t.x = in.nextInt();
	            t.y = in.nextInt();
	            t.size = in.nextInt();
	            t.health = in.nextInt();
	            t.fruits = in.nextInt();
	            t.cooldown = in.nextInt();
	            state.trees.add(t);
	        }
	        int trollsCount = in.nextInt();
	        state.trolls.clear();
	        for (int i = 0; i < trollsCount; i++) {
	            Troll tr = new Troll();
	            tr.id = in.nextInt();
	            tr.player = in.nextInt();
	            tr.x = in.nextInt();
	            tr.y = in.nextInt();
	            tr.movementSpeed = in.nextInt();
	            tr.carryCapacity = in.nextInt();
	            tr.harvestPower = in.nextInt();
	            tr.chopPower = in.nextInt();
	            for (int k = 0; k < ResourceType.COUNT; k++) {
	                tr.carry[k] = in.nextInt();
	            }
	            state.trolls.add(tr);
	        }
	    }
	    public Tile tileAt(int x, int y) {
	        return Tile.byOrdinal(grid[y * width + x]);
	    }
	    public boolean walkable(int x, int y) {
	        if (x < 0 || y < 0 || x >= width || y >= height) {
	            return false;
	        }
	        return tileAt(x, y).isWalkable();
	    }
	    public Troll trollById(int id) {
	        for (Troll t : trolls) {
	            if (t.id == id) {
	                return t;
	            }
	        }
	        return null;
	    }
	    public GameState copy() {
	        GameState c = new GameState();
	        c.width = this.width;
	        c.height = this.height;
	        c.grid = this.grid.clone();
	        c.myShackX = this.myShackX;
	        c.myShackY = this.myShackY;
	        c.oppShackX = this.oppShackX;
	        c.oppShackY = this.oppShackY;
	        c.myShackInv = this.myShackInv.clone();
	        c.oppShackInv = this.oppShackInv.clone();
	        c.trees = new ArrayList<>(this.trees.size());
	        for (Tree t : this.trees) {
	            c.trees.add(t.copy());
	        }
	        c.trolls = new ArrayList<>(this.trolls.size());
	        for (Troll tr : this.trolls) {
	            c.trolls.add(tr.copy());
	        }
	        c.turn = this.turn;
	        return c;
	    }
	}
	private static enum ResourceType {
	    PLUM,
	    LEMON,
	    APPLE,
	    BANANA,
	    IRON,
	    WOOD;
	    private static final ResourceType[] VALUES = values();
	    public static final int COUNT = VALUES.length;
	    public static ResourceType byOrdinal(int ordinal) {
	        return VALUES[ordinal];
	    }
	    public int scorePerUnit() {
	        return switch (this) {
	            case PLUM, LEMON, APPLE, BANANA -> 1;
	            case WOOD -> 4;
	            case IRON -> 0;
	        };
	    }
	}
	private static interface Action {
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
	private static enum Zone {
	    MINE,
	    OPP,
	    NEUTRAL,
	    UNREACHABLE
	}
	private static class TreeZoning {
	    private static final int INF = Integer.MAX_VALUE;
	    private final int width;
	    private final int height;
	    private final Zone[] zones;
	    private TreeZoning(int width, int height, Zone[] zones) {
	        this.width = width;
	        this.height = height;
	        this.zones = zones;
	    }
	    public static TreeZoning precompute(GameState state) {
	        int w = state.width;
	        int h = state.height;
	        int[] distMine = bfsFromShack(state, state.myShackX, state.myShackY);
	        int[] distOpp = bfsFromShack(state, state.oppShackX, state.oppShackY);
	        Zone[] zones = new Zone[w * h];
	        for (int i = 0; i < zones.length; i++) {
	            int dm = distMine[i];
	            int dp = distOpp[i];
	            if (dm == INF && dp == INF) {
	                zones[i] = Zone.UNREACHABLE;
	            } else if (dm < dp) {
	                zones[i] = Zone.MINE;
	            } else if (dm > dp) {
	                zones[i] = Zone.OPP;
	            } else {
	                zones[i] = Zone.NEUTRAL;
	            }
	        }
	        return new TreeZoning(w, h, zones);
	    }
	    private static int[] bfsFromShack(GameState state, int shackX, int shackY) {
	        int w = state.width;
	        int h = state.height;
	        int[] dist = new int[w * h];
	        for (int i = 0; i < dist.length; i++) {
	            dist[i] = INF;
	        }
	        Deque<int[]> queue = new ArrayDeque<>();
	        int[] dx = {1, -1, 0, 0};
	        int[] dy = {0, 0, 1, -1};
	        for (int k = 0; k < 4; k++) {
	            int nx = shackX + dx[k];
	            int ny = shackY + dy[k];
	            if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
	                dist[ny * w + nx] = 1;
	                queue.add(new int[]{nx, ny});
	            }
	        }
	        while (!queue.isEmpty()) {
	            int[] cur = queue.poll();
	            int cx = cur[0];
	            int cy = cur[1];
	            int cd = dist[cy * w + cx];
	            for (int k = 0; k < 4; k++) {
	                int nx = cx + dx[k];
	                int ny = cy + dy[k];
	                if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
	                    dist[ny * w + nx] = cd + 1;
	                    queue.add(new int[]{nx, ny});
	                }
	            }
	        }
	        return dist;
	    }
	    public Zone zoneOf(int x, int y) {
	        return zones[y * width + x];
	    }
	}
	private static class TrainPlanner {
	    public static final int V_MAX = 4;
	    private TrainPlanner() {
	    }
	    public static Action.Train plan(GameState state) {
	        if (state.turn != 1) {
	            return null;
	        }
	        int n = countMyTrolls(state);
	        int plums = state.myShackInv[ResourceType.PLUM.ordinal()];
	        int lemons = state.myShackInv[ResourceType.LEMON.ordinal()];
	        int iron = state.myShackInv[ResourceType.IRON.ordinal()];
	        for (int v = V_MAX; v >= 1; v--) {
	            int cost = n + v * v;
	            if (plums >= cost && lemons >= cost && iron >= cost) {
	                return new Action.Train(v, v, 0, v);
	            }
	        }
	        for (int v = V_MAX; v >= 1; v--) {
	            int cost = n + v * v;
	            if (plums >= cost && lemons >= cost) {
	                return new Action.Train(v, v, 0, 0);
	            }
	        }
	        return null;
	    }
	    private static int countMyTrolls(GameState state) {
	        int n = 0;
	        for (var t : state.trolls) {
	            if (t.player == 0) {
	                n++;
	            }
	        }
	        return n;
	    }
	}
	private static class TargetSelector {
	    private TargetSelector() {
	    }
	    public static Tree pickTree(Troll troll, Role role, GameState state,
	                                Set<Long> assignedTrees, TreeZoning zoning) {
	        if (state.trees.isEmpty()) {
	            return null;
	        }
	        List<Tree> candidates = new ArrayList<>(state.trees.size());
	        boolean lastTree = state.trees.size() == 1;
	        for (Tree tree : state.trees) {
	            if (lastTree || !assignedTrees.contains(key(tree, state.width))) {
	                candidates.add(tree);
	            }
	        }
	        if (candidates.isEmpty()) {
	            return null;
	        }
	        List<Tree> zoned;
	        if (role == Role.LEADER) {
	            zoned = filterByZones(candidates, zoning, Zone.OPP);
	            if (zoned.isEmpty()) {
	                zoned = filterByZones(candidates, zoning, Zone.OPP, Zone.NEUTRAL);
	            }
	            if (zoned.isEmpty()) {
	                zoned = candidates;
	            }
	        } else {
	            zoned = filterByZones(candidates, zoning, Zone.MINE, Zone.NEUTRAL);
	            if (zoned.isEmpty()) {
	                zoned = candidates;
	            }
	        }
	        if (lastTree) {
	            zoned = candidates;
	        }
	        List<Tree> matures = new ArrayList<>(zoned.size());
	        for (Tree tree : zoned) {
	            if (tree.size == 4) {
	                matures.add(tree);
	            }
	        }
	        List<Tree> pool = matures.isEmpty() ? zoned : matures;
	        Tree best = pool.get(0);
	        int bestDist = manhattan(troll, best);
	        for (int i = 1; i < pool.size(); i++) {
	            Tree cur = pool.get(i);
	            int d = manhattan(troll, cur);
	            if (d < bestDist
	                    || (d == bestDist && cur.y < best.y)
	                    || (d == bestDist && cur.y == best.y && cur.x < best.x)) {
	                best = cur;
	                bestDist = d;
	            }
	        }
	        assignedTrees.add(key(best, state.width));
	        return best;
	    }
	    private static List<Tree> filterByZones(List<Tree> candidates, TreeZoning zoning, Zone... allowed) {
	        List<Tree> out = new ArrayList<>();
	        for (Tree tree : candidates) {
	            Zone z = zoning.zoneOf(tree.x, tree.y);
	            for (Zone a : allowed) {
	                if (z == a) {
	                    out.add(tree);
	                    break;
	                }
	            }
	        }
	        return out;
	    }
	    private static int manhattan(Troll troll, Tree tree) {
	        return Math.abs(troll.x - tree.x) + Math.abs(troll.y - tree.y);
	    }
	    private static long key(Tree tree, int width) {
	        return (long) tree.y * width + tree.x;
	    }
	}
	private static class RoleAssigner {
	    private RoleAssigner() {
	    }
	    public static Map<Integer, Role> assign(List<Troll> myTrolls) {
	        Map<Integer, Role> result = new HashMap<>();
	        if (myTrolls.isEmpty()) {
	            return result;
	        }
	        if (myTrolls.size() == 1) {
	            result.put(myTrolls.get(0).id, Role.LOCAL);
	            return result;
	        }
	        Troll leader = myTrolls.get(0);
	        int leaderScore = score(leader);
	        for (int i = 1; i < myTrolls.size(); i++) {
	            Troll t = myTrolls.get(i);
	            int s = score(t);
	            if (s > leaderScore || (s == leaderScore && t.id < leader.id)) {
	                leader = t;
	                leaderScore = s;
	            }
	        }
	        for (Troll t : myTrolls) {
	            result.put(t.id, t == leader ? Role.LEADER : Role.LOCAL);
	        }
	        return result;
	    }
	    private static int score(Troll t) {
	        return t.movementSpeed + t.carryCapacity + t.chopPower;
	    }
	}
	private static enum Role {
	    LEADER,
	    LOCAL
	}
	private static class RandomWalk {
	    private static final int RADIUS = 5;
	    private static final int ATTEMPTS = 16;
	    private RandomWalk() {
	    }
	    public static Action.Move pick(Troll troll, GameState state) {
	        Random rng = new Random((long) state.turn * 1000L + troll.id);
	        for (int i = 0; i < ATTEMPTS; i++) {
	            int dx = rng.nextInt(2 * RADIUS + 1) - RADIUS;
	            int dy = rng.nextInt(2 * RADIUS + 1) - RADIUS;
	            int tx = clamp(troll.x + dx, 0, state.width - 1);
	            int ty = clamp(troll.y + dy, 0, state.height - 1);
	            if ((tx != troll.x || ty != troll.y) && state.walkable(tx, ty)) {
	                return new Action.Move(troll.id, tx, ty);
	            }
	        }
	        return new Action.Move(troll.id, troll.x, troll.y);
	    }
	    private static int clamp(int v, int lo, int hi) {
	        return v < lo ? lo : (v > hi ? hi : v);
	    }
	}
	private static class GreedyAi {
	    private TreeZoning zoning;
	    public List<Action> decide(GameState state) {
	        if (zoning == null) {
	            zoning = TreeZoning.precompute(state);
	        }
	        List<Troll> myTrolls = new ArrayList<>();
	        for (Troll t : state.trolls) {
	            if (t.player == 0) {
	                myTrolls.add(t);
	            }
	        }
	        Action.Train trainAction = TrainPlanner.plan(state);
	        Map<Integer, Role> roles = RoleAssigner.assign(myTrolls);
	        myTrolls.sort(Comparator.<Troll>comparingInt(t -> roles.get(t.id) == Role.LEADER ? 0 : 1)
	                .thenComparingInt(t -> t.id));
	        Set<Long> assignedTrees = new HashSet<>();
	        List<Action> actions = new ArrayList<>(myTrolls.size() + 1);
	        for (Troll t : myTrolls) {
	            actions.add(decideForTroll(t, roles.get(t.id), state, assignedTrees));
	        }
	        if (trainAction != null) {
	            actions.add(trainAction);
	        }
	        return actions;
	    }
	    private Action decideForTroll(Troll troll, Role role, GameState state, Set<Long> assignedTrees) {
	        if (troll.carryTotal() >= troll.carryCapacity && troll.carryCapacity > 0) {
	            if (adjacentToMyShack(troll, state)) {
	                return new Action.Drop(troll.id);
	            }
	            return moveTowardShack(troll, state);
	        }
	        Tree target = TargetSelector.pickTree(troll, role, state, assignedTrees, zoning);
	        if (target == null) {
	            return RandomWalk.pick(troll, state);
	        }
	        if (troll.x == target.x && troll.y == target.y) {
	            return new Action.Chop(troll.id);
	        }
	        return new Action.Move(troll.id, target.x, target.y);
	    }
	    private static boolean adjacentToMyShack(Troll troll, GameState state) {
	        return Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) == 1;
	    }
	    private static Action.Move moveTowardShack(Troll troll, GameState state) {
	        int sx = state.myShackX;
	        int sy = state.myShackY;
	        int[] dx = {1, -1, 0, 0};
	        int[] dy = {0, 0, 1, -1};
	        int bestX = troll.x;
	        int bestY = troll.y;
	        int bestDist = Integer.MAX_VALUE;
	        for (int k = 0; k < 4; k++) {
	            int nx = sx + dx[k];
	            int ny = sy + dy[k];
	            if (!state.walkable(nx, ny)) {
	                continue;
	            }
	            int d = Math.abs(nx - troll.x) + Math.abs(ny - troll.y);
	            if (d < bestDist || (d == bestDist && ny < bestY) || (d == bestDist && ny == bestY && nx < bestX)) {
	                bestDist = d;
	                bestX = nx;
	                bestY = ny;
	            }
	        }
	        return new Action.Move(troll.id, bestX, bestY);
	    }
	}
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);
        GreedyAi ai = new GreedyAi();
        while (true) {
            GameState.readTurn(in, state);
            List<Action> actions = ai.decide(state);
            String output;
            if (actions.isEmpty()) {
                output = new Action.Msg("idle").toCommand();
            } else {
                output = actions.stream()
                        .map(Action::toCommand)
                        .collect(Collectors.joining(";"));
            }
            System.out.println(output);
        }
    }
}
