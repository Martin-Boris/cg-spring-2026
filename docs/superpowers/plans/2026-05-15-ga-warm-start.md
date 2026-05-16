# GA Warm-Start Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire un individu seed à l'index 0 de la population initiale du GA, construit par round-robin "plus proche arbre depuis la position courante du troll", en respectant la contrainte d'unicité d'arbre par individu.

**Architecture:** Nouvelle méthode statique `GenomeOps.initWarm(state, buf, lenBuf, individuIdx)` purement déterministe utilisant `PathTable.distance`. Intégration ciblée dans `GeneticAgent.initPopulation` pour seeder uniquement l'index 0 (les 19 autres individus conservent `initRandom`, préservant la diversité).

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. Code dans `src/main/java/com/bmrt/cgspring2026/ga/`, tests dans `src/test/java/com/bmrt/cgspring2026/ga/`.

---

## File Structure

- **Modify** `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
  Ajout de la méthode `initWarm` + 3 scratch buffers statiques (`warmTreeTakenBuf`, `warmCurX`, `warmCurY`). Aucun changement aux méthodes existantes.

- **Modify** `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
  Modification de `initPopulation` pour appeler `initWarm` sur l'individu 0.

- **Create** `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`
  Tests JUnit 5 couvrant les invariants et plusieurs scénarios déterministes (closest first, chaînage, round-robin, unicité, vide).

- **Create** `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentInitWarmTest.java`
  Test d'intégration : `initPopulation` produit bien un individu seed à l'index 0 et 19 individus restants distincts.

---

## Pré-requis

- `mvn -q -DskipTests=false test` doit passer en l'état actuel avant de commencer.
- Branche : continuer sur `ga-v-1`.

---

### Task 1: Squelette `initWarm` + cas vides

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Écrire les tests des cas vides**

Crée `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsWarmStartTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            "......",
            "......",
            "......",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                GameState.tiles[y * GameState.width + x] = TileType.fromChar(rows[y].charAt(x));
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    private static short[] newBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newLen() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private static GameState stateWith(int[][] trolls, int[][] trees) {
        GameState s = new GameState();
        for (int[] tr : trolls) {
            int i = s.trollCount++;
            s.trollPlayer[i] = (byte) tr[0];
            s.trollX[i]      = (byte) tr[1];
            s.trollY[i]      = (byte) tr[2];
        }
        for (int[] t : trees) {
            int i = s.treeCount++;
            s.treeX[i]      = (byte) t[0];
            s.treeY[i]      = (byte) t[1];
            s.treeHealth[i] = (byte) t[2];
        }
        return s;
    }

    @Test void emptyWhenNoOwnTrolls() {
        GameState s = stateWith(new int[][]{ {1, 0, 0} }, new int[][]{ {3, 3, 5} });
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(len, 0, j)).isEqualTo(0);
        }
    }

    @Test void emptyWhenNoTrees() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{});
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(0);
    }

    @Test void emptyWhenAllTreesDead() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{ {3, 3, 0}, {4, 4, 0} });
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(0);
    }
}
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent à la compilation**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest`
Résultat attendu : ÉCHEC à la compilation (`GenomeOps.initWarm` n'existe pas encore).

- [ ] **Step 3 : Ajouter la signature et les scratch buffers**

Dans `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`, après la ligne `private static final int[] freeTrollsBuf = new int[GameState.MAX_TROLLS];`, ajouter :

```java
    private static final boolean[] warmTreeTakenBuf = new boolean[GameState.MAX_TREES];
    private static final int[]     warmCurX         = new int[GameState.MAX_TROLLS];
    private static final int[]     warmCurY         = new int[GameState.MAX_TROLLS];
```

Puis ajouter la méthode `initWarm` avec uniquement reset + early returns pour les cas vides. Place-la juste après `initRandom` :

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

        // 3. Init position courante par troll + reset trees pris
        for (int k = 0; k < ownCount; k++) {
            int troll = ownTrollsBuf[k];
            warmCurX[troll] = state.trollX[troll] & 0xFF;
            warmCurY[troll] = state.trollY[troll] & 0xFF;
        }
        for (int t = 0; t < state.treeCount; t++) warmTreeTakenBuf[t] = false;

        // 4. Boucle round-robin par couche — à compléter dans la tâche suivante
    }
```

- [ ] **Step 4 : Vérifier que les tests passent**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest`
Résultat attendu : 3 tests OK (emptyWhenNoOwnTrolls, emptyWhenNoTrees, emptyWhenAllTreesDead).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: initWarm squelette + cas vides (no trolls / no trees)"
```

---

### Task 2: Plus proche arbre depuis position du troll (couche 0)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Ajouter le test "closest first"**

Dans `GenomeOpsWarmStartTest.java`, ajouter :

```java
    @Test void assignsClosestTreeFirst() {
        // troll en (1,1), arbres en (1,2) [d=1] et (5,5) [d=8]
        GameState s = stateWith(
            new int[][]{ {0, 1, 1} },
            new int[][]{ {5, 5, 5}, {1, 2, 5} }   // ordre exprès non trié
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(1);
        assertThat(Genome.geneY(g0)).isEqualTo(2);
    }
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest#assignsClosestTreeFirst`
Résultat attendu : ÉCHEC — `len == 0` car la boucle round-robin n'est pas encore implémentée.

- [ ] **Step 3 : Implémenter la boucle round-robin**

Dans `GenomeOps.initWarm`, remplacer le commentaire `// 4. Boucle round-robin par couche — à compléter dans la tâche suivante` par :

```java
        // 4. Boucle round-robin par couche
        for (int kLayer = 0; kLayer < Genome.MAX_TARGETS_PER_TROLL; kLayer++) {
            boolean anyAssigned = false;
            for (int kT = 0; kT < ownCount; kT++) {
                int troll = ownTrollsBuf[kT];
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
```

Ajoute l'import en haut du fichier si pas déjà présent :

```java
import com.bmrt.cgspring2026.pathfinding.PathTable;
```

- [ ] **Step 4 : Vérifier que tous les tests existants passent**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest`
Résultat attendu : 4 tests OK (3 cas vides + closest first).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: initWarm round-robin couche par couche, closest-first"
```

---

### Task 3: Chaînage depuis le dernier arbre assigné

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Ajouter le test de chaînage**

Dans `GenomeOpsWarmStartTest.java` :

```java
    @Test void chainsFromLastAssignedTree() {
        // troll (0,0) ; arbres en (5,5), (1,0), (2,0).
        // Attendu : depuis (0,0) → (1,0) d=1 ; depuis (1,0) → (2,0) d=1 ; depuis (2,0) → (5,5) d=6
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {5, 5, 5}, {1, 0, 5}, {2, 0, 5} }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(Genome.len(len, 0, 0)).isEqualTo(3);
        short g0 = (short) Genome.gene(buf, 0, 0, 0);
        short g1 = (short) Genome.gene(buf, 0, 0, 1);
        short g2 = (short) Genome.gene(buf, 0, 0, 2);
        assertThat(Genome.geneX(g0)).isEqualTo(1); assertThat(Genome.geneY(g0)).isEqualTo(0);
        assertThat(Genome.geneX(g1)).isEqualTo(2); assertThat(Genome.geneY(g1)).isEqualTo(0);
        assertThat(Genome.geneX(g2)).isEqualTo(5); assertThat(Genome.geneY(g2)).isEqualTo(5);
    }
```

- [ ] **Step 2 : Lancer pour vérifier que le test passe directement**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest#chainsFromLastAssignedTree`
Résultat attendu : OK (l'implé de Task 2 met déjà à jour `warmCurX/Y`).

Si le test échoue : vérifier que `warmCurX[troll] = bx; warmCurY[troll] = by;` est bien écrit dans le bloc `bestTree >= 0`.

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: test — initWarm enchaîne les targets depuis la dernière assignée"
```

---

### Task 4: Round-robin équitable entre trolls

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Ajouter le test**

```java
    @Test void roundRobinDistributesBetweenTrolls() {
        // Troll0 (0,0), Troll1 (5,5).
        // Arbres : (1,0) près du T0 ; (5,4) près du T1 ; (0,1) près du T0 ; (4,5) près du T1.
        // Attendu : chaque troll récupère ses arbres voisins (couche 0 : (1,0) et (5,4)).
        GameState s = stateWith(
            new int[][]{ {0, 0, 0}, {0, 5, 5} },
            new int[][]{ {1, 0, 5}, {5, 4, 5}, {0, 1, 5}, {4, 5, 5} }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        // T0 prend (1,0) au k=0
        short t0k0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.geneX(t0k0)).isEqualTo(1);
        assertThat(Genome.geneY(t0k0)).isEqualTo(0);
        // T1 prend (5,4) au k=0
        short t1k0 = (short) Genome.gene(buf, 0, 1, 0);
        assertThat(Genome.geneX(t1k0)).isEqualTo(5);
        assertThat(Genome.geneY(t1k0)).isEqualTo(4);
    }
```

- [ ] **Step 2 : Lancer le test**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest#roundRobinDistributesBetweenTrolls`
Résultat attendu : OK (la boucle round-robin par couche garantit ce comportement).

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: test — initWarm distribue équitablement entre trolls"
```

---

### Task 5: Unicité quand deux trolls visent le même arbre

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Ajouter le test**

```java
    @Test void uniquenessWhenTrollsCompeteForSameTree() {
        // T0 (0,0), T1 (2,0). Un seul arbre proche en (1,0) (d=1 pour les deux).
        // En round-robin couche 0 : T0 prend (1,0) ; T1 doit prendre un autre arbre ou rien.
        // Ajoutons un autre arbre lointain en (5,5) pour que T1 ait quelque chose.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0}, {0, 2, 0} },
            new int[][]{ {1, 0, 5}, {5, 5, 5} }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        // T0 prend (1,0)
        short t0k0 = (short) Genome.gene(buf, 0, 0, 0);
        assertThat(Genome.geneX(t0k0)).isEqualTo(1);
        assertThat(Genome.geneY(t0k0)).isEqualTo(0);
        // T1 ne prend PAS (1,0) ; il prend (5,5)
        short t1k0 = (short) Genome.gene(buf, 0, 1, 0);
        assertThat(Genome.geneX(t1k0)).isEqualTo(5);
        assertThat(Genome.geneY(t1k0)).isEqualTo(5);
        // Invariants tenus
        assertThat(GenomeInvariants.check(buf, len, 0)).isTrue();
    }
```

- [ ] **Step 2 : Lancer le test**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest#uniquenessWhenTrollsCompeteForSameTree`
Résultat attendu : OK.

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: test — initWarm respecte l'unicité d'arbre entre trolls"
```

---

### Task 6: Invariants globaux sur un scénario plus dense

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java`

- [ ] **Step 1 : Ajouter le test**

```java
    @Test void satisfiesInvariantsOnDenseScenario() {
        // 3 trolls own + 1 troll opp, 12 arbres répartis sur la grille 6x6.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0}, {0, 5, 0}, {0, 0, 5}, {1, 5, 5} },
            new int[][]{
                {1, 0, 5}, {2, 0, 5}, {3, 0, 5}, {4, 0, 5},
                {0, 2, 5}, {2, 2, 5}, {4, 2, 5},
                {1, 4, 5}, {3, 4, 5},
                {0, 5, 5}, {2, 5, 5}, {4, 5, 5}
            }
        );
        short[] buf = newBuf();
        byte[]  len = newLen();
        GenomeOps.initWarm(s, buf, len, 0);
        assertThat(GenomeInvariants.check(buf, len, 0)).isTrue();
        // Le troll opp (idx 3) n'a aucune target
        assertThat(Genome.len(len, 0, 3)).isEqualTo(0);
        // Au moins 3 targets globalement assignées (chaque own troll a son arbre voisin)
        int total = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) total += Genome.len(len, 0, j);
        assertThat(total).isGreaterThanOrEqualTo(3);
    }
```

- [ ] **Step 2 : Lancer**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsWarmStartTest`
Résultat attendu : 7 tests OK au total.

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java
git commit -m "ga: test — initWarm tient les invariants sur scénario dense"
```

---

### Task 7: Câblage dans `GeneticAgent.initPopulation`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentInitWarmTest.java`

- [ ] **Step 1 : Écrire le test d'intégration**

Crée `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentInitWarmTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentInitWarmTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            "......",
            "......",
            "......",
            "......",
            "......"
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                GameState.tiles[y * GameState.width + x] = TileType.fromChar(rows[y].charAt(x));
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    @Test void individual0MatchesWarmStartOutput() {
        // Reproduit le câblage : initPopulation doit produire à l'index 0
        // exactement ce que initWarm produit isolément sur le même state.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.treeCount = 2;
        s.treeX[0] = 5; s.treeY[0] = 5; s.treeHealth[0] = 5;
        s.treeX[1] = 1; s.treeY[1] = 0; s.treeHealth[1] = 5;

        // Référence : ce que initWarm doit produire
        short[] refBuf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(refBuf, Genome.EMPTY_GENE);
        byte[]  refLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GenomeOps.initWarm(s, refBuf, refLen, 0);

        // Exécution réelle via l'agent — deadline DÉJÀ PASSÉE
        // pour qu'aucune génération ne s'exécute (sinon l'élitisme peut écraser pop.cur[0]).
        // initPopulation + evaluatePopulation s'exécutent, puis la boucle while sort
        // immédiatement, et pop.cur[0] reste l'individu warm-start initial.
        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        // Sanity : initWarm produit bien (1,0) en premier
        assertThat(Genome.len(refLen, 0, 0)).isGreaterThanOrEqualTo(1);
        short g0 = (short) Genome.gene(refBuf, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(1);
        assertThat(Genome.geneY(g0)).isEqualTo(0);
    }
}
```

Note : ce test vérifie surtout que `initWarm` est appelé sur l'individu 0 via le contrat (la sortie de `initWarm` isolée). Le `decide` est lancé en plus pour s'assurer qu'aucune exception ne fuit. Si on veut tester plus finement le câblage, on peut promouvoir `pop` en package-private — cf. Step 4.

- [ ] **Step 2 : Lancer le test (il doit passer ou échouer selon l'état)**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentInitWarmTest`
Résultat attendu : le test peut déjà passer puisqu'il vérifie surtout la sortie de `initWarm`. Si oui, on passe au Step 3. S'il échoue, c'est que `initWarm` ne fonctionne pas comme attendu — corriger dans `GenomeOps`.

- [ ] **Step 3 : Modifier `GeneticAgent.initPopulation`**

Dans `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`, remplacer :

```java
    private void initPopulation(GameState state) {
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
        }
    }
```

par :

```java
    private void initPopulation(GameState state) {
        GenomeOps.initWarm(state, pop.cur, pop.curLen, 0);
        for (int i = 1; i < Genome.POP_SIZE; i++) {
            GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
        }
    }
```

- [ ] **Step 4 : Promouvoir `pop` en package-private pour permettre une assertion fine**

Toujours dans `GeneticAgent.java`, changer :

```java
    private final Population pop = new Population();
```

en :

```java
    final Population pop = new Population();
```

Puis renforcer le test : ajouter dans `GeneticAgentInitWarmTest.java`, à la fin de `individual0MatchesWarmStartOutput`, juste après le `agent.decide(...)` :

```java
        // Câblage : l'individu 0 de la population doit être identique au warm-start de référence
        // (puisque deadline est dépassée, aucune génération n'a été exécutée).
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(agent.pop.curLen, 0, j))
                .as("len troll %d", j)
                .isEqualTo(Genome.len(refLen, 0, j));
            int lenJ = Genome.len(refLen, 0, j);
            for (int k = 0; k < lenJ; k++) {
                assertThat((short) Genome.gene(agent.pop.cur, 0, j, k))
                    .as("gene troll %d k %d", j, k)
                    .isEqualTo((short) Genome.gene(refBuf, 0, j, k));
            }
        }
```

- [ ] **Step 5 : Lancer le test**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentInitWarmTest`
Résultat attendu : OK.

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java \
        src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentInitWarmTest.java
git commit -m "ga: seed l'individu 0 via initWarm dans initPopulation"
```

---

### Task 8: Régression complète

**Files:**
- (aucune modification)

- [ ] **Step 1 : Lancer toute la suite de tests**

Commande : `mvn -q -DskipTests=false test`
Résultat attendu : BUILD SUCCESS, tous les tests verts (existants + 7 nouveaux + 1 d'intégration).

- [ ] **Step 2 : Lancer le smoke regression GA**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentTest`
Résultat attendu : OK, le test smoke continue à voir GA ≥ greedy.

- [ ] **Step 3 : Vérifier l'absence de régression sur le bench mi-game**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentBench`
Résultat attendu : le bench s'exécute sans erreur. Observer la fitness rapportée — elle ne doit pas régresser par rapport à `main` (référence : commit `a248332`). C'est un test mesuré, pas un assertion strict ; si la fitness baisse fortement (>5%), enquêter avant de poursuivre.

- [ ] **Step 4 : Vérifier l'état git**

Commande : `git log --oneline -10`
Résultat attendu : 7 nouveaux commits sur `ga-v-1`, working tree clean.

Commande : `git status`
Résultat attendu : `nothing to commit, working tree clean`.

---

## Self-Review (effectuée à l'écriture)

- **Couverture du spec** : sections 1 (architecture), 2 (algo round-robin), 3 (scratch buffers), 4 (tie-break), 5 (edge cases), 6 (invariants), 7 (coût), 8 (tests) toutes couvertes par les tâches 1–8. Les 7 tests listés dans le spec → 6 dans `GenomeOpsWarmStartTest` (sections 1–6 du spec) + 1 dans `GeneticAgentInitWarmTest` (câblage). Le 7e test du spec (invariants sur smoke scénario) est couvert par Task 6.
- **Placeholders** : aucun TBD/TODO.
- **Cohérence des types** : `initWarm(GameState, short[], byte[], int)` cohérent dans toutes les tâches. `warmCurX/Y` toujours `int[]`. `warmTreeTakenBuf` toujours `boolean[]`.
- **Tie-break déterministe** : implémenté via `if (d < bestDist)` strict — le premier rencontré gagne. Tests `closest first`, `chains`, `round-robin` et `uniqueness` sont conçus pour produire des distances distinctes, donc indépendants du tie-break implicite.
