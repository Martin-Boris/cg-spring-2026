# Simulator Hot-Loop Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Éliminer 9 chemins chauds redondants dans `Simulator.tick()` en introduisant des index spatiaux (cell→tree, cell→troll, isNearWater) maintenus dans `GameState`/`PathTable`, des resets partiels de buffers, et la déduplication de calculs répétés.

**Architecture:** Les structures mutables (`treeCellIndex`, `trollCellIndex`, `trollCarryTotal`, `nextTrollId`) vivent dans `GameState` (suivent l'état, sont copiées par `copyFrom`). La structure immuable `isNearWater` vit dans `PathTable` (statique, fonction du grid). Le `Simulator` les consomme et les maintient via des helpers `GameState`. Un drapeau `DEBUG_INVARIANTS` (`false` par défaut) permet d'activer des assertions en test pour vérifier la cohérence des index ; les tests dédiés l'activent localement.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Maven. Tableaux primitifs SoA (`byte[]`/`int[]`), pas de collections — chemin chaud d'un bot CodinGame.

**Ordre d'implémentation (par dépendances, pas par impact):**
1. T1 — `isNearWater[]` (autonome, simple, set la pattern d'index statique dans `PathTable`)
2. T2 — `treeCellIndex` + refonte de `findTreeAt` (HARVEST, CHOP, PLANT)
3. T3 — `trollCellIndex` + `nextTrollId` (remplace `shackOccupied`, `maxTrollId`, scans cell→troll)
4. T4 — `resolvePlayerMoves` : cycle detection via `trollCellIndex` (dépend de T3)
5. T5 — Reset partiel de `freq[]`
6. T6 — Reset partiel de `occupied[]`
7. T7 — `countOwnTrolls` dédupliqué via cost-cache local dans `applyTrains`
8. T8 — `trollCarryTotal[]` pré-calculé + maintenu incrémentalement (HARVEST, CHOP, PICK, MINE, DROP)

**Règles de discipline TDD pour chaque tâche:**
- RED: écrire le test, le lancer, vérifier qu'il échoue avec le bon message.
- GREEN: implémentation minimale, relancer le test, vérifier qu'il passe.
- REGRESSION: relancer **toute** la suite simulation (`mvn -Dtest='Simulator*Test,GameState*Test' test`) pour s'assurer qu'aucun test existant ne casse.
- COMMIT: petit commit ciblé.

---

## Task 0 : Setup — drapeau d'assertions debug

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Create: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java`

- [ ] **Step 1: Ajouter le drapeau `DEBUG_INVARIANTS` dans `Simulator`**

Dans `Simulator.java`, juste après la déclaration de classe :

```java
public final class Simulator {

    /** Si vrai, des `checkInvariants` sont exécutés après chaque phase (tests uniquement, désactivé en prod). */
    public static boolean DEBUG_INVARIANTS = false;

    private static final byte[] WATER_BOOST = { 5, 5, 7, 2 };
    // ... reste inchangé
```

- [ ] **Step 2: Créer le squelette du test d'invariants**

Créer `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java` :

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorInvariantsTest {

    @BeforeEach void setupGrid() {
        String[] rows = {
                "........",
                ".0......",
                "........",
                "....1...",
                "........",
                "........",
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        Simulator.DEBUG_INVARIANTS = true;
    }

    @AfterEach void teardown() {
        Simulator.DEBUG_INVARIANTS = false;
    }

    @Test void debugFlagDefaultsToFalseInProduction() {
        // Cet « anti-test » documente que le drapeau ne doit JAMAIS être laissé à true ailleurs.
        // Il échouera si quelqu'un commit DEBUG_INVARIANTS = true par défaut.
        Simulator.DEBUG_INVARIANTS = false;
        assertThat(Simulator.DEBUG_INVARIANTS).isFalse();
    }
}
```

- [ ] **Step 3: Lancer ce test, vérifier qu'il passe**

Run: `mvn -Dtest=SimulatorInvariantsTest test`
Expected: PASS (1 test).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java
git commit -m "simulator: add DEBUG_INVARIANTS flag + invariants test skeleton"
```

---

## Task 1 : `isNearWater[]` précalculé dans `PathTable`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java:589-599`
- Create: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableNearWaterTest.java`

**Pourquoi:** `nearWater(x,y)` est appelé à chaque `plantTick` sur tout arbre cooldown==0. La grille est figée pour toute la partie → précalcul.

- [ ] **Step 1: Écrire le test `nearWaterPrecomputed` (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableNearWaterTest.java` :

```java
package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableNearWaterTest {

    private static void setGrid(String[] rows) {
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
    }

    @Test void isNearWaterFlagsAllOrthogonalNeighbours() {
        setGrid(new String[]{
                ".0..",
                ".~..",
                "....",
                "...1",
        });
        PathTable.init();
        // Cellules adjacentes à l'eau (1,1) : (0,1), (2,1), (1,0), (1,2)
        assertThat(PathTable.isNearWater[1 * GameState.width + 0]).isTrue();
        assertThat(PathTable.isNearWater[1 * GameState.width + 2]).isTrue();
        assertThat(PathTable.isNearWater[0 * GameState.width + 1]).isTrue();
        assertThat(PathTable.isNearWater[2 * GameState.width + 1]).isTrue();
        // La case d'eau elle-même : isNearWater est true pour les voisins, on documente que la case
        // d'eau a son propre flag non significatif (les arbres ne peuvent pas pousser dessus).
        // Cellule loin de l'eau (3,3)
        assertThat(PathTable.isNearWater[3 * GameState.width + 3]).isFalse();
    }

    @Test void isNearWaterFalseWhenNoWaterOnGrid() {
        setGrid(new String[]{
                ".0..",
                "....",
                "...1",
        });
        PathTable.init();
        for (int i = 0; i < PathTable.isNearWater.length; i++) {
            assertThat(PathTable.isNearWater[i]).as("cell %d", i).isFalse();
        }
    }
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il échoue (RED)**

Run: `mvn -Dtest=PathTableNearWaterTest test`
Expected: FAIL — `PathTable.isNearWater` n'existe pas (compile error attendu).

- [ ] **Step 3: Implémenter `isNearWater[]` dans `PathTable`**

Dans `PathTable.java`, ajouter le champ public et la précomputation. Repérer la fin de `init()` (ou `indexCells()`) ; ajouter dans la même classe :

```java
public static boolean[] isNearWater;   // [W*H] true si une case orthogonale est de l'eau
```

Puis, dans la méthode `init()` (juste après `indexCells()` ou après le calcul de BFS), ajouter à la toute fin :

```java
isNearWater = new boolean[GameState.width * GameState.height];
int W = GameState.width;
int H = GameState.height;
for (int y = 0; y < H; y++) {
    for (int x = 0; x < W; x++) {
        boolean near = (x + 1 < W && GameState.tiles[y * W + (x + 1)] == TileType.WATER)
                    || (x - 1 >= 0 && GameState.tiles[y * W + (x - 1)] == TileType.WATER)
                    || (y + 1 < H && GameState.tiles[(y + 1) * W + x] == TileType.WATER)
                    || (y - 1 >= 0 && GameState.tiles[(y - 1) * W + x] == TileType.WATER);
        isNearWater[y * W + x] = near;
    }
}
```

> Si `PathTable.init()` n'existe pas en l'état (vérifie le fichier), place le bloc à la fin de la méthode publique d'init qui est appelée par les tests existants (cherche `PathTable.init();` dans les tests pour confirmer le nom).

- [ ] **Step 4: Lancer le test, vérifier qu'il passe (GREEN)**

Run: `mvn -Dtest=PathTableNearWaterTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Remplacer `nearWater(x,y)` dans `Simulator` par le lookup précalculé**

Dans `Simulator.java`, modifier `growthCooldown` (autour ligne 581) et supprimer `nearWater` + `tileEquals` :

```java
private static byte growthCooldown(GameState s, int treeIdx, int treeType) {
    int base = TreeType.COOLDOWN_NORMAL[treeType] & 0xFF;
    int x = s.treeX[treeIdx] & 0xFF;
    int y = s.treeY[treeIdx] & 0xFF;
    if (PathTable.isNearWater[y * GameState.width + x]) base -= WATER_BOOST[treeType] & 0xFF;
    return (byte) base;
}
```

Supprimer les méthodes `nearWater(int, int)` et `tileEquals(int, int, byte)` devenues mortes (sauf si elles sont utilisées ailleurs — `grep` avant suppression).

- [ ] **Step 6: Lancer toute la suite simulation, vérifier zéro régression**

Run: `mvn -Dtest='Simulator*Test,PathTable*Test' test`
Expected: PASS sur tous les tests existants, dont `SimulatorPlantTickTest`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java \
        src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableNearWaterTest.java
git commit -m "simulator: precompute PathTable.isNearWater, drop runtime tile scan in plantTick"
```

---

## Task 2 : `treeCellIndex` — map cell→treeIdx dans `GameState`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/model/GameState.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java` (suppr. `findTreeAt`, usage dans HARVEST/CHOP/PLANT, MAJ dans `applyPlants`, `applyChops`, `compactDeadTrees`)
- Create: `src/test/java/com/bmrt/cgspring2026/model/GameStateTreeIndexTest.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java`

**Invariant maintenu:** `treeCellIndex[y*W + x] == i` ⇔ `treeX[i] == x && treeY[i] == y && treeHealth[i] > 0`, et sinon `treeCellIndex[y*W + x] == -1`. Indices sur 8 bits suffisent (`MAX_TREES = 128`), donc `byte[]` avec `-1` comme sentinelle.

- [ ] **Step 1: Écrire les tests `treeIndexAt` sur `GameState` (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/model/GameStateTreeIndexTest.java` :

```java
package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateTreeIndexTest {

    @BeforeEach void initGrid() {
        GameState.width  = 8;
        GameState.height = 8;
        GameState.tiles  = new byte[64];
    }

    @Test void addTreeAtRegistersInIndex() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 3, 4, /*size*/ 0, /*health*/ 8);
        assertThat(s.treeIndexAt(3, 4)).isEqualTo(0);
        assertThat(s.treeIndexAt(0, 0)).isEqualTo(-1);
    }

    @Test void treeIndexAtIgnoresDeadTrees() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 3, 4, 0, 8);
        s.killTreeAt(0);
        assertThat(s.treeIndexAt(3, 4)).isEqualTo(-1);
    }

    @Test void compactDeadTreesKeepsIndexConsistent() {
        GameState s = new GameState();
        s.addTree(TreeType.APPLE, 1, 1, 0, 8);   // idx 0
        s.addTree(TreeType.PLUM,  2, 2, 0, 4);   // idx 1
        s.addTree(TreeType.LEMON, 3, 3, 0, 4);   // idx 2
        s.killTreeAt(1);                          // kill du milieu
        s.compactDeadTrees();
        assertThat(s.treeCount).isEqualTo(2);
        // L'arbre de (3,3) a été swap-déplacé en idx 1, (1,1) reste idx 0
        assertThat(s.treeIndexAt(1, 1)).isEqualTo(0);
        assertThat(s.treeIndexAt(3, 3)).isEqualTo(1);
        assertThat(s.treeIndexAt(2, 2)).isEqualTo(-1);
    }

    @Test void copyFromCopiesTreeCellIndex() {
        GameState src = new GameState();
        src.addTree(TreeType.APPLE, 3, 4, 0, 8);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.treeIndexAt(3, 4)).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il échoue (RED)**

Run: `mvn -Dtest=GameStateTreeIndexTest test`
Expected: FAIL — `addTree`, `killTreeAt`, `compactDeadTrees` n'existent pas sur `GameState`.

- [ ] **Step 3: Implémenter le champ + helpers dans `GameState`**

Dans `GameState.java`, ajouter le champ après `treeCooldown` :

```java
/** Cell -> live tree index, -1 if none. byte suffit car MAX_TREES <= 127. Taille W*H, allouée à la demande. */
public byte[] treeCellIndex;
```

Réécrire `treeIndexAt` :

```java
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
```

> `slowTreeIndexAt` est un fallback de transition : tant que tous les callers n'ont pas migré, ne casse rien. Sera supprimé en fin de tâche.

Ajouter les helpers :

```java
private void ensureTreeCellIndex() {
    if (treeCellIndex == null || treeCellIndex.length < width * height) {
        treeCellIndex = new byte[width * height];
        java.util.Arrays.fill(treeCellIndex, (byte) -1);
        for (int i = 0; i < treeCount; i++) {
            if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
        }
    }
}

/** Ajoute un arbre, renvoie son index. L'arbre doit être vivant. */
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

/** Marque un arbre comme mort (health=0) et le retire de l'index. */
public void killTreeAt(int idx) {
    ensureTreeCellIndex();
    treeHealth[idx] = 0;
    treeCellIndex[(treeY[idx] & 0xFF) * width + (treeX[idx] & 0xFF)] = -1;
}

/** Compacte les arbres morts par swap-last. Maintient `treeCellIndex` pour les arbres vivants déplacés. */
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
```

Mettre à jour `copyFrom` : ajouter en fin de méthode :

```java
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
```

Mettre à jour `readTurn` : juste après la boucle de lecture des arbres, reconstruire l'index :

```java
ensureTreeCellIndex();
java.util.Arrays.fill(treeCellIndex, (byte) -1);
for (int i = 0; i < treeCount; i++) {
    if (treeHealth[i] > 0) treeCellIndex[(treeY[i] & 0xFF) * width + (treeX[i] & 0xFF)] = (byte) i;
}
```

- [ ] **Step 4: Lancer les tests `GameStateTreeIndexTest`, vérifier qu'ils passent (GREEN)**

Run: `mvn -Dtest=GameStateTreeIndexTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Migrer `Simulator.applyPlants` pour utiliser `addTree`**

Dans `Simulator.java`, remplacer le bloc de création d'arbre dans `applyPlants` (lignes ~371-378) :

```java
// AVANT :
int newIdx = s.treeCount++;
s.treeType[newIdx]     = (byte) treeType;
s.treeX[newIdx]        = (byte) tx;
s.treeY[newIdx]        = (byte) ty;
s.treeSize[newIdx]     = 0;
s.treeHealth[newIdx]   = (byte) initialPlantHealth(treeType);
s.treeFruits[newIdx]   = 0;
s.treeCooldown[newIdx] = 0;

// APRÈS :
s.addTree((byte) treeType, tx, ty, 0, initialPlantHealth(treeType));
```

Dans le même bloc, remplacer `findTreeAt(s, tx, ty) >= 0` par `s.treeIndexAt(tx, ty) >= 0`.

- [ ] **Step 6: Migrer `applyHarvests` et `applyChops` pour utiliser `treeIndexAt`**

Dans `applyHarvests` (ligne ~224), remplacer :

```java
int treeIdx = findTreeAt(s, tx, ty);
```

par :

```java
int treeIdx = s.treeIndexAt(tx, ty);
```

Idem dans `applyChops` (ligne ~273).

Dans `applyChops`, quand un arbre meurt après damage (ligne ~292-294), remplacer le simple `s.treeHealth[treeIdx] = (byte) Math.max(h, 0);` par une boucle qui appelle `killTreeAt` quand health atteint 0 :

```java
// AVANT :
for (int k = 0; k < shared; k++) {
    int dmg = s.trollCP[harvestSharedTrolls[k]] & 0xFF;
    int h = (s.treeHealth[treeIdx] & 0xFF) - dmg;
    s.treeHealth[treeIdx] = (byte) Math.max(h, 0);
}
if (s.treeHealth[treeIdx] != 0) continue;

// APRÈS :
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
```

- [ ] **Step 7: Remplacer `Simulator.compactDeadTrees` par l'appel à `GameState.compactDeadTrees`**

Dans `Simulator.tick`, remplacer `compactDeadTrees(s);` par `s.compactDeadTrees();`.
Supprimer la méthode `static void compactDeadTrees(GameState s)` du `Simulator` (lignes ~532-551).

- [ ] **Step 8: Supprimer `Simulator.findTreeAt`**

Supprimer la méthode `findTreeAt(GameState, int, int)` (lignes ~257-262). `grep` confirme qu'elle n'est plus utilisée :

Run: `grep -n "findTreeAt" src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
Expected: zéro occurrence.

- [ ] **Step 9: Ajouter un test d'invariant dans `SimulatorInvariantsTest`**

Dans `SimulatorInvariantsTest.java`, ajouter le helper et le test :

```java
private static void assertTreeIndexConsistent(GameState s) {
    int W = GameState.width;
    int H = GameState.height;
    // forward : chaque arbre vivant est indexé à sa position
    for (int i = 0; i < s.treeCount; i++) {
        if (s.treeHealth[i] > 0) {
            int x = s.treeX[i] & 0xFF, y = s.treeY[i] & 0xFF;
            assertThat(s.treeCellIndex[y * W + x] & 0xFF)
                .as("tree %d at (%d,%d)", i, x, y)
                .isEqualTo(i);
        }
    }
    // reverse : chaque cellule indexée pointe vers un arbre vivant à cette position
    for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
            byte v = s.treeCellIndex[y * W + x];
            if (v != -1) {
                int idx = v & 0xFF;
                assertThat(idx).isLessThan(s.treeCount);
                assertThat(s.treeX[idx] & 0xFF).isEqualTo(x);
                assertThat(s.treeY[idx] & 0xFF).isEqualTo(y);
                assertThat(s.treeHealth[idx]).isGreaterThan((byte) 0);
            }
        }
    }
}

@Test void treeIndexRemainsConsistentAfterPlantChopCompact() {
    GameState s = new GameState();
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.trollX[0] = 4; s.trollY[0] = 4;
    s.trollMS[0] = 1; s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollCP[0] = 3;
    s.trollId[0] = 0;
    s.trollInventory[ResourceType.APPLE] = 1; // seed for PLANT
    int[] acts = { Action.plant(0, TreeType.APPLE) };
    Simulator.tick(s, acts, 1);
    assertTreeIndexConsistent(s);
    // Now CHOP it to 0
    s.trollInventory[ResourceType.APPLE] = 0;
    int[] acts2 = { Action.chop(0) };
    // Force-kill : set health low to make a single CHOP fatal
    s.treeHealth[0] = 1;
    Simulator.tick(s, acts2, 1);
    assertTreeIndexConsistent(s);
    assertThat(s.treeCount).isEqualTo(0);
}
```

> Import requis : `import com.bmrt.cgspring2026.action.Action;`, `import com.bmrt.cgspring2026.model.ResourceType;`, `import com.bmrt.cgspring2026.model.TreeType;`.
> Si `Action.plant`/`Action.chop` n'existent pas sous ces noms, vérifie avec `grep -rn "static int plant\|static int chop" src/main/java/com/bmrt/cgspring2026/action/`.

- [ ] **Step 10: Lancer toute la suite, vérifier zéro régression**

Run: `mvn -Dtest='Simulator*Test,GameState*Test' test`
Expected: PASS sur tous les tests.

- [ ] **Step 11: Supprimer le fallback `slowTreeIndexAt` (cleanup)**

Une fois la suite verte, supprimer `slowTreeIndexAt` et simplifier `treeIndexAt` :

```java
public int treeIndexAt(int x, int y) {
    int v = treeCellIndex[y * width + x];
    return (v == -1) ? -1 : (v & 0xFF);
}
```

> Vérifie que `ensureTreeCellIndex` est bien appelé partout où un arbre est touché avant `treeIndexAt` (constructeur ne le fait pas — `readTurn` et les setters le font).

Run: `mvn -Dtest='Simulator*Test,GameState*Test' test`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/model/GameState.java \
        src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/model/GameStateTreeIndexTest.java \
        src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java
git commit -m "simulator: O(1) tree-at-cell via GameState.treeCellIndex (HARVEST/CHOP/PLANT)"
```

---

## Task 3 : `trollCellIndex` + `nextTrollId` — map cell→trollIdx & ID monotone

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/model/GameState.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java` (suppr. `shackOccupied`, `maxTrollId`)
- Create: `src/test/java/com/bmrt/cgspring2026/model/GameStateTrollIndexTest.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java`

**Invariant:** `trollCellIndex[y*W + x] == i` ⇔ `trollX[i] == x && trollY[i] == y && i < trollCount`. Une seule cellule par troll, un seul troll par cellule (les conflits MOVE sont déjà résolus par `resolvePlayerMoves`). `nextTrollId` incrémente à chaque spawn ; jamais décrémenté.

- [ ] **Step 1: Écrire `GameStateTrollIndexTest` (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/model/GameStateTrollIndexTest.java` :

```java
package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateTrollIndexTest {

    @BeforeEach void initGrid() {
        GameState.width = 8;
        GameState.height = 8;
        GameState.tiles = new byte[64];
    }

    @Test void addTrollRegistersInIndexAndAssignsId() {
        GameState s = new GameState();
        int i = s.addTroll(/*player*/ 0, /*x*/ 3, /*y*/ 4, /*ms*/ 1, /*cc*/ 5, /*hp*/ 2, /*cp*/ 1);
        assertThat(i).isEqualTo(0);
        assertThat(s.trollIndexAtCell(3, 4)).isEqualTo(0);
        assertThat(s.trollId[i] & 0xFF).isEqualTo(0);
        int j = s.addTroll(1, 5, 5, 1, 5, 1, 0);
        assertThat(s.trollId[j] & 0xFF).isEqualTo(1);
        assertThat(s.nextTrollId).isEqualTo(2);
    }

    @Test void moveTrollUpdatesIndex() {
        GameState s = new GameState();
        int i = s.addTroll(0, 3, 4, 1, 5, 1, 0);
        s.moveTroll(i, 5, 6);
        assertThat(s.trollIndexAtCell(3, 4)).isEqualTo(-1);
        assertThat(s.trollIndexAtCell(5, 6)).isEqualTo(i);
    }

    @Test void copyFromCopiesTrollIndexAndNextId() {
        GameState src = new GameState();
        src.addTroll(0, 3, 4, 1, 5, 1, 0);
        src.addTroll(1, 5, 5, 1, 5, 1, 0);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.trollIndexAtCell(3, 4)).isEqualTo(0);
        assertThat(dst.trollIndexAtCell(5, 5)).isEqualTo(1);
        assertThat(dst.nextTrollId).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il échoue (RED)**

Run: `mvn -Dtest=GameStateTrollIndexTest test`
Expected: FAIL — `addTroll`, `moveTroll`, `trollIndexAtCell`, `nextTrollId` n'existent pas.

- [ ] **Step 3: Implémenter le champ + helpers dans `GameState`**

Dans `GameState.java`, ajouter (après les champs troll existants) :

```java
/** Cell -> troll index, -1 if none. byte suffit car MAX_TROLLS = 32. */
public byte[] trollCellIndex;

/** Prochain ID à attribuer ; égal au max des IDs alloués + 1. Monotone. */
public int nextTrollId;
```

Helpers :

```java
private void ensureTrollCellIndex() {
    if (trollCellIndex == null || trollCellIndex.length < width * height) {
        trollCellIndex = new byte[width * height];
        java.util.Arrays.fill(trollCellIndex, (byte) -1);
        for (int i = 0; i < trollCount; i++) {
            trollCellIndex[(trollY[i] & 0xFF) * width + (trollX[i] & 0xFF)] = (byte) i;
        }
    }
}

public int trollIndexAtCell(int x, int y) {
    if (trollCellIndex == null) return -1;
    byte v = trollCellIndex[y * width + x];
    return (v == -1) ? -1 : (v & 0xFF);
}

/** Spawne un troll à (x,y). ID auto-incrémenté. Retourne l'index. */
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
    trollCellIndex[y * width + x] = (byte) idx;
    return idx;
}

/** Bouge un troll vers (x,y). Maintient `trollCellIndex`. */
public void moveTroll(int idx, int x, int y) {
    ensureTrollCellIndex();
    int W = width;
    trollCellIndex[(trollY[idx] & 0xFF) * W + (trollX[idx] & 0xFF)] = -1;
    trollX[idx] = (byte) x;
    trollY[idx] = (byte) y;
    trollCellIndex[y * W + x] = (byte) idx;
}
```

Mettre à jour `copyFrom` : à la fin, ajouter :

```java
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
```

Mettre à jour `readTurn` : juste après la boucle troll, reconstruire l'index ET `nextTrollId` :

```java
ensureTrollCellIndex();
java.util.Arrays.fill(trollCellIndex, (byte) -1);
int maxId = -1;
for (int i = 0; i < trollCount; i++) {
    trollCellIndex[(trollY[i] & 0xFF) * width + (trollX[i] & 0xFF)] = (byte) i;
    int id = trollId[i] & 0xFF;
    if (id > maxId) maxId = id;
}
nextTrollId = maxId + 1;
```

- [ ] **Step 4: Lancer `GameStateTrollIndexTest`, vérifier qu'il passe (GREEN)**

Run: `mvn -Dtest=GameStateTrollIndexTest test`
Expected: PASS (3 tests).

- [ ] **Step 5: Migrer `Simulator.applyTrains` pour utiliser `addTroll` + `trollIndexAtCell`**

Dans `Simulator.java`, remplacer le corps d'`applyTrains` (lignes ~398-434) :

```java
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
```

> On garde `countOwnTrolls` privé pour l'instant ; T7 le dédupliquera.

Supprimer les méthodes `shackOccupied`, `maxTrollId`, et l'ancienne `canAffordTrain` (la nouvelle version avec count précalculé `canAffordTrainWithCount` les remplace).

- [ ] **Step 6: Migrer `Simulator.applyMoves` pour utiliser `moveTroll`**

Dans `resolvePlayerMoves`, partout où le code fait :

```java
occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
s.trollX[idx] = (byte) moveTargetX[idx];
s.trollY[idx] = (byte) moveTargetY[idx];
occupied[destCell] = true;
```

et l'équivalent en cycle, **garder le maintien d'`occupied[]` mais ajouter** `s.moveTroll(idx, moveTargetX[idx], moveTargetY[idx])` au lieu d'écrire `trollX/trollY` à la main :

```java
// pour le path "single-target"
occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
s.moveTroll(idx, moveTargetX[idx], moveTargetY[idx]);
occupied[destCell] = true;

// pour le path "cycle"
for (int c = 0; c < len; c++) {
    int idx = cycle[c];
    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
}
for (int c = 0; c < len; c++) {
    int idx = cycle[c];
    s.moveTroll(idx, moveTargetX[idx], moveTargetY[idx]);
    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = true;
    resolverDone[idx] = true;
}
```

> ⚠️ `moveTroll` met à jour `trollX`/`trollY` ET `trollCellIndex` en une seule passe. Ne pas écrire `trollX[idx] = ...` séparément après.

- [ ] **Step 7: Ajouter le check d'invariant `trollCellIndex` dans `SimulatorInvariantsTest`**

Dans `SimulatorInvariantsTest.java`, ajouter :

```java
private static void assertTrollIndexConsistent(GameState s) {
    int W = GameState.width;
    int H = GameState.height;
    for (int i = 0; i < s.trollCount; i++) {
        int x = s.trollX[i] & 0xFF, y = s.trollY[i] & 0xFF;
        assertThat(s.trollCellIndex[y * W + x] & 0xFF)
            .as("troll %d at (%d,%d)", i, x, y)
            .isEqualTo(i);
    }
    int counted = 0;
    for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
            byte v = s.trollCellIndex[y * W + x];
            if (v != -1) {
                int idx = v & 0xFF;
                assertThat(idx).isLessThan(s.trollCount);
                assertThat(s.trollX[idx] & 0xFF).isEqualTo(x);
                assertThat(s.trollY[idx] & 0xFF).isEqualTo(y);
                counted++;
            }
        }
    }
    assertThat(counted).isEqualTo(s.trollCount);
}

@Test void trollIndexRemainsConsistentAfterTrainAndMove() {
    GameState s = new GameState();
    s.shackInventory[ResourceType.PLUM]  = 100;
    s.shackInventory[ResourceType.LEMON] = 100;
    s.shackInventory[ResourceType.APPLE] = 100;
    s.shackInventory[ResourceType.IRON]  = 100;
    // TRAIN
    int[] acts = { Action.train(1, 1, 1, 0) };
    Simulator.tick(s, acts, 1);
    assertTrollIndexConsistent(s);
    assertThat(s.trollCount).isEqualTo(1);
    // MOVE
    int t = 0;
    int dx = (s.trollX[t] & 0xFF) + 1, dy = s.trollY[t] & 0xFF;
    // assume (dx,dy) is grass — adjuste si besoin via la grille définie en @BeforeEach
    int[] acts2 = { Action.move(t, dx, dy) };
    Simulator.tick(s, acts2, 1);
    assertTrollIndexConsistent(s);
}
```

> Si le shack `0` se trouve à `(1,1)` dans la grille de `@BeforeEach`, alors `(dx,dy)` peut sortir grille — adapte l'init de la grille ou le spawn pour avoir un déplacement valide.

- [ ] **Step 8: Activer `DEBUG_INVARIANTS` automatiquement dans `Simulator.tick` quand on est en test**

Modifier `Simulator.tick` pour appeler les checks à la fin si le drapeau est actif :

```java
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
    s.compactDeadTrees();
    s.turn++;
    if (DEBUG_INVARIANTS) checkInvariants(s);
}

static void checkInvariants(GameState s) {
    int W = GameState.width;
    for (int i = 0; i < s.trollCount; i++) {
        int x = s.trollX[i] & 0xFF, y = s.trollY[i] & 0xFF;
        if ((s.trollCellIndex[y * W + x] & 0xFF) != i) {
            throw new IllegalStateException("trollCellIndex inconsistent at troll " + i);
        }
    }
    for (int i = 0; i < s.treeCount; i++) {
        if (s.treeHealth[i] > 0) {
            int x = s.treeX[i] & 0xFF, y = s.treeY[i] & 0xFF;
            if ((s.treeCellIndex[y * W + x] & 0xFF) != i) {
                throw new IllegalStateException("treeCellIndex inconsistent at tree " + i);
            }
        }
    }
}
```

- [ ] **Step 9: Lancer toute la suite, vérifier zéro régression**

Run: `mvn -Dtest='Simulator*Test,GameState*Test' test`
Expected: PASS sur tous les tests existants + les nouveaux invariants.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/model/GameState.java \
        src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/model/GameStateTrollIndexTest.java \
        src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java
git commit -m "simulator: O(1) troll-at-cell + monotonic nextTrollId (drop shackOccupied/maxTrollId scans)"
```

---

## Task 4 : Cycle detection via `trollCellIndex` dans `resolvePlayerMoves`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java` (`resolvePlayerMoves`)

**Pourquoi:** Les deux scans imbriqués (lignes ~155-159 puis ~173-177) cherchent « quel troll est sur la cellule `destCell` ? ». Avec `trollCellIndex` maintenu par `moveTroll` (T3), ce lookup devient O(1). Garder le filtre « undone + même player » via les flags existants.

- [ ] **Step 1: Écrire un test ciblé cycle 3-trolls (RED — devrait déjà passer, mais sert de bouée)**

Dans `SimulatorMoveTest.java` (ou un fichier dédié si nécessaire), vérifie qu'un test 3-cycle existe. `grep -n "cycle\|3.troll" src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java`. S'il existe, sauter. Sinon, ajouter :

```java
@Test void threeTrollCycleResolves() {
    GameState s = new GameState();
    // 3 trolls en triangle, chacun veut la case du suivant
    int a = placeTroll(s, 0, 2, 2, 1);
    int b = placeTroll(s, 0, 3, 2, 1);
    int c = placeTroll(s, 0, 2, 3, 1);
    int[] acts = {
        Action.move(a, 3, 2),
        Action.move(b, 2, 3),
        Action.move(c, 2, 2),
    };
    Simulator.tick(s, acts, 3);
    assertThat(s.trollX[a] & 0xFF).isEqualTo(3);
    assertThat(s.trollY[a] & 0xFF).isEqualTo(2);
    assertThat(s.trollX[b] & 0xFF).isEqualTo(2);
    assertThat(s.trollY[b] & 0xFF).isEqualTo(3);
    assertThat(s.trollX[c] & 0xFF).isEqualTo(2);
    assertThat(s.trollY[c] & 0xFF).isEqualTo(2);
}
```

- [ ] **Step 2: Vérifier que le test passe (GREEN — pré-refactor)**

Run: `mvn -Dtest=SimulatorMoveTest test`
Expected: PASS.

- [ ] **Step 3: Refactorer la cycle detection pour utiliser `s.trollIndexAtCell`**

Dans `resolvePlayerMoves` (Simulator.java), remplacer les deux blocs O(count) imbriqués :

```java
// AVANT (boucle 1) :
int next = -1;
for (int k = 0; k < count; k++) {
    int j = resolverIdx[k];
    if (resolverDone[j]) continue;
    if ((s.trollY[j] & 0xFF) * W + (s.trollX[j] & 0xFF) == destCell) { next = j; break; }
}

// APRÈS :
int targetX = destCell % W, targetY = destCell / W;
int next = s.trollIndexAtCell(targetX, targetY);
if (next >= 0) {
    if ((s.trollPlayer[next] & 0xFF) != player || resolverDone[next]) next = -1;
}
```

Faire la même substitution dans le second bloc (lignes ~173-177).

> Pourquoi le filtre `player + done` reste-t-il nécessaire ? Parce qu'un cycle ne peut impliquer que des trolls du joueur courant, non résolus. `trollCellIndex` voit tous les trolls — il faut filtrer.

- [ ] **Step 4: Lancer la suite move + integration**

Run: `mvn -Dtest='SimulatorMoveTest,SimulatorTickIntegrationTest,SimulatorInvariantsTest' test`
Expected: PASS — comportement identique.

- [ ] **Step 5: Lancer toute la suite simulation**

Run: `mvn -Dtest='Simulator*Test' test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java
git commit -m "simulator: O(1) cycle-step lookup via GameState.trollIndexAtCell"
```

---

## Task 5 : Reset partiel de `freq[]` dans `resolvePlayerMoves`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`

**Pourquoi:** À chaque itération du `while (progressed)`, on remet à zéro `freq[]` (taille W*H) alors que seules `count` cellules sont écrites. Solution : tracer les cellules touchées dans un petit buffer, ne resetter que celles-ci.

- [ ] **Step 1: Modifier `resolvePlayerMoves` pour tracker les cellules touchées**

Dans `Simulator.java`, ajouter un buffer scratch en haut du fichier (à côté des autres `private static final`) :

```java
private static final int[] freqDirty = new int[GameState.MAX_TROLLS];
```

Dans `resolvePlayerMoves`, remplacer le bloc :

```java
// AVANT :
for (int i = 0; i < freq.length; i++) freq[i] = 0;
for (int k = 0; k < count; k++) {
    int idx = resolverIdx[k];
    if (resolverDone[idx]) continue;
    freq[moveTargetY[idx] * W + moveTargetX[idx]]++;
}

// APRÈS :
int dirty = 0;
for (int k = 0; k < count; k++) {
    int idx = resolverIdx[k];
    if (resolverDone[idx]) continue;
    int cell = moveTargetY[idx] * W + moveTargetX[idx];
    if (freq[cell] == 0) freqDirty[dirty++] = cell;
    freq[cell]++;
}
```

Et ajouter à la **fin du `while (progressed)`** (juste avant le `}` de la boucle, après le code de cycle) :

```java
for (int d = 0; d < dirty; d++) freq[freqDirty[d]] = 0;
```

> ⚠️ Le reset doit avoir lieu avant la prochaine itération **ou** avant la sortie de fonction, sinon une exécution suivante hérite de valeurs. La position la plus sûre est en fin de boucle. Si `progressed = false` casse la boucle sans repasser, le buffer reste sale → l'option robuste : déclarer `int dirty = 0;` AVANT la boucle, et resetter après chaque utilisation.

Version finale propre :

```java
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
    // ... (single-target moves, cycle detection — INCHANGÉ)
    // Cleanup avant la prochaine itération ou l'exit
    for (int d = 0; d < dirty; d++) freq[freqDirty[d]] = 0;
    if (!progressed && !allowBlocked) {
        allowBlocked = true;
        progressed = true;
    }
}
```

- [ ] **Step 2: Lancer toute la suite simulation**

Run: `mvn -Dtest='Simulator*Test' test`
Expected: PASS — aucun changement de comportement.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java
git commit -m "simulator: partial reset of freq[] in resolver (touched cells only)"
```

---

## Task 6 : Reset partiel de `occupied[]` dans `resolvePlayerMoves`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`

**Pourquoi:** Idem que `freq[]` — on n'écrit que `count` positions, mais on reset tout `W*H`.

- [ ] **Step 1: Ajouter un buffer scratch `occupiedDirty[]`**

Dans `Simulator.java`, juste à côté de `freqDirty` :

```java
private static final int[] occupiedDirty = new int[GameState.MAX_TROLLS];
```

- [ ] **Step 2: Remplacer le reset initial par un reset partiel basé sur les positions courantes**

Dans `resolvePlayerMoves`, remplacer :

```java
// AVANT :
boolean[] occupied = ensureOccupiedBuffer(W * GameState.height);
for (int i = 0; i < occupied.length; i++) occupied[i] = false;
for (int k = 0; k < count; k++) {
    int idx = resolverIdx[k];
    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = true;
}

// APRÈS :
boolean[] occupied = ensureOccupiedBuffer(W * GameState.height);
int occDirty = 0;
for (int k = 0; k < count; k++) {
    int idx = resolverIdx[k];
    int cell = (s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF);
    occupied[cell] = true;
    occupiedDirty[occDirty++] = cell;
}
```

À la **fin de la fonction `resolvePlayerMoves`** (après la boucle `while`), ajouter le cleanup :

```java
// Restaure occupied[] à false pour les cellules touchées (incluant celles libérées en cours).
// Comme on a pu écrire dans des cellules destination différentes pendant la résolution, on
// doit aussi décocher les positions finales actuelles des trolls du joueur, qui peuvent
// différer des positions initiales.
for (int d = 0; d < occDirty; d++) occupied[occupiedDirty[d]] = false;
for (int k = 0; k < count; k++) {
    int idx = resolverIdx[k];
    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
}
```

> ⚠️ Subtilité : pendant la résolution, on `occupied[destCell] = true` sur des cellules **pas** dans la liste initiale `occupiedDirty`. Le cleanup doit donc considérer **les positions finales aussi**. La double passe ci-dessus est sûre — chaque cellule est mise à false 0 ou 1 fois, et la boucle est O(count) au lieu de O(W*H).

- [ ] **Step 3: Vérifier la sortie multi-player**

`resolvePlayerMoves` est appelé une fois par joueur. Le second appel doit voir `occupied[]` complètement à zéro. Tester via `SimulatorInvariantsTest` (les invariants vérifient les positions finales).

Run: `mvn -Dtest='Simulator*Test' test`
Expected: PASS.

- [ ] **Step 4: Test de stress 2-joueurs**

Ajouter dans `SimulatorMoveTest.java` :

```java
@Test void twoPlayersMoveSimultaneously() {
    GameState s = new GameState();
    int p0 = placeTroll(s, 0, 1, 1, 1);
    int p1 = placeTroll(s, 1, 6, 4, 1);
    int[] acts = {
        Action.move(p0, 2, 1),
        Action.move(p1, 5, 4),
    };
    Simulator.tick(s, acts, 2);
    assertThat(s.trollX[p0] & 0xFF).isEqualTo(2);
    assertThat(s.trollX[p1] & 0xFF).isEqualTo(5);
}
```

Run: `mvn -Dtest=SimulatorMoveTest#twoPlayersMoveSimultaneously test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java
git commit -m "simulator: partial reset of occupied[] in resolver (touched cells only)"
```

---

## Task 7 : `countOwnTrolls` dédupliqué dans `applyTrains`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`

**Pourquoi:** T3 a déjà refactoré `applyTrains` pour calculer `nUnits` une seule fois (`chosenN`). Cette tâche est essentiellement un **audit** que cette déduplication tient et que `countOwnTrolls` n'est plus appelé deux fois par TRAIN.

- [ ] **Step 1: Vérifier l'audit**

Run: `grep -n "countOwnTrolls" src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
Expected: 1 seul appel dans `applyTrains` (à l'intérieur de la boucle player, avant `canAffordTrainWithCount`).

- [ ] **Step 2: Si plus d'un appel subsiste, refactorer**

Si `grep` montre `> 1` appel, factoriser ainsi : la valeur calculée pour le check d'affordability doit être conservée et passée à la déduction du coût. Le pattern ressemble à :

```java
int nUnits = countOwnTrolls(s, p);  // UN SEUL APPEL
if (!canAffordTrainWithCount(s, p, ms, cc, hp, cp, nUnits)) continue;
// ... plus tard, réutiliser nUnits, pas re-appeler countOwnTrolls
```

- [ ] **Step 3: Lancer la suite train**

Run: `mvn -Dtest='SimulatorTrainTest,SimulatorTickIntegrationTest' test`
Expected: PASS.

- [ ] **Step 4: Commit (si changement, sinon skip)**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java
git commit -m "simulator: dedupe countOwnTrolls call in TRAIN (compute once, reuse)"
```

---

## Task 8 : `trollCarryTotal[]` pré-calculé dans `GameState`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/model/GameState.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java` (HARVEST, CHOP, PICK, DROP, MINE)
- Create: `src/test/java/com/bmrt/cgspring2026/model/GameStateCarryTotalTest.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java`

**Invariant:** `trollCarryTotal[i] == sum(trollInventory[i*COUNT + r] & 0xFF for r in 0..COUNT-1)` pour `i < trollCount`.

**Stratégie:** maintenir incrémentalement à chaque modification d'inventaire. Toutes les modifications passent par 4 patterns identifiables :
- `s.trollInventory[invBase + r]++` (HARVEST, CHOP, PICK, MINE) → `trollCarryTotal[idx]++`
- `s.trollInventory[invBase + r] += k` (MINE) → `trollCarryTotal[idx] += k`
- `s.trollInventory[invBase + r] = 0` (DROP, troll spawn) → recalcul ou tracking direct
- `s.trollInventory[invBase + r]--` (PLANT consume seed) → `trollCarryTotal[idx]--`

On factorise dans des helpers `GameState.addToInventory(idx, r, k)` et `GameState.clearInventory(idx)` pour garantir la cohérence.

- [ ] **Step 1: Écrire `GameStateCarryTotalTest` (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/model/GameStateCarryTotalTest.java` :

```java
package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateCarryTotalTest {

    @BeforeEach void initGrid() {
        GameState.width = 8;
        GameState.height = 8;
        GameState.tiles = new byte[64];
    }

    @Test void addToInventoryUpdatesCarryTotal() {
        GameState s = new GameState();
        int i = s.addTroll(0, 1, 1, 1, 5, 1, 0);
        assertThat(s.trollCarryTotal[i]).isEqualTo(0);
        s.addToInventory(i, ResourceType.APPLE, 2);
        assertThat(s.trollCarryTotal[i]).isEqualTo(2);
        s.addToInventory(i, ResourceType.WOOD, 1);
        assertThat(s.trollCarryTotal[i]).isEqualTo(3);
    }

    @Test void clearInventoryResetsCarryTotal() {
        GameState s = new GameState();
        int i = s.addTroll(0, 1, 1, 1, 5, 1, 0);
        s.addToInventory(i, ResourceType.APPLE, 2);
        s.addToInventory(i, ResourceType.WOOD, 1);
        s.clearInventory(i);
        assertThat(s.trollCarryTotal[i]).isEqualTo(0);
        for (int r = 0; r < ResourceType.COUNT; r++) {
            assertThat(s.trollInventory[i * ResourceType.COUNT + r]).isEqualTo((byte) 0);
        }
    }

    @Test void copyFromCopiesCarryTotal() {
        GameState src = new GameState();
        int i = src.addTroll(0, 1, 1, 1, 5, 1, 0);
        src.addToInventory(i, ResourceType.APPLE, 2);
        GameState dst = new GameState();
        dst.copyFrom(src);
        assertThat(dst.trollCarryTotal[i]).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il échoue (RED)**

Run: `mvn -Dtest=GameStateCarryTotalTest test`
Expected: FAIL — `trollCarryTotal`, `addToInventory`, `clearInventory` n'existent pas.

- [ ] **Step 3: Implémenter le champ + helpers dans `GameState`**

Dans `GameState.java`, ajouter :

```java
/** Somme courante de trollInventory[i*COUNT..i*COUNT+COUNT-1]. Indexé par troll idx. */
public final int[] trollCarryTotal = new int[MAX_TROLLS];

public void addToInventory(int idx, int resource, int delta) {
    int base = idx * ResourceType.COUNT;
    int cur = trollInventory[base + resource] & 0xFF;
    trollInventory[base + resource] = (byte) (cur + delta);
    trollCarryTotal[idx] += delta;
}

public void clearInventory(int idx) {
    int base = idx * ResourceType.COUNT;
    for (int r = 0; r < ResourceType.COUNT; r++) trollInventory[base + r] = 0;
    trollCarryTotal[idx] = 0;
}
```

Mettre à jour `addTroll` : juste avant le `return idx;`, ajouter :

```java
trollCarryTotal[idx] = 0;
```

Mettre à jour `copyFrom` : juste avant les autres `System.arraycopy` troll, ajouter :

```java
System.arraycopy(src.trollCarryTotal, 0, trollCarryTotal, 0, MAX_TROLLS);
```

Mettre à jour `readTurn` : juste après la boucle troll inventory, ajouter :

```java
for (int i = 0; i < trollCount; i++) {
    int base = i * ResourceType.COUNT;
    int tot = 0;
    for (int r = 0; r < ResourceType.COUNT; r++) tot += trollInventory[base + r] & 0xFF;
    trollCarryTotal[i] = tot;
}
```

- [ ] **Step 4: Lancer `GameStateCarryTotalTest`, vérifier qu'il passe (GREEN)**

Run: `mvn -Dtest=GameStateCarryTotalTest test`
Expected: PASS.

- [ ] **Step 5: Migrer `applyHarvests` (Simulator.java)**

Remplacer le bloc dans la boucle HARVEST :

```java
// AVANT :
int total = 0;
for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
if (total >= cc) continue;
s.trollInventory[invBase + type]++;
if (s.treeFruits[treeIdx] > 0) s.treeFruits[treeIdx]--;

// APRÈS :
if (s.trollCarryTotal[trollIdx] >= cc) continue;
s.addToInventory(trollIdx, type, 1);
if (s.treeFruits[treeIdx] > 0) s.treeFruits[treeIdx]--;
```

> `invBase` peut désormais être supprimé localement si plus utilisé après cette substitution.

- [ ] **Step 6: Migrer `applyChops`**

Pour la distribution de bois :

```java
// AVANT :
int invBase = trollIdx * ResourceType.COUNT;
int cc = s.trollCC[trollIdx] & 0xFF;
int total = 0;
for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
if (total >= cc) continue;
s.trollInventory[invBase + ResourceType.WOOD]++;
remaining--;

// APRÈS :
int cc = s.trollCC[trollIdx] & 0xFF;
if (s.trollCarryTotal[trollIdx] >= cc) continue;
s.addToInventory(trollIdx, ResourceType.WOOD, 1);
remaining--;
```

- [ ] **Step 7: Migrer `applyPicks`**

```java
// AVANT :
int cc = s.trollCC[idx] & 0xFF;
int invBase = idx * ResourceType.COUNT;
int total = 0;
for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
if (total >= cc) continue;
int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
if (s.shackInventory[shackBase + type] <= 0) continue;
s.shackInventory[shackBase + type]--;
s.trollInventory[invBase + type]++;

// APRÈS :
int cc = s.trollCC[idx] & 0xFF;
if (s.trollCarryTotal[idx] >= cc) continue;
int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
if (s.shackInventory[shackBase + type] <= 0) continue;
s.shackInventory[shackBase + type]--;
s.addToInventory(idx, type, 1);
```

- [ ] **Step 8: Migrer `applyDrops`**

```java
// AVANT :
int invBase = idx * ResourceType.COUNT;
int total = 0;
for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
if (total == 0) continue;
int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
for (int r = 0; r < ResourceType.COUNT; r++) {
    s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
    s.trollInventory[invBase + r] = 0;
}

// APRÈS :
if (s.trollCarryTotal[idx] == 0) continue;
int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
int invBase = idx * ResourceType.COUNT;
for (int r = 0; r < ResourceType.COUNT; r++) {
    s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
}
s.clearInventory(idx);
```

- [ ] **Step 9: Migrer `applyMines`**

```java
// AVANT :
int cc = s.trollCC[idx] & 0xFF;
int invBase = idx * ResourceType.COUNT;
int total = 0;
for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
int free = cc - total;
int gain = Math.min(cp, free);
if (gain <= 0) continue;
s.trollInventory[invBase + ResourceType.IRON] += gain;

// APRÈS :
int cc = s.trollCC[idx] & 0xFF;
int free = cc - s.trollCarryTotal[idx];
int gain = Math.min(cp, free);
if (gain <= 0) continue;
s.addToInventory(idx, ResourceType.IRON, gain);
```

- [ ] **Step 10: Migrer la consommation de seed dans `applyPlants`**

```java
// AVANT :
for (int k = 0; k < sharedCount; k++) {
    int jdx = sharedIdx[k];
    int jtype = sharedType[k];
    s.trollInventory[jdx * ResourceType.COUNT + jtype]--;
}

// APRÈS :
for (int k = 0; k < sharedCount; k++) {
    int jdx = sharedIdx[k];
    int jtype = sharedType[k];
    s.addToInventory(jdx, jtype, -1);
}
```

- [ ] **Step 11: Ajouter un invariant `trollCarryTotal` dans `SimulatorInvariantsTest`**

Dans `SimulatorInvariantsTest.java`, ajouter :

```java
private static void assertCarryTotalConsistent(GameState s) {
    for (int i = 0; i < s.trollCount; i++) {
        int base = i * ResourceType.COUNT;
        int sum = 0;
        for (int r = 0; r < ResourceType.COUNT; r++) sum += s.trollInventory[base + r] & 0xFF;
        assertThat(s.trollCarryTotal[i]).as("troll %d carryTotal", i).isEqualTo(sum);
    }
}
```

Et dans `Simulator.checkInvariants` (ajouté en T3), étendre :

```java
static void checkInvariants(GameState s) {
    // ... (existants : tree+troll cell indexes)
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
```

- [ ] **Step 12: Lancer toute la suite simulation**

Run: `mvn -Dtest='Simulator*Test,GameState*Test' test`
Expected: PASS sur tous les tests.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/model/GameState.java \
        src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java \
        src/test/java/com/bmrt/cgspring2026/model/GameStateCarryTotalTest.java \
        src/test/java/com/bmrt/cgspring2026/simulation/SimulatorInvariantsTest.java
git commit -m "simulator: precompute trollCarryTotal[], drop per-action inventory-sum loops"
```

---

## Task 9 : Vérification finale, FileBuilder, mesure perf

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/builder/FileBuilder.java` (si nécessaire)

**Pourquoi:** S'assurer que `FileBuilder` (qui fusionne en `Player.java`) intègre bien les nouveaux champs/méthodes, et mesurer rapidement le gain.

- [ ] **Step 1: Vérifier `FileBuilder` ne casse rien**

Si `FileBuilder` est piloté par convention (lecture de tous les `.java` du package), aucun changement nécessaire. Sinon, mettre à jour la liste statique.

Run: `mvn -Dtest=FileBuilderTest test` (si présent) ou `mvn compile`.

- [ ] **Step 2: Vérifier que la build complète passe**

Run: `mvn clean test`
Expected: BUILD SUCCESS sur toute la suite.

- [ ] **Step 3: Mesure micro (optionnel)**

Créer une mesure rapide dans un main jetable ou un test `@Disabled` qui simule N ticks et chronomètre. Cible : <50% du temps initial sur N=10000 ticks dans un scénario dense (8 trolls, 30 arbres).

> Cette mesure est diagnostique, pas une assertion CI. Documente le gain dans le commit message.

- [ ] **Step 4: Commit final (si changement FileBuilder ou mesure)**

```bash
git add src/main/java/com/bmrt/cgspring2026/builder/FileBuilder.java
git commit -m "simulator: ensure FileBuilder picks up new GameState fields"
```

---

## Récapitulatif des invariants

À tout moment, après un appel à `Simulator.tick`, **les conditions suivantes doivent être vraies** :

| Invariant | Vérifié par |
|---|---|
| `treeCellIndex[y*W+x] == i` ssi `treeX[i]==x ∧ treeY[i]==y ∧ treeHealth[i]>0` | `assertTreeIndexConsistent` |
| `trollCellIndex[y*W+x] == i` ssi `trollX[i]==x ∧ trollY[i]==y ∧ i<trollCount` | `assertTrollIndexConsistent` |
| `trollCarryTotal[i] == sum(trollInventory[i*COUNT..])` | `assertCarryTotalConsistent` |
| `nextTrollId > max(trollId[i] & 0xFF for i<trollCount)` (ou ≥ 0 si aucun troll) | `nextTrollId` non décroissant |
| `isNearWater[c]` constant tout au long de la partie (figé à `init`) | implicite |

Les trois premiers invariants sont vérifiés par `Simulator.checkInvariants` quand `DEBUG_INVARIANTS=true`. Les tests d'invariant activent ce drapeau dans leur `@BeforeEach`.

---

## Self-Review

**Spec coverage:**
- ✅ Point 1 (findTreeAt → O(1) map cell→tree) : Task 2
- ✅ Point 2 (reset partiel freq[]) : Task 5
- ✅ Point 3 (reset partiel occupied[]) : Task 6
- ✅ Point 4 (countOwnTrolls dédupliqué) : Task 7 (réalisé en partie dans T3, audité en T7)
- ✅ Point 5 (trollCarryTotal pré-calculé) : Task 8
- ✅ Point 6 (cycle detection O(1) via map cell→troll) : Task 4
- ✅ Point 7 (isNearWater[] précalculé) : Task 1
- ✅ Point 8 (maxTrollId → nextTrollId champ) : Task 3
- ✅ Point 9 (shackOccupied via map cell→troll) : Task 3

**Placeholder scan:** Aucun placeholder TBD/TODO/«similar to». Tous les snippets sont autonomes et complets.

**Type consistency:**
- `treeCellIndex` : `byte[]`, sentinelle `-1`, méthodes `addTree`/`killTreeAt`/`treeIndexAt`/`compactDeadTrees` cohérentes.
- `trollCellIndex` : `byte[]`, sentinelle `-1`, méthodes `addTroll`/`moveTroll`/`trollIndexAtCell` cohérentes.
- `trollCarryTotal` : `int[]`, méthodes `addToInventory(idx, r, delta)` et `clearInventory(idx)` cohérentes partout.
- `isNearWater` : `boolean[]`, lookup direct par `y*W+x`.
- `DEBUG_INVARIANTS` : `static boolean`, drapeau public testable.
- `nextTrollId` : `int`, monotone croissant, reconstruit dans `readTurn`.

**Dépendances inter-tâches:**
- T2 indépendante (sauf de T0).
- T3 indépendante (sauf de T0).
- T4 dépend de T3 (utilise `trollIndexAtCell`).
- T5, T6 indépendantes l'une de l'autre, indépendantes des autres.
- T7 audit-only après T3.
- T8 dépend de T3 (utilise `addTroll`).

Plan complet et auto-cohérent.

