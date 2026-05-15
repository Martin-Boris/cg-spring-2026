package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class Simulator {

    /** Si vrai, des assertions sont exécutées après chaque tick (tests uniquement, désactivé en prod). */
    public static boolean DEBUG_INVARIANTS = false;

    private static final byte[] WATER_BOOST = { 5, 5, 7, 2 };

    private static final boolean[] harvestTreeProcessed = new boolean[GameState.MAX_TREES];
    private static final int[]     harvestSharedTrolls  = new int[GameState.MAX_TROLLS];

    private static final boolean[] plantCellProcessed = new boolean[GameState.MAX_TROLLS + 8];
    private static final int[]     plantTypeBuf       = new int[GameState.MAX_TROLLS];

    private static final boolean[] chopTreeProcessed = new boolean[GameState.MAX_TREES];

    private Simulator() {}

    public static void tick(GameState s, int[] actions, int n) {
        applyMoves(s, actions, n);
        applyHarvests(s, actions, n);
        applyPlants(s, actions, n);
        applyChops(s, actions, n);
        applyPicks(s, actions, n);
        applyTrains(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    // Scratch: per-troll requested target (or -1 = no move / stationary)
    private static final int[] moveTargetX = new int[GameState.MAX_TROLLS];
    private static final int[] moveTargetY = new int[GameState.MAX_TROLLS];
    private static final boolean[] hasMove = new boolean[GameState.MAX_TROLLS];

    // Per-player resolver scratch
    private static final int[] resolverIdx = new int[GameState.MAX_TROLLS];
    private static final boolean[] resolverDone = new boolean[GameState.MAX_TROLLS];

    static void applyMoves(GameState s, int[] actions, int n) {
        // Reset & compute pre-resolved targets
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
        // Resolve per player
        for (int player = 0; player < 2; player++) resolvePlayerMoves(s, player);
    }

    // Returns {x,y} or null. Uses PathTable.stepAlong; never lands on non-grass.
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
        // Collect player's trolls
        int count = 0;
        for (int i = 0; i < s.trollCount; i++) {
            if ((s.trollPlayer[i] & 0xFF) != player) continue;
            resolverIdx[count] = i;
            resolverDone[i] = !hasMove[i] || (moveTargetX[i] == (s.trollX[i] & 0xFF)
                                            && moveTargetY[i] == (s.trollY[i] & 0xFF));
            count++;
        }
        // Mark occupied[] = current positions of all player's trolls (including stationary)
        int W = GameState.width;
        boolean[] occupied = ensureOccupiedBuffer(W * GameState.height);
        for (int i = 0; i < occupied.length; i++) occupied[i] = false;
        for (int k = 0; k < count; k++) {
            int idx = resolverIdx[k];
            occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = true;
        }
        boolean progressed = true;
        boolean allowBlocked = false;
        int[] freq = ensureFreqBuffer(W * GameState.height);
        while (progressed) {
            progressed = false;
            // recompute target frequency among undone trolls
            for (int i = 0; i < freq.length; i++) freq[i] = 0;
            for (int k = 0; k < count; k++) {
                int idx = resolverIdx[k];
                if (resolverDone[idx]) continue;
                freq[moveTargetY[idx] * W + moveTargetX[idx]]++;
            }
            // single-target moves into free cells
            for (int k = 0; k < count; k++) {
                int idx = resolverIdx[k];
                if (resolverDone[idx]) continue;
                int destCell = moveTargetY[idx] * W + moveTargetX[idx];
                if (!occupied[destCell] && (allowBlocked || freq[destCell] == 1)) {
                    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
                    s.trollX[idx] = (byte) moveTargetX[idx];
                    s.trollY[idx] = (byte) moveTargetY[idx];
                    occupied[destCell] = true;
                    resolverDone[idx] = true;
                    progressed = true;
                    allowBlocked = false;
                }
            }
            if (progressed) continue;
            // cycle detection
            for (int startK = 0; startK < count && !progressed; startK++) {
                int startIdx = resolverIdx[startK];
                if (resolverDone[startIdx]) continue;
                int cur = startIdx;
                int hops = 0;
                int found = -1;
                while (hops <= count) {
                    int destCell = moveTargetY[cur] * W + moveTargetX[cur];
                    // find a troll whose CURRENT cell == destCell, undone, same player
                    int next = -1;
                    for (int k = 0; k < count; k++) {
                        int j = resolverIdx[k];
                        if (resolverDone[j]) continue;
                        if ((s.trollY[j] & 0xFF) * W + (s.trollX[j] & 0xFF) == destCell) { next = j; break; }
                    }
                    if (next < 0) break;
                    if (next == startIdx) { found = hops; break; }
                    cur = next; hops++;
                }
                if (found >= 0) {
                    // execute the cycle
                    int cur2 = startIdx;
                    int[] cycle = cycleBuf;
                    int len = 0;
                    cycle[len++] = cur2;
                    for (int h = 0; h <= found; h++) {
                        int destCell = moveTargetY[cur2] * W + moveTargetX[cur2];
                        int next = -1;
                        for (int k = 0; k < count; k++) {
                            int j = resolverIdx[k];
                            if (resolverDone[j]) continue;
                            if ((s.trollY[j] & 0xFF) * W + (s.trollX[j] & 0xFF) == destCell) { next = j; break; }
                        }
                        if (next < 0 || next == startIdx) break;
                        cycle[len++] = next;
                        cur2 = next;
                    }
                    for (int c = 0; c < len; c++) {
                        int idx = cycle[c];
                        occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
                    }
                    for (int c = 0; c < len; c++) {
                        int idx = cycle[c];
                        s.trollX[idx] = (byte) moveTargetX[idx];
                        s.trollY[idx] = (byte) moveTargetY[idx];
                        occupied[moveTargetY[idx] * W + moveTargetX[idx]] = true;
                        resolverDone[idx] = true;
                    }
                    progressed = true;
                }
            }
            if (!progressed && !allowBlocked) {
                allowBlocked = true;
                progressed = true; // re-enter loop with relaxed rule
            }
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
            int treeIdx = findTreeAt(s, tx, ty);
            if (treeIdx < 0) continue;
            if (harvestTreeProcessed[treeIdx]) continue;
            // collect all HARVEST actions targeting this tree, in actions[] order
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
                    int invBase = trollIdx * ResourceType.COUNT;
                    int cc = s.trollCC[trollIdx] & 0xFF;
                    int total = 0;
                    for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
                    if (total >= cc) continue;
                    s.trollInventory[invBase + type]++;
                    if (s.treeFruits[treeIdx] > 0) s.treeFruits[treeIdx]--;
                }
            }
        }
    }

    static int findTreeAt(GameState s, int x, int y) {
        for (int i = 0; i < s.treeCount; i++) {
            if ((s.treeX[i] & 0xFF) == x && (s.treeY[i] & 0xFF) == y && s.treeHealth[i] > 0) return i;
        }
        return -1;
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
            int treeIdx = findTreeAt(s, tx, ty);
            if (treeIdx < 0) continue;
            if (chopTreeProcessed[treeIdx]) continue;
            // collect concurrent CHOP actions on this tree
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
            // damage sequentially
            for (int k = 0; k < shared; k++) {
                int dmg = s.trollCP[harvestSharedTrolls[k]] & 0xFF;
                int h = (s.treeHealth[treeIdx] & 0xFF) - dmg;
                s.treeHealth[treeIdx] = (byte) Math.max(h, 0);
            }
            if (s.treeHealth[treeIdx] != 0) continue;
            // distribute wood
            int size = s.treeSize[treeIdx] & 0xFF;
            int remaining = size;
            for (int round = 0; round < size && remaining > 0; round++) {
                for (int k = 0; k < shared; k++) {
                    int trollIdx = harvestSharedTrolls[k];
                    int invBase = trollIdx * ResourceType.COUNT;
                    int cc = s.trollCC[trollIdx] & 0xFF;
                    int total = 0;
                    for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
                    if (total >= cc) continue;
                    s.trollInventory[invBase + ResourceType.WOOD]++;
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
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            if (total >= cc) continue;
            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
            if (s.shackInventory[shackBase + type] <= 0) continue;
            s.shackInventory[shackBase + type]--;
            s.trollInventory[invBase + type]++;
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
            // skip if not grass, or already a tree present
            if (GameState.tiles[ty * GameState.width + tx] != TileType.GRASS) continue;
            if (findTreeAt(s, tx, ty) >= 0) continue;
            // collect concurrent PLANT actions on same cell
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
                // parse-time: troll must have the seed
                int jtype = Action.arg1(b);
                if (s.trollInventory[jdx * ResourceType.COUNT + jtype] <= 0) continue;
                sharedIdx[sharedCount]  = jdx;
                sharedType[sharedCount] = jtype;
                if (jtype != firstType) contradictory = true;
                sharedCount++;
                plantCellProcessed[j] = true;
            }
            if (sharedCount == 0 || contradictory) continue;
            // all agree; each loses a seed; exactly one tree is created
            int treeType = firstType;
            int newIdx = s.treeCount++;
            s.treeType[newIdx]     = (byte) treeType;
            s.treeX[newIdx]        = (byte) tx;
            s.treeY[newIdx]        = (byte) ty;
            s.treeSize[newIdx]     = 0;
            s.treeHealth[newIdx]   = (byte) initialPlantHealth(treeType);
            s.treeFruits[newIdx]   = 0;
            s.treeCooldown[newIdx] = 0;
            for (int k = 0; k < sharedCount; k++) {
                int jdx = sharedIdx[k];
                int jtype = sharedType[k];
                s.trollInventory[jdx * ResourceType.COUNT + jtype]--;
            }
        }
    }

    private static int initialPlantHealth(int treeType) {
        // FINAL - DELTA * MAX_SIZE: PLUM=12-8=4, LEMON=12-8=4, APPLE=20-12=8, BANANA=6-4=2
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
            // try player 0 first, then player 1
            int player = -1;
            for (int p = 0; p < 2; p++) {
                if (!canAffordTrain(s, p, ms, cc, hp, cp)) continue;
                if (shackOccupied(s, p)) continue;
                player = p; break;
            }
            if (player < 0) continue;
            // deduct costs
            int base = player * ResourceType.COUNT;
            int nUnits = countOwnTrolls(s, player);
            s.shackInventory[base + ResourceType.PLUM]  -= nUnits + ms * ms;
            s.shackInventory[base + ResourceType.LEMON] -= nUnits + cc * cc;
            s.shackInventory[base + ResourceType.APPLE] -= nUnits + hp * hp;
            s.shackInventory[base + ResourceType.IRON]  -= nUnits + cp * cp;
            // spawn troll at shack
            int newIdx = s.trollCount++;
            s.trollPlayer[newIdx] = (byte) player;
            s.trollId[newIdx]     = (byte) (maxTrollId(s) + 1);
            s.trollX[newIdx] = (byte) (player == 0 ? GameState.shackMeX : GameState.shackOppX);
            s.trollY[newIdx] = (byte) (player == 0 ? GameState.shackMeY : GameState.shackOppY);
            s.trollMS[newIdx] = (byte) ms;
            s.trollCC[newIdx] = (byte) cc;
            s.trollHP[newIdx] = (byte) hp;
            s.trollCP[newIdx] = (byte) cp;
            int invBase = newIdx * ResourceType.COUNT;
            for (int r = 0; r < ResourceType.COUNT; r++) s.trollInventory[invBase + r] = 0;
        }
    }

    private static boolean canAffordTrain(GameState s, int player, int ms, int cc, int hp, int cp) {
        int base = player * ResourceType.COUNT;
        int n = countOwnTrolls(s, player);
        return s.shackInventory[base + ResourceType.PLUM]  >= n + ms * ms
            && s.shackInventory[base + ResourceType.LEMON] >= n + cc * cc
            && s.shackInventory[base + ResourceType.APPLE] >= n + hp * hp
            && s.shackInventory[base + ResourceType.IRON]  >= n + cp * cp;
    }

    private static boolean shackOccupied(GameState s, int player) {
        int sx = (player == 0) ? GameState.shackMeX  : GameState.shackOppX;
        int sy = (player == 0) ? GameState.shackMeY  : GameState.shackOppY;
        for (int i = 0; i < s.trollCount; i++) {
            if ((s.trollX[i] & 0xFF) == sx && (s.trollY[i] & 0xFF) == sy) return true;
        }
        return false;
    }

    private static int countOwnTrolls(GameState s, int player) {
        int c = 0;
        for (int i = 0; i < s.trollCount; i++) if ((s.trollPlayer[i] & 0xFF) == player) c++;
        return c;
    }

    private static int maxTrollId(GameState s) {
        int m = -1;
        for (int i = 0; i < s.trollCount; i++) {
            int v = s.trollId[i] & 0xFF;
            if (v > m) m = v;
        }
        return m;
    }

    static void applyDrops(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.DROP) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            if (!trollNearOwnShack(s, idx)) continue;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            if (total == 0) continue;
            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
            for (int r = 0; r < ResourceType.COUNT; r++) {
                s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
                s.trollInventory[invBase + r] = 0;
            }
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
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            int free = cc - total;
            int gain = Math.min(cp, free);
            if (gain <= 0) continue;
            s.trollInventory[invBase + ResourceType.IRON] += gain;
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

    static void compactDeadTrees(GameState s) {
        int i = 0;
        while (i < s.treeCount) {
            if (s.treeHealth[i] <= 0) {
                int last = s.treeCount - 1;
                if (i != last) {
                    s.treeType[i]     = s.treeType[last];
                    s.treeX[i]        = s.treeX[last];
                    s.treeY[i]        = s.treeY[last];
                    s.treeSize[i]     = s.treeSize[last];
                    s.treeHealth[i]   = s.treeHealth[last];
                    s.treeFruits[i]   = s.treeFruits[last];
                    s.treeCooldown[i] = s.treeCooldown[last];
                }
                s.treeCount--;
            } else {
                i++;
            }
        }
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
        // PLUM=2, LEMON=2, APPLE=3, BANANA=1 (mirrors Constants.PLANT_DELTA_HEALTH)
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
