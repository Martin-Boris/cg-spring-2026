# Greedy Train Every Turn — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entraîner un nouveau troll dès que les ressources le permettent (chaque tour, pas seulement le tour 0), avec des stats minimales obligatoires, désactivation après le tour 200, max 5 trolls, et exemption des gènes CUT pour les trolls cp=0.

**Architecture:** `GreedyAgent.maybeTrain()` porte toutes les guards (turn, n, stats minimales) et est appelé à chaque tour dans `GeneticAgent.decide()`. Le filtre cp=0 est appliqué en amont dans `GenomeOps` (initRandom, initWarm, mutateInsertCut) et en filet de sécurité dans `TrollPolicy`.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Maven (`mvn test`)

---

### Task 1 — Refactor `GreedyAgent.maybeTrain()`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java:15-32`
- Modify: `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentTrainTest.java`

- [ ] **Step 1 — Mettre à jour les deux tests qui vont changer de comportement**

Dans `GreedyAgentTrainTest.java`, remplacer les deux méthodes suivantes :

```java
// AVANT (supprimer ces deux méthodes)
@Test void noTrainWhenAppleInsufficient() { ... }
@Test void noTrainWhenIronInsufficient() { ... }

// APRÈS (les remplacer par)
@Test
void trainWhenAppleZeroButIronSufficient() {
    // n=1 : iron=5 → cp=maxV(5,1,0)=2 ; apple=0 → hp=0 ; cp≠0 → train
    GameState s = stateWithSingleTroll(5, 5, 0, 5);
    int a = GreedyAgent.maybeTrain(s);
    assertThat(Action.type(a)).isEqualTo(ActionType.TRAIN);
    assertThat(Action.trainHP(a)).isEqualTo(0);
    assertThat(Action.trainCP(a)).isEqualTo(2);
}

@Test
void trainWhenIronZeroButAppleSufficient() {
    // n=1 : apple=5 → hp=maxV(5,1,0)=2 ; iron=0 → cp=0 ; hp≠0 → train
    GameState s = stateWithSingleTroll(5, 5, 5, 0);
    int a = GreedyAgent.maybeTrain(s);
    assertThat(Action.type(a)).isEqualTo(ActionType.TRAIN);
    assertThat(Action.trainCP(a)).isEqualTo(0);
    assertThat(Action.trainHP(a)).isEqualTo(2);
}
```

- [ ] **Step 2 — Ajouter les 4 nouveaux tests**

Toujours dans `GreedyAgentTrainTest.java`, ajouter après les tests existants :

```java
@Test
void noTrainWhenBothHpAndCpImpossible() {
    // n=1 : apple=0 → hp=0, iron=0 → cp=0, les deux nuls → -1
    GameState s = stateWithSingleTroll(5, 5, 0, 0);
    assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
}

@Test
void noTrainWhenTurnIsAtOrAbove200() {
    GameState s = stateWithSingleTroll(5, 5, 5, 5);
    s.turn = 200;
    assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
}

@Test
void trainStillPossibleAtTurn199() {
    GameState s = stateWithSingleTroll(5, 5, 5, 5);
    s.turn = 199;
    assertThat(Action.type(GreedyAgent.maybeTrain(s))).isEqualTo(ActionType.TRAIN);
}

@Test
void noTrainWhenFiveTrolls() {
    GameState.width = 4; GameState.height = 3;
    GameState.tiles = new byte[12];
    java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
    GameState s = new GameState();
    s.trollCount = 5;
    for (int i = 0; i < 5; i++) s.trollPlayer[i] = 0;
    s.shackInventory[ResourceType.PLUM]  = 99;
    s.shackInventory[ResourceType.LEMON] = 99;
    s.shackInventory[ResourceType.APPLE] = 99;
    s.shackInventory[ResourceType.IRON]  = 99;
    assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
}
```

- [ ] **Step 3 — Vérifier que les tests échouent**

```
mvn test -pl . -Dtest=GreedyAgentTrainTest -q
```

Attendu : 6 tests FAIL (`trainWhenAppleZeroButIronSufficient`, `trainWhenIronZeroButAppleSufficient`, `noTrainWhenBothHpAndCpImpossible`, `noTrainWhenTurnIsAtOrAbove200`, `trainStillPossibleAtTurn199`, `noTrainWhenFiveTrolls`)

- [ ] **Step 4 — Implémenter le nouveau `maybeTrain()`**

Dans `GreedyAgent.java`, remplacer la méthode `maybeTrain` (lignes 15–32) :

```java
public static int maybeTrain(GameState s) {
    int n = countOwnTrolls(s);
    if (n >= 5) return -1;
    if (s.turn >= 200) return -1;

    int plum  = s.shackInventory[ResourceType.PLUM];
    int lemon = s.shackInventory[ResourceType.LEMON];
    int apple = s.shackInventory[ResourceType.APPLE];
    int iron  = s.shackInventory[ResourceType.IRON];

    if (plum  < n + 1) return -1;
    if (lemon < n + 1) return -1;

    int ms = maxV(plum,  n, 1);
    int cc = maxV(lemon, n, 1);
    int hp = maxV(apple, n, 0);
    int cp = maxV(iron,  n, 0);

    if (hp == 0 && cp == 0) return -1;

    return Action.train(ms, cc, hp, cp);
}
```

- [ ] **Step 5 — Vérifier que tous les tests de GreedyAgentTrainTest passent**

```
mvn test -pl . -Dtest=GreedyAgentTrainTest -q
```

Attendu : BUILD SUCCESS, 10 tests PASS

- [ ] **Step 6 — Commit**

```
git add src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java \
        src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentTrainTest.java
git commit -m "feat(greedy): maybeTrain — turn<200, n<5, ms+cc+min(hp|cp)"
```

---

### Task 2 — GeneticAgent : injection TRAIN à chaque tour

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java:307-315`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java`

- [ ] **Step 1 — Ajouter un test Red**

Dans `GeneticAgentTest.java`, ajouter après `decideEmitsTrainAtTurnZero` :

```java
@Test
void decideEmitsTrainAtNonZeroTurnWhenResourcesAllow() {
    GameState s = seededState();
    s.turn = 5;
    s.shackInventory[ResourceType.PLUM]  = 5;
    s.shackInventory[ResourceType.LEMON] = 5;
    s.shackInventory[ResourceType.APPLE] = 5;
    s.shackInventory[ResourceType.IRON]  = 5;
    GeneticAgent agent = new GeneticAgent();
    int[] out = new int[GameState.MAX_TROLLS + 1];
    long deadline = System.nanoTime() + 100_000_000L;
    int n = agent.decide(s, deadline, out);
    boolean hasTrain = false;
    for (int i = 0; i < n; i++)
        if (Action.type(out[i]) == ActionType.TRAIN) { hasTrain = true; break; }
    assertThat(hasTrain).isTrue();
}
```

- [ ] **Step 2 — Vérifier que le test échoue**

```
mvn test -pl . -Dtest=GeneticAgentTest#decideEmitsTrainAtNonZeroTurnWhenResourcesAllow -q
```

Attendu : FAIL

- [ ] **Step 3 — Supprimer la garde `turn == 0` dans GeneticAgent**

Dans `GeneticAgent.java`, remplacer le bloc (autour de la ligne 307) :

```java
// AVANT
// 5. Ajouter TRAIN au tour 0
if (state.turn == 0) {
    int trainAction = GreedyAgent.maybeTrain(state);
    if (trainAction != -1) {
        System.arraycopy(outActions, 0, outActions, 1, n);
        outActions[0] = trainAction;
        n++;
    }
}
```

par :

```java
// APRÈS
int trainAction = GreedyAgent.maybeTrain(state);
if (trainAction != -1) {
    System.arraycopy(outActions, 0, outActions, 1, n);
    outActions[0] = trainAction;
    n++;
}
```

- [ ] **Step 4 — Vérifier que tous les tests de GeneticAgentTest passent**

```
mvn test -pl . -Dtest=GeneticAgentTest -q
```

Attendu : BUILD SUCCESS. Note : `decideProducesOneActionPerTrollWhenNoTrain` passe toujours car les ressources de `seededState()` sont à 0 (maybeTrain → -1).

- [ ] **Step 5 — Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java \
        src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java
git commit -m "feat(ga): inject TRAIN every turn, not just turn 0"
```

---

### Task 3 — GenomeOps `initRandom` : filtre cp=0

**Files:**
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java:51-66`

- [ ] **Step 1 — Créer le fichier de test avec les deux premiers tests**

Créer `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java` :

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

class GenomeOpsCutFilterTest {

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
                if (!Genome.isPlant(g) && !Genome.isHarvest(g)) return true;
            }
        }
        return false;
    }

    // ── initRandom ──────────────────────────────────────────────────────────

    @Test void initRandom_noCutGeneWhenTrollHasNoCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 0; s.trollHP[0] = 0; // cp=0 : doit pas recevoir CUT
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        Genome.initHarvestCandidates(s);

        short[] buf = newBuf();
        byte[]  lens = newLen();
        SplittableRandom rng = new SplittableRandom(42);
        for (int i = 0; i < 50; i++) GenomeOps.initRandom(s, buf, lens, 0, rng);
        assertThat(hasCutGene(buf, lens, 0)).isFalse();
    }

    @Test void initRandom_canAssignCutGeneWhenTrollHasCP() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 1; s.trollHP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;
        Genome.initHarvestCandidates(s);

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
}
```

- [ ] **Step 2 — Vérifier que le premier test échoue**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest#initRandom_noCutGeneWhenTrollHasNoCP -q
```

Attendu : FAIL (l'arbre est assigné même avec cp=0)

- [ ] **Step 3 — Ajouter le filtre cp=0 dans `initRandom` étape 4**

Dans `GenomeOps.java`, dans la boucle de distribution des gènes CUT (étape 4, lignes ~54-59) :

```java
// AVANT
for (int k = 0; k < ownTrollsCount; k++) {
    int trollIdx = ownTrollsBuf[k];
    if (Genome.len(lenBuf, individuIdx, trollIdx) < Genome.MAX_TARGETS_PER_TROLL) {
        freeTrollsBuf[freeCount++] = trollIdx;
    }
}

// APRÈS
for (int k = 0; k < ownTrollsCount; k++) {
    int trollIdx = ownTrollsBuf[k];
    if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;
    if (Genome.len(lenBuf, individuIdx, trollIdx) < Genome.MAX_TARGETS_PER_TROLL) {
        freeTrollsBuf[freeCount++] = trollIdx;
    }
}
```

- [ ] **Step 4 — Vérifier que les deux tests passent et que la suite ne régresse pas**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest,GenomeOpsHarvestTest,GenomeOpsInitTest -q
```

Attendu : BUILD SUCCESS. Si `GenomeOpsInitTest` échoue, vérifier que les trolls dans ses états ont `trollCP > 0`.

- [ ] **Step 5 — Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java
git commit -m "feat(ga): initRandom — skip CUT genes for trolls with cp=0"
```

---

### Task 4 — GenomeOps `initWarm` : filtre cp=0

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java:148-175` (initWarm inner loop)
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java`

- [ ] **Step 1 — Ajouter le test Red dans `GenomeOpsCutFilterTest`**

Ajouter dans `GenomeOpsCutFilterTest.java` :

```java
@Test void initWarm_noCutGeneWhenTrollHasNoCP() {
    GameState s = new GameState();
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.trollX[0] = 0; s.trollY[0] = 0;
    s.trollCP[0] = 0;
    s.treeCount = 1;
    s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

    short[] buf = newBuf();
    byte[]  lens = newLen();
    GenomeOps.initWarm(s, buf, lens, 0);
    assertThat(hasCutGene(buf, lens, 0)).isFalse();
}
```

- [ ] **Step 2 — Vérifier que le test échoue**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest#initWarm_noCutGeneWhenTrollHasNoCP -q
```

Attendu : FAIL

- [ ] **Step 3 — Ajouter le filtre cp=0 dans `initWarm`**

Dans `GenomeOps.java`, dans la boucle interne de `initWarm` (autour de la ligne 148), ajouter le guard en tête du bloc pour chaque troll :

```java
// AVANT
for (int kT = 0; kT < ownCount; kT++) {
    int troll = ownTrollsBuf[kT];
    int bestTree = -1;
    int bestDist = PathTable.UNREACHABLE;

// APRÈS
for (int kT = 0; kT < ownCount; kT++) {
    int troll = ownTrollsBuf[kT];
    if ((state.trollCP[troll] & 0xFF) == 0) continue;
    int bestTree = -1;
    int bestDist = PathTable.UNREACHABLE;
```

- [ ] **Step 4 — Lancer les tests GenomeOpsWarmStartTest**

```
mvn test -pl . -Dtest=GenomeOpsWarmStartTest -q
```

Attendu : certains tests FAIL car `stateWith` ne positionne pas `trollCP`. Passer à l'étape suivante.

- [ ] **Step 5 — Mettre à jour `stateWith` dans `GenomeOpsWarmStartTest`**

Dans `GenomeOpsWarmStartTest.java`, mettre à jour la méthode `stateWith` pour positionner `trollCP` :

```java
private static GameState stateWith(int[][] trolls, int[][] trees) {
    GameState s = new GameState();
    for (int[] tr : trolls) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) tr[0];
        s.trollX[i]      = (byte) tr[1];
        s.trollY[i]      = (byte) tr[2];
        s.trollCP[i]     = (byte) (tr[0] == 0 ? 1 : 0); // own trolls: cp=1 par défaut
    }
    for (int[] t : trees) {
        int i = s.treeCount++;
        s.treeX[i]      = (byte) t[0];
        s.treeY[i]      = (byte) t[1];
        s.treeHealth[i] = (byte) t[2];
    }
    return s;
}
```

- [ ] **Step 6 — Vérifier que tous les tests passent**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest,GenomeOpsWarmStartTest -q
```

Attendu : BUILD SUCCESS

- [ ] **Step 7 — Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "feat(ga): initWarm — skip CUT genes for trolls with cp=0"
```

---

### Task 5 — GenomeOps `mutateInsertCut` : filtre cp=0

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java:378-384` (sélection du troll dans mutateInsertCut)
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java`

- [ ] **Step 1 — Ajouter le test Red dans `GenomeOpsCutFilterTest`**

Ajouter dans `GenomeOpsCutFilterTest.java` :

```java
@Test void mutateInsertCut_noopWhenTrollHasNoCP() {
    GameState s = new GameState();
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.trollX[0] = 0; s.trollY[0] = 0;
    s.trollCP[0] = 0;
    s.treeCount = 1;
    s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

    short[] buf = newBuf();
    byte[]  lens = newLen();
    SplittableRandom rng = new SplittableRandom(99);
    for (int i = 0; i < 50; i++) GenomeOps.mutateInsertCut(s, buf, lens, 0, rng);
    assertThat(hasCutGene(buf, lens, 0)).isFalse();
}
```

- [ ] **Step 2 — Vérifier que le test échoue**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest#mutateInsertCut_noopWhenTrollHasNoCP -q
```

Attendu : FAIL

- [ ] **Step 3 — Mettre à jour la sélection du troll dans `mutateInsertCut`**

Dans `GenomeOps.java`, dans `mutateInsertCut`, remplacer la boucle de sélection du troll (lignes ~378-384) :

```java
// AVANT
int count = 0;
for (int j = 0; j < GameState.MAX_TROLLS; j++) {
    if (Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL) {
        freeTrollsBuf[count++] = j;
    }
}

// APRÈS
int count = 0;
for (int j = 0; j < state.trollCount; j++) {
    if ((state.trollPlayer[j] & 0xFF) != 0) continue;
    if ((state.trollCP[j] & 0xFF) == 0) continue;
    if (Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL)
        freeTrollsBuf[count++] = j;
}
```

- [ ] **Step 4 — Vérifier que tous les tests GenomeOps passent**

```
mvn test -pl . -Dtest=GenomeOpsCutFilterTest,GenomeOpsMutationTest -q
```

Attendu : BUILD SUCCESS

- [ ] **Step 5 — Commit**

```
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCutFilterTest.java
git commit -m "feat(ga): mutateInsertCut — exclut les trolls avec cp=0"
```

---

### Task 6 — TrollPolicy : guard défensif cp=0 sur les gènes CUT

**Files:**
- Create: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyCutFilterTest.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java:88-92`

- [ ] **Step 1 — Créer le fichier de test**

Créer `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyCutFilterTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrollPolicyCutFilterTest {

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

    @Test void cutGeneSkippedWhenTrollHasNoCp() {
        // Troll cp=0 en (0,0), arbre en (3,2). Gène CUT pointant vers l'arbre.
        // Attendu : le gène CUT est sauté, curseur avance, plus de gènes → WAIT.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollMS[0] = 2; s.trollCC[0] = 2; s.trollHP[0] = 0; s.trollCP[0] = 0;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2)); // gène CUT vers (3,2)
        Genome.setLen(lens, 0, 0, 1);

        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillOwnActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(n).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo(ActionType.WAIT);
    }

    @Test void cutGeneExecutedWhenTrollHasCp() {
        // Troll cp>0 en (0,0), arbre en (3,2). Gène CUT → doit se déplacer vers l'arbre.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollMS[0] = 2; s.trollCC[0] = 2; s.trollHP[0] = 0; s.trollCP[0] = 1;
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2; s.treeHealth[0] = 5;

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2));
        Genome.setLen(lens, 0, 0, 1);

        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillOwnActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);

        assertThat(Action.type(out[0])).isEqualTo(ActionType.MOVE);
    }
}
```

- [ ] **Step 2 — Vérifier que le premier test échoue**

```
mvn test -pl . -Dtest=TrollPolicyCutFilterTest#cutGeneSkippedWhenTrollHasNoCp -q
```

Attendu : FAIL (le troll se déplace vers l'arbre au lieu de WAIT)

- [ ] **Step 3 — Ajouter le guard cp=0 dans `TrollPolicy.decideForOwnTroll`**

Dans `TrollPolicy.java`, dans le branchement CUT (lignes 88-92), ajouter la garde en tête :

```java
// AVANT
if (!Genome.isPlant(g)) {
    if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    if (tx == gx && ty == gy) return Action.chop(trollIdx);
    return Action.move(trollIdx, gx, gy);
}

// APRÈS
if (!Genome.isPlant(g)) {
    if ((s.trollCP[trollIdx] & 0xFF) == 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
    if (tx == gx && ty == gy) return Action.chop(trollIdx);
    return Action.move(trollIdx, gx, gy);
}
```

- [ ] **Step 4 — Vérifier que tous les tests TrollPolicy passent**

```
mvn test -pl . -Dtest=TrollPolicyCutFilterTest,TrollPolicyTest,TrollPolicyHarvestTest,TrollPolicyPlantTest -q
```

Attendu : BUILD SUCCESS

- [ ] **Step 5 — Lancer la suite complète**

```
mvn test -q
```

Attendu : BUILD SUCCESS

- [ ] **Step 6 — Commit final**

```
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java \
        src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyCutFilterTest.java
git commit -m "feat(policy): skip CUT gene when troll has cp=0"
```
