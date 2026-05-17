package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class TrollPolicy {

    public static final int[]     cursorBuf      = new int[GameState.MAX_TROLLS];
    public static final byte[]    policyPhase    = new byte[GameState.MAX_TROLLS];
    public static final boolean[] oppTreeTakenBuf = new boolean[GameState.MAX_TREES];

    private TrollPolicy() {}

    public static int fillActions(GameState s, short[] popBuf, byte[] popLen, int idx,
                                  int[] cursor, int[] outActions) {
        // Reset adversaire scratch
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

    public static int fillOwnActions(GameState s, short[] popBuf, byte[] popLen, int idx,
                                      int[] cursor, int[] outActions) {
        int count = 0;
        for (int trollIdx = 0; trollIdx < s.trollCount; trollIdx++) {
            if ((s.trollPlayer[trollIdx] & 0xFF) == 0) {
                outActions[count++] = decideForOwnTroll(s, popBuf, popLen, idx, cursor, trollIdx);
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
        if (len == 0) return Action.wait(trollIdx);

        // Intent rescue : si le troll porte un fruit, sauter le cursor sur le 1er gène
        // PLANT compatible (même fruit, case de plantation libre). Évite que le cursor
        // persisté hérite d'un gène HARVEST/CUT d'un best individu différent du tour
        // précédent, qui finirait par drop le fruit pické pour un PLANT initial.
        int rescueInvBase = trollIdx * ResourceType.COUNT;
        int carriedFruitMask = 0;
        for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++) {
            if ((s.trollInventory[rescueInvBase + r] & 0xFF) > 0) carriedFruitMask |= (1 << r);
        }
        if (carriedFruitMask != 0) {
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(popBuf, idx, trollIdx, k);
                if (!Genome.isPlant(g)) continue;
                if ((carriedFruitMask & (1 << Genome.plantFruitType(g))) == 0) continue;
                if (s.treeIndexAt(Genome.geneX(g), Genome.geneY(g)) >= 0) continue;
                cursor[trollIdx] = k;
                policyPhase[trollIdx] = 0;
                break;
            }
        }

        // Wrap-around : si le cursor a dépassé la fin du plan (gènes complétés/skippés
        // au fil des tours via le cursor persisté), on relance le plan une fois depuis 0.
        // PICK n'avance jamais le cursor (return sans cursor++), donc on ne peut pas
        // wrap au milieu d'un cycle PICK→PLANT — pas de régression du bug PICK→DROP.
        boolean wrappedOnce = false;
        while (true) {
        while (cursor[trollIdx] < len) {
            short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
            if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            int gx = Genome.geneX(g), gy = Genome.geneY(g);

            if (Genome.isHarvest(g)) {
                int treeIdx = s.treeIndexAt(gx, gy);
                if (treeIdx < 0 || (s.treeSize[treeIdx] & 0xFF) < 4 || s.treeHealth[treeIdx] <= 0) {
                    cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
                }
                int fruitCarry = 0;
                int invBase = trollIdx * ResourceType.COUNT;
                for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
                    fruitCarry += s.trollInventory[invBase + r] & 0xFF;
                if (fruitCarry > 0) {
                    if (isShackAdjacent(tx, ty)) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
                        return Action.drop(trollIdx);
                    }
                    return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
                }
                if (tx == gx && ty == gy) {
                    if ((s.treeFruits[treeIdx] & 0xFF) == 0) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
                    }
                    return Action.harvest(trollIdx);
                }
                return Action.move(trollIdx, gx, gy);
            }

            if (Genome.isMine(g)) {
                if (GameState.tileAt(gx, gy) != TileType.IRON) {
                    cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
                }
                if ((s.trollCP[trollIdx] & 0xFF) == 0) {
                    cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
                }
                int cc = s.trollCC[trollIdx] & 0xFF;
                int carryTotal = s.trollCarryTotal[trollIdx];
                if (carryTotal >= cc) {
                    if (isShackAdjacent(tx, ty)) return Action.drop(trollIdx);
                    return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
                }
                if (isAdjacentToCell(tx, ty, gx, gy)) {
                    int cp = s.trollCP[trollIdx] & 0xFF;
                    int gain = Math.min(cp, cc - carryTotal);
                    if (carryTotal + gain >= cc) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
                    }
                    return Action.mine(trollIdx);
                }
                int[] adj = closestGrassAdjToIron(tx, ty, gx, gy);
                return Action.move(trollIdx, adj[0], adj[1]);
            }

            if (Genome.isCut(g)) {
                if ((s.trollCP[trollIdx] & 0xFF) == 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
                if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
                if (tx == gx && ty == gy) return Action.chop(trollIdx);
                return Action.move(trollIdx, gx, gy);
            }

            int fruit = Genome.plantFruitType(g);
            int t = s.treeIndexAt(gx, gy);

            if (policyPhase[trollIdx] == 0 && t >= 0) policyPhase[trollIdx] = 1;

            if (policyPhase[trollIdx] == 0) {
                int carryFruit = s.trollInventory[trollIdx * ResourceType.COUNT + fruit] & 0xFF;
                if (carryFruit == 0) {
                    int shackStock = s.shackInventory[fruit];
                    if (shackStock <= 0) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
                        continue;
                    }
                    if (isShackAdjacent(tx, ty)) return Action.pick(trollIdx, fruit);
                    return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
                }
                if (tx == gx && ty == gy) {
                    policyPhase[trollIdx] = 1;
                    return Action.plant(trollIdx, fruit);
                }
                return Action.move(trollIdx, gx, gy);
            }

            if (t < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            if (tx == gx && ty == gy) return Action.chop(trollIdx);
            return Action.move(trollIdx, gx, gy);
        }
        // Cursor >= len : tente un wrap-around unique.
        if (wrappedOnce) return Action.wait(trollIdx);
        cursor[trollIdx] = 0;
        policyPhase[trollIdx] = 0;
        wrappedOnce = true;
        }
    }

    private static boolean isShackAdjacent(int x, int y) {
        for (int i = 0; i < ShackAdjacency.count; i++) {
            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
        }
        return false;
    }

    private static int closestShackAdjX(int x, int y) { return closestShackAdj(x, y, true); }
    private static int closestShackAdjY(int x, int y) { return closestShackAdj(x, y, false); }

    private static boolean isAdjacentToCell(int x, int y, int targetX, int targetY) {
        return Math.abs(x - targetX) + Math.abs(y - targetY) == 1;
    }

    private static int[] closestGrassAdjToIron(int fromX, int fromY, int ix, int iy) {
        int bestX = ix, bestY = iy, bestD = PathTable.UNREACHABLE;
        int W = GameState.width, H = GameState.height;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        for (int k = 0; k < 4; k++) {
            int nx = ix + dx[k], ny = iy + dy[k];
            if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
            if (GameState.tileAt(nx, ny) != TileType.GRASS) continue;
            int d = PathTable.distance(fromX, fromY, nx, ny);
            if (d == PathTable.UNREACHABLE) continue;
            if (d < bestD) { bestD = d; bestX = nx; bestY = ny; }
        }
        return new int[]{bestX, bestY};
    }

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
