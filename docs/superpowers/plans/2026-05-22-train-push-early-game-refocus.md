# Train Push Early-Game Refocus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Avant le tour `TRAIN_PUSH_TURN_CUTOFF` (= 150), supprimer les gènes CUT de la pop, ne plus chop après PLANT, et récompenser la présence d'arbres PLUM/LEMON/APPLE proches du shack allié.

**Architecture:** Gating local par `state.turn < CUTOFF` dans 6 zones (fitness, initRandom, initWarm, initFromPrevBest, mutateInsertCut, TrollPolicy PLANT). Aucune nouvelle classe. Pattern existant (cf. `mutateInsertMine`).

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. Tests : `mvn -q test -Dtest=<ClassName>`. Regen Player.java pour soumission : `mvn -q exec:java -Dexec.mainClass=com.bmrt.cgspring2026.builder.FileBuilder -Dexec.args=src/main/java/com/bmrt/cgspring2026/Player.java`.

**Spec source :** [`docs/superpowers/specs/2026-05-22-train-push-early-game-refocus-design.md`](../specs/2026-05-22-train-push-early-game-refocus-design.md)

---

## File Structure

**Modified files :**
- `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java` — fitness `nearTreeBonus`
- `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java` — `initRandom` gate, `initWarm` split, `initFromPrevBest` filtre CUT, `mutateInsertCut` early return
- `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java` — bloc PLANT bifurqué
- `src/main/java/com/bmrt/cgspring2026/Player.java` — regen via FileBuilder (étape finale)

**New test files :**
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorNearTreeTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitWarmEarlyTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantCutoffTest.java`

---

## Task 1 — Fitness `nearTreeBonus` (Section 1 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorNearTreeTest.java`

- [ ] **Step 1.1 : Écrire le test qui échoue**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorNearTreeTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorNearTreeTest {

    @BeforeEach void grid() {
        // Shack en (1,1). Carte 10x6 grass.
        String[] rows = {
            "..........",
            ".0........",
            "..........",
            "..........",
            "..........",
            ".........."
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
        s.turn = 10;
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

    private void addTree(GameState s, int x, int y, byte type) {
        int i = s.treeCount++;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeHealth[i] = 5;
        s.treeType[i] = type;
        s.treeCellIndex[y * GameState.width + x] = (byte) i;
    }

    @Test void plumNearShackAddsAlphaNearTree() {
        GameState withPlum = emptyState();
        addTree(withPlum, 3, 1, TreeType.PLUM);   // d=2 du shack (1,1)
        GameState withoutTrees = emptyState();

        double fitWith    = fitnessOf(withPlum);
        double fitWithout = fitnessOf(withoutTrees);

        assertThat(fitWith - fitWithout).isGreaterThanOrEqualTo(GenomeEvaluator.ALPHA_NEAR_TREE - 1e-9);
    }

    @Test void threeTypesNearShackAddsThreeAlpha() {
        GameState s = emptyState();
        addTree(s, 2, 1, TreeType.PLUM);
        addTree(s, 3, 1, TreeType.LEMON);
        addTree(s, 4, 1, TreeType.APPLE);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit - fitRef).isGreaterThanOrEqualTo(3 * GenomeEvaluator.ALPHA_NEAR_TREE - 1e-9);
        assertThat(fit - fitRef).isLessThanOrEqualTo(3 * GenomeEvaluator.ALPHA_NEAR_TREE + 1e-9);
    }

    @Test void duplicateTypeNotDoubleCounted() {
        GameState s = emptyState();
        addTree(s, 2, 1, TreeType.PLUM);
        addTree(s, 3, 1, TreeType.PLUM);  // 2e PLUM proche : pas de bonus additionnel

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit - fitRef).isLessThanOrEqualTo(GenomeEvaluator.ALPHA_NEAR_TREE + 1e-9);
    }

    @Test void treeBeyondDistance5IsIgnored() {
        GameState s = emptyState();
        // shack (1,1), arbre en (8,5) : dist Manhattan = 7+4 = 11. Hors zone.
        addTree(s, 8, 5, TreeType.PLUM);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void bananaNearShackIsIgnored() {
        GameState s = emptyState();
        addTree(s, 3, 1, TreeType.BANANA);

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void deadTreeIsIgnored() {
        GameState s = emptyState();
        int i = s.treeCount++;
        s.treeX[i] = 3; s.treeY[i] = 1;
        s.treeHealth[i] = 0;  // mort
        s.treeType[i] = TreeType.PLUM;

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(emptyState());

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test void disabledAfterCutoff() {
        GameState s = emptyState();
        addTree(s, 3, 1, TreeType.PLUM);
        s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        GameState ref = emptyState();
        ref.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

        double fit    = fitnessOf(s);
        double fitRef = fitnessOf(ref);

        assertThat(fit).isCloseTo(fitRef, org.assertj.core.data.Offset.offset(1e-9));
    }
}
```

- [ ] **Step 1.2 : Vérifier que les tests échouent (compile error : `ALPHA_NEAR_TREE` n'existe pas)**

```bash
mvn -q test -Dtest=GenomeEvaluatorNearTreeTest
```

Expected: compile fail sur `GenomeEvaluator.ALPHA_NEAR_TREE`.

- [ ] **Step 1.3 : Ajouter `ALPHA_NEAR_TREE` et `nearTreeBonus` dans `GenomeEvaluator.java`**

Dans `GenomeEvaluator.java` :

(a) Ajouter l'import :

```java
import com.bmrt.cgspring2026.model.TreeType;
```

(b) Ajouter la constante après `ALPHA_TRAIN_PUSH` (ligne 15) :

```java
public static final double ALPHA_NEAR_TREE = 2.0;
```

(c) Dans `fitness(GameState finalState)`, après le bloc `trainPush` (ligne 73 actuelle) et avant le `return`, insérer :

```java
double nearTreeBonus = 0.0;
if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF) {
    boolean hasPlum = false, hasLemon = false, hasApple = false;
    int sx = GameState.shackMeX, sy = GameState.shackMeY;
    for (int t = 0; t < finalState.treeCount; t++) {
        if (finalState.treeHealth[t] <= 0) continue;
        byte type = finalState.treeType[t];
        if (type == TreeType.BANANA) continue;
        int dx = (finalState.treeX[t] & 0xFF) - sx;
        int dy = (finalState.treeY[t] & 0xFF) - sy;
        if (Math.abs(dx) + Math.abs(dy) > 5) continue;
        if      (type == TreeType.PLUM  && !hasPlum)  hasPlum  = true;
        else if (type == TreeType.LEMON && !hasLemon) hasLemon = true;
        else if (type == TreeType.APPLE && !hasApple) hasApple = true;
        if (hasPlum && hasLemon && hasApple) break;
    }
    int typesCovered = (hasPlum ? 1 : 0) + (hasLemon ? 1 : 0) + (hasApple ? 1 : 0);
    nearTreeBonus = ALPHA_NEAR_TREE * typesCovered;
}
```

(d) Modifier le `return` pour inclure `nearTreeBonus` :

```java
return (scoreMe - scoreOpp)
        + ALPHA_WOOD_CARRY * woodCarryMe
        + ALPHA_FRUIT_CARRY * fruitCarryMe
        + ALPHA_IRON_CARRY * ironCarryMe
        + ALPHA_TRAIN_PUSH * trainPush
        + nearTreeBonus;
```

- [ ] **Step 1.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=GenomeEvaluatorNearTreeTest
```

Expected: 7 tests PASS.

- [ ] **Step 1.5 : Vérifier qu'aucun test existant ne casse**

```bash
mvn -q test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 1.6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorNearTreeTest.java
git commit -m "feat(fitness): ALPHA_NEAR_TREE bonus for PLUM/LEMON/APPLE within dist 5"
```

---

## Task 2 — `mutateInsertCut` early return pré-cutoff (Section 5 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java`

- [ ] **Step 2.1 : Écrire le test qui échoue**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java` :

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

class GenomeOpsCutCutoffTest {

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

    private static short[] newBuf() {
        short[] b = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(b, Genome.EMPTY_GENE);
        return b;
    }
    private static byte[] newLen() { return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS]; }

    private static boolean hasCutGene(short[] buf, byte[] lens, int individu) {
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, individu, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, individu, j, k);
                if (Genome.isCut(g)) return true;
            }
        }
        return false;
    }

    private static GameState stateWithTree(int turn) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 1; s.trollHP[0] = 1;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        s.turn = turn;
        return s;
    }

    @Test void mutateInsertCut_noopBeforeCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);
        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(42);
        for (int i = 0; i < 100; i++) GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }

    @Test void mutateInsertCut_worksAtCutoff() {
        GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);
        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(7);
        boolean found = false;
        for (int i = 0; i < 50 && !found; i++) {
            GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
            found = hasCutGene(buf, lens, 0);
        }
        assertThat(found).isTrue();
    }
}
```

- [ ] **Step 2.2 : Vérifier que les tests échouent**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: `mutateInsertCut_noopBeforeCutoff` FAIL (CUT gene injecté avant cutoff).

- [ ] **Step 2.3 : Ajouter l'early return**

Dans `GenomeOps.java`, méthode `mutateInsertCut` (autour de la ligne 420), ajouter en première ligne du corps :

```java
public static void mutateInsertCut(GameState state, short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) return;
    // 1. Pick un troll avec de la place
    int count = 0;
    // ... reste inchangé
```

- [ ] **Step 2.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: 2 tests PASS.

- [ ] **Step 2.5 : Régression globale**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2.6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java
git commit -m "feat(ga): mutateInsertCut no-op before TRAIN_PUSH_TURN_CUTOFF"
```

---

## Task 3 — `initFromPrevBest` filtre CUT pré-cutoff (Section 4 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java` (ajouter)

- [ ] **Step 3.1 : Ajouter le test qui échoue**

Dans `GenomeOpsCutCutoffTest.java`, ajouter deux tests :

```java
@Test void initFromPrevBest_dropsCutGenesBeforeCutoff() {
    GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);

    short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
    byte[] prevLen = new byte[GameState.MAX_TROLLS];
    // Place a CUT gene on troll 0 targeting tree (3,2)
    prevBuf[0] = Genome.encode(3, 2);
    prevLen[0] = 1;

    short[] dstBuf = newBuf();
    byte[]  dstLen = newLen();
    GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

    assertThat(hasCutGene(dstBuf, dstLen, 0)).isFalse();
    assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(0);
}

@Test void initFromPrevBest_keepsCutGenesAtCutoff() {
    GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);

    short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
    byte[] prevLen = new byte[GameState.MAX_TROLLS];
    prevBuf[0] = Genome.encode(3, 2);
    prevLen[0] = 1;

    short[] dstBuf = newBuf();
    byte[]  dstLen = newLen();
    GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

    assertThat(hasCutGene(dstBuf, dstLen, 0)).isTrue();
    assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
}

@Test void initFromPrevBest_keepsMineGenesBeforeCutoff() {
    GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);

    short[] prevBuf = new short[Genome.SLOTS_PER_GENOME];
    java.util.Arrays.fill(prevBuf, Genome.EMPTY_GENE);
    byte[] prevLen = new byte[GameState.MAX_TROLLS];
    // MINE gene n'a pas besoin d'arbre, juste d'une cellule IRON dans la carte.
    // On vérifie qu'il survit indépendamment du gate CUT.
    prevBuf[0] = Genome.makeMine(2, 2);
    prevLen[0] = 1;

    short[] dstBuf = newBuf();
    byte[]  dstLen = newLen();
    GenomeOps.initFromPrevBest(s, prevBuf, prevLen, dstBuf, dstLen, 0);

    assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
    short g = (short) Genome.gene(dstBuf, 0, 0, 0);
    assertThat(Genome.isMine(g)).isTrue();
}
```

- [ ] **Step 3.2 : Vérifier qu'au moins un test échoue**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: `initFromPrevBest_dropsCutGenesBeforeCutoff` FAIL (le gène CUT survit).

- [ ] **Step 3.3 : Ajouter le filtre CUT dans `initFromPrevBest`**

Dans `GenomeOps.java`, méthode `initFromPrevBest`, dans la boucle ligne 228 (after the MINE short-circuit, before `treeIndexAt`), insérer :

```java
for (int k = 0; k < prevLen; k++) {
    short g = prevBuf[srcOff + k];
    if (g == Genome.EMPTY_GENE) continue;
    if (Genome.isMine(g)) {
        dstBuf[dstOff + written] = g;
        written++;
        continue;
    }
    // Pré-cutoff : pas de CUT
    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF && Genome.isCut(g)) {
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

- [ ] **Step 3.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: 5 tests PASS (2 anciens + 3 nouveaux).

- [ ] **Step 3.5 : Régression globale**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 3.6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java
git commit -m "feat(ga): initFromPrevBest filters CUT genes before cutoff"
```

---

## Task 4 — `initRandom` gate étapes 3-4 pré-cutoff (Section 2 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java` (ajouter)

- [ ] **Step 4.1 : Ajouter les tests qui échouent**

Dans `GenomeOpsCutCutoffTest.java`, ajouter :

```java
@Test void initRandom_noCutGeneBeforeCutoff() {
    GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);
    Genome.initHarvestCandidates(s);
    Genome.initIronCandidates();

    short[] buf = newBuf();
    byte[]  lens = newLen();
    SplittableRandom rng = new SplittableRandom(42);
    for (int i = 0; i < 100; i++) GenomeOps.initRandom(s, buf, lens, 0, rng);

    assertThat(hasCutGene(buf, lens, 0)).isFalse();
}

@Test void initRandom_canEmitCutGeneAtCutoff() {
    GameState s = stateWithTree(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);
    Genome.initHarvestCandidates(s);
    Genome.initIronCandidates();

    short[] buf = newBuf();
    byte[]  lens = newLen();
    SplittableRandom rng = new SplittableRandom(7);
    boolean found = false;
    for (int i = 0; i < 50 && !found; i++) {
        GenomeOps.initRandom(s, buf, lens, 0, rng);
        found = hasCutGene(buf, lens, 0);
    }
    assertThat(found).isTrue();
}
```

- [ ] **Step 4.2 : Vérifier qu'au moins un test échoue**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: `initRandom_noCutGeneBeforeCutoff` FAIL.

- [ ] **Step 4.3 : Wrapper les étapes 3-4 d'`initRandom`**

Dans `GenomeOps.java`, méthode `initRandom`, entourer les étapes 3-4 (lignes 39-68 actuelles, depuis « // 3. Récupérer arbres vivants » jusqu'au `}` de fermeture de la boucle « // 4. Distribuer ») d'un `if` :

```java
// 3. Récupérer arbres vivants + 4. distribuer (UNIQUEMENT post-cutoff)
if (state.turn >= GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) {
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

    // 4. Distribuer
    for (int i = 0; i < treeCount; i++) {
        if (rng.nextDouble() < P_SKIP_INIT) continue;
        int freeCount = 0;
        for (int k = 0; k < ownTrollsCount; k++) {
            int trollIdx = ownTrollsBuf[k];
            if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;
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
```

Les étapes 5 (PLANT), 6 (HARVEST), 7 (MINE) restent inchangées en aval.

- [ ] **Step 4.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=GenomeOpsCutCutoffTest
```

Expected: 7 tests PASS.

- [ ] **Step 4.5 : Régression globale**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 4.6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutCutoffTest.java
git commit -m "feat(ga): initRandom skips tree distribution (CUT) before cutoff"
```

---

## Task 5 — TrollPolicy PLANT sans chop pré-cutoff (Section 6 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantCutoffTest.java`

- [ ] **Step 5.1 : Écrire les tests qui échouent**

Créer `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantCutoffTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyPlantCutoffTest {

    @BeforeEach void grid() {
        String[] rows = { "......", ".0....", "......", "......", "......" };
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
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            TrollPolicy.cursorBuf[j] = 0;
            TrollPolicy.policyPhase[j] = 0;
        }
    }

    private GameState trollAt(int x, int y, int turn) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
        s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
        s.turn = turn;
        return s;
    }

    private static short[] popBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] lenBuf() { return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS]; }

    @Test void earlyGame_plantThenAdvanceCursorWithoutChopPhase() {
        GameState s = trollAt(0, 2, GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.LEMON] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PLANT);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 0);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
    }

    @Test void earlyGame_skipPlantGeneWhenTreeAlreadyOnCell() {
        GameState s = trollAt(0, 2, GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1);
        s.treeCount = 1;
        s.treeX[0] = 0; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        s.treeType[0] = TreeType.PLUM;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        // Avant cutoff : pas de chop, gène entièrement sauté.
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void postCutoff_treeAlreadyOnCell_stillTriggersChop() {
        GameState s = trollAt(0, 2, GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);
        s.treeCount = 1;
        s.treeX[0] = 0; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        s.treeType[0] = TreeType.PLUM;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.CHOP);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 1);
    }

    @Test void postCutoff_plantThenChopOnSameGene() {
        GameState s = trollAt(0, 2, GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF);
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.LEMON] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PLANT);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 1);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0);
    }
}
```

- [ ] **Step 5.2 : Vérifier que les tests échouent**

```bash
mvn -q test -Dtest=TrollPolicyPlantCutoffTest
```

Expected: les tests `earlyGame_*` FAIL (la policy chop/transition phase 1 actuellement).

- [ ] **Step 5.3 : Modifier le bloc PLANT dans `TrollPolicy.decideForOwnTroll`**

Dans `TrollPolicy.java`, remplacer les lignes 150-175 (le bloc PLANT depuis `int fruit = ...` jusqu'au dernier `return Action.move(trollIdx, gx, gy);`) par :

```java
int fruit = Genome.plantFruitType(g);
int t = s.treeIndexAt(gx, gy);
boolean earlyGame = s.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

if (policyPhase[trollIdx] == 0 && t >= 0) {
    if (earlyGame) {
        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
        continue;
    }
    policyPhase[trollIdx] = 1;
}

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
        if (earlyGame) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0;
        } else {
            policyPhase[trollIdx] = 1;
        }
        return Action.plant(trollIdx, fruit);
    }
    return Action.move(trollIdx, gx, gy);
}

if (t < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
if (tx == gx && ty == gy) return Action.chop(trollIdx);
return Action.move(trollIdx, gx, gy);
```

- [ ] **Step 5.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=TrollPolicyPlantCutoffTest
```

Expected: 4 tests PASS.

- [ ] **Step 5.5 : Vérifier qu'aucun test existant ne casse**

```bash
mvn -q test -Dtest=TrollPolicyPlantTest
```

Expected: tous les tests existants PASS (`turn = 0` par défaut → comportement pré-cutoff. **Important** : les tests `lazyPhaseInit_treeAlreadyOnTargetGoesToChop`, `phase1_chopsWhenOnTargetWithLiveTree`, `phase0_plantsWhenOnTargetWithFruit` utilisent `turn = 0` qui est pré-cutoff, donc ils vont casser).

Si tests cassent : c'est attendu. Ces tests reflètent l'ancien comportement. **Action :** modifier ces 3 tests pour passer `turn = TRAIN_PUSH_TURN_CUTOFF` aux GameState afin de retrouver le comportement post-cutoff testé.

Modifications dans `TrollPolicyPlantTest.java` :

(a) Méthode `trollAt(int x, int y)` — ajouter `s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;` avant `return s;` :

```java
private GameState trollAt(int x, int y) {
    GameState s = new GameState();
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
    s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
    s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;
    return s;
}
```

Tous les tests de cette classe testent le comportement « post-cutoff » désormais.

```bash
mvn -q test -Dtest=TrollPolicyPlantTest
```

Expected: tous PASS.

- [ ] **Step 5.6 : Régression globale**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5.7 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantCutoffTest.java src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "feat(policy): PLANT skips chop phase before TRAIN_PUSH_TURN_CUTOFF"
```

---

## Task 6 — `initWarm` split `initWarmEarly` / `initWarmLate` (Section 3 du spec)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitWarmEarlyTest.java`

- [ ] **Step 6.1 : Écrire les tests qui échouent**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitWarmEarlyTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsInitWarmEarlyTest {

    @BeforeEach void grid() {
        // 6x6 avec une cellule IRON en (4,4) — '+' = TileType.IRON
        String[] rows = {
            "......",
            ".0....",
            "......",
            "......",
            "....+.",
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
    }

    private static short[] newBuf() {
        short[] b = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(b, Genome.EMPTY_GENE);
        return b;
    }
    private static byte[] newLen() { return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS]; }

    private GameState stateWith(int turn, int trollX, int trollY, int[][] matureTrees) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) trollX; s.trollY[0] = (byte) trollY;
        s.trollCP[0] = 1; s.trollHP[0] = 1;
        s.turn = turn;
        for (int[] t : matureTrees) {
            int i = s.treeCount++;
            s.treeX[i] = (byte) t[0]; s.treeY[i] = (byte) t[1];
            s.treeHealth[i] = 5; s.treeSize[i] = 4;
        }
        Genome.initHarvestCandidates(s);
        return s;
    }

    @Test void earlyGame_noCutGenesProduced() {
        GameState s = stateWith(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1, 0, 0,
                                new int[][]{ {2, 2}, {3, 3} });

        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);

        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, 0, j, k);
                assertThat(Genome.isCut(g)).isFalse();
            }
        }
    }

    @Test void earlyGame_pickClosestHarvestOrMine() {
        // Troll (0,0), arbre mature (1,0) d=1, IRON (4,4) d=8 (via path).
        // Closest = (1,0) HARVEST.
        GameState s = stateWith(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1, 0, 0,
                                new int[][]{ {1, 0} });
        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);

        assertThat(Genome.len(lens, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.isHarvest(g0)).isTrue();
        assertThat(Genome.geneX(g0)).isEqualTo(1);
        assertThat(Genome.geneY(g0)).isEqualTo(0);
    }

    @Test void earlyGame_minePickedWhenNoHarvest() {
        // Troll en (3,4) — GRASS, adjacent à IRON (4,4). Sans arbre mature → MINE pris.
        GameState s = stateWith(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1, 3, 4,
                                new int[][]{});
        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);

        assertThat(Genome.len(lens, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.isMine(g0)).isTrue();
        assertThat(Genome.geneX(g0)).isEqualTo(4);
        assertThat(Genome.geneY(g0)).isEqualTo(4);
    }

    @Test void earlyGame_emptyWhenNoCandidates() {
        // Troll sans arbre mature et hors d'atteinte d'IRON ? Note: ironCandidates
        // est statique au tile (immutable). On garde le test avec aucun arbre,
        // donc seule la cellule IRON (4,4) est candidate.
        GameState s = stateWith(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF - 1, 0, 0,
                                new int[][]{});
        s.trollCP[0] = 0;  // sans CP : ne peut pas miner
        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);
        // Sans CP : pas de gène MINE, et pas de HARVEST candidates → empty
        assertThat(Genome.len(lens, 0, 0)).isEqualTo(0);
    }

    @Test void postCutoff_behavesAsBeforeWithTreeTargets() {
        // Comportement late (CUT vers arbre vivant peu importe maturité).
        GameState s = stateWith(GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF, 0, 0,
                                new int[][]{ {1, 0} });
        short[] buf = newBuf();
        byte[]  lens = newLen();
        GenomeOps.initWarm(s, buf, lens, 0);

        assertThat(Genome.len(lens, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        // Late : encodage = encode(x, y) = CUT (bit15=bit14=bit13=0).
        assertThat(Genome.isCut(g0)).isTrue();
    }
}
```

- [ ] **Step 6.2 : Vérifier que les tests échouent**

```bash
mvn -q test -Dtest=GenomeOpsInitWarmEarlyTest
```

Expected: les tests `earlyGame_*` FAIL (initWarm actuel produit du CUT pour les arbres).

- [ ] **Step 6.3 : Split `initWarm` en `initWarmEarly` / `initWarmLate`**

Dans `GenomeOps.java`, **étape 1** — ajouter les scratchs statiques au niveau classe (à proximité des autres `private static final` vers la ligne 19) :

```java
private static final int      WARM_EARLY_CAP = GameState.MAX_TREES + Genome.MAX_IRON_CANDIDATES;
private static final short[]  warmEarlyX     = new short[WARM_EARLY_CAP];
private static final short[]  warmEarlyY     = new short[WARM_EARLY_CAP];
private static final byte[]   warmEarlyType  = new byte [WARM_EARLY_CAP];  // 0=HARVEST, 1=MINE
private static final boolean[] warmEarlyTaken = new boolean[WARM_EARLY_CAP];
```

**Étape 1.bis** — ajouter aussi l'import dans `GenomeOps.java` (au top du fichier, à côté des autres imports) :

```java
import com.bmrt.cgspring2026.model.TileType;
```

**Étape 2** — refactorer `initWarm` (lignes 155-211 actuelles). Remplacer le corps complet de la méthode par :

```java
public static void initWarm(GameState state, short[] buf, byte[] lenBuf, int individuIdx) {
    // 1. Reset segment de cet individu
    int base = Genome.offset(individuIdx, 0);
    for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) buf[base + k] = Genome.EMPTY_GENE;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(lenBuf, individuIdx, j, 0);

    // 2. Trolls own
    int ownCount = 0;
    for (int i = 0; i < state.trollCount; i++) {
        if ((state.trollPlayer[i] & 0xFF) == 0) ownTrollsBuf[ownCount++] = i;
    }
    if (ownCount == 0) return;

    // 3. Init position courante par troll
    for (int k = 0; k < ownCount; k++) {
        int troll = ownTrollsBuf[k];
        warmCurX[troll] = state.trollX[troll] & 0xFF;
        warmCurY[troll] = state.trollY[troll] & 0xFF;
    }

    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) {
        initWarmEarly(state, buf, lenBuf, individuIdx, ownCount);
    } else {
        initWarmLate(state, buf, lenBuf, individuIdx, ownCount);
    }
}

private static void initWarmLate(GameState state, short[] buf, byte[] lenBuf,
                                 int individuIdx, int ownCount) {
    for (int t = 0; t < state.treeCount; t++) warmTreeTakenBuf[t] = false;

    for (int kLayer = 0; kLayer < Genome.MAX_TARGETS_PER_TROLL; kLayer++) {
        boolean anyAssigned = false;
        for (int kT = 0; kT < ownCount; kT++) {
            int troll = ownTrollsBuf[kT];
            if ((state.trollCP[troll] & 0xFF) == 0) continue;
            int bestTree = -1;
            int bestDist = PathTable.UNREACHABLE;
            int cx = warmCurX[troll];
            int cy = warmCurY[troll];
            for (int t = 0; t < state.treeCount; t++) {
                if (state.treeHealth[t] <= 0) continue;
                if (warmTreeTakenBuf[t])      continue;
                int tx = state.treeX[t] & 0xFF;
                int ty = state.treeY[t] & 0xFF;
                int d  = PathTable.distance(cx, cy, tx, ty);
                if (d == PathTable.UNREACHABLE) continue;
                if (d < bestDist) {
                    bestDist = d;
                    bestTree = t;
                }
            }
            if (bestTree >= 0) {
                int bx = state.treeX[bestTree] & 0xFF;
                int by = state.treeY[bestTree] & 0xFF;
                Genome.setGene(buf, individuIdx, troll, kLayer, Genome.encode(bx, by));
                Genome.setLen(lenBuf, individuIdx, troll, kLayer + 1);
                warmTreeTakenBuf[bestTree] = true;
                warmCurX[troll] = bx;
                warmCurY[troll] = by;
                anyAssigned = true;
            }
        }
        if (!anyAssigned) break;
    }
}

private static void initWarmEarly(GameState state, short[] buf, byte[] lenBuf,
                                  int individuIdx, int ownCount) {
    int poolN = 0;
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        warmEarlyX[poolN]    = (short)(Genome.candX(c) & 0xFF);
        warmEarlyY[poolN]    = (short)(Genome.candY(c) & 0xFF);
        warmEarlyType[poolN] = 0;
        poolN++;
    }
    for (int i = 0; i < Genome.ironCandidateCount; i++) {
        short c = Genome.ironCandidates[i];
        warmEarlyX[poolN]    = (short)(Genome.candX(c) & 0xFF);
        warmEarlyY[poolN]    = (short)(Genome.candY(c) & 0xFF);
        warmEarlyType[poolN] = 1;
        poolN++;
    }
    for (int i = 0; i < poolN; i++) warmEarlyTaken[i] = false;
    if (poolN == 0) return;

    for (int kLayer = 0; kLayer < Genome.MAX_TARGETS_PER_TROLL; kLayer++) {
        boolean anyAssigned = false;
        for (int kT = 0; kT < ownCount; kT++) {
            int troll = ownTrollsBuf[kT];
            if ((state.trollCP[troll] & 0xFF) == 0) continue;
            int cx = warmCurX[troll], cy = warmCurY[troll];
            int hp = state.trollHP[troll] & 0xFF;

            int bestIdx = -1, bestDist = PathTable.UNREACHABLE;
            for (int i = 0; i < poolN; i++) {
                if (warmEarlyTaken[i]) continue;
                if (warmEarlyType[i] == 0 && hp == 0) continue;
                int px = warmEarlyX[i] & 0xFF, py = warmEarlyY[i] & 0xFF;
                int d = (warmEarlyType[i] == 0)
                        ? PathTable.distance(cx, cy, px, py)
                        : distToIronCell(cx, cy, px, py);
                if (d == PathTable.UNREACHABLE) continue;
                if (d < bestDist) { bestDist = d; bestIdx = i; }
            }
            if (bestIdx >= 0) {
                int bx = warmEarlyX[bestIdx] & 0xFF, by = warmEarlyY[bestIdx] & 0xFF;
                short gene = (warmEarlyType[bestIdx] == 0)
                        ? Genome.makeHarvest(bx, by)
                        : Genome.makeMine(bx, by);
                Genome.setGene(buf, individuIdx, troll, kLayer, gene);
                Genome.setLen(lenBuf, individuIdx, troll, kLayer + 1);
                warmEarlyTaken[bestIdx] = true;
                // Post-pick : positionner le troll sur la cellule cible (HARVEST = grass)
                // ou sur la dernière grass-adj utilisée (MINE = obstacle, on garde la position
                // actuelle pour le calcul layer suivant — approximation acceptable car le
                // troll s'arrêtera sur la grass-adj lors de l'exécution).
                if (warmEarlyType[bestIdx] == 0) {
                    warmCurX[troll] = bx;
                    warmCurY[troll] = by;
                }
                anyAssigned = true;
            }
        }
        if (!anyAssigned) break;
    }
}

/** Distance vers une cellule IRON via sa grass-adj la plus proche. UNREACHABLE si aucune. */
private static int distToIronCell(int fromX, int fromY, int ix, int iy) {
    int W = GameState.width, H = GameState.height;
    int best = PathTable.UNREACHABLE;
    int[] dx = {1, -1, 0, 0};
    int[] dy = {0, 0, 1, -1};
    for (int k = 0; k < 4; k++) {
        int nx = ix + dx[k], ny = iy + dy[k];
        if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
        if (GameState.tiles[ny * W + nx] != TileType.GRASS) continue;
        int d = PathTable.distance(fromX, fromY, nx, ny);
        if (d == PathTable.UNREACHABLE) continue;
        if (d < best) best = d;
    }
    return best;
}
```

- [ ] **Step 6.4 : Vérifier que les tests passent**

```bash
mvn -q test -Dtest=GenomeOpsInitWarmEarlyTest
```

Expected: 5 tests PASS.

- [ ] **Step 6.5 : Vérifier les tests existants — surtout `GenomeOpsWarmStartTest`**

```bash
mvn -q test -Dtest=GenomeOpsWarmStartTest
```

Expected: tous les tests existants `GenomeOpsWarmStartTest` PASS (turn=0 par défaut **dans l'état créé via `stateWith`**, donc early game pour ces tests).

**Si tests cassent** (les tests existants supposaient le comportement late) : forcer `turn = CUTOFF` dans `GenomeOpsWarmStartTest.stateWith` :

```java
private static GameState stateWith(int[][] trolls, int[][] trees) {
    GameState s = new GameState();
    s.turn = GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;  // ← ajouté
    // ... reste inchangé
}
```

Re-run :

```bash
mvn -q test -Dtest=GenomeOpsWarmStartTest
```

Expected: tous PASS.

- [ ] **Step 6.6 : Régression globale complète**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6.7 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitWarmEarlyTest.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "feat(ga): initWarm uses HARVEST+MINE pool before cutoff"
```

---

## Task 7 — Regen `Player.java` pour soumission CG

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/Player.java` (généré)

- [ ] **Step 7.1 : Régénérer `Player.java` via FileBuilder**

```bash
mvn -q compile exec:java -Dexec.mainClass=com.bmrt.cgspring2026.builder.FileBuilder -Dexec.args=src/main/java/com/bmrt/cgspring2026/Player.java
```

Expected: `Player.java` mis à jour avec tous les changements (fitness, GenomeOps, TrollPolicy).

- [ ] **Step 7.2 : Vérifier que `Player.java` compile seul**

```bash
javac -d target/playercheck src/main/java/com/bmrt/cgspring2026/Player.java
```

Expected: aucune erreur.

- [ ] **Step 7.3 : Régression globale finale**

```bash
mvn -q test
```

Expected: BUILD SUCCESS.

- [ ] **Step 7.4 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/Player.java
git commit -m "build(player): regen Player.java for train-push early-game refocus"
```

---

## Self-Review Check

**Spec coverage :**

| Spec Section | Task |
|---|---|
| 1 — Fitness `nearTreeBonus` | Task 1 |
| 2 — `initRandom` gate étapes 3-4 | Task 4 |
| 3 — `initWarm` split early/late | Task 6 |
| 4 — `initFromPrevBest` filtre CUT | Task 3 |
| 5 — `mutateInsertCut` early return | Task 2 |
| 6 — `TrollPolicy` PLANT skip phase 2 | Task 5 |
| Récap fichiers | Tasks 1-6 |
| Regen Player.java | Task 7 |

Toutes les sections du spec sont couvertes.

**Type/method consistency :**
- `ALPHA_NEAR_TREE` : utilisé dans Task 1 (création) et Task 1 tests (référence) — cohérent.
- `Genome.isCut(g)` : utilisé dans Task 2 (test), Task 3 (impl + test), Task 4 (test), Task 6 (test) — existe déjà dans le code (vérifié `Genome.java:69-72`).
- `Genome.makeMine`, `Genome.makeHarvest` : utilisés dans Task 6 — existent déjà.
- `Genome.harvestCandidates`, `Genome.ironCandidates`, `Genome.candX`, `Genome.candY` : utilisés dans Task 6 — existent déjà.
- `GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF` : utilisé partout — existe déjà.
- `state.turn` : utilisé Task 2, 3, 4, 5 — champ public de `GameState`.
- `s.treeType[]` : utilisé Task 1 test — champ `byte[]` public.
- `WARM_EARLY_CAP` : défini dans Task 6 step 6.3 (étape 1). Utilisé immédiatement (étape 2 même step).

**Placeholders :** aucun. Tout code montré in extenso.

**Ordre :** sécurisé — chaque tâche compile et passe les tests existants indépendamment grâce au `state.turn` qui par défaut vaut 0 (early-game), donc les tests existants peuvent nécessiter une adaptation (Tasks 5 et 6) qui est documentée.
