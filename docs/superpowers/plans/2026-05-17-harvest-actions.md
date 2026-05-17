# Harvest Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter les gènes HARVEST au génome GA pour que les trolls avec HP > 0 récoltent les fruits des arbres de taille 4 et les rapportent au shack.

**Architecture:** Bit14 du short 16 bits est réservé au flag HARVEST (bit15=PLANT, bit14=HARVEST). TrollPolicy exécute le cycle move→harvest→drop implicitement via l'état `fruitCarry`. Les candidats harvest sont recalculés chaque tour dans `GeneticAgent.decide()`.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ

---

## Fichiers modifiés / créés

| Fichier | Rôle |
|---|---|
| `src/main/java/.../ga/Genome.java` | Ajouter `makeHarvest`, `isHarvest`, `harvestCandidates[]`, `initHarvestCandidates()` |
| `src/main/java/.../ga/GeneticAgent.java` | Appeler `initHarvestCandidates(state)` chaque tour dans `decide()` |
| `src/main/java/.../ga/TrollPolicy.java` | Branchement HARVEST dans `decideForOwnTroll` |
| `src/main/java/.../ga/GenomeOps.java` | Étape 6 dans `initRandom`, `mutateInsertHarvest`, `pickMutationKind` rebalancé |
| `src/main/java/.../ga/GenomeEvaluator.java` | `ALPHA_FRUIT_CARRY`, calcul `fruitCarryMe` dans `fitness()` |
| `src/test/java/.../ga/GenomeHarvestTest.java` | Tests unitaires de l'encodage HARVEST |
| `src/test/java/.../ga/TrollPolicyHarvestTest.java` | Tests de la politique d'exécution HARVEST |
| `src/test/java/.../ga/GenomeOpsHarvestTest.java` | Tests init + mutation HARVEST |
| `src/test/java/.../ga/GenomeEvaluatorHarvestTest.java` | Test fitness fruit-carry |

---

## Task 1 : Encodage HARVEST dans `Genome.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeHarvestTest.java`

- [ ] **Step 1.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeHarvestTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeHarvestTest {

    @BeforeEach void grid() {
        GameState.width = 6; GameState.height = 5;
        GameState.tiles = new byte[6 * 5];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState.tiles[1 * 6 + 1] = TileType.SHACK_ME;
        GameState.shackMeX = 1; GameState.shackMeY = 1;
    }

    @Test void makeHarvestRoundTrip() {
        short g = Genome.makeHarvest(5, 12);
        assertThat(Genome.isHarvest(g)).isTrue();
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(12);
    }

    @Test void isHarvestFalseForPlantGene() {
        short g = Genome.makePlant(3, 4, TreeType.LEMON);
        assertThat(Genome.isHarvest(g)).isFalse();
    }

    @Test void isHarvestFalseForCutGene() {
        short g = Genome.encode(5, 12);
        assertThat(Genome.isHarvest(g)).isFalse();
    }

    @Test void isHarvestFalseForEmptyGene() {
        assertThat(Genome.isHarvest(Genome.EMPTY_GENE)).isFalse();
    }

    @Test void initHarvestCandidates_picksSize4Trees() {
        GameState s = new GameState();
        s.treeCount = 3;
        s.treeX[0] = 2; s.treeY[0] = 2; s.treeSize[0] = 4; s.treeHealth[0] = 6;
        s.treeX[1] = 3; s.treeY[1] = 3; s.treeSize[1] = 2; s.treeHealth[1] = 8; // trop petit
        s.treeX[2] = 4; s.treeY[2] = 1; s.treeSize[2] = 4; s.treeHealth[2] = 0; // mort
        Genome.initHarvestCandidates(s);
        assertThat(Genome.harvestCandidateCount).isEqualTo(1);
        short c = Genome.harvestCandidates[0];
        assertThat(Genome.candX(c)).isEqualTo(2);
        assertThat(Genome.candY(c)).isEqualTo(2);
    }

    @Test void initHarvestCandidates_emptyWhenNoMatureTree() {
        GameState s = new GameState();
        s.treeCount = 2;
        s.treeX[0] = 1; s.treeY[0] = 3; s.treeSize[0] = 3; s.treeHealth[0] = 5;
        s.treeX[1] = 2; s.treeY[1] = 2; s.treeSize[1] = 1; s.treeHealth[1] = 3;
        Genome.initHarvestCandidates(s);
        assertThat(Genome.harvestCandidateCount).isEqualTo(0);
    }
}
```

- [ ] **Step 1.2 : Vérifier RED**

```
mvn test -Dtest=GenomeHarvestTest -q
```

Attendu : FAIL avec `cannot find symbol` pour `makeHarvest`, `isHarvest`, `harvestCandidates`, `initHarvestCandidates`.

- [ ] **Step 1.3 : Implémenter dans `Genome.java`**

Ajouter après les constantes existantes (après `PLANT_FLAG_MASK`) :

```java
private static final int HARVEST_FLAG_MASK = 0x4000;
public static final short[] harvestCandidates = new short[MAX_TREES];
public static int harvestCandidateCount = 0;
```

Ajouter les méthodes après `isPlant` :

```java
public static short makeHarvest(int x, int y) {
    return (short) (HARVEST_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
}

public static boolean isHarvest(short g) {
    return (g & HARVEST_FLAG_MASK) != 0 && (g & PLANT_FLAG_MASK) == 0;
}

public static void initHarvestCandidates(GameState state) {
    harvestCandidateCount = 0;
    for (int t = 0; t < state.treeCount; t++) {
        if ((state.treeSize[t] & 0xFF) == 4 && state.treeHealth[t] > 0) {
            harvestCandidates[harvestCandidateCount++] =
                encode(state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
        }
    }
}
```

- [ ] **Step 1.4 : Vérifier GREEN**

```
mvn test -Dtest=GenomeHarvestTest -q
```

Attendu : BUILD SUCCESS, 6 tests passent.

- [ ] **Step 1.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java src/test/java/com/bmrt/cgspring2026/ga/GenomeHarvestTest.java
git commit -m "feat(genome): HARVEST flag encoding + harvestCandidates"
```

---

## Task 2 : Appel de `initHarvestCandidates` dans `GeneticAgent.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java:224-231`

> Note : `initPlantCandidates()` est appelé une seule fois (candidats statiques liés au shack). `initHarvestCandidates()` doit être appelé **chaque tour** car les arbres grandissent ou meurent entre les tours. Pas de flag de garde.

- [ ] **Step 2.1 : Modifier `GeneticAgent.decide()`**

Dans `decide()`, juste après `if (!plantCandidatesInitialized) { ... }` (ligne ~225), ajouter :

```java
Genome.initHarvestCandidates(state);
```

Le bloc devient :

```java
if (!plantCandidatesInitialized) {
    Genome.initPlantCandidates();
    plantCandidatesInitialized = true;
}
Genome.initHarvestCandidates(state);   // ← ajout
```

- [ ] **Step 2.2 : Vérifier que les tests existants passent toujours**

```
mvn test -Dtest=GeneticAgentTest,GeneticAgentPlantSmokeTest,GeneticAgentInitWarmTest -q
```

Attendu : BUILD SUCCESS.

- [ ] **Step 2.3 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java
git commit -m "feat(agent): initHarvestCandidates chaque tour dans decide()"
```

---

## Task 3 : Politique HARVEST dans `TrollPolicy.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyHarvestTest.java`

- [ ] **Step 3.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyHarvestTest.java` :

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

class TrollPolicyHarvestTest {

    // Grille 6x5, shack en (1,1)
    // Arbre de taille 4 en (4,2) avec 2 fruits
    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
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

    private GameState stateWithHarvestTree(int trollX, int trollY, int treeFruits) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) trollX; s.trollY[0] = (byte) trollY;
        s.trollMS[0] = 2; s.trollCC[0] = 3; s.trollHP[0] = 2; s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 4; s.treeY[0] = 2;
        s.treeSize[0] = 4; s.treeHealth[0] = 6;
        s.treeFruits[0] = (byte) treeFruits;
        s.treeType[0] = TreeType.BANANA;
        return s;
    }

    private static short[] popWithHarvestGene(int gx, int gy) {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        Genome.setGene(buf, 0, 0, 0, Genome.makeHarvest(gx, gy));
        return buf;
    }

    private static byte[] lenBufOf1() {
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lens, 0, 0, 1);
        return lens;
    }

    @Test void movesToTreeWhenNotOnIt() {
        GameState s = stateWithHarvestTree(0, 0, 2);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
    }

    @Test void harvestsWhenOnTreeWithFruits() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.HARVEST);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0); // cursor ne bouge pas encore
    }

    @Test void skipsGeneWhenOnTreeWithZeroFruits() {
        GameState s = stateWithHarvestTree(4, 2, 0);
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1); // gene skippé
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void movesToShackWhenCarryingFruit() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        // Troll porte déjà un fruit (post-harvest)
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        // La destination doit être adjacente au shack (1,1) → (0,1) ou (1,0) ou (2,1) ou (1,2)
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dShack = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dShack).isEqualTo(1);
    }

    @Test void dropsAndAdvancesCursorWhenAdjacentToShackWithFruit() {
        GameState s = stateWithHarvestTree(1, 2, 2); // adjacent shack en (1,1)
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1); // cursor avance
    }

    @Test void skipsGeneWhenTreeDeadOrSmall() {
        GameState s = stateWithHarvestTree(0, 0, 2);
        s.treeHealth[0] = 0; // arbre mort
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void skipsGeneWhenTreeTooSmall() {
        GameState s = stateWithHarvestTree(4, 2, 2);
        s.treeSize[0] = 3; // pas assez grand
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodDropStillTakesPriorityOverHarvest() {
        // Troll porte du bois ET a un gène HARVEST → drop du bois en premier
        GameState s = stateWithHarvestTree(1, 2, 2); // adjacent shack
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 2;
        s.trollCarryTotal[0] = 2;
        short[] buf = popWithHarvestGene(4, 2);
        byte[] lens = lenBufOf1();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
        // cursor NE doit PAS avancer (c'est le wood-drop, pas le harvest-drop)
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(0);
    }
}
```

- [ ] **Step 3.2 : Vérifier RED**

```
mvn test -Dtest=TrollPolicyHarvestTest -q
```

Attendu : FAIL (les HARVEST genes sont traités comme CUT, donc comportement incorrect).

- [ ] **Step 3.3 : Implémenter dans `TrollPolicy.java`**

Dans `decideForOwnTroll`, localiser la boucle `while (cursor[trollIdx] < len)` et son contenu. Avant le bloc `if (!Genome.isPlant(g))` (qui gère CUT), insérer le branchement HARVEST :

```java
if (Genome.isHarvest(g)) {
    int gx = Genome.geneX(g), gy = Genome.geneY(g);
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
```

La structure complète de la boucle doit ressembler à :

```java
while (cursor[trollIdx] < len) {
    short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
    if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    int gx = Genome.geneX(g), gy = Genome.geneY(g);

    if (Genome.isHarvest(g)) {
        // ... bloc HARVEST ci-dessus ...
    }

    if (!Genome.isPlant(g)) {
        // ... bloc CUT existant ...
    }

    // ... bloc PLANT existant ...
}
```

> **Note** : `gx`/`gy` sont extraits avant le branchement — ils sont valides pour tous les types de gènes.

- [ ] **Step 3.4 : Vérifier GREEN**

```
mvn test -Dtest=TrollPolicyHarvestTest,TrollPolicyTest,TrollPolicyPlantTest -q
```

Attendu : BUILD SUCCESS, tous les tests passent.

- [ ] **Step 3.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyHarvestTest.java
git commit -m "feat(policy): branchement HARVEST dans decideForOwnTroll"
```

---

## Task 4 : Seeding HARVEST dans `initRandom` (`GenomeOps.java`)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsHarvestTest.java`

- [ ] **Step 4.1 : Écrire les tests (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsHarvestTest.java` :

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

class GenomeOpsHarvestTest {

    private GameState state;

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
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

        state = new GameState();
        state.trollCount = 1;
        state.trollPlayer[0] = 0;
        state.trollX[0] = 0; state.trollY[0] = 0;
        state.trollMS[0] = 2; state.trollCC[0] = 3; state.trollHP[0] = 2; state.trollCP[0] = 1;
        // Un arbre de taille 4 vivant
        state.treeCount = 1;
        state.treeX[0] = 4; state.treeY[0] = 3;
        state.treeSize[0] = 4; state.treeHealth[0] = 6;
        Genome.initHarvestCandidates(state);
    }

    // --- initRandom seeds harvest ---

    @Test void initRandom_seedsHarvestGeneWhenTrollHasHP() {
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
                    if (Genome.isHarvest((short) Genome.gene(buf, 0, j, k))) found = true;
                }
            }
        }
        assertThat(found).isTrue();
    }

    @Test void initRandom_noHarvestGeneWhenTrollHasNoHP() {
        state.trollHP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void initRandom_noHarvestWhenNoCandidates() {
        Genome.harvestCandidateCount = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(3);
        for (int i = 0; i < 30; i++) GenomeOps.initRandom(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    // --- mutateInsertHarvest ---

    @Test void mutateInsertHarvest_insertsGeneForTrollWithHP() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(13);
        boolean inserted = false;
        for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
            GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isHarvest((short) Genome.gene(buf, 0, j, k))) inserted = true;
                }
            }
        }
        assertThat(inserted).isTrue();
    }

    @Test void mutateInsertHarvest_noopWhenNoCandidates() {
        int saved = Genome.harvestCandidateCount;
        Genome.harvestCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(5);
            for (int i = 0; i < 30; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
                }
            }
        } finally {
            Genome.harvestCandidateCount = saved;
        }
    }

    @Test void mutateInsertHarvest_noopWhenTrollHasNoHP() {
        state.trollHP[0] = 0;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(9);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, 0, j);
            for (int k = 0; k < len; k++) {
                assertThat(Genome.isHarvest((short) Genome.gene(buf, 0, j, k))).isFalse();
            }
        }
    }

    @Test void mutateInsertHarvest_noDuplicateInIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // Pré-remplir avec le seul candidat existant
        short c = Genome.harvestCandidates[0];
        Genome.setGene(buf, 0, 0, 0, Genome.makeHarvest(Genome.candX(c), Genome.candY(c)));
        Genome.setLen(lens, 0, 0, 1);
        SplittableRandom rng = new SplittableRandom(17);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertHarvest(state, buf, lens, 0, rng);
        // Il n'y a qu'un seul candidat → pas d'insertion possible → toujours 1 gène
        int total = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) total += Genome.len(lens, 0, j);
        assertThat(total).isEqualTo(1);
    }
}
```

- [ ] **Step 4.2 : Vérifier RED**

```
mvn test -Dtest=GenomeOpsHarvestTest -q
```

Attendu : FAIL (`mutateInsertHarvest` n'existe pas, `initRandom` ne seed pas de HARVEST).

- [ ] **Step 4.3 : Implémenter dans `GenomeOps.java`**

**A) Ajouter les constantes** après `P_PLANT_INIT = 0.30` :

```java
public static final double P_HARVEST_INIT = 0.30;
```

**B) Ajouter les scratch buffers** après `initSeenPlant` :

```java
private static final boolean[] initSeenHarvest = new boolean[256 * 256];
private static final boolean[] mutSeenHarvest   = new boolean[256 * 256];
```

**C) Ajouter l'étape 6 dans `initRandom`**, après le bloc `// 5. Injecter gènes PLANT` :

```java
// 6. Injecter gènes HARVEST pour trolls avec HP > 0
if (Genome.harvestCandidateCount > 0) {
    int W = GameState.width;
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        initSeenHarvest[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int k = 0; k < ownTrollsCount; k++) {
        int trollIdx = ownTrollsBuf[k];
        if ((state.trollHP[trollIdx] & 0xFF) == 0) continue;
        int len = Genome.len(lenBuf, individuIdx, trollIdx);
        if (len >= Genome.MAX_TARGETS_PER_TROLL) continue;
        if (rng.nextDouble() >= P_HARVEST_INIT) continue;
        int tries = 0;
        while (tries < Genome.harvestCandidateCount) {
            short c = Genome.harvestCandidates[rng.nextInt(Genome.harvestCandidateCount)];
            int cx = Genome.candX(c), cy = Genome.candY(c);
            if (!initSeenHarvest[cy * W + cx]) {
                Genome.setGene(buf, individuIdx, trollIdx, len, Genome.makeHarvest(cx, cy));
                Genome.setLen(lenBuf, individuIdx, trollIdx, len + 1);
                initSeenHarvest[cy * W + cx] = true;
                break;
            }
            tries++;
        }
    }
}
```

**D) Ajouter `mutateInsertHarvest`** après `mutateInsertCut` :

```java
public static void mutateInsertHarvest(GameState state, short[] buf, byte[] lenBuf,
                                       int individuIdx, SplittableRandom rng) {
    if (Genome.harvestCandidateCount == 0) return;
    int count = 0;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) {
        if ((state.trollHP[j] & 0xFF) > 0
                && Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL)
            freeTrollsBuf[count++] = j;
    }
    if (count == 0) return;
    int j = freeTrollsBuf[rng.nextInt(count)];
    int len = Genome.len(lenBuf, individuIdx, j);
    int W = GameState.width;
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        mutSeenHarvest[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int tj = 0; tj < GameState.MAX_TROLLS; tj++) {
        int tjLen = Genome.len(lenBuf, individuIdx, tj);
        int base = Genome.offset(individuIdx, tj);
        for (int k = 0; k < tjLen; k++) {
            short g = buf[base + k];
            if (Genome.isHarvest(g))
                mutSeenHarvest[Genome.geneY(g) * W + Genome.geneX(g)] = true;
        }
    }
    int tries = 0;
    while (tries < Genome.harvestCandidateCount) {
        short c = Genome.harvestCandidates[rng.nextInt(Genome.harvestCandidateCount)];
        int cx = Genome.candX(c), cy = Genome.candY(c);
        if (!mutSeenHarvest[cy * W + cx]) {
            int pos = rng.nextInt(len + 1);
            int base = Genome.offset(individuIdx, j);
            for (int k = len; k > pos; k--) buf[base + k] = buf[base + k - 1];
            buf[base + pos] = Genome.makeHarvest(cx, cy);
            Genome.setLen(lenBuf, individuIdx, j, len + 1);
            return;
        }
        tries++;
    }
}
```

- [ ] **Step 4.4 : Vérifier GREEN**

```
mvn test -Dtest=GenomeOpsHarvestTest,GenomeOpsInitTest,GenomeOpsMutationTest -q
```

Attendu : BUILD SUCCESS.

- [ ] **Step 4.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsHarvestTest.java
git commit -m "feat(ops): initRandom étape 6 HARVEST + mutateInsertHarvest"
```

---

## Task 5 : Rebalancement de `pickMutationKind` dans `GenomeOps.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`

> Note : pas de test dédié pour les probabilités — elles seront calibrées via fine-tuning. On vérifie juste que la somme fait 1.0 et que `runMutation` ne lève pas d'exception pour `MUT_INSERT_HARVEST`.

- [ ] **Step 5.1 : Mettre à jour les constantes et la constante `MUT_INSERT_HARVEST`**

Remplacer les constantes de probabilité existantes et ajouter `MUT_INSERT_HARVEST` :

```java
public static final double P_MUT_SWAP_INTRA     = 0.22;
public static final double P_MUT_SWAP_INTER     = 0.18;
public static final double P_MUT_REVERSE        = 0.09;
public static final double P_MUT_DELETE         = 0.09;
public static final double P_MUT_INSERT_PLANT   = 0.13;
public static final double P_MUT_INSERT_CUT     = 0.19;
public static final double P_MUT_INSERT_HARVEST = 0.10;

public static final int MUT_SWAP_INTRA     = 0;
public static final int MUT_SWAP_INTER     = 1;
public static final int MUT_REVERSE        = 2;
public static final int MUT_DELETE         = 3;
public static final int MUT_INSERT_PLANT   = 4;
public static final int MUT_INSERT_CUT     = 5;
public static final int MUT_INSERT_HARVEST = 6;  // ← nouveau
```

- [ ] **Step 5.2 : Mettre à jour `pickMutationKind`**

Remplacer la méthode `pickMutationKind` :

```java
public static int pickMutationKind(SplittableRandom rng) {
    double r = rng.nextDouble();
    if (r < P_MUT_SWAP_INTRA)    return MUT_SWAP_INTRA;
    r -= P_MUT_SWAP_INTRA;
    if (r < P_MUT_SWAP_INTER)    return MUT_SWAP_INTER;
    r -= P_MUT_SWAP_INTER;
    if (r < P_MUT_REVERSE)       return MUT_REVERSE;
    r -= P_MUT_REVERSE;
    if (r < P_MUT_DELETE)        return MUT_DELETE;
    r -= P_MUT_DELETE;
    if (r < P_MUT_INSERT_PLANT)  return MUT_INSERT_PLANT;
    r -= P_MUT_INSERT_PLANT;
    if (r < P_MUT_INSERT_CUT)    return MUT_INSERT_CUT;
    return MUT_INSERT_HARVEST;
}
```

- [ ] **Step 5.3 : Mettre à jour `runMutation`**

Ajouter le cas `MUT_INSERT_HARVEST` dans le switch :

```java
public static void runMutation(GameState state, short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
    switch (pickMutationKind(rng)) {
        case MUT_SWAP_INTRA     -> mutateSwapIntra  (buf, lenBuf, individuIdx, rng);
        case MUT_SWAP_INTER     -> mutateSwapInter  (buf, lenBuf, individuIdx, rng);
        case MUT_REVERSE        -> mutateReverse    (buf, lenBuf, individuIdx, rng);
        case MUT_DELETE         -> mutateDelete     (buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_PLANT   -> mutateInsertPlant(buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_CUT     -> mutateInsertCut  (state, buf, lenBuf, individuIdx, rng);
        case MUT_INSERT_HARVEST -> mutateInsertHarvest(state, buf, lenBuf, individuIdx, rng);
        default -> throw new IllegalStateException();
    }
}
```

- [ ] **Step 5.4 : Vérifier**

```
mvn test -Dtest=GenomeOpsMutationTest,GenomeOpsHarvestTest -q
```

Attendu : BUILD SUCCESS.

- [ ] **Step 5.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java
git commit -m "feat(ops): MUT_INSERT_HARVEST + rebalancement pickMutationKind"
```

---

## Task 6 : Fitness fruit-carry dans `GenomeEvaluator.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorHarvestTest.java`

- [ ] **Step 6.1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorHarvestTest.java` :

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

class GenomeEvaluatorHarvestTest {

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

    @Test void fruitCarryContributesToFitness() {
        // État final : troll player=0 porte 2 bananes, 0 wood, score identique
        GameState finalState = new GameState();
        finalState.trollCount = 1;
        finalState.trollPlayer[0] = 0;
        finalState.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 2;
        finalState.trollCarryTotal[0] = 2;
        // shackInventory à 0 pour les deux joueurs

        // On accède à fitness via evaluate sur un état figé (HORIZON=0 n'existe pas,
        // donc on teste la logique fitness directement via un état copié et 0 tours simulés).
        // Trick : on évalue avec un génome vide (WAIT partout) → finalState ≈ source
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        source.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 2;
        source.trollCarryTotal[0] = 2;
        if (source.treeCellIndex == null)
            source.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.treeCellIndex, (byte) -1);
        if (source.trollCellIndex == null)
            source.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.trollCellIndex, (byte) -1);
        source.trollCellIndex[source.trollY[0] * GameState.width + source.trollX[0]] = 0;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();

        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
        // 2 bananes en transit × ALPHA_FRUIT_CARRY(2.0) = 4.0 au minimum
        assertThat(fit).isGreaterThanOrEqualTo(2 * GenomeEvaluator.ALPHA_FRUIT_CARRY - 1e-9);
    }

    @Test void fruitCarryIgnoresOpponent() {
        // Troll player=1 porte des fruits → ne contribue pas à la fitness
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 1; // adversaire
        source.trollInventory[0 * ResourceType.COUNT + ResourceType.PLUM] = 3;
        source.trollCarryTotal[0] = 3;
        if (source.treeCellIndex == null)
            source.treeCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.treeCellIndex, (byte) -1);
        if (source.trollCellIndex == null)
            source.trollCellIndex = new byte[GameState.width * GameState.height];
        java.util.Arrays.fill(source.trollCellIndex, (byte) -1);
        source.trollCellIndex[source.trollY[0] * GameState.width + source.trollX[0]] = 0;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        GameState scratch = new GameState();

        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lens, 0, actionBuf);
        assertThat(fit).isLessThanOrEqualTo(1e-9); // 0.0, les fruits adversaire ne comptent pas
    }
}
```

- [ ] **Step 6.2 : Vérifier RED**

```
mvn test -Dtest=GenomeEvaluatorHarvestTest -q
```

Attendu : FAIL (fitness ne tient pas compte des fruits en transit).

- [ ] **Step 6.3 : Implémenter dans `GenomeEvaluator.java`**

Ajouter la constante après `ALPHA_WOOD_CARRY` :

```java
public static final double ALPHA_FRUIT_CARRY = 2.0;
```

Remplacer la méthode `fitness` :

```java
private static double fitness(GameState finalState) {
    int scoreMe = finalState.score(0);
    int scoreOpp = finalState.score(1);
    int woodCarryMe = 0;
    int fruitCarryMe = 0;
    for (int i = 0; i < finalState.trollCount; i++) {
        if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
        int base = i * ResourceType.COUNT;
        woodCarryMe += finalState.trollInventory[base + ResourceType.WOOD] & 0xFF;
        for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
            fruitCarryMe += finalState.trollInventory[base + r] & 0xFF;
    }
    return (scoreMe - scoreOpp)
         + ALPHA_WOOD_CARRY  * woodCarryMe
         + ALPHA_FRUIT_CARRY * fruitCarryMe;
}
```

- [ ] **Step 6.4 : Vérifier GREEN**

```
mvn test -Dtest=GenomeEvaluatorHarvestTest,GenomeEvaluatorTest -q
```

Attendu : BUILD SUCCESS.

- [ ] **Step 6.5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorHarvestTest.java
git commit -m "feat(fitness): ALPHA_FRUIT_CARRY — score fruits en transit"
```

---

## Task 7 : Régression complète

- [ ] **Step 7.1 : Lancer toute la suite de tests**

```
mvn test -q
```

Attendu : BUILD SUCCESS, 0 failures.

- [ ] **Step 7.2 : Commit final si tout est vert**

```
git commit --allow-empty -m "test: régression complète harvest actions OK"
```

---

## Résumé des commits attendus

1. `feat(genome): HARVEST flag encoding + harvestCandidates`
2. `feat(agent): initHarvestCandidates chaque tour dans decide()`
3. `feat(policy): branchement HARVEST dans decideForOwnTroll`
4. `feat(ops): initRandom étape 6 HARVEST + mutateInsertHarvest`
5. `feat(ops): MUT_INSERT_HARVEST + rebalancement pickMutationKind`
6. `feat(fitness): ALPHA_FRUIT_CARRY — score fruits en transit`
7. `test: régression complète harvest actions OK`
