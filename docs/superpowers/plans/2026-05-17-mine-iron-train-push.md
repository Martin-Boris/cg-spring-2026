# MINE IRON + TRAIN-push Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un 4ème type de gène (MINE) au GA pour que les trolls extraient de l'IRON, et une récompense fitness "TRAIN-push" qui favorise l'accumulation au shack du palier TRAIN, sous condition `turn < 150` et `ownTrolls < 5`.

**Architecture:** Bit13 du short 16 bits est réservé au flag MINE (mutually exclusive avec PLANT bit15 et HARVEST bit14). TrollPolicy exécute le cycle move→mine→drop sur la cellule GRASS adjacente à un IRON. Cellules IRON statiques (immuables sur toute la partie) → `ironCandidates[]` calculé une seule fois. Fitness ajoute `ALPHA_IRON_CARRY * ironCarryMe + ALPHA_TRAIN_PUSH * trainPush` où trainPush borne le bonus shack au palier `ownTrolls + 1`.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. Test command : `mvn -q test -Dtest=ClassName` (ou `ClassName#methodName`).

---

## Spec source

`docs/superpowers/specs/2026-05-17-mine-iron-train-push-design.md`

## Fichiers modifiés / créés

| Fichier | Rôle |
|---|---|
| `src/main/java/.../ga/Genome.java` | `MINE_FLAG_MASK`, `makeMine`, `isMine`, `isCut`, `ironCandidates[]`, `initIronCandidates` |
| `src/main/java/.../ga/TrollPolicy.java` | Branchement MINE dans `decideForOwnTroll`, helpers grass-adj, remplacement `!isPlant` par `isCut` |
| `src/main/java/.../ga/GenomeOps.java` | `P_MINE_INIT`, `P_MUT_INSERT_MINE`, `MUT_INSERT_MINE`, `mutateInsertMine`, étape 7 dans `initRandom`, court-circuit MINE dans `initFromPrevBest`, rebalance `pickMutationKind` |
| `src/main/java/.../ga/GenomeEvaluator.java` | `TRAIN_PUSH_TURN_CUTOFF`, `TRAIN_PUSH_TROLL_CAP`, `ALPHA_TRAIN_PUSH`, `ALPHA_IRON_CARRY`, `TRAIN_RESOURCES`, fitness shaping |
| `src/main/java/.../ga/GeneticAgent.java` | Appel one-shot `Genome.initIronCandidates()` (parallèle à `initPlantCandidates`) |
| `src/test/java/.../ga/GenomeMineTest.java` | Tests encoding MINE |
| `src/test/java/.../ga/GenomeIronCandidatesTest.java` | Tests scan cellules IRON |
| `src/test/java/.../ga/TrollPolicyMineTest.java` | Tests politique MINE |
| `src/test/java/.../ga/GenomeOpsMineTest.java` | Tests init + mutate MINE |
| `src/test/java/.../ga/GenomeOpsPrevBestMineTest.java` | Test court-circuit MINE dans initFromPrevBest |
| `src/test/java/.../ga/GenomeEvaluatorTrainPushTest.java` | Tests fitness shaping |

---

## Task 1 : Encodage MINE dans `Genome.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeMineTest.java`

- [ ] **Step 1.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeMineTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeMineTest {

    @Test void makeMineRoundTrip() {
        short g = Genome.makeMine(5, 12);
        assertThat(Genome.isMine(g)).isTrue();
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.isHarvest(g)).isFalse();
        assertThat(Genome.isCut(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(12);
    }

    @Test void isMineFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForHarvestGene() {
        short g = Genome.makeHarvest(3, 4);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForCutGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isMine(g)).isFalse();
    }

    @Test void isMineFalseForEmptyGene() {
        assertThat(Genome.isMine(Genome.EMPTY_GENE)).isFalse();
    }

    @Test void isCutTrueForPlainEncodedGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isCut(g)).isTrue();
    }

    @Test void isCutFalseForMineGene() {
        short g = Genome.makeMine(5, 12);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForHarvestGene() {
        short g = Genome.makeHarvest(5, 12);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isCut(g)).isFalse();
    }

    @Test void isCutFalseForEmptyGene() {
        assertThat(Genome.isCut(Genome.EMPTY_GENE)).isFalse();
    }
}
```

- [ ] **Step 1.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeMineTest
```
Expected: compile errors (méthodes `makeMine`, `isMine`, `isCut` absentes).

- [ ] **Step 1.3 : Implémentation (GREEN)**

Dans `Genome.java`, après la déclaration de `HARVEST_FLAG_MASK` (ligne ~19) :

```java
private static final int MINE_FLAG_MASK = 0x2000;  // bit13
```

Mettre à jour le commentaire de layout en tête de classe :

```java
// Gene type dispatch (16-bit short):
//   bit15=1                    → PLANT gene  (bits13-14=fruitType, bits8-12=x, bits0-7=y)
//   bit15=0, bit14=1           → HARVEST gene (bits8-12=x, bits0-7=y)
//   bit15=0, bit14=0, bit13=1  → MINE gene    (bits8-12=x, bits0-7=y)
//   bit15=0, bit14=0, bit13=0  → CUT gene     (bits8-12=x, bits0-7=y; valid only for x<32)
//   all bits=1                 → EMPTY_GENE
```

Ajouter les méthodes (après `isHarvest`, ligne ~53) :

```java
public static short makeMine(int x, int y) {
    return (short) (MINE_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
}

public static boolean isMine(short g) {
    if (g == EMPTY_GENE) return false;
    return (g & 0xE000) == MINE_FLAG_MASK;  // bit15=0, bit14=0, bit13=1
}

public static boolean isCut(short g) {
    if (g == EMPTY_GENE) return false;
    return (g & 0xE000) == 0;  // bit15=0, bit14=0, bit13=0
}
```

- [ ] **Step 1.4 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeMineTest
```
Expected: BUILD SUCCESS, 9 tests passed.

- [ ] **Step 1.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java src/test/java/com/bmrt/cgspring2026/ga/GenomeMineTest.java
git commit -m "feat(genome): MINE flag encoding (bit13) + isCut helper"
```

---

## Task 2 : `Genome.ironCandidates` + `initIronCandidates`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeIronCandidatesTest.java`

- [ ] **Step 2.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeIronCandidatesTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeIronCandidatesTest {

    @Test void initIronCandidates_picksIronCellWithGrassNeighbor() {
        GameState.width = 4; GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 4 + 1] = TileType.IRON;

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(1);
        short c = Genome.ironCandidates[0];
        assertThat(Genome.candX(c)).isEqualTo(1);
        assertThat(Genome.candY(c)).isEqualTo(1);
    }

    @Test void initIronCandidates_emptyWhenNoIron() {
        GameState.width = 4; GameState.height = 3;
        GameState.tiles = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(0);
    }

    @Test void initIronCandidates_excludesIronWithNoGrassNeighbor() {
        GameState.width = 3; GameState.height = 3;
        GameState.tiles = new byte[9];
        java.util.Arrays.fill(GameState.tiles, TileType.ROCK);
        GameState.tiles[1 * 3 + 1] = TileType.IRON;
        // les 4 voisins sont ROCK → cellule IRON non minable

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(0);
    }

    @Test void initIronCandidates_multipleIronCells() {
        GameState.width = 5; GameState.height = 3;
        GameState.tiles = new byte[15];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 5 + 1] = TileType.IRON;
        GameState.tiles[1 * 5 + 3] = TileType.IRON;

        Genome.initIronCandidates();

        assertThat(Genome.ironCandidateCount).isEqualTo(2);
    }
}
```

- [ ] **Step 2.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeIronCandidatesTest
```
Expected: compile errors (`ironCandidates`, `ironCandidateCount`, `initIronCandidates` absents).

- [ ] **Step 2.3 : Implémentation (GREEN)**

Dans `Genome.java`, ajouter constante + buffer (vers le haut, après `harvestCandidates`) :

```java
public static final int MAX_IRON_CANDIDATES = 64;
public static final short[] ironCandidates = new short[MAX_IRON_CANDIDATES];
public static int ironCandidateCount = 0;
```

Ajouter la méthode (après `initHarvestCandidates`) :

```java
public static void initIronCandidates() {
    ironCandidateCount = 0;
    int W = GameState.width, H = GameState.height;
    for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
            if (GameState.tiles[y * W + x] != TileType.IRON) continue;
            if (!hasAdjacentGrass(x, y)) continue;
            if (ironCandidateCount >= MAX_IRON_CANDIDATES) break;
            ironCandidates[ironCandidateCount++] = encode(x, y);
        }
    }
}

private static boolean hasAdjacentGrass(int x, int y) {
    int W = GameState.width, H = GameState.height;
    if (x + 1 < W && GameState.tiles[y * W + (x + 1)] == TileType.GRASS) return true;
    if (x - 1 >= 0 && GameState.tiles[y * W + (x - 1)] == TileType.GRASS) return true;
    if (y + 1 < H && GameState.tiles[(y + 1) * W + x] == TileType.GRASS) return true;
    if (y - 1 >= 0 && GameState.tiles[(y - 1) * W + x] == TileType.GRASS) return true;
    return false;
}
```

- [ ] **Step 2.4 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeIronCandidatesTest
```
Expected: 4 tests passed.

- [ ] **Step 2.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java src/test/java/com/bmrt/cgspring2026/ga/GenomeIronCandidatesTest.java
git commit -m "feat(genome): initIronCandidates + ironCandidates[] buffer"
```

---

## Task 3 : Branchement MINE dans `TrollPolicy`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyMineTest.java`

- [ ] **Step 3.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyMineTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyMineTest {

    // Grille 6x5, shack (1,1), IRON (4,2)
    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            TrollPolicy.cursorBuf[j] = 0;
            TrollPolicy.policyPhase[j] = 0;
        }
    }

    private GameState stateWithMineGene(int trollX, int trollY, int cp, int cc) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) trollX; s.trollY[0] = (byte) trollY;
        s.trollMS[0] = 2; s.trollCC[0] = (byte) cc; s.trollHP[0] = 0; s.trollCP[0] = (byte) cp;
        return s;
    }

    private static short[] popWithMineGene(int gx, int gy) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        Genome.setGene(buf, 0, 0, 0, Genome.makeMine(gx, gy));
        return buf;
    }

    private static byte[] lenBufOf1() {
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lens, 0, 0, 1);
        return lens;
    }

    @Test void movesToGrassAdjacentToIronWhenFar() {
        GameState s = stateWithMineGene(0, 0, 2, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dIron = Math.abs(tx - 4) + Math.abs(ty - 2);
        assertThat(dIron).isEqualTo(1);
    }

    @Test void minesWhenAdjacentToIron() {
        GameState s = stateWithMineGene(3, 2, 2, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
    }

    @Test void advancesCursorWhenMineFillsCarry() {
        // cp=2, cc=2 → gain min(cp, cc-carryTotal) = 2 → carry plein après MINE
        GameState s = stateWithMineGene(3, 2, 2, 2);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
    }

    @Test void doesNotAdvanceCursorWhenMineDoesNotFillCarry() {
        // cp=1, cc=5 → gain=1, carry restera 1 < 5 → cursor inchangé
        GameState s = stateWithMineGene(3, 2, 1, 5);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MINE);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0);
    }

    @Test void dropsWhenCarryFull() {
        // Troll plein d'IRON, adjacent au shack
        GameState s = stateWithMineGene(1, 2, 2, 3);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 3;
        s.trollCarryTotal[0] = 3;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // ne pas consumer le gène : on revient miner
    }

    @Test void movesToShackWhenCarryFullAndFar() {
        GameState s = stateWithMineGene(4, 4, 2, 3);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 3;
        s.trollCarryTotal[0] = 3;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dShack = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dShack).isEqualTo(1);
    }

    @Test void skipsMineGeneWhenCpZero() {
        GameState s = stateWithMineGene(3, 2, 0, 3);
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodDropTakesPriorityOverMine() {
        GameState s = stateWithMineGene(1, 2, 2, 3); // adjacent shack
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 2;
        s.trollCarryTotal[0] = 2;
        short[] buf = popWithMineGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // wood-drop, pas mine-drop
    }
}
```

- [ ] **Step 3.2 : Run / verify FAIL**

```
mvn -q test -Dtest=TrollPolicyMineTest
```
Expected: 8 tests, tous échouent (le branchement MINE n'existe pas → comportement WAIT par défaut sur gène non reconnu — en réalité le gène `makeMine` sera traité comme CUT par la branche `!isPlant` et échouera autrement).

- [ ] **Step 3.3 : Implémentation (GREEN)**

Dans `TrollPolicy.java`, modifier `decideForOwnTroll` :

1. Remplacer `if (!Genome.isPlant(g))` par `if (Genome.isCut(g))` :

```java
if (Genome.isCut(g)) {
    if ((s.trollCP[trollIdx] & 0xFF) == 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    if (tx == gx && ty == gy) return Action.chop(trollIdx);
    return Action.move(trollIdx, gx, gy);
}
```

2. Insérer le branchement MINE entre `isHarvest` et `isCut` :

```java
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
```

3. Ajouter les helpers privés en bas de classe (au-dessus de `closestShackAdj`) :

```java
private static boolean isAdjacentToCell(int x, int y, int targetX, int targetY) {
    return Math.abs(x - targetX) + Math.abs(y - targetY) == 1;
}

private static int[] closestGrassAdjToIron(int fromX, int fromY, int ix, int iy) {
    int bestX = ix, bestY = iy, bestD = PathTable.UNREACHABLE;
    int W = GameState.width, H = GameState.height;
    // Voisins 4-connexité
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
```

Imports à ajouter en tête : `com.bmrt.cgspring2026.model.TileType` (si absent).

- [ ] **Step 3.4 : Run / verify PASS**

```
mvn -q test -Dtest=TrollPolicyMineTest
```
Expected: 8 tests passed.

- [ ] **Step 3.5 : Run regression sur les autres TrollPolicy tests**

```
mvn -q test -Dtest=TrollPolicyTest,TrollPolicyHarvestTest,TrollPolicyPlantTest,TrollPolicyCutFilterTest
```
Expected: tous passent (substitution `!isPlant` → `isCut` est sémantiquement équivalente avec MINE en place).

- [ ] **Step 3.6 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyMineTest.java
git commit -m "feat(policy): branchement MINE dans decideForOwnTroll"
```

---

## Task 4 : Étape 7 dans `initRandom` (injection MINE)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMineTest.java`

- [ ] **Step 4.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMineTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsMineTest {

    private GameState state;

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        Genome.initIronCandidates();

        state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 1; state.trollY[0] = 0;
        state.trollMS[0] = 2; state.trollCC[0] = 3;
        state.trollHP[0] = 0; state.trollCP[0] = 1;
        state.turn = 10;
    }

    @Test void initRandom_seedsMineGeneBeforeCutoff() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(42);
        boolean found = false;
        for (int attempt = 0; attempt < 50 && !found; attempt++) {
            GenomeOps.initRandom(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isMine((short) Genome.gene(buf, 0, j, k))) found = true;
                }
            }
        }
        assertThat(found).isTrue();
    }

    @Test void initRandom_noMineGeneAtCutoff() {
        state.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noMineGeneWhenTrollHasNoCp() {
        state.trollCP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(11);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noMineWhenNoCandidates() {
        int saved = Genome.ironCandidateCount;
        Genome.ironCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(3);
            for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
                }
            }
        } finally {
            Genome.ironCandidateCount = saved;
        }
    }
}
```

- [ ] **Step 4.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeOpsMineTest
```
Expected: compile error (`GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF` absent), ou si on hardcode 150 d'abord, test `initRandom_seedsMineGeneBeforeCutoff` échoue (aucun gène MINE).

**Note** : pour débloquer la compilation, déclarer temporairement `public static final int TRAIN_PUSH_TURN_CUTOFF = 150;` dans `GenomeEvaluator` (sera complété en Task 7).

- [ ] **Step 4.3 : Pré-déclaration de la constante**

Dans `GenomeEvaluator.java`, ajouter :

```java
public static final int TRAIN_PUSH_TURN_CUTOFF = 150;
```

Re-run tests : compilation OK, le test fail attendu est sur `initRandom_seedsMineGeneBeforeCutoff`.

- [ ] **Step 4.4 : Implémentation initRandom étape 7 (GREEN)**

Dans `GenomeOps.java`, ajouter constante :

```java
public static final double P_MINE_INIT = 0.20;
```

Ajouter buffer scratch (à côté de `initSeenHarvest`) :

```java
private static final boolean[] initSeenMine = new boolean[256 * 256];
```

Dans `initRandom`, ajouter l'étape 7 après l'étape 6 HARVEST :

```java
// 7. Injecter gènes MINE (si turn < cutoff)
if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF
    && Genome.ironCandidateCount > 0) {
    int W = GameState.width;
    for (int h = 0; h < Genome.ironCandidateCount; h++) {
        short c = Genome.ironCandidates[h];
        initSeenMine[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int k = 0; k < ownTrollsCount; k++) {
        int trollIdx = ownTrollsBuf[k];
        if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;
        int len = Genome.len(lenBuf, individuIdx, trollIdx);
        if (len >= Genome.MAX_TARGETS_PER_TROLL) continue;
        if (rng.nextDouble() >= P_MINE_INIT) continue;
        int tries = 0;
        while (tries < Genome.ironCandidateCount) {
            short c = Genome.ironCandidates[rng.nextInt(Genome.ironCandidateCount)];
            int cx = Genome.candX(c), cy = Genome.candY(c);
            if (!initSeenMine[cy * W + cx]) {
                Genome.setGene(buf, individuIdx, trollIdx, len, Genome.makeMine(cx, cy));
                Genome.setLen(lenBuf, individuIdx, trollIdx, len + 1);
                initSeenMine[cy * W + cx] = true;
                break;
            }
            tries++;
        }
    }
}
```

- [ ] **Step 4.5 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeOpsMineTest
```
Expected: 4 tests passed.

- [ ] **Step 4.6 : Regression suite GenomeOps**

```
mvn -q test -Dtest=GenomeOpsInitTest,GenomeOpsHarvestTest,GenomeOpsInsertPlantTest,GenomeOpsCutFilterTest
```
Expected: tous passent.

- [ ] **Step 4.7 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMineTest.java
git commit -m "feat(ops): initRandom étape 7 MINE + TRAIN_PUSH_TURN_CUTOFF const"
```

---

## Task 5 : `mutateInsertMine` + rebalance `pickMutationKind`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMineTest.java`

- [ ] **Step 5.1 : Écrire les tests (RED)**

Ajouter à `GenomeOpsMineTest.java` :

```java
@Test void mutateInsertMine_insertsGeneBeforeCutoff() {
    short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
    byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    SplittableRandom rng = new SplittableRandom(13);
    boolean inserted = false;
    for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
        GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                if (Genome.isMine((short) Genome.gene(buf, 0, j, k))) inserted = true;
            }
        }
    }
    assertThat(inserted).isTrue();
}

@Test void mutateInsertMine_noopAtCutoff() {
    state.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
    short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
    byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    SplittableRandom rng = new SplittableRandom(5);
    for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
    for (int j = 0; j < GameState.MAX_TROLLS; j++) {
        int len = Genome.len(lens, 0, j);
        for (int k = 0; k < len; k++) {
            assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
        }
    }
}

@Test void mutateInsertMine_noopWhenNoCandidates() {
    int saved = Genome.ironCandidateCount;
    Genome.ironCandidateCount = 0;
    try {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(9);
        for (int i = 0; i < 30; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    } finally {
        Genome.ironCandidateCount = saved;
    }
}

@Test void mutateInsertMine_noopWhenTrollHasNoCp() {
    state.trollCP[0] = 0;
    short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
    byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    SplittableRandom rng = new SplittableRandom(15);
    for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
    for (int j = 0; j < GameState.MAX_TROLLS; j++) {
        int len = Genome.len(lens, 0, j);
        for (int k = 0; k < len; k++) {
            assertThat(Genome.isMine((short) Genome.gene(buf, 0, j, k))).isFalse();
        }
    }
}

@Test void mutateInsertMine_noDuplicateInIndividual() {
    short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
    byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    // Pré-remplir avec le seul candidat existant
    short c = Genome.ironCandidates[0];
    Genome.setGene(buf, 0, 0, 0, Genome.makeMine(Genome.candX(c), Genome.candY(c)));
    Genome.setLen(lens, 0, 0, 1);
    SplittableRandom rng = new SplittableRandom(21);
    for (int i = 0; i < 50; i++) GenomeOps.mutateInsertMine(state, buf, lens, 0, rng);
    int total = 0;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) total += Genome.len(lens, 0, j);
    assertThat(total).isEqualTo(1);
}
```

- [ ] **Step 5.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeOpsMineTest
```
Expected: compile error (`mutateInsertMine` absent).

- [ ] **Step 5.3 : Implémentation (GREEN)**

Dans `GenomeOps.java`, ajouter constante + identifiant + buffer scratch :

```java
public static final double P_MUT_INSERT_MINE = 0.08;
public static final int    MUT_INSERT_MINE   = 7;
private static final boolean[] mutSeenMine = new boolean[256 * 256];
```

Ajouter la méthode :

```java
public static void mutateInsertMine(GameState state, short[] buf, byte[] lenBuf,
                                    int individuIdx, SplittableRandom rng) {
    if (state.turn >= GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) return;
    if (Genome.ironCandidateCount == 0) return;

    int count = 0;
    for (int j = 0; j < state.trollCount; j++) {
        if ((state.trollPlayer[j] & 0xFF) != 0) continue;
        if ((state.trollCP[j] & 0xFF) == 0) continue;
        if (Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL)
            freeTrollsBuf[count++] = j;
    }
    if (count == 0) return;
    int j = freeTrollsBuf[rng.nextInt(count)];
    int len = Genome.len(lenBuf, individuIdx, j);
    int W = GameState.width;

    for (int h = 0; h < Genome.ironCandidateCount; h++) {
        short c = Genome.ironCandidates[h];
        mutSeenMine[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int tj = 0; tj < GameState.MAX_TROLLS; tj++) {
        int tjLen = Genome.len(lenBuf, individuIdx, tj);
        int base = Genome.offset(individuIdx, tj);
        for (int k = 0; k < tjLen; k++) {
            short g = buf[base + k];
            if (Genome.isMine(g))
                mutSeenMine[Genome.geneY(g) * W + Genome.geneX(g)] = true;
        }
    }

    int tries = 0;
    while (tries < Genome.ironCandidateCount) {
        short c = Genome.ironCandidates[rng.nextInt(Genome.ironCandidateCount)];
        int cx = Genome.candX(c), cy = Genome.candY(c);
        if (!mutSeenMine[cy * W + cx]) {
            int pos = rng.nextInt(len + 1);
            int base = Genome.offset(individuIdx, j);
            for (int k = len; k > pos; k--) buf[base + k] = buf[base + k - 1];
            buf[base + pos] = Genome.makeMine(cx, cy);
            Genome.setLen(lenBuf, individuIdx, j, len + 1);
            return;
        }
        tries++;
    }
}
```

Modifier les probas et le dispatcher. Remplacer le bloc actuel :

```java
public static final double P_MUT_SWAP_INTRA      = 0.20;
public static final double P_MUT_SWAP_INTER      = 0.17;
public static final double P_MUT_REVERSE         = 0.08;
public static final double P_MUT_DELETE          = 0.09;
public static final double P_MUT_INSERT_PLANT    = 0.12;
public static final double P_MUT_INSERT_CUT      = 0.17;
public static final double P_MUT_INSERT_HARVEST  = 0.09;
// P_MUT_INSERT_MINE = 0.08 déclaré plus haut

public static int pickMutationKind(SplittableRandom rng) {
    double r = rng.nextDouble();
    if (r < P_MUT_SWAP_INTRA)   return MUT_SWAP_INTRA;
    r -= P_MUT_SWAP_INTRA;
    if (r < P_MUT_SWAP_INTER)   return MUT_SWAP_INTER;
    r -= P_MUT_SWAP_INTER;
    if (r < P_MUT_REVERSE)      return MUT_REVERSE;
    r -= P_MUT_REVERSE;
    if (r < P_MUT_DELETE)       return MUT_DELETE;
    r -= P_MUT_DELETE;
    if (r < P_MUT_INSERT_PLANT) return MUT_INSERT_PLANT;
    r -= P_MUT_INSERT_PLANT;
    if (r < P_MUT_INSERT_CUT)   return MUT_INSERT_CUT;
    r -= P_MUT_INSERT_CUT;
    if (r < P_MUT_INSERT_HARVEST) return MUT_INSERT_HARVEST;
    return MUT_INSERT_MINE;
}
```

Et dans `runMutation` :

```java
public static void runMutation(GameState state, short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
    switch (pickMutationKind(rng)) {
        case MUT_SWAP_INTRA     -> mutateSwapIntra    (buf, lenBuf, individuIdx, rng);
        case MUT_SWAP_INTER     -> mutateSwapInter    (buf, lenBuf, individuIdx, rng);
        case MUT_REVERSE        -> mutateReverse      (buf, lenBuf, individuIdx, rng);
        case MUT_DELETE         -> mutateDelete       (buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_PLANT   -> mutateInsertPlant  (buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_CUT     -> mutateInsertCut    (state, buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_HARVEST -> mutateInsertHarvest(state, buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_MINE    -> mutateInsertMine   (state, buf, lenBuf, individuIdx, rng);
        default -> throw new IllegalStateException();
    }
}
```

- [ ] **Step 5.4 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeOpsMineTest,GenomeOpsMutationTest
```
Expected: tous passent.

- [ ] **Step 5.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMineTest.java
git commit -m "feat(ops): mutateInsertMine + rebalance pickMutationKind"
```

---

## Task 6 : Court-circuit MINE dans `initFromPrevBest`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestMineTest.java`

- [ ] **Step 6.1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestMineTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsPrevBestMineTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        Genome.initIronCandidates();
    }

    @Test void initFromPrevBest_preservesMineGeneEvenWithoutTree() {
        // Source prev best : un gène MINE vers IRON (4,2)
        short[] prev = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(prev, Genome.EMPTY_GENE);
        prev[0] = Genome.makeMine(4, 2);
        byte[] prevLen = new byte[GameState.MAX_TROLLS];
        prevLen[0] = 1;

        // GameState : aucun arbre (juste un troll)
        GameState state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 1; state.trollY[0] = 0;
        state.treeCount = 0;

        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        GenomeOps.initFromPrevBest(state, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        assertThat(Genome.isMine((short) Genome.gene(dst, 0, 0, 0))).isTrue();
        assertThat(Genome.geneX((short) Genome.gene(dst, 0, 0, 0))).isEqualTo(4);
        assertThat(Genome.geneY((short) Genome.gene(dst, 0, 0, 0))).isEqualTo(2);
    }
}
```

- [ ] **Step 6.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeOpsPrevBestMineTest
```
Expected: le test échoue — le code actuel filtre les gènes MINE car `state.treeIndexAt(4, 2) < 0`.

- [ ] **Step 6.3 : Implémentation (GREEN)**

Dans `GenomeOps.initFromPrevBest`, à l'intérieur de la boucle `for (int k = 0; k < prevLen; k++)`, ajouter un court-circuit pour MINE avant le check `treeIndexAt` :

```java
for (int k = 0; k < prevLen; k++) {
    short g = prevBuf[srcOff + k];
    if (g == Genome.EMPTY_GENE) continue;
    if (Genome.isMine(g)) {
        // Cellule IRON immuable : toujours valide
        dstBuf[dstOff + written] = g;
        written++;
        continue;
    }
    int gx = Genome.geneX(g);
    int gy = Genome.geneY(g);
    int t = state.treeIndexAt(gx, gy);
    if (t < 0) continue;
    if (state.treeHealth[t] <= 0) continue;
    dstBuf[dstOff + written] = g;
    written++;
}
```

- [ ] **Step 6.4 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeOpsPrevBestMineTest,GenomeOpsPrevBestTest
```
Expected: tous passent (le test régression `GenomeOpsPrevBestTest` doit rester vert).

- [ ] **Step 6.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestMineTest.java
git commit -m "feat(ops): initFromPrevBest preserves MINE genes (immutable target)"
```

---

## Task 7 : Fitness shaping — `ALPHA_IRON_CARRY` + `trainPush`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTrainPushTest.java`

- [ ] **Step 7.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTrainPushTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorTrainPushTest {

    @BeforeEach void grid() {
        String[] rows = { "......", ".0....", "......", "......", "......" };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
    }

    private GameState emptyState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        if (s.treeCellIndex == null)
            s.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(s.treeCellIndex, (byte) -1);
        if (s.trollCellIndex == null)
            s.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(s.trollCellIndex, (byte) -1);
        s.trollCellIndex[0] = 0;
        return s;
    }

    private double fitnessOf(GameState source) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();
        return GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
    }

    @Test void ironCarryContributesToFitness() {
        GameState s = emptyState();
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.IRON] = 2;
        s.trollCarryTotal[0] = 2;
        // À horizon 25, le troll ne fait rien (pas de gènes, pas d'arbres) → état stable
        double fit = fitnessOf(s);
        assertThat(fit).isGreaterThanOrEqualTo(2 * GenomeEvaluator.ALPHA_IRON_CARRY - 1e-9);
    }

    @Test void trainPushBoostsShackIronTowardThreshold() {
        // 1 troll own + 1 IRON dans shack, turn=10 → trainPush actif sur IRON (target=2)
        GameState withStock = emptyState();
        withStock.shackInventory[ResourceType.IRON] = 1;
        withStock.turn = 10;
        GameState withoutStock = emptyState();
        withoutStock.turn = 10;

        double fitWith    = fitnessOf(withStock);
        double fitWithout = fitnessOf(withoutStock);

        // Différence = ALPHA_TRAIN_PUSH × 1 unité d'IRON dans la zone palier
        assertThat(fitWith - fitWithout).isGreaterThanOrEqualTo(GenomeEvaluator.ALPHA_TRAIN_PUSH - 1e-9);
    }

    @Test void trainPushDisabledAfterCutoff() {
        GameState withStock = emptyState();
        withStock.shackInventory[ResourceType.IRON] = 1;
        withStock.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
        GameState withoutStock = emptyState();
        withoutStock.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        double fitWith    = fitnessOf(withStock);
        double fitWithout = fitnessOf(withoutStock);

        // turn == cutoff → bonus push désactivé
        assertThat(fitWith).isCloseTo(fitWithout, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void trainPushDisabledAtTrollCap() {
        GameState s = emptyState();
        s.trollCount = GenomeEvaluator.TRAIN_PUSH_TROLL_CAP;
        for (int i = 0; i < s.trollCount; i++) {
            s.trollPlayer[i] = 0;
            s.trollX[i] = (byte) (i % GameState.width);
            s.trollY[i] = 0;
        }
        s.shackInventory[ResourceType.IRON] = 1;
        s.turn = 10;
        java.util.Arrays.fill(s.trollCellIndex, (byte) -1);
        for (int i = 0; i < s.trollCount; i++) {
            int cell = s.trollY[i] * GameState.width + s.trollX[i];
            if (s.trollCellIndex[cell] == -1) s.trollCellIndex[cell] = (byte) i;
        }

        GameState ref = emptyState();
        ref.trollCount = GenomeEvaluator.TRAIN_PUSH_TROLL_CAP;
        for (int i = 0; i < ref.trollCount; i++) {
            ref.trollPlayer[i] = 0;
            ref.trollX[i] = (byte) (i % GameState.width);
            ref.trollY[i] = 0;
        }
        ref.turn = 10;
        java.util.Arrays.fill(ref.trollCellIndex, (byte) -1);
        for (int i = 0; i < ref.trollCount; i++) {
            int cell = ref.trollY[i] * GameState.width + ref.trollX[i];
            if (ref.trollCellIndex[cell] == -1) ref.trollCellIndex[cell] = (byte) i;
        }

        double fitWith    = fitnessOf(s);
        double fitWithout = fitnessOf(ref);

        assertThat(fitWith).isCloseTo(fitWithout, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void trainPushCapsAtThreshold() {
        // 1 troll own : target = 2. Stock IRON = 10 → cap à 2.
        // Différence avec stock=2 doit être nulle (au-delà du palier, plus de bonus).
        GameState big = emptyState();
        big.shackInventory[ResourceType.IRON] = 10;
        big.turn = 10;
        GameState atTarget = emptyState();
        atTarget.shackInventory[ResourceType.IRON] = 2;
        atTarget.turn = 10;

        double fitBig    = fitnessOf(big);
        double fitTarget = fitnessOf(atTarget);

        assertThat(fitBig).isCloseTo(fitTarget, org.assertj.core.data.Offset.offset(1e-9));
    }
}
```

- [ ] **Step 7.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GenomeEvaluatorTrainPushTest
```
Expected: compile errors (constantes `ALPHA_IRON_CARRY`, `ALPHA_TRAIN_PUSH`, `TRAIN_PUSH_TROLL_CAP` absentes ; `TRAIN_PUSH_TURN_CUTOFF` déjà déclarée en Task 4.3).

- [ ] **Step 7.3 : Implémentation (GREEN)**

Dans `GenomeEvaluator.java`, ajouter constantes (à côté de celles existantes) :

```java
public static final int    TRAIN_PUSH_TROLL_CAP = 5;
public static final double ALPHA_TRAIN_PUSH     = 0.5;
public static final double ALPHA_IRON_CARRY     = 0.5;

private static final int[] TRAIN_RESOURCES = {
    ResourceType.PLUM, ResourceType.LEMON, ResourceType.APPLE, ResourceType.IRON
};
```

Remplacer la méthode `fitness` :

```java
private static double fitness(GameState finalState) {
    int scoreMe  = finalState.score(0);
    int scoreOpp = finalState.score(1);
    int woodCarryMe  = 0;
    int fruitCarryMe = 0;
    int ironCarryMe  = 0;
    int ownTrolls = 0;
    for (int i = 0; i < finalState.trollCount; i++) {
        if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
        ownTrolls++;
        int base = i * ResourceType.COUNT;
        woodCarryMe += finalState.trollInventory[base + ResourceType.WOOD] & 0xFF;
        ironCarryMe += finalState.trollInventory[base + ResourceType.IRON] & 0xFF;
        for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
            fruitCarryMe += finalState.trollInventory[base + r] & 0xFF;
    }

    double trainPush = 0.0;
    if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF && ownTrolls < TRAIN_PUSH_TROLL_CAP) {
        int target = ownTrolls + 1;
        for (int k = 0; k < TRAIN_RESOURCES.length; k++) {
            int stock = finalState.shackInventory[TRAIN_RESOURCES[k]];
            trainPush += Math.min(stock, target);
        }
    }

    return (scoreMe - scoreOpp)
         + ALPHA_WOOD_CARRY  * woodCarryMe
         + ALPHA_FRUIT_CARRY * fruitCarryMe
         + ALPHA_IRON_CARRY  * ironCarryMe
         + ALPHA_TRAIN_PUSH  * trainPush;
}
```

- [ ] **Step 7.4 : Run / verify PASS**

```
mvn -q test -Dtest=GenomeEvaluatorTrainPushTest,GenomeEvaluatorTest,GenomeEvaluatorHarvestTest
```
Expected: tous passent.

- [ ] **Step 7.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTrainPushTest.java
git commit -m "feat(fitness): ALPHA_IRON_CARRY + trainPush bonus before turn 150 / cap 5"
```

---

## Task 8 : Appel `initIronCandidates` dans `GeneticAgent`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentIronInitTest.java`

- [ ] **Step 8.1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentIronInitTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentIronInitTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "....+.",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX = x; GameState.shackMeY = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        // Volontairement NE PAS appeler Genome.initIronCandidates ici
        Genome.ironCandidateCount = 0;
    }

    @Test void decide_initializesIronCandidates() {
        GameState state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 0; state.trollY[0] = 0; // case GRASS (shack en (1,1))
        state.trollMS[0] = 1; state.trollCC[0] = 3;
        state.trollHP[0] = 1; state.trollCP[0] = 1;
        state.turn = 0;
        if (state.treeCellIndex == null)
            state.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(state.treeCellIndex, (byte) -1);
        if (state.trollCellIndex == null)
            state.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(state.trollCellIndex, (byte) -1);
        state.trollCellIndex[0] = 0;

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 200_000_000L; // 200ms
        agent.decide(state, deadline, out);

        assertThat(Genome.ironCandidateCount).isEqualTo(1);
    }
}
```

- [ ] **Step 8.2 : Run / verify FAIL**

```
mvn -q test -Dtest=GeneticAgentIronInitTest
```
Expected: assertion failed — `ironCandidateCount` reste 0.

- [ ] **Step 8.3 : Implémentation (GREEN)**

Dans `GeneticAgent.java`, méthode `decide`, à côté de l'init existant de `plantCandidates` :

```java
if (!plantCandidatesInitialized) {
    Genome.initPlantCandidates();
    Genome.initIronCandidates();
    plantCandidatesInitialized = true;
}
```

- [ ] **Step 8.4 : Run / verify PASS**

```
mvn -q test -Dtest=GeneticAgentIronInitTest
```
Expected: PASS.

- [ ] **Step 8.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentIronInitTest.java
git commit -m "feat(agent): initIronCandidates one-shot in GeneticAgent.decide"
```

---

## Task 9 : Full suite + smoke FileBuilder

**Files:** (aucun changement de code)

- [ ] **Step 9.1 : Full test suite**

```
mvn -q test
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9.2 : Vérifier que FileBuilder assemble correctement le Player.java mono-fichier**

```
mvn -q exec:java -Dexec.mainClass="com.bmrt.cgspring2026.builder.FileBuilder"
```
Expected: pas d'erreur ; `Player.java` mis à jour. Vérifier rapidement (`grep -c 'class Genome' Player.java` doit retourner 1 ou plus).

```
git status
```
Si `Player.java` modifié :

```
git add src/main/java/com/bmrt/cgspring2026/Player.java
git commit -m "build(player): regen Player.java with MINE gene + TRAIN-push"
```

---

## Notes d'exécution

- Le **cutoff turn 150** est un paramètre dans `GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF`. Le tester en fine-tune se fait en modifiant cette constante.
- Le **cap 5 trolls** est `GenomeEvaluator.TRAIN_PUSH_TROLL_CAP`. Idem fine-tune.
- Les valeurs `ALPHA_IRON_CARRY = 0.5` et `ALPHA_TRAIN_PUSH = 0.5` sont des starting points à fine-tuner via match local après merge.
- L'invariant `GenomeInvariants.check` n'a PAS besoin d'être modifié : un gène MINE n'est pas un `isPlant` → mappé sur `seenTarget` comme HARVEST/CUT. Aucune collision possible en pratique (case IRON ≠ case arbre).

## Vérifications avant PR

- [ ] `mvn -q test` : verts.
- [ ] Match local au moins 1 partie : TRAIN se déclenche avant tour 150 (vérifier les logs MSG `TRAIN ...`).
- [ ] Aucun crash sur cartes sans IRON proche (`ironCandidateCount == 0` → branche skipped proprement).
