# GA Warm-Start Temporel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persister entre tours le génome du meilleur individu du GA et le réinjecter au slot 0 de la population du tour suivant, après nettoyage des arbres devenus invalides. Le seed `initWarm` est repoussé au slot 1.

**Architecture:** Nouvelle méthode statique pure `GenomeOps.initFromPrevBest` qui copie+compacte un buffer "à plat" (taille `SLOTS_PER_GENOME`) vers un slot d'individu. `GeneticAgent` gagne trois champs package-private (`prevBestBuf`, `prevBestLen`, `hasPrevBest`) ; `decide` stashe le best en fin de méthode, `initPopulation` branche entre les deux schémas selon `hasPrevBest`.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. Code dans `src/main/java/com/bmrt/cgspring2026/ga/`, tests dans `src/test/java/com/bmrt/cgspring2026/ga/`.

---

## File Structure

- **Modify** `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
  Ajout de la méthode statique `initFromPrevBest`. Aucun changement aux méthodes existantes ni aux scratch buffers.

- **Modify** `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
  Ajout des champs `prevBestBuf`, `prevBestLen`, `hasPrevBest` (package-private). Promotion de `lastBestIdx` en package-private. Stash en fin de `decide`. Branchement dans `initPopulation`.

- **Create** `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`
  Tests JUnit 5 sur `initFromPrevBest` (compactage, cas vides, isolation entre slots, invariants).

- **Create** `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java`
  Tests d'intégration sur la persistance turn-à-turn (stash, branchement initPopulation, fallback turn 0).

---

## Pré-requis

- `mvn -q -DskipTests=false test` doit passer en l'état actuel avant de commencer.
- Branche : continuer sur `ga-v-1`.

---

### Task 1: Squelette `initFromPrevBest` + cas du segment vide

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`

- [ ] **Step 1 : Écrire le test du cas trivial**

Crée `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsPrevBestTest {

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

    private static short[] newPopBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newPopLen() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private static short[] newPrevBuf() {
        short[] buf = new short[Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] newPrevLen() {
        return new byte[GameState.MAX_TROLLS];
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

    @Test void emptyPrevLenProducesEmptySegment() {
        GameState s = stateWith(new int[][]{ {0, 0, 0} }, new int[][]{ {3, 3, 5} });
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        // prevLen[j] = 0 partout → segment destination doit être entièrement vide
        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(dstLen, 0, j)).isEqualTo(0);
        }
        // Et le segment buffer du slot 0 entièrement EMPTY_GENE
        int base = Genome.offset(0, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) {
            assertThat(dst[base + k]).isEqualTo(Genome.EMPTY_GENE);
        }
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue à la compilation**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest`
Résultat attendu : ÉCHEC à la compilation (`GenomeOps.initFromPrevBest` n'existe pas encore).

- [ ] **Step 3 : Ajouter la signature et le reset du segment**

Dans `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`, ajouter la méthode `initFromPrevBest` juste après `initWarm` :

```java
    public static void initFromPrevBest(GameState state,
                                        short[] prevBuf, byte[] prevLenBuf,
                                        short[] dstBuf,  byte[] dstLenBuf,
                                        int individuIdx) {
        // 1. Reset du segment de destination
        int base = Genome.offset(individuIdx, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) dstBuf[base + k] = Genome.EMPTY_GENE;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(dstLenBuf, individuIdx, j, 0);

        // 2. Compactage troll par troll — à compléter dans la tâche suivante
    }
```

- [ ] **Step 4 : Vérifier que le test passe**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest`
Résultat attendu : OK (1 test).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java
git commit -m "ga: initFromPrevBest squelette + reset segment destination"
```

---

### Task 2: Compactage — suppression du premier gène mort

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`

- [ ] **Step 1 : Ajouter le test**

Dans `GenomeOpsPrevBestTest.java`, ajouter une méthode utilitaire (juste après `newPrevLen`) :

```java
    private static void putPrev(short[] prevBuf, byte[] prevLenBuf, int trollIdx, int[][] cells) {
        int off = trollIdx * Genome.MAX_TARGETS_PER_TROLL;
        for (int k = 0; k < cells.length; k++) {
            prevBuf[off + k] = Genome.encode(cells[k][0], cells[k][1]);
        }
        prevLenBuf[trollIdx] = (byte) cells.length;
    }
```

Puis ajouter le test :

```java
    @Test void dropsFirstGeneWhenTargetChopped() {
        // Trolls (0,0). Trees : (1,0) MORT, (2,0) vivant. Prev-best troll 0 : [(1,0), (2,0)].
        // Attendu : (1,0) retiré, len=1, gène [(2,0)].
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 0}, {2, 0, 5} }   // (1,0) health=0 → mort
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {2, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        short g0 = (short) Genome.gene(dst, 0, 0, 0);
        assertThat(Genome.geneX(g0)).isEqualTo(2);
        assertThat(Genome.geneY(g0)).isEqualTo(0);
    }
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest#dropsFirstGeneWhenTargetChopped`
Résultat attendu : ÉCHEC — `len == 0` car la boucle de compactage n'est pas encore implémentée.

- [ ] **Step 3 : Implémenter le compactage**

Dans `GenomeOps.initFromPrevBest`, remplacer le commentaire `// 2. Compactage troll par troll — à compléter dans la tâche suivante` par :

```java
        // 2. Compactage troll par troll
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int prevLen = prevLenBuf[j] & 0xFF;
            int written = 0;
            int srcOff = j * Genome.MAX_TARGETS_PER_TROLL;
            int dstOff = Genome.offset(individuIdx, j);
            for (int k = 0; k < prevLen; k++) {
                short g = prevBuf[srcOff + k];
                if (g == Genome.EMPTY_GENE) continue;
                int gx = Genome.geneX(g);
                int gy = Genome.geneY(g);
                int t = state.treeIndexAt(gx, gy);
                if (t < 0) continue;
                if (state.treeHealth[t] <= 0) continue;
                dstBuf[dstOff + written] = g;
                written++;
            }
            Genome.setLen(dstLenBuf, individuIdx, j, written);
        }
```

- [ ] **Step 4 : Vérifier que tous les tests passent**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest`
Résultat attendu : 2 tests OK.

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java
git commit -m "ga: initFromPrevBest compacte les gènes invalides"
```

---

### Task 3: Compactage — arbre mort au milieu de la séquence

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`

- [ ] **Step 1 : Ajouter le test**

```java
    @Test void compactsDeadTreeInMiddle() {
        // Trolls (0,0). Prev-best troll 0 : [(1,0), (3,0)_MORT, (2,0)]. Attendu : [(1,0), (2,0)].
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 5}, {3, 0, 0}, {2, 0, 5} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {3, 0}, {2, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(2);
        short g0 = (short) Genome.gene(dst, 0, 0, 0);
        short g1 = (short) Genome.gene(dst, 0, 0, 1);
        assertThat(Genome.geneX(g0)).isEqualTo(1); assertThat(Genome.geneY(g0)).isEqualTo(0);
        assertThat(Genome.geneX(g1)).isEqualTo(2); assertThat(Genome.geneY(g1)).isEqualTo(0);
    }
```

- [ ] **Step 2 : Lancer le test**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest#compactsDeadTreeInMiddle`
Résultat attendu : OK.

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java
git commit -m "ga: test — initFromPrevBest compacte un arbre mort au milieu"
```

---

### Task 4: Compactage — tail intact + tous morts + invariants

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`

- [ ] **Step 1 : Ajouter trois tests**

```java
    @Test void keepsTailIntact() {
        // Aucun arbre mort → l'ordre est conservé tel quel.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 5}, {2, 0, 5}, {3, 0, 5} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {2, 0}, {3, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(3);
        int[][] expected = { {1, 0}, {2, 0}, {3, 0} };
        for (int k = 0; k < 3; k++) {
            short g = (short) Genome.gene(dst, 0, 0, k);
            assertThat(Genome.geneX(g)).isEqualTo(expected[k][0]);
            assertThat(Genome.geneY(g)).isEqualTo(expected[k][1]);
        }
    }

    @Test void emptyWhenAllTreesDead() {
        // Tous les arbres du prev-best sont morts → segment vide.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 0}, {2, 0, 0} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {2, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(0);
        int base = Genome.offset(0, 0);
        for (int k = 0; k < Genome.MAX_TARGETS_PER_TROLL; k++) {
            assertThat(dst[base + k]).isEqualTo(Genome.EMPTY_GENE);
        }
    }

    @Test void satisfiesInvariants() {
        // Scénario mixte : 2 trolls own + 1 opp, mélange vivants/morts dans le prev-best.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0}, {0, 5, 5}, {1, 3, 3} },
            new int[][]{ {1, 0, 5}, {2, 0, 0}, {3, 0, 5}, {4, 4, 5}, {5, 4, 0} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0}, {2, 0}, {3, 0} });
        putPrev(prev, prevLen, 1, new int[][]{ {5, 4}, {4, 4} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        assertThat(GenomeInvariants.check(dst, dstLen, 0)).isTrue();
        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(2); // (2,0) mort, (1,0) et (3,0) gardés
        assertThat(Genome.len(dstLen, 0, 1)).isEqualTo(1); // (5,4) mort, (4,4) gardé
    }
```

- [ ] **Step 2 : Lancer les tests**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest`
Résultat attendu : 5 tests OK.

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java
git commit -m "ga: test — initFromPrevBest préserve l'ordre, vide si tout mort, invariants OK"
```

---

### Task 5: Isolation — n'écrit que dans le slot demandé

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java`

- [ ] **Step 1 : Ajouter le test**

```java
    @Test void doesNotTouchOtherIndividuals() {
        // Pré-remplir le slot 1 avec un sentinel, écrire au slot 0, vérifier que le slot 1 reste intact.
        GameState s = stateWith(
            new int[][]{ {0, 0, 0} },
            new int[][]{ {1, 0, 5} }
        );
        short[] dst = newPopBuf();
        byte[]  dstLen = newPopLen();
        // Sentinel au slot 1, troll 0, k=0,1 (valeurs arbitraires non-EMPTY)
        int base1 = Genome.offset(1, 0);
        dst[base1] = Genome.encode(4, 4);
        dst[base1 + 1] = Genome.encode(5, 5);
        Genome.setLen(dstLen, 1, 0, 2);

        short[] prev = newPrevBuf();
        byte[]  prevLen = newPrevLen();
        putPrev(prev, prevLen, 0, new int[][]{ {1, 0} });

        GenomeOps.initFromPrevBest(s, prev, prevLen, dst, dstLen, 0);

        // Slot 0 écrit
        assertThat(Genome.len(dstLen, 0, 0)).isEqualTo(1);
        // Slot 1 intact
        assertThat(Genome.len(dstLen, 1, 0)).isEqualTo(2);
        assertThat(dst[base1]).isEqualTo(Genome.encode(4, 4));
        assertThat(dst[base1 + 1]).isEqualTo(Genome.encode(5, 5));
    }
```

- [ ] **Step 2 : Lancer le test**

Commande : `mvn -q -DskipTests=false test -Dtest=GenomeOpsPrevBestTest#doesNotTouchOtherIndividuals`
Résultat attendu : OK (le reset ne touche que `[base..base+SLOTS_PER_GENOME[` du slot demandé, et `Genome.setLen` n'écrit qu'au lenOffset du slot demandé).

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java
git commit -m "ga: test — initFromPrevBest n'écrit que dans le slot demandé"
```

---

### Task 6: Champs `GeneticAgent` + stash en fin de `decide`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java`

- [ ] **Step 1 : Écrire le test du stash**

Crée `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java` :

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentPrevBestTest {

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

    private static GameState simpleState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.treeCount = 2;
        s.treeX[0] = 5; s.treeY[0] = 5; s.treeHealth[0] = 5;
        s.treeX[1] = 1; s.treeY[1] = 0; s.treeHealth[1] = 5;
        return s;
    }

    @Test void stashesBestGenomeBetweenTurns() {
        GameState s = simpleState();
        GeneticAgent agent = new GeneticAgent();
        // Avant : pas de stash
        assertThat(agent.hasPrevBest).isFalse();

        // Deadline déjà passée → aucune génération ne tourne, lastBestIdx est
        // simplement l'argmax des fitness initiales.
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        // Après : stash actif et égal au génome de lastBestIdx
        assertThat(agent.hasPrevBest).isTrue();
        int bestIdx = agent.lastBestIdx;
        int srcBase = Genome.offset(bestIdx, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) {
            assertThat(agent.prevBestBuf[k])
                .as("prevBestBuf[%d] vs pop.cur slot %d", k, bestIdx)
                .isEqualTo(agent.pop.cur[srcBase + k]);
        }
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(agent.prevBestLen[j])
                .as("prevBestLen[%d] vs pop.curLen slot %d", j, bestIdx)
                .isEqualTo(agent.pop.curLen[Genome.lenOffset(bestIdx, j)]);
        }
    }
}
```

- [ ] **Step 2 : Lancer le test pour vérifier l'échec**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentPrevBestTest`
Résultat attendu : ÉCHEC à la compilation (`agent.hasPrevBest`, `agent.lastBestIdx`, `agent.prevBestBuf`, `agent.prevBestLen` inaccessibles ou inexistants).

- [ ] **Step 3 : Ajouter les champs et le stash dans `GeneticAgent`**

Dans `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java` :

a) **Promouvoir `lastBestIdx` en package-private.** Remplacer :

```java
    private int lastBestIdx;
```

par :

```java
    int lastBestIdx;
```

b) **Ajouter les nouveaux champs.** Juste après la ligne `final Population pop = new Population();`, ajouter :

```java
    final short[] prevBestBuf  = new short[Genome.SLOTS_PER_GENOME];
    final byte[]  prevBestLen  = new byte[GameState.MAX_TROLLS];
    boolean       hasPrevBest  = false;
```

c) **Ajouter le stash en fin de `decide`.** Dans la méthode `decide`, juste après les lignes :

```java
        lastBestIdx = argmax(pop.curFit);
        lastBestFitness = pop.curFit[lastBestIdx];
```

ajouter :

```java
        // Stash du best pour réinjection au tour suivant
        System.arraycopy(pop.cur, Genome.offset(lastBestIdx, 0),
                         prevBestBuf, 0, Genome.SLOTS_PER_GENOME);
        System.arraycopy(pop.curLen, Genome.lenOffset(lastBestIdx, 0),
                         prevBestLen, 0, GameState.MAX_TROLLS);
        hasPrevBest = true;
```

- [ ] **Step 4 : Vérifier que le test passe**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentPrevBestTest`
Résultat attendu : OK (1 test).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java \
        src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java
git commit -m "ga: stash du best en fin de decide pour réinjection au tour suivant"
```

---

### Task 7: Branchement `initPopulation` (turn 0 vs N+1)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java`

- [ ] **Step 1 : Ajouter les deux tests d'intégration**

Dans `GeneticAgentPrevBestTest.java`, ajouter :

```java
    @Test void firstTurnSlotZeroEqualsInitWarm() {
        // Sur un agent neuf, le tour 1 doit utiliser initWarm au slot 0 (fallback hasPrevBest=false).
        GameState s = simpleState();

        // Référence : ce que initWarm produit
        short[] refBuf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(refBuf, Genome.EMPTY_GENE);
        byte[]  refLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GenomeOps.initWarm(s, refBuf, refLen, 0);

        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1; // pas d'évolution
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, deadline, out);

        // Slot 0 == initWarm de référence
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
    }

    @Test void secondTurnSlotZeroSeededFromPrevBest() {
        // Deux decide() consécutifs sur le même state (tous arbres vivants).
        // Au tour 2, slot 0 doit être égal au compactage du prev-best stocké à la fin du tour 1.
        // Comme tous les arbres sont vivants, compactage = identité ⇒ slot 0 == prev-best.
        GameState s = simpleState();
        GeneticAgent agent = new GeneticAgent();
        long deadline = System.nanoTime() - 1;
        int[] out = new int[GameState.MAX_TROLLS + 1];

        // Tour 1 — peuple prevBestBuf/prevBestLen
        agent.decide(s, deadline, out);
        // Snapshot du stash avant tour 2
        short[] stashBuf = java.util.Arrays.copyOf(agent.prevBestBuf, Genome.SLOTS_PER_GENOME);
        byte[]  stashLen = java.util.Arrays.copyOf(agent.prevBestLen, GameState.MAX_TROLLS);

        // Tour 2 — initPopulation doit injecter prev-best (compacté = identique ici) au slot 0
        agent.decide(s, deadline, out);

        // Slot 0 == stash (puisque rien n'est mort entre les deux decide)
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(agent.pop.curLen, 0, j))
                .as("len troll %d", j)
                .isEqualTo(stashLen[j] & 0xFF);
            int lenJ = stashLen[j] & 0xFF;
            for (int k = 0; k < lenJ; k++) {
                assertThat((short) Genome.gene(agent.pop.cur, 0, j, k))
                    .as("gene troll %d k %d", j, k)
                    .isEqualTo(stashBuf[j * Genome.MAX_TARGETS_PER_TROLL + k]);
            }
        }
    }
```

- [ ] **Step 2 : Lancer les tests pour vérifier les échecs**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentPrevBestTest`
Résultat attendu : `firstTurnSlotZeroEqualsInitWarm` passe (initPopulation actuel met déjà initWarm au slot 0). `secondTurnSlotZeroSeededFromPrevBest` ÉCHOUE — au tour 2, initPopulation actuel re-fait initWarm au slot 0 (pas de réinjection du stash).

- [ ] **Step 3 : Modifier `initPopulation`**

Dans `GeneticAgent.java`, remplacer la méthode `initPopulation` :

```java
    private void initPopulation(GameState state) {
        GenomeOps.initWarm(state, pop.cur, pop.curLen, 0);
        for (int i = 1; i < Genome.POP_SIZE; i++) {
            GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
        }
    }
```

par :

```java
    private void initPopulation(GameState state) {
        if (hasPrevBest) {
            GenomeOps.initFromPrevBest(state, prevBestBuf, prevBestLen,
                                       pop.cur, pop.curLen, 0);
            GenomeOps.initWarm(state, pop.cur, pop.curLen, 1);
            for (int i = 2; i < Genome.POP_SIZE; i++) {
                GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
            }
        } else {
            GenomeOps.initWarm(state, pop.cur, pop.curLen, 0);
            for (int i = 1; i < Genome.POP_SIZE; i++) {
                GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
            }
        }
    }
```

- [ ] **Step 4 : Vérifier que les deux tests passent**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentPrevBestTest`
Résultat attendu : 3 tests OK.

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java \
        src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java
git commit -m "ga: initPopulation réinjecte le prev-best au slot 0, initWarm au slot 1"
```

---

### Task 8: Régression complète

**Files:**
- (aucune modification)

- [ ] **Step 1 : Lancer toute la suite de tests**

Commande : `mvn -q -DskipTests=false test`
Résultat attendu : BUILD SUCCESS, tous les tests verts (existants + 7 nouveaux dans `GenomeOpsPrevBestTest` + 3 dans `GeneticAgentPrevBestTest`).

- [ ] **Step 2 : Lancer spécifiquement le smoke regression GA**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentTest`
Résultat attendu : OK, le test smoke continue à voir GA ≥ greedy.

- [ ] **Step 3 : Vérifier l'absence de régression sur le bench mi-game**

Commande : `mvn -q -DskipTests=false test -Dtest=GeneticAgentBench`
Résultat attendu : le bench s'exécute sans erreur. Observer la fitness rapportée et le compteur `gen/s` — ne doivent pas régresser par rapport à la référence (commit `e27386b`, dernier commit avant cette feature). C'est un test mesuré, pas un assertion strict ; si la fitness baisse fortement (>5%) ou si `gen/s` chute, enquêter avant de poursuivre.

- [ ] **Step 4 : Vérifier l'état git**

Commande : `git log --oneline -10`
Résultat attendu : 7 nouveaux commits sur `ga-v-1` (Tasks 1–7), working tree clean.

Commande : `git status`
Résultat attendu : `nothing to commit, working tree clean` (modulo `Player.java` qui était modifié avant la feature — laisser tel quel).

---

## Self-Review (effectuée à l'écriture)

- **Couverture du spec :**
  - Architecture / fichiers touchés → Tasks 1–7 (GenomeOps, GeneticAgent, deux tests).
  - Signature `initFromPrevBest` → Task 1.
  - Champs `prevBestBuf/Len`, `hasPrevBest` package-private → Task 6.
  - Algorithme reset + compactage → Tasks 1–2.
  - Stash en fin de `decide` → Task 6.
  - Branchement `initPopulation` (turn 0 fallback) → Task 7.
  - Tests 1–7 du spec (GenomeOps) : `dropsFirstGeneWhenTargetChopped` (T2), `compactsDeadTreeInMiddle` (T3), `keepsTailIntact` + `emptyWhenAllTreesDead` + `satisfiesInvariants` (T4), `emptyPrevLenProducesEmptySegment` (T1), `doesNotTouchOtherIndividuals` (T5).
  - Tests 8–10 du spec (GeneticAgent) : `stashesBestGenomeBetweenTurns` (T6), `secondTurnSlotZeroSeededFromPrevBest` + `firstTurnSlotZeroEqualsInitWarm` (T7).
  - Régression complète + bench → Task 8.

- **Placeholders :** aucun TBD/TODO.

- **Cohérence des types et signatures :**
  - `initFromPrevBest(GameState, short[], byte[], short[], byte[], int)` cohérente dans toutes les tâches.
  - `prevBestBuf` toujours `short[Genome.SLOTS_PER_GENOME]`, `prevBestLen` toujours `byte[GameState.MAX_TROLLS]`, `hasPrevBest` toujours `boolean`. Visibilité `package-private` (sans modifier `final` sur les arrays).
  - `lastBestIdx` promu en package-private (suppression du mot-clé `private`) — utilisé par Task 6 dans l'assertion `agent.lastBestIdx`.
  - L'utilitaire `putPrev(prevBuf, prevLenBuf, trollIdx, cells)` est défini en Task 2 et utilisé tel quel dans Tasks 3, 4, 5.

- **Sémantique du compactage :** `treeIndexAt < 0` couvre déjà les arbres morts ; le double-check `treeHealth[t] <= 0` est défensif et explicité comme tel dans le spec. Implémentation Task 2 respecte cet ordre.

- **Pas de régression sur le contrat existant :** `GeneticAgentInitWarmTest.individual0MatchesWarmStartOutput` reste valide — un nouveau `GeneticAgent` a `hasPrevBest = false`, donc slot 0 = `initWarm`, exactement ce que ce test attend.
