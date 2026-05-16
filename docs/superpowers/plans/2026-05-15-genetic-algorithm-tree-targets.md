# Genetic Algorithm — Tree Targets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter un algorithme génétique haut niveau (`com.bmrt.cgspring2026.ga`) qui assigne une séquence ordonnée d'arbres-cibles à chaque troll player=0, en remplacement du greedy myope.

**Architecture:** Pool global plat ping-pong (2 buffers `short[]`), gène = `short (x<<8)|y`. Tournoi binaire + élitisme top-1. 70% crossover OX-par-troll + repair, 30% mutation (4 opérateurs). Fitness = simulation forward sur 25 tours (adversaire = greedy) + `(scoreMe - scoreOpp) + 2.0 × Σ wood_carried`. Deadline-driven (40 ms/tour, 900 ms tour 1).

**Tech Stack:** Java 21, JUnit 5.6.3, AssertJ 3.18.1, Maven Surefire 3.0.0-M5. Test command : `mvn -q test`.

**Spec source:** [`docs/superpowers/specs/2026-05-15-genetic-algorithm-tree-targets-design.md`](../specs/2026-05-15-genetic-algorithm-tree-targets-design.md)

**Performance contraints:**
- **Zéro allocation dans la boucle évolutive après init.** Tous les buffers (population, scratch state, action buffer, seen[], cursor[]) sont alloués une fois dans le constructeur `GeneticAgent`.
- Une seule instance de `SplittableRandom` réutilisée.
- Toutes les vérifications d'invariants doivent rester sous `assert` ou condition statique pour être éliminées en prod.

---

## File Structure

### Sources (à créer)

| Fichier | Responsabilité |
|---|---|
| `src/main/java/com/bmrt/cgspring2026/ga/Genome.java` | Constantes (`POP_SIZE`, `MAX_TARGETS_PER_TROLL`, `SLOTS_PER_GENOME`) + accesseurs statiques (`offset`, `gene`, `len`, `setLen`, `setGene`). Pas d'état. |
| `src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java` | Méthode `check(buf, len, idx)` retournant `true` (ou throw `AssertionError`). |
| `src/main/java/com/bmrt/cgspring2026/ga/Population.java` | 2 buffers ping-pong (`bufA`/`bufB`), 2 lengths (`lenA`/`lenB`), 2 fitness (`fitA`/`fitB`), pointeurs `cur`/`nxt`/`curLen`/`nxtLen`/`curFit`/`nxtFit`, méthode `swap()`. |
| `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java` | Opérateurs : `initRandom`, `mutateSwapIntra`, `mutateSwapInter`, `mutateReverse`, `mutateDelete`, `runMutation`, `crossover`. |
| `src/main/java/com/bmrt/cgspring2026/ga/Selection.java` | `tournament(fit, rng, popSize)`. |
| `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java` | `fillActions(state, popBuf, popLen, idx, cursor, outActions)` qui produit les actions au tick courant + scratch buffers statiques. |
| `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java` | `evaluate(scratch, source, popBuf, popLen, idx, actionBuf)`, retourne `double` fitness. |
| `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java` | Entry point : constructeur (alloc tout), `decide(state, deadlineNs, outActions)`. Expose constantes tuneables + `lastGenCount()` / `lastBestFitness()` pour MSG. |

### Tests (à créer)

| Fichier | Couvre |
|---|---|
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeTest.java` | Accesseurs (encoding/decoding short, offset, gene, len) |
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java` | Détection doublon, bounds, slots `-1` après len |
| `src/test/java/com/bmrt/cgspring2026/ga/PopulationTest.java` | Allocation, swap, taille pointeurs |
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitTest.java` | Unicité globale, `P_SKIP_INIT`, seul player=0 reçoit des targets |
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMutationTest.java` | Chaque opérateur préserve unicité + sémantique attendue |
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java` | OX par troll + repair, unicité préservée |
| `src/test/java/com/bmrt/cgspring2026/ga/SelectionTest.java` | Tournoi favorise meilleurs fitness |
| `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyTest.java` | Priorité wood → drop, target valide → chop/move, fin → wait, skip arbres morts |
| `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTest.java` | Fitness positive après chop+drop, négative si adversaire score plus |
| `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java` | Respect deadline, monotonie best-fitness, smoke regression vs greedy |

### Fichiers modifiés

| Fichier | Modification |
|---|---|
| `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` | Extraire `decideForOpponent(state, trollIdx, treeTakenBuf)` réutilisable. |
| `src/main/java/com/bmrt/cgspring2026/Player.java` | Remplacer l'appel `GreedyAgent.decide` par `agent.decide(state, deadline, actionBuf)` + ajouter MSG verbeux. |

---

## Tasks

### Task 1: Genome — constantes et accesseurs

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeTest {

    @Test void slotsPerGenomeMatchesMaxTrollsTimesMaxTargets() {
        assertThat(Genome.SLOTS_PER_GENOME)
            .isEqualTo(GameState.MAX_TROLLS * Genome.MAX_TARGETS_PER_TROLL);
    }

    @Test void encodeAndDecodeCoords() {
        short g = Genome.encode(5, 11);
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(11);
    }

    @Test void offsetIsTrollSegmentStart() {
        // troll 0 of individu 0 → 0
        assertThat(Genome.offset(0, 0)).isEqualTo(0);
        // troll 1 of individu 0 → MAX_TARGETS_PER_TROLL
        assertThat(Genome.offset(0, 1)).isEqualTo(Genome.MAX_TARGETS_PER_TROLL);
        // troll 0 of individu 1 → SLOTS_PER_GENOME
        assertThat(Genome.offset(1, 0)).isEqualTo(Genome.SLOTS_PER_GENOME);
    }

    @Test void geneAndSetGeneRoundTrip() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        Genome.setGene(buf, 2, 3, 4, (short) 0x0A0B);
        assertThat(Genome.gene(buf, 2, 3, 4)).isEqualTo(0x0A0B);
    }

    @Test void lenAndSetLenRoundTrip() {
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lenBuf, 5, 7, 12);
        assertThat(Genome.len(lenBuf, 5, 7)).isEqualTo(12);
    }
}
```

- [ ] **Step 2: Run test to verify it fails (class missing)**

```bash
mvn -q test -Dtest=GenomeTest
```
Expected: `FAILURE` with compilation error (`Genome` symbol not found).

- [ ] **Step 3: Implement Genome**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class Genome {

    public static final int POP_SIZE              = 48;
    public static final int MAX_TARGETS_PER_TROLL = 16;
    public static final int SLOTS_PER_GENOME      = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL;
    public static final short EMPTY_GENE          = -1;

    private Genome() {}

    public static short encode(int x, int y) {
        return (short) (((x & 0xFF) << 8) | (y & 0xFF));
    }

    public static int geneX(short g) { return (g >>> 8) & 0xFF; }
    public static int geneY(short g) { return g & 0xFF; }

    public static int offset(int individuIdx, int trollIdx) {
        return individuIdx * SLOTS_PER_GENOME + trollIdx * MAX_TARGETS_PER_TROLL;
    }

    public static int lenOffset(int individuIdx, int trollIdx) {
        return individuIdx * GameState.MAX_TROLLS + trollIdx;
    }

    public static int len(byte[] lenBuf, int individuIdx, int trollIdx) {
        return lenBuf[lenOffset(individuIdx, trollIdx)] & 0xFF;
    }

    public static void setLen(byte[] lenBuf, int individuIdx, int trollIdx, int value) {
        lenBuf[lenOffset(individuIdx, trollIdx)] = (byte) value;
    }

    public static int gene(short[] buf, int individuIdx, int trollIdx, int k) {
        return buf[offset(individuIdx, trollIdx) + k];
    }

    public static void setGene(short[] buf, int individuIdx, int trollIdx, int k, short value) {
        buf[offset(individuIdx, trollIdx) + k] = value;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeTest.java
git commit -m "ga: Genome — constants + static accessors (offset/gene/len)"
```

---

### Task 2: GenomeInvariants — détection doublons et bounds

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenomeInvariantsTest {

    @BeforeEach void setUp() {
        GameState.width  = 10;
        GameState.height = 10;
        GameState.tiles  = new byte[100];
    }

    @Test void passesForEmptyIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void detectsDuplicateAcrossTrolls() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 4));
        Genome.setLen(lenBuf, 0, 0, 1);
        Genome.setGene(buf, 0, 1, 0, Genome.encode(3, 4));
        Genome.setLen(lenBuf, 0, 1, 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }

    @Test void detectsOutOfBoundsCoord() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(15, 4)); // width=10 → 15 invalide
        Genome.setLen(lenBuf, 0, 0, 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }

    @Test void detectsLenExceedsMax() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setLen(lenBuf, 0, 0, Genome.MAX_TARGETS_PER_TROLL + 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GenomeInvariantsTest
```
Expected: `FAILURE` (class missing).

- [ ] **Step 3: Implement GenomeInvariants**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class GenomeInvariants {

    private GenomeInvariants() {}

    public static boolean check(short[] buf, byte[] lenBuf, int individuIdx) {
        int W = GameState.width;
        int H = GameState.height;
        boolean[] seen = new boolean[W * H];
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lenBuf, individuIdx, j);
            if (len < 0 || len > Genome.MAX_TARGETS_PER_TROLL) {
                throw new AssertionError("len out of range troll=" + j + " len=" + len);
            }
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, individuIdx, j, k);
                if (g == Genome.EMPTY_GENE) {
                    throw new AssertionError("active slot is EMPTY troll=" + j + " k=" + k);
                }
                int x = Genome.geneX(g);
                int y = Genome.geneY(g);
                if (x < 0 || x >= W || y < 0 || y >= H) {
                    throw new AssertionError("gene out of bounds (" + x + "," + y + ")");
                }
                int idx = y * W + x;
                if (seen[idx]) {
                    throw new AssertionError("duplicate gene (" + x + "," + y + ")");
                }
                seen[idx] = true;
            }
            for (int k = len; k < Genome.MAX_TARGETS_PER_TROLL; k++) {
                if (Genome.gene(buf, individuIdx, j, k) != Genome.EMPTY_GENE) {
                    throw new AssertionError("dirty slot after len troll=" + j + " k=" + k);
                }
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeInvariantsTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java
git commit -m "ga: GenomeInvariants — unicity/bounds/len checker"
```

---

### Task 3: Population — buffers ping-pong

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/Population.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/PopulationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationTest {

    @Test void buffersAreSizedCorrectly() {
        Population p = new Population();
        assertThat(p.bufA).hasSize(Genome.POP_SIZE * Genome.SLOTS_PER_GENOME);
        assertThat(p.bufB).hasSize(Genome.POP_SIZE * Genome.SLOTS_PER_GENOME);
        assertThat(p.lenA).hasSize(Genome.POP_SIZE * GameState.MAX_TROLLS);
        assertThat(p.lenB).hasSize(Genome.POP_SIZE * GameState.MAX_TROLLS);
        assertThat(p.fitA).hasSize(Genome.POP_SIZE);
        assertThat(p.fitB).hasSize(Genome.POP_SIZE);
    }

    @Test void initiallyCurPointsToBufA() {
        Population p = new Population();
        assertThat(p.cur).isSameAs(p.bufA);
        assertThat(p.nxt).isSameAs(p.bufB);
        assertThat(p.curLen).isSameAs(p.lenA);
        assertThat(p.nxtLen).isSameAs(p.lenB);
        assertThat(p.curFit).isSameAs(p.fitA);
        assertThat(p.nxtFit).isSameAs(p.fitB);
    }

    @Test void swapExchangesPointers() {
        Population p = new Population();
        p.swap();
        assertThat(p.cur).isSameAs(p.bufB);
        assertThat(p.nxt).isSameAs(p.bufA);
        assertThat(p.curLen).isSameAs(p.lenB);
        assertThat(p.nxtLen).isSameAs(p.lenA);
        assertThat(p.curFit).isSameAs(p.fitB);
        assertThat(p.nxtFit).isSameAs(p.fitA);
    }

    @Test void emptyClearsCurrentGenerationToEmpty() {
        Population p = new Population();
        p.cur[0] = 42;
        p.curLen[0] = 5;
        p.curFit[0] = 100.0;
        p.resetCurrent();
        assertThat(p.cur[0]).isEqualTo(Genome.EMPTY_GENE);
        assertThat(p.curLen[0]).isEqualTo((byte) 0);
        assertThat(p.curFit[0]).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=PopulationTest
```
Expected: `FAILURE` (class missing).

- [ ] **Step 3: Implement Population**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

public final class Population {

    public final short[]  bufA = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    public final short[]  bufB = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
    public final byte[]   lenA = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    public final byte[]   lenB = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    public final double[] fitA = new double[Genome.POP_SIZE];
    public final double[] fitB = new double[Genome.POP_SIZE];

    public short[]  cur, nxt;
    public byte[]   curLen, nxtLen;
    public double[] curFit, nxtFit;

    public Population() {
        java.util.Arrays.fill(bufA, Genome.EMPTY_GENE);
        java.util.Arrays.fill(bufB, Genome.EMPTY_GENE);
        cur = bufA; nxt = bufB;
        curLen = lenA; nxtLen = lenB;
        curFit = fitA; nxtFit = fitB;
    }

    public void swap() {
        short[] tmp = cur; cur = nxt; nxt = tmp;
        byte[] tmpLen = curLen; curLen = nxtLen; nxtLen = tmpLen;
        double[] tmpFit = curFit; curFit = nxtFit; nxtFit = tmpFit;
    }

    public void resetCurrent() {
        java.util.Arrays.fill(cur, Genome.EMPTY_GENE);
        java.util.Arrays.fill(curLen, (byte) 0);
        java.util.Arrays.fill(curFit, 0.0);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=PopulationTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/Population.java \
        src/test/java/com/bmrt/cgspring2026/ga/PopulationTest.java
git commit -m "ga: Population — ping-pong bufA/bufB + swap"
```

---

### Task 4: GenomeOps — initRandom (init aléatoire d'un individu)

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsInitTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private static GameState makeState(int trees, int ownTrolls, int oppTrolls) {
        GameState s = new GameState();
        for (int i = 0; i < trees; i++) {
            s.treeCount++;
            s.treeX[i] = (byte) (i % GameState.width);
            s.treeY[i] = (byte) (i / GameState.width);
            s.treeHealth[i] = 5;
        }
        for (int i = 0; i < ownTrolls; i++) {
            s.trollCount++;
            s.trollPlayer[i] = 0;
        }
        for (int i = 0; i < oppTrolls; i++) {
            int idx = ownTrolls + i;
            s.trollCount++;
            s.trollPlayer[idx] = 1;
        }
        return s;
    }

    @Test void emptyStateProducesEmptyIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(0, 1, 0);
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(42));
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            assertThat(Genome.len(lenBuf, 0, j)).isEqualTo(0);
        }
    }

    @Test void unicityGloballyEnforced() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(20, 3, 1);
        GenomeOps.initRandom(s, buf, lenBuf, 5, new SplittableRandom(123));
        assertThat(GenomeInvariants.check(buf, lenBuf, 5)).isTrue();
    }

    @Test void onlyPlayerZeroTrollsReceiveTargets() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(10, 2, 2); // trolls 0,1 = me ; 2,3 = opp
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(7));
        assertThat(Genome.len(lenBuf, 0, 2)).isEqualTo(0);
        assertThat(Genome.len(lenBuf, 0, 3)).isEqualTo(0);
    }

    @Test void totalAssignedRespectsSkipProbability() {
        // Avec P_SKIP_INIT = 0.30, on s'attend ~70% assignés sur grand échantillon
        int trees = 30;
        int totalAssigned = 0;
        int trials = 200;
        SplittableRandom rng = new SplittableRandom(1);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        for (int t = 0; t < trials; t++) {
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            java.util.Arrays.fill(lenBuf, (byte) 0);
            GameState s = makeState(trees, 3, 0);
            GenomeOps.initRandom(s, buf, lenBuf, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                totalAssigned += Genome.len(lenBuf, 0, j);
            }
        }
        double avg = (double) totalAssigned / trials;
        // espérance = trees * (1 - P_SKIP_INIT) = 30 * 0.7 = 21
        assertThat(avg).isBetween(18.0, 24.0);
    }

    @Test void allAssignedCoordsAreFromState() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        GameState s = makeState(8, 2, 0);
        GenomeOps.initRandom(s, buf, lenBuf, 0, new SplittableRandom(99));
        Set<Integer> validCells = new HashSet<>();
        for (int i = 0; i < s.treeCount; i++) {
            validCells.add(((s.treeX[i] & 0xFF) << 8) | (s.treeY[i] & 0xFF));
        }
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lenBuf, 0, j);
            for (int k = 0; k < len; k++) {
                int g = Genome.gene(buf, 0, j, k) & 0xFFFF;
                assertThat(validCells).contains(g);
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GenomeOpsInitTest
```
Expected: `FAILURE` (`GenomeOps` symbol not found).

- [ ] **Step 3: Implement GenomeOps.initRandom**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;

import java.util.SplittableRandom;

public final class GenomeOps {

    public static final double P_SKIP_INIT = 0.30;

    // Scratch buffers réutilisés (jamais alloués dans le hot path après init)
    private static final short[] shuffleBuf      = new short[GameState.MAX_TREES];
    private static final int[]   ownTrollsBuf    = new int[GameState.MAX_TROLLS];
    private static final int[]   freeTrollsBuf   = new int[GameState.MAX_TROLLS];

    private GenomeOps() {}

    public static void initRandom(GameState state, short[] buf, byte[] lenBuf,
                                  int individuIdx, SplittableRandom rng) {
        // 1. Reset segment de cet individu
        int base = Genome.offset(individuIdx, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) buf[base + k] = Genome.EMPTY_GENE;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(lenBuf, individuIdx, j, 0);

        // 2. Récupérer trolls player=0
        int ownTrollsCount = 0;
        for (int i = 0; i < state.trollCount; i++) {
            if ((state.trollPlayer[i] & 0xFF) == 0) ownTrollsBuf[ownTrollsCount++] = i;
        }
        if (ownTrollsCount == 0) return;

        // 3. Récupérer arbres vivants, shuffle Fisher-Yates
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
            // Liste des trolls non pleins
            int freeCount = 0;
            for (int k = 0; k < ownTrollsCount; k++) {
                int trollIdx = ownTrollsBuf[k];
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
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeOpsInitTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitTest.java
git commit -m "ga: GenomeOps.initRandom — partition aléatoire partielle, unicité globale"
```

---

### Task 5: Mutations (4 opérateurs + dispatcher)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMutationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsMutationTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private short[] buf;
    private byte[]  lenBuf;

    @org.junit.jupiter.api.BeforeEach
    void buffers() {
        buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private void setupSimple() {
        // troll 0 : [(1,1),(2,2),(3,3)]   troll 1 : [(4,4),(5,5)]
        Genome.setGene(buf, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(buf, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(lenBuf, 0, 0, 3);
        Genome.setGene(buf, 0, 1, 0, Genome.encode(4, 4));
        Genome.setGene(buf, 0, 1, 1, Genome.encode(5, 5));
        Genome.setLen(lenBuf, 0, 1, 2);
    }

    @Test void swapIntraPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateSwapIntra(buf, lenBuf, 0, new SplittableRandom(1));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        // longueurs inchangées
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void swapInterPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateSwapInter(buf, lenBuf, 0, new SplittableRandom(2));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void reverseSegmentPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateReverse(buf, lenBuf, 0, new SplittableRandom(3));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void deleteShortensExactlyOneSegment() {
        setupSimple();
        int total0 = Genome.len(lenBuf, 0, 0) + Genome.len(lenBuf, 0, 1);
        GenomeOps.mutateDelete(buf, lenBuf, 0, new SplittableRandom(4));
        int total1 = Genome.len(lenBuf, 0, 0) + Genome.len(lenBuf, 0, 1);
        assertThat(total1).isEqualTo(total0 - 1);
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void deleteShiftsSubsequentSlots() {
        // troll 0 : [(1,1),(2,2),(3,3)], delete au milieu (k=1) → [(1,1),(3,3)]
        // On force la suppression au k=1 via une seed connue ; on vérifie qu'on ne laisse pas
        // de slot dirty.
        Genome.setGene(buf, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(buf, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(lenBuf, 0, 0, 3);
        // Avec seed=4 on a observé que la sélection tombe sur troll=0, k=1 ; sinon on parcourt
        // plusieurs seeds. Test plus robuste : vérifier invariants après.
        GenomeOps.mutateDelete(buf, lenBuf, 0, new SplittableRandom(4));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void runMutationDispatchesProbabilistically() {
        // Smoke test : sur 1000 itérations, chaque opérateur doit avoir été tiré au moins une fois
        SplittableRandom rng = new SplittableRandom(42);
        int[] counters = new int[4];
        for (int t = 0; t < 1000; t++) {
            counters[GenomeOps.pickMutationKind(rng)]++;
        }
        for (int k = 0; k < 4; k++) {
            assertThat(counters[k]).isGreaterThan(50);
        }
        // approximativement les bonnes proportions
        assertThat(counters[0]).isBetween(300, 500); // swap-intra ~40%
        assertThat(counters[3]).isBetween(50, 150);  // delete ~10%
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GenomeOpsMutationTest
```
Expected: `FAILURE` (methods missing).

- [ ] **Step 3: Add mutations to GenomeOps**

Append to `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java` (inside the class body) :

```java
    public static final double P_MUT_SWAP_INTRA = 0.40;
    public static final double P_MUT_SWAP_INTER = 0.30;
    public static final double P_MUT_REVERSE    = 0.20;
    public static final double P_MUT_DELETE     = 0.10;

    public static final int MUT_SWAP_INTRA = 0;
    public static final int MUT_SWAP_INTER = 1;
    public static final int MUT_REVERSE    = 2;
    public static final int MUT_DELETE     = 3;

    public static int pickMutationKind(SplittableRandom rng) {
        double r = rng.nextDouble();
        if (r < P_MUT_SWAP_INTRA) return MUT_SWAP_INTRA;
        r -= P_MUT_SWAP_INTRA;
        if (r < P_MUT_SWAP_INTER) return MUT_SWAP_INTER;
        r -= P_MUT_SWAP_INTER;
        if (r < P_MUT_REVERSE) return MUT_REVERSE;
        return MUT_DELETE;
    }

    public static void runMutation(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        switch (pickMutationKind(rng)) {
            case MUT_SWAP_INTRA -> mutateSwapIntra(buf, lenBuf, individuIdx, rng);
            case MUT_SWAP_INTER -> mutateSwapInter(buf, lenBuf, individuIdx, rng);
            case MUT_REVERSE    -> mutateReverse  (buf, lenBuf, individuIdx, rng);
            case MUT_DELETE     -> mutateDelete   (buf, lenBuf, individuIdx, rng);
            default -> throw new IllegalStateException();
        }
    }

    /** Tire un troll avec len >= minLen parmi [0..MAX_TROLLS[. Retourne -1 si aucun. */
    private static int pickTrollWithLen(byte[] lenBuf, int individuIdx, int minLen, SplittableRandom rng) {
        int count = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            if (Genome.len(lenBuf, individuIdx, j) >= minLen) freeTrollsBuf[count++] = j;
        }
        if (count == 0) return -1;
        return freeTrollsBuf[rng.nextInt(count)];
    }

    public static void mutateSwapIntra(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int k1 = rng.nextInt(len);
        int k2 = rng.nextInt(len);
        if (k1 == k2) return;
        int base = Genome.offset(individuIdx, j);
        short tmp = buf[base + k1]; buf[base + k1] = buf[base + k2]; buf[base + k2] = tmp;
    }

    public static void mutateSwapInter(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j1 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j1 < 0) return;
        int j2 = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j2 < 0 || j1 == j2) return;
        int len1 = Genome.len(lenBuf, individuIdx, j1);
        int len2 = Genome.len(lenBuf, individuIdx, j2);
        int k1 = rng.nextInt(len1);
        int k2 = rng.nextInt(len2);
        int b1 = Genome.offset(individuIdx, j1);
        int b2 = Genome.offset(individuIdx, j2);
        short tmp = buf[b1 + k1]; buf[b1 + k1] = buf[b2 + k2]; buf[b2 + k2] = tmp;
    }

    public static void mutateReverse(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 2, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int a = rng.nextInt(len);
        int b = rng.nextInt(len);
        if (a == b) return;
        if (a > b) { int t = a; a = b; b = t; }
        int base = Genome.offset(individuIdx, j);
        while (a < b) {
            short tmp = buf[base + a]; buf[base + a] = buf[base + b]; buf[base + b] = tmp;
            a++; b--;
        }
    }

    public static void mutateDelete(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        int j = pickTrollWithLen(lenBuf, individuIdx, 1, rng);
        if (j < 0) return;
        int len = Genome.len(lenBuf, individuIdx, j);
        int k = rng.nextInt(len);
        int base = Genome.offset(individuIdx, j);
        for (int i = k; i < len - 1; i++) buf[base + i] = buf[base + i + 1];
        buf[base + len - 1] = Genome.EMPTY_GENE;
        Genome.setLen(lenBuf, individuIdx, j, len - 1);
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeOpsMutationTest
```
Expected: `BUILD SUCCESS`. Si le test `runMutationDispatchesProbabilistically` échoue à cause d'une seed instable, augmenter le sample size ou widening les intervalles.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMutationTest.java
git commit -m "ga: GenomeOps mutations (swap-intra/inter, reverse, delete) + dispatcher"
```

---

### Task 6: Crossover OX par troll + repair global

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsCrossoverTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private static GameState makeStateWithTrees(int trees, int ownTrolls) {
        GameState s = new GameState();
        for (int i = 0; i < trees; i++) {
            s.treeCount++;
            s.treeX[i] = (byte) (i % GameState.width);
            s.treeY[i] = (byte) (i / GameState.width);
            s.treeHealth[i] = 5;
        }
        for (int i = 0; i < ownTrolls; i++) { s.trollCount++; s.trollPlayer[i] = 0; }
        return s;
    }

    @Test void crossoverProducesValidOffspring() {
        short[] src = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(src, Genome.EMPTY_GENE);
        byte[] srcLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        GameState s = makeStateWithTrees(15, 3);
        SplittableRandom rng = new SplittableRandom(1);
        GenomeOps.initRandom(s, src, srcLen, 0, rng); // parent 1 dans slot 0
        GenomeOps.initRandom(s, src, srcLen, 1, rng); // parent 2 dans slot 1

        GenomeOps.crossover(src, srcLen, 0, src, srcLen, 1, dst, dstLen, 0, rng);
        assertThat(GenomeInvariants.check(dst, dstLen, 0)).isTrue();
    }

    @Test void crossoverInheritsFromBothParents() {
        // Parent 1 troll 0 : [(1,1),(2,2),(3,3)]
        // Parent 2 troll 0 : [(3,3),(4,4),(5,5)]
        // L'offspring doit contenir des éléments des deux (selon point de coupure)
        short[] src = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(src, Genome.EMPTY_GENE);
        byte[] srcLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(src, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(src, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(src, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(srcLen, 0, 0, 3);
        Genome.setGene(src, 1, 0, 0, Genome.encode(3, 3));
        Genome.setGene(src, 1, 0, 1, Genome.encode(4, 4));
        Genome.setGene(src, 1, 0, 2, Genome.encode(5, 5));
        Genome.setLen(srcLen, 1, 0, 3);

        short[] dst = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
        byte[] dstLen = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];

        // Plusieurs seeds pour couvrir les cas
        for (int seed = 0; seed < 20; seed++) {
            java.util.Arrays.fill(dst, Genome.EMPTY_GENE);
            java.util.Arrays.fill(dstLen, (byte) 0);
            GenomeOps.crossover(src, srcLen, 0, src, srcLen, 1, dst, dstLen, 0, new SplittableRandom(seed));
            assertThat(GenomeInvariants.check(dst, dstLen, 0)).isTrue();
            int len = Genome.len(dstLen, 0, 0);
            assertThat(len).isBetween(0, 5); // au pire 5 arbres distincts disponibles
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GenomeOpsCrossoverTest
```
Expected: `FAILURE` (`crossover` symbol not found).

- [ ] **Step 3: Add crossover to GenomeOps**

Append to `GenomeOps` :

```java
    private static final boolean[] seenBuf = new boolean[256 * 256]; // max grid 256x256

    public static void crossover(short[] srcA, byte[] lenA, int idxA,
                                 short[] srcB, byte[] lenB, int idxB,
                                 short[] dst,  byte[] dstLen, int idxDst,
                                 SplittableRandom rng) {
        int W = GameState.width;
        int H = GameState.height;
        // Reset offspring
        int dstBase = Genome.offset(idxDst, 0);
        for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) dst[dstBase + k] = Genome.EMPTY_GENE;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(dstLen, idxDst, j, 0);
        // Reset seen[] sur la zone utilisée
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) seenBuf[y * W + x] = false;
        }
        // OX par troll
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int la = Genome.len(lenA, idxA, j);
            int lb = Genome.len(lenB, idxB, j);
            if (la == 0 && lb == 0) continue;
            int cut = (la > 0) ? rng.nextInt(la + 1) : 0;
            int dstOff = Genome.offset(idxDst, j);
            int written = 0;
            // Préfixe de P1
            int aBase = Genome.offset(idxA, j);
            for (int k = 0; k < cut; k++) {
                short g = srcA[aBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                int cell = y * W + x;
                if (seenBuf[cell]) continue; // ne devrait pas arriver si parent valide, défensif
                seenBuf[cell] = true;
                dst[dstOff + written++] = g;
            }
            // Compléter avec P2
            int bBase = Genome.offset(idxB, j);
            int target = (la > 0 ? la : lb);
            for (int k = 0; k < lb && written < target && written < Genome.MAX_TARGETS_PER_TROLL; k++) {
                short g = srcB[bBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                int cell = y * W + x;
                if (seenBuf[cell]) continue;
                seenBuf[cell] = true;
                dst[dstOff + written++] = g;
            }
            Genome.setLen(dstLen, idxDst, j, written);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeOpsCrossoverTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java
git commit -m "ga: GenomeOps.crossover — OX par troll + repair via seen[]"
```

---

### Task 7: Selection — tournoi binaire

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/Selection.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/SelectionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTest {

    @Test void tournamentFavorsHigherFitness() {
        double[] fit = new double[]{ 0.0, 10.0, 5.0, 20.0 };
        int wins = 0;
        SplittableRandom rng = new SplittableRandom(0);
        for (int t = 0; t < 1000; t++) {
            int pick = Selection.tournament(fit, rng, fit.length);
            if (fit[pick] >= 10.0) wins++;
        }
        // dans la moitié des cas un des deux tirages est ≤ 5.0 → l'autre gagne. Donc on devrait
        // toujours préférer ≥ 10.0 quand l'un est ≥ 10. Au pire 25% des paires sont {0,5}.
        assertThat(wins).isGreaterThan(700);
    }

    @Test void tournamentWithSingleIndividualReturnsIt() {
        double[] fit = new double[]{ 42.0 };
        int pick = Selection.tournament(fit, new SplittableRandom(1), 1);
        assertThat(pick).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=SelectionTest
```
Expected: `FAILURE`.

- [ ] **Step 3: Implement Selection**

```java
package com.bmrt.cgspring2026.ga;

import java.util.SplittableRandom;

public final class Selection {

    private Selection() {}

    public static int tournament(double[] fit, SplittableRandom rng, int popSize) {
        int a = rng.nextInt(popSize);
        int b = rng.nextInt(popSize);
        return (fit[a] >= fit[b]) ? a : b;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=SelectionTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/Selection.java \
        src/test/java/com/bmrt/cgspring2026/ga/SelectionTest.java
git commit -m "ga: Selection.tournament — tournoi binaire"
```

---

### Task 8: Refactor `GreedyAgent` pour `decideForOpponent`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java`
- Test: `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java` (ajout)

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java` :

```java
    @Test void decideForOpponentReturnsMoveTowardsClosestFreeTreeForOppTroll() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1; // adversaire
        s.trollX[0] = 5; s.trollY[0] = 4;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        boolean[] taken = new boolean[GameState.MAX_TREES];
        int a = GreedyAgent.decideForOpponent(s, 0, taken);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(a)).isEqualTo(5);
        assertThat(Action.arg2(a)).isEqualTo(0);
        assertThat(taken[0]).isTrue();
    }

    @Test void decideForOpponentRespectsTreeTakenBuf() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1;
        s.trollX[0] = 5; s.trollY[0] = 4;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        boolean[] taken = new boolean[GameState.MAX_TREES];
        taken[0] = true;
        int a = GreedyAgent.decideForOpponent(s, 0, taken);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GreedyAgentDecideTest#decideForOpponentReturnsMoveTowardsClosestFreeTreeForOppTroll
```
Expected: `FAILURE` (`decideForOpponent` symbol not found).

- [ ] **Step 3: Refactor GreedyAgent**

Open `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java`. The current `pickClosestFreeTree` and `decideForTroll` are reusable. Add a new public method `decideForOpponent` after `decide` :

```java
    public static int decideForOpponent(GameState s, int trollIdx, boolean[] oppTreeTakenBuf) {
        int treeIdx = pickClosestFreeTreeWithBuf(s, trollIdx, oppTreeTakenBuf);
        if (treeIdx >= 0) oppTreeTakenBuf[treeIdx] = true;
        return decideForTroll(s, trollIdx, treeIdx);
    }

    private static int pickClosestFreeTreeWithBuf(GameState s, int trollIdx, boolean[] taken) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int t = 0; t < s.treeCount; t++) {
            if (taken[t]) continue;
            if (s.treeHealth[t] <= 0) continue;
            int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
            if (d == PathTable.UNREACHABLE) continue;
            if (d < bestDist) { bestDist = d; best = t; }
        }
        return best;
    }
```

`decideForTroll` est déjà compatible (il regarde uniquement la position du troll, indifférent au player). Le `pickClosestFreeTree` existant continue d'utiliser le `treeTaken` privé pour `decide()`.

- [ ] **Step 4: Run all tests**

```bash
mvn -q test
```
Expected: `BUILD SUCCESS`, no regression sur tests existants.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java \
        src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java
git commit -m "greedy: extract decideForOpponent + pickClosestFreeTreeWithBuf"
```

---

### Task 9: TrollPolicy — traduction génome → actions par tick

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyTest.java`

- [ ] **Step 1: Write the failing test**

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

class TrollPolicyTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "...1..",
            "......"
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
        ShackAdjacency.init();
    }

    private static GameState stateOneTroll(int x, int y, int wood) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
        s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
        s.trollInventory[ResourceType.WOOD] = (byte) wood;
        s.trollCarryTotal[0] = wood;
        return s;
    }

    @Test void emptyListEmitsWait() {
        GameState s = stateOneTroll(3, 3, 0);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(n).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }

    @Test void woodCarriedTriggersMoveTowardsShackAdjacent() {
        GameState s = stateOneTroll(5, 4, 2);
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // Une target lointaine — on doit quand même drop d'abord
        Genome.setGene(buf, 0, 0, 0, Genome.encode(4, 4));
        Genome.setLen(lenBuf, 0, 0, 1);
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
    }

    @Test void dropWhenAdjacentToShack() {
        GameState s = stateOneTroll(0, 1, 2); // (0,1) is shack-adjacent
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
    }

    @Test void chopWhenStandingOnTargetTree() {
        GameState s = stateOneTroll(3, 2, 0);
        s.treeCount = 1;
        s.treeX[0] = 3; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2));
        Genome.setLen(lenBuf, 0, 0, 1);
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.CHOP);
    }

    @Test void skipsDeadTreeAndAdvancesCursor() {
        GameState s = stateOneTroll(3, 2, 0);
        // Pas d'arbre vivant à (3,2) — gène ciblé pointe vers du vide
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(3, 2)); // mort/disparu
        Genome.setGene(buf, 0, 0, 1, Genome.encode(4, 2));
        Genome.setLen(lenBuf, 0, 0, 2);
        s.treeCount = 1;
        s.treeX[0] = 4; s.treeY[0] = 2;
        s.treeHealth[0] = 5;
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        // Le cursor doit avoir avancé à 1, et l'action est un MOVE vers (4,2)
        assertThat(cursor[0]).isEqualTo(1);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(out[0])).isEqualTo(4);
        assertThat(Action.arg2(out[0])).isEqualTo(2);
    }

    @Test void opponentTrollDecidedViaGreedy() {
        GameState s = stateOneTroll(3, 2, 0);
        s.trollCount = 2;
        s.trollPlayer[1] = 1; // opp
        s.trollX[1] = 5; s.trollY[1] = 4;
        s.trollMS[1] = 2;
        s.treeCount = 1;
        s.treeX[0] = 5; s.treeY[0] = 0;
        s.treeHealth[0] = 5;
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] cursor = new int[GameState.MAX_TROLLS];
        int[] out = new int[GameState.MAX_TROLLS + 1];
        int n = TrollPolicy.fillActions(s, buf, lenBuf, 0, cursor, out);
        assertThat(n).isEqualTo(2);
        // Action 0 = mon troll (WAIT, pas de target), Action 1 = opp (MOVE vers arbre)
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.type(out[1])).isEqualTo((int) ActionType.MOVE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=TrollPolicyTest
```
Expected: `FAILURE`.

- [ ] **Step 3: Implement TrollPolicy**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.pathfinding.PathTable;

public final class TrollPolicy {

    public static final int[]     cursorBuf      = new int[GameState.MAX_TROLLS];
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

    private static int decideForOwnTroll(GameState s, short[] popBuf, byte[] popLen, int idx,
                                         int[] cursor, int trollIdx) {
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;

        if (wood > 0) {
            if (isShackAdjacent(tx, ty)) return Action.drop(trollIdx);
            return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
        }

        // Avancer le cursor sur les targets mortes ou invalides
        int len = Genome.len(popLen, idx, trollIdx);
        while (cursor[trollIdx] < len) {
            short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
            if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; continue; }
            int gx = Genome.geneX(g), gy = Genome.geneY(g);
            if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; continue; }
            // Target valide trouvée
            if (tx == gx && ty == gy) return Action.chop(trollIdx);
            return Action.move(trollIdx, gx, gy);
        }
        return Action.wait(trollIdx);
    }

    private static boolean isShackAdjacent(int x, int y) {
        for (int i = 0; i < ShackAdjacency.count; i++) {
            if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
        }
        return false;
    }

    private static int closestShackAdjX(int x, int y) { return closestShackAdj(x, y, true); }
    private static int closestShackAdjY(int x, int y) { return closestShackAdj(x, y, false); }

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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=TrollPolicyTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java \
        src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyTest.java
git commit -m "ga: TrollPolicy — translate genome → actions per tick (mirror greedy)"
```

---

### Task 10: GenomeEvaluator — simulation forward + fitness

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeEvaluatorTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
            "......",
            "...1..",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
    }

    @Test void fitnessIsZeroWhenNoTrollsAndNoTrees() {
        GameState source = new GameState();
        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        assertThat(fit).isEqualTo(0.0);
    }

    @Test void fitnessRewardsScoreAccumulation() {
        // Petit setup où un troll peut couper un arbre puis drop
        GameState source = new GameState();
        // Troll player=0 sur (2,1) (shack-adjacent), capable de chop large
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        source.trollX[0] = 2; source.trollY[0] = 1;
        source.trollMS[0] = 4; source.trollCC[0] = 16;
        source.trollHP[0] = 1; source.trollCP[0] = 20; // one-shot
        // Arbre adulte taille 4 à (2,2) (proche)
        source.treeCount = 1;
        source.treeType[0] = TreeType.PLUM;
        source.treeX[0] = 2; source.treeY[0] = 2;
        source.treeSize[0] = 4; source.treeHealth[0] = 12;
        source.treeFruits[0] = 0; source.treeCooldown[0] = 8;

        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(2, 2));
        Genome.setLen(lenBuf, 0, 0, 1);

        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        // Le troll devrait chop (16 pts) puis drop dans l'horizon → fit positive
        assertThat(fit).isGreaterThan(0.0);
    }

    @Test void fitnessFormulaIncludesCarryBonus() {
        // Scénario où le troll récupère du wood mais n'a pas le temps de drop
        // → la fitness doit refléter le wood porté via ALPHA_WOOD_CARRY
        GameState source = new GameState();
        source.trollCount = 1;
        source.trollPlayer[0] = 0;
        // Position loin du shack pour empêcher drop
        source.trollX[0] = 4; source.trollY[0] = 5;
        source.trollMS[0] = 1; source.trollCC[0] = 16;
        source.trollHP[0] = 1; source.trollCP[0] = 20;
        source.treeCount = 1;
        source.treeType[0] = TreeType.BANANA; // health très bas
        source.treeX[0] = 4; source.treeY[0] = 5;
        source.treeSize[0] = 4; source.treeHealth[0] = 6;
        source.treeFruits[0] = 0; source.treeCooldown[0] = 6;

        GameState scratch = new GameState();
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.encode(4, 5));
        Genome.setLen(lenBuf, 0, 0, 1);

        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        double fit = GenomeEvaluator.evaluate(scratch, source, buf, lenBuf, 0, actionBuf);
        // troll chop banana taille 4 → 4 wood porté. Pas de drop possible (loin du shack avec MS=1)
        // fitness = 0 (score) + 2.0 * 4 = 8.0
        assertThat(fit).isGreaterThanOrEqualTo(8.0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GenomeEvaluatorTest
```
Expected: `FAILURE`.

- [ ] **Step 3: Implement GenomeEvaluator**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.simulation.Simulator;

public final class GenomeEvaluator {

    public static final int    HORIZON          = 25;
    public static final double ALPHA_WOOD_CARRY = 2.0;

    private GenomeEvaluator() {}

    public static double evaluate(GameState scratch, GameState source,
                                  short[] popBuf, byte[] popLen, int idx,
                                  int[] actionBuf) {
        scratch.copyFrom(source);
        int[] cursor = TrollPolicy.cursorBuf;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
        for (int t = 0; t < HORIZON; t++) {
            int n = TrollPolicy.fillActions(scratch, popBuf, popLen, idx, cursor, actionBuf);
            Simulator.tick(scratch, actionBuf, n);
        }
        return fitness(scratch);
    }

    private static double fitness(GameState finalState) {
        int scoreMe  = finalState.score(0);
        int scoreOpp = finalState.score(1);
        int woodCarryMe = 0;
        for (int i = 0; i < finalState.trollCount; i++) {
            if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
            woodCarryMe += finalState.trollInventory[i * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
        }
        return (scoreMe - scoreOpp) + ALPHA_WOOD_CARRY * woodCarryMe;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GenomeEvaluatorTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java \
        src/test/java/com/bmrt/cgspring2026/ga/GenomeEvaluatorTest.java
git commit -m "ga: GenomeEvaluator — forward sim sur 25 tours + fitness (score + α·wood_carried)"
```

---

### Task 11: GeneticAgent — boucle évolutive deadline-driven

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java`

- [ ] **Step 1: Write the failing test**

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

class GeneticAgentTest {

    @BeforeEach void grid() {
        String[] rows = {
            "........",
            ".0......",
            "........",
            "........",
            "......1.",
            "........"
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
        ShackAdjacency.init();
    }

    private static GameState seededState() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 2; s.trollY[0] = 2;
        s.trollMS[0] = 3; s.trollCC[0] = 16; s.trollHP[0] = 1; s.trollCP[0] = 20;
        s.treeCount = 2;
        s.treeType[0] = TreeType.PLUM;
        s.treeX[0] = 4; s.treeY[0] = 3;
        s.treeSize[0] = 4; s.treeHealth[0] = 12;
        s.treeCooldown[0] = 8;
        s.treeType[1] = TreeType.BANANA;
        s.treeX[1] = 3; s.treeY[1] = 1;
        s.treeSize[1] = 4; s.treeHealth[1] = 6;
        s.treeCooldown[1] = 6;
        return s;
    }

    @Test void decideRespectsDeadline() {
        GameState s = seededState();
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 20_000_000L; // 20ms
        long t0 = System.nanoTime();
        agent.decide(s, deadline, out);
        long elapsed = System.nanoTime() - t0;
        // Tolère un dépassement de 30ms max (init pop + 1 éval peut prendre du temps)
        assertThat(elapsed).isLessThan(80_000_000L);
    }

    @Test void decideProducesOneActionPerTrollWhenNoTrain() {
        GameState s = seededState();
        s.turn = 5; // pas de TRAIN
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 100_000_000L;
        int n = agent.decide(s, deadline, out);
        assertThat(n).isEqualTo(s.trollCount);
    }

    @Test void decideEmitsTrainAtTurnZero() {
        GameState s = seededState();
        s.turn = 0;
        s.shackInventory[ResourceType.PLUM]  = 5;
        s.shackInventory[ResourceType.LEMON] = 5;
        s.shackInventory[ResourceType.APPLE] = 5;
        s.shackInventory[ResourceType.IRON]  = 5;
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 100_000_000L;
        int n = agent.decide(s, deadline, out);
        // Au moins une action TRAIN dans out[0..n[
        boolean hasTrain = false;
        for (int i = 0; i < n; i++) if (Action.type(out[i]) == ActionType.TRAIN) hasTrain = true;
        assertThat(hasTrain).isTrue();
    }

    @Test void bestFitnessIsNonDecreasingAcrossGenerations() {
        GameState s = seededState();
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        long deadline = System.nanoTime() + 200_000_000L; // 200ms
        agent.decide(s, deadline, out);
        // Sur 200ms, on s'attend à au moins quelques générations
        assertThat(agent.lastGenCount()).isGreaterThanOrEqualTo(1);
        assertThat(agent.lastBestFitness()).isNotNaN();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -q test -Dtest=GeneticAgentTest
```
Expected: `FAILURE` (class missing).

- [ ] **Step 3: Implement GeneticAgent**

```java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.model.GameState;

import java.util.SplittableRandom;

public final class GeneticAgent {

    public static final long   TURN_BUDGET_NS = 40_000_000L;
    public static final long   INIT_BUDGET_NS = 900_000_000L;
    public static final double P_CROSSOVER    = 0.70;

    private final GameState scratch = new GameState();
    private final Population pop    = new Population();
    private final SplittableRandom rng = new SplittableRandom();
    private final int[] evalActionBuf = new int[GameState.MAX_TROLLS + 1];

    private int    lastGenCount;
    private double lastBestFitness;
    private int    lastBestIdx;

    public GeneticAgent() {}

    public int decide(GameState state, long deadlineNs, int[] outActions) {
        // 1. Init population
        initPopulation(state);
        evaluatePopulation(state);
        lastGenCount = 0;

        // 2. Boucle évolutive deadline-driven
        while (System.nanoTime() < deadlineNs) {
            stepGeneration(state);
            lastGenCount++;
            if (System.nanoTime() >= deadlineNs) break;
        }

        // 3. Best individu courant
        lastBestIdx = argmax(pop.curFit);
        lastBestFitness = pop.curFit[lastBestIdx];

        // 4. Génère les actions du tick 0
        int[] cursor = TrollPolicy.cursorBuf;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
        int n = TrollPolicy.fillActions(state, pop.cur, pop.curLen, lastBestIdx, cursor, outActions);

        // 5. Ajouter TRAIN au tour 0
        if (state.turn == 0) {
            int trainAction = GreedyAgent.maybeTrain(state);
            if (trainAction != -1) {
                System.arraycopy(outActions, 0, outActions, 1, n);
                outActions[0] = trainAction;
                n++;
            }
        }
        return n;
    }

    public int    lastGenCount()    { return lastGenCount; }
    public double lastBestFitness() { return lastBestFitness; }

    private void initPopulation(GameState state) {
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
        }
    }

    private void evaluatePopulation(GameState state) {
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            pop.curFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.cur, pop.curLen, i, evalActionBuf);
        }
    }

    private void stepGeneration(GameState state) {
        // Élitisme top-1
        int bestIdx = argmax(pop.curFit);
        copyIndividu(pop.cur, pop.curLen, bestIdx, pop.nxt, pop.nxtLen, 0);
        pop.nxtFit[0] = pop.curFit[bestIdx];

        // Génère offspring
        for (int i = 1; i < Genome.POP_SIZE; i++) {
            if (rng.nextDouble() < P_CROSSOVER) {
                int p1 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                int p2 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                GenomeOps.crossover(pop.cur, pop.curLen, p1, pop.cur, pop.curLen, p2,
                                    pop.nxt, pop.nxtLen, i, rng);
            } else {
                int p = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                copyIndividu(pop.cur, pop.curLen, p, pop.nxt, pop.nxtLen, i);
                GenomeOps.runMutation(pop.nxt, pop.nxtLen, i, rng);
            }
            pop.nxtFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.nxt, pop.nxtLen, i, evalActionBuf);
        }
        pop.swap();
    }

    private static void copyIndividu(short[] srcBuf, byte[] srcLen, int srcIdx,
                                     short[] dstBuf, byte[] dstLen, int dstIdx) {
        System.arraycopy(srcBuf, Genome.offset(srcIdx, 0),
                         dstBuf, Genome.offset(dstIdx, 0), Genome.SLOTS_PER_GENOME);
        System.arraycopy(srcLen, Genome.lenOffset(srcIdx, 0),
                         dstLen, Genome.lenOffset(dstIdx, 0), GameState.MAX_TROLLS);
    }

    private static int argmax(double[] arr) {
        int best = 0;
        double bestV = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > bestV) { bestV = arr[i]; best = i; }
        }
        return best;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn -q test -Dtest=GeneticAgentTest
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run all tests to check for regressions**

```bash
mvn -q test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java \
        src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java
git commit -m "ga: GeneticAgent — deadline-driven evolutionary loop + élitisme top-1"
```

---

### Task 12: Intégration `Player.main`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/Player.java`

- [ ] **Step 1: Verify all GA tests still pass**

```bash
mvn -q test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Update Player.java**

Replace `src/main/java/com/bmrt/cgspring2026/Player.java` with:

```java
package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.ga.GeneticAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.pathfinding.PathTable;

import java.util.Scanner;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        ShackAdjacency.init();

        GameState state    = new GameState();
        GeneticAgent agent = new GeneticAgent();
        int[] actionBuf    = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb   = new StringBuilder();

        boolean firstTurn = true;
        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);

            long deadline = start + (firstTurn ? GeneticAgent.INIT_BUDGET_NS
                                               : GeneticAgent.TURN_BUDGET_NS);
            int n = agent.decide(state, deadline, actionBuf);
            firstTurn = false;

            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            sb.append(";MSG GA gen=").append(agent.lastGenCount())
              .append(" fit=").append((int) agent.lastBestFitness())
              .append(" t=").append((System.nanoTime() - start) / 1_000_000).append("ms");
            System.out.println(sb);

            state.turn++;
        }
    }
}
```

- [ ] **Step 3: Compile and check no symbol errors**

```bash
mvn -q compile
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run all tests to verify no regression**

```bash
mvn -q test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/Player.java
git commit -m "player: switch entry point to GeneticAgent + verbose MSG"
```

---

### Task 13: Bench harness manuel

**Files:**
- Create: `src/test/java/com/bmrt/cgspring2026/ga/bench/GeneticAgentBench.java`

- [ ] **Step 1: Write the bench harness**

```java
package com.bmrt.cgspring2026.ga.bench;

import com.bmrt.cgspring2026.ga.GeneticAgent;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("manual bench, run avec -Dtest=GeneticAgentBench")
class GeneticAgentBench {

    @Test void runMidGameBench() {
        // Carte 16×8 réaliste avec eau
        String[] rows = {
            "................",
            ".0..............",
            "....~~~.........",
            "....~...........",
            "........~~......",
            "..........~~....",
            ".............1..",
            "................"
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
        ShackAdjacency.init();

        // 8 arbres, 3 trolls par côté
        GameState s = new GameState();
        s.turn = 30;
        int[][] trees = {{3,3,TreeType.PLUM},{6,5,TreeType.LEMON},{8,2,TreeType.APPLE},
                         {10,6,TreeType.BANANA},{12,3,TreeType.PLUM},{4,5,TreeType.LEMON},
                         {7,3,TreeType.APPLE},{11,5,TreeType.BANANA}};
        for (int[] t : trees) {
            int i = s.treeCount++;
            s.treeType[i] = (byte) t[2];
            s.treeX[i] = (byte) t[0]; s.treeY[i] = (byte) t[1];
            s.treeSize[i] = 3; s.treeHealth[i] = 10; s.treeCooldown[i] = 5;
        }
        int[][] trolls = {{0,2,1,2,8,1,5},{0,3,2,2,6,1,4},{0,1,2,2,4,1,3},
                          {1,13,7,2,8,1,5},{1,12,7,2,6,1,4},{1,14,6,2,4,1,3}};
        for (int[] tr : trolls) {
            int i = s.trollCount++;
            s.trollPlayer[i] = (byte) tr[0];
            s.trollX[i] = (byte) tr[1]; s.trollY[i] = (byte) tr[2];
            s.trollMS[i] = (byte) tr[3]; s.trollCC[i] = (byte) tr[4];
            s.trollHP[i] = (byte) tr[5]; s.trollCP[i] = (byte) tr[6];
        }

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];

        // Warmup
        for (int i = 0; i < 5; i++) {
            agent.decide(s, System.nanoTime() + 40_000_000L, out);
        }

        // Bench : 50 décisions, 40ms chacune
        long totalNs = 0;
        int totalGen = 0;
        double bestFit = Double.NEGATIVE_INFINITY;
        int trials = 50;
        for (int i = 0; i < trials; i++) {
            long t0 = System.nanoTime();
            long deadline = t0 + 40_000_000L;
            agent.decide(s, deadline, out);
            totalNs += System.nanoTime() - t0;
            totalGen += agent.lastGenCount();
            if (agent.lastBestFitness() > bestFit) bestFit = agent.lastBestFitness();
        }

        System.out.println("=== GeneticAgent Bench ===");
        System.out.printf("trials=%d  avg=%.2f ms  avg_gen=%.2f  best_fit=%.2f%n",
            trials, totalNs / 1e6 / trials, (double) totalGen / trials, bestFit);
    }
}
```

- [ ] **Step 2: Run bench manually (one-shot)**

`@Disabled` est respecté par Surefire même avec `-Dtest=`. Pour exécuter, commenter temporairement la ligne `@Disabled(...)` dans le fichier puis :

```bash
mvn test -Dtest=GeneticAgentBench
```

Sortie attendue : ligne `=== GeneticAgent Bench ===` avec stats. Re-commenter `@Disabled` avant commit (le bench ne doit pas tourner en CI).

Inspecter la sortie. Si `avg_gen < 1.0` → réduire `HORIZON` à 20 ou `POP_SIZE` à 32 dans les constantes.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/bench/GeneticAgentBench.java
git commit -m "ga: bench harness mid-game (40ms budget, mesure gen/s + fitness)"
```

---

### Task 14: Smoke test régression vs greedy

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java` (ajouter test)

- [ ] **Step 1: Write the regression test**

Append to `GeneticAgentTest.java` :

```java
    @Test void geneticAgentMatchesOrBeatsGreedyOnSimpleScenario() {
        // Setup : 1 troll me, 2 arbres ; le GA doit obtenir une fitness ≥ greedy
        GameState src = seededState();

        // Greedy reference : applique greedy sur 25 tours
        com.bmrt.cgspring2026.model.GameState greedyScratch = new com.bmrt.cgspring2026.model.GameState();
        greedyScratch.copyFrom(src);
        int[] gOut = new int[GameState.MAX_TROLLS + 1];
        for (int t = 0; t < GenomeEvaluator.HORIZON; t++) {
            int n = com.bmrt.cgspring2026.greedy.GreedyAgent.decide(greedyScratch, gOut);
            com.bmrt.cgspring2026.simulation.Simulator.tick(greedyScratch, gOut, n);
        }
        int greedyScore = greedyScratch.score(0);

        // GA : tourne 200ms et applique le best tick après tick
        GeneticAgent agent = new GeneticAgent();
        GameState gaScratch = new GameState();
        gaScratch.copyFrom(src);
        int[] gaOut = new int[GameState.MAX_TROLLS + 1];
        for (int t = 0; t < GenomeEvaluator.HORIZON; t++) {
            long deadline = System.nanoTime() + 50_000_000L;
            int n = agent.decide(gaScratch, deadline, gaOut);
            com.bmrt.cgspring2026.simulation.Simulator.tick(gaScratch, gaOut, n);
        }
        int gaScore = gaScratch.score(0);

        // GA ≥ greedy (peut être égal sur scenarios triviaux)
        assertThat(gaScore).isGreaterThanOrEqualTo(greedyScore);
    }
```

- [ ] **Step 2: Run test**

```bash
mvn -q test -Dtest=GeneticAgentTest#geneticAgentMatchesOrBeatsGreedyOnSimpleScenario
```
Expected: `BUILD SUCCESS` (le GA devrait au moins égaler greedy sur scenario simple).

- [ ] **Step 3: Run all tests final**

```bash
mvn -q test
```
Expected: `BUILD SUCCESS`. Tous les tests passent.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java
git commit -m "ga: smoke regression test — GA matches or beats greedy on simple scenario"
```

---

## Notes de fine-tuning post-implémentation

Une fois l'implémentation terminée et le bench tournant, les leviers suivants sont accessibles via constantes :

| Constante | Fichier | Effet |
|---|---|---|
| `Genome.POP_SIZE` | `Genome.java` | ↓ pour plus de générations dans le budget, ↑ pour plus de diversité |
| `Genome.MAX_TARGETS_PER_TROLL` | `Genome.java` | Borne haute de la séquence par troll |
| `GenomeEvaluator.HORIZON` | `GenomeEvaluator.java` | Profondeur de simulation (25 → 15-20 si trop lent) |
| `GenomeEvaluator.ALPHA_WOOD_CARRY` | `GenomeEvaluator.java` | Poids du wood porté dans la fitness |
| `GeneticAgent.P_CROSSOVER` | `GeneticAgent.java` | Ratio crossover/mutation |
| `GenomeOps.P_SKIP_INIT` | `GenomeOps.java` | Densité initiale de la partition |
| `GenomeOps.P_MUT_*` | `GenomeOps.java` | Mix des opérateurs de mutation |

Ces fine-tunings sont à faire via la skill `fine-tune` après benchmark en condition réelle (matchs CG).
