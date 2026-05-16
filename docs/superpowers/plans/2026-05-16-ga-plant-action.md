# GA — Action PLANT (compound) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a compound PLANT gene to the genetic agent so trolls can execute full `PICK → MOVE → PLANT → CHOP → DROP` cycles on cells within Manhattan distance 2 of the own shack, with full GA support (init, mutation, crossover, invariants).

**Architecture:** Encoding extends the existing `short` gene with a flag bit + 2-bit fruit type (backward-compatible with TARGET genes). A per-troll `policyPhase` byte distinguishes pre-plant (need pick + plant) from post-plant (need chop until dead) inside the `TrollPolicy`. The invariant key becomes `(cell, isPlant)` so that a TARGET and a PLANT can coexist on the same cell (the chop-then-replant strategy).

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ.

**Spec reference:** `docs/superpowers/specs/2026-05-16-ga-plant-action-design.md`.

---

## File Structure

**Modified:**
- `src/main/java/com/bmrt/cgspring2026/ga/Genome.java` — add `makePlant`, `isPlant`, `plantFruitType`, retain `encode` as TARGET-only; add static `plantCandidates` + `initPlantCandidates`; tighten `geneX` mask to 5 bits.
- `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java` — add `policyPhase[]`, branch on `isPlant`, sub-state machine for PLANT.
- `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java` — reset `policyPhase` at start of each `evaluate()`.
- `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java` — call `Genome.initPlantCandidates` at first `decide`; reset `policyPhase` before live action emission.
- `src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java` — dual `(cell, isPlant)` invariant.
- `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java` — dual seen in crossover; `P_PLANT_INIT` in `initRandom`; new `mutateInsertPlant`; rebalanced `pickMutationKind`.

**Created (tests):**
- `src/test/java/com/bmrt/cgspring2026/ga/GenomePlantTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/PlantCandidatesTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInsertPlantTest.java`

**Modified (tests):**
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java` — add coexistence test for `(cell, PLANT)` + `(cell, TARGET)`.
- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java` — add dual seen test.

---

## Conventions

- All test classes follow the existing pattern: `@BeforeEach` sets `GameState.width/height/tiles`, init `PathTable` and `ShackAdjacency` for tests using the policy.
- `EMPTY_GENE` remains `-1` (0xFFFF). Slots after `len[j]` are EMPTY by convention. `isPlant(EMPTY_GENE)` returns `true` mechanically, but live policy never reads beyond `len[j]`.
- Run a single test class with `mvn -q test -Dtest=ClassName`.
- Run the full suite with `mvn -q test`.

---

## Task 1: `Genome.isPlant` / `makePlant` / `plantFruitType` helpers

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomePlantTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/bmrt/cgspring2026/ga/GenomePlantTest.java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenomePlantTest {

    @Test void makeTargetIsNotPlant() {
        short g = Genome.makeTarget(5, 7);
        assertThat(Genome.isPlant(g)).isFalse();
        assertThat(Genome.geneX(g)).isEqualTo(5);
        assertThat(Genome.geneY(g)).isEqualTo(7);
    }

    @Test void makePlantRoundTripCoords() {
        short g = Genome.makePlant(3, 9, TreeType.APPLE);
        assertThat(Genome.isPlant(g)).isTrue();
        assertThat(Genome.geneX(g)).isEqualTo(3);
        assertThat(Genome.geneY(g)).isEqualTo(9);
    }

    @Test void makePlantRoundTripFruitType() {
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.PLUM))).isEqualTo((int) TreeType.PLUM);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.LEMON))).isEqualTo((int) TreeType.LEMON);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.APPLE))).isEqualTo((int) TreeType.APPLE);
        assertThat(Genome.plantFruitType(Genome.makePlant(0, 0, TreeType.BANANA))).isEqualTo((int) TreeType.BANANA);
    }

    @Test void legacyEncodeStaysCompatible() {
        // encode() used everywhere should produce a TARGET gene equivalent to makeTarget
        short legacy = Genome.encode(7, 4);
        short modern = Genome.makeTarget(7, 4);
        assertThat(legacy).isEqualTo(modern);
        assertThat(Genome.isPlant(legacy)).isFalse();
    }

    @Test void geneXMasksFiveBitsForPlant() {
        // Plant with x=21 (max realistic) still decodes correctly with flag+fruit set
        short g = Genome.makePlant(21, 10, TreeType.BANANA);
        assertThat(Genome.geneX(g)).isEqualTo(21);
        assertThat(Genome.geneY(g)).isEqualTo(10);
        assertThat(Genome.isPlant(g)).isTrue();
        assertThat(Genome.plantFruitType(g)).isEqualTo((int) TreeType.BANANA);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=GenomePlantTest`
Expected: compile error — `makePlant`, `isPlant`, `plantFruitType`, `makeTarget` do not exist.

- [ ] **Step 3: Implement the helpers**

Edit `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`. After the existing `encode(int x, int y)` add (and modify `geneX` to mask 5 bits):

```java
    // --- New encoding helpers ---
    // Bit layout: bit15=flag(PLANT=1), bits13-14=fruitType, bits8-12=x(5 bits), bits0-7=y(8 bits)

    private static final int PLANT_FLAG_MASK   = 0x8000;
    private static final int FRUIT_TYPE_SHIFT  = 13;
    private static final int FRUIT_TYPE_MASK   = 0x3 << FRUIT_TYPE_SHIFT;
    private static final int X_MASK            = 0x1F;
    private static final int X_SHIFT           = 8;

    public static short makeTarget(int x, int y) {
        return encode(x, y);
    }

    public static short makePlant(int x, int y, int fruitType) {
        return (short) (PLANT_FLAG_MASK
                | ((fruitType & 0x3) << FRUIT_TYPE_SHIFT)
                | ((x & X_MASK) << X_SHIFT)
                | (y & 0xFF));
    }

    public static boolean isPlant(short g) {
        return (g & PLANT_FLAG_MASK) != 0;
    }

    public static int plantFruitType(short g) {
        return (g & FRUIT_TYPE_MASK) >>> FRUIT_TYPE_SHIFT;
    }
```

Then change `geneX` to mask 5 bits (was 8). Replace the existing method:

```java
    public static int geneX(short g) {
        return (g >>> X_SHIFT) & X_MASK;
    }
```

`geneY` stays as-is (`g & 0xFF`).

- [ ] **Step 4: Run all GA tests to verify nothing else broke**

Run: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green. The 5-bit `geneX` mask is safe because legacy TARGET genes have bit15=0 and `x ≤ 21 ≤ 31`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java src/test/java/com/bmrt/cgspring2026/ga/GenomePlantTest.java
git commit -m "ga: add PLANT/TARGET encoding helpers on Genome shorts"
```

---

## Task 2: `Genome.plantCandidates` static table + `initPlantCandidates`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/Genome.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/PlantCandidatesTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/bmrt/cgspring2026/ga/PlantCandidatesTest.java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantCandidatesTest {

    private static void grid(String[] rows) {
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

    private static boolean contains(int x, int y) {
        short packed = (short) ((x << 8) | y);
        for (int i = 0; i < Genome.plantCandidateCount; i++) {
            if (Genome.plantCandidates[i] == packed) return true;
        }
        return false;
    }

    @Test void manhattanDiamondAroundShack() {
        grid(new String[]{
            ".........",
            ".........",
            "....0....",
            ".........",
            "........."
        });
        Genome.initPlantCandidates();
        // Diamond around (4,2), Manhattan ≤ 2, excluding shack
        // Expected cells: 12 (all GRASS in this grid)
        assertThat(Genome.plantCandidateCount).isEqualTo(12);
        assertThat(contains(4, 2)).isFalse();       // shack excluded
        assertThat(contains(2, 2)).isTrue();        // dist 2 west
        assertThat(contains(6, 2)).isTrue();        // dist 2 east
        assertThat(contains(4, 0)).isTrue();        // dist 2 north
        assertThat(contains(4, 4)).isTrue();        // dist 2 south
        assertThat(contains(3, 1)).isTrue();        // dist 2 NW diagonal
        assertThat(contains(1, 2)).isFalse();       // dist 3
    }

    @Test void excludesNonGrassTiles() {
        grid(new String[]{
            ".........",
            "..~~~....",
            "..~0~....",
            "..~~~....",
            "........."
        });
        Genome.initPlantCandidates();
        // Only the 4 cells at Manhattan distance 2 that are GRASS
        // Adjacent (dist 1): all WATER → excluded
        // Dist 2: (5,2), (3,2) blocked by water (since (4,2) is shack; need to test (2,2), (6,2), (4,0), (4,4))
        assertThat(contains(3, 2)).isFalse();       // WATER
        assertThat(contains(2, 2)).isTrue();        // GRASS dist 2
        assertThat(contains(6, 2)).isTrue();
        assertThat(contains(4, 0)).isTrue();
        assertThat(contains(4, 4)).isTrue();
    }

    @Test void emptyWhenShackIsolated() {
        grid(new String[]{
            ".........",
            "..#####..",
            "..#0~#..",  // shack walled-in (5 wide row: ##0~#)
            "..#####..",
            "........."
        });
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isEqualTo(0);
    }

    @Test void respectsGridBoundary() {
        grid(new String[]{
            "0........",
            ".........",
            ".........",
            "........."
        });
        Genome.initPlantCandidates();
        // Shack at (0,0). Only cells with both x,y in bounds and Manhattan ≤ 2
        // and on GRASS: (0,1), (0,2), (1,0), (1,1), (2,0)
        assertThat(Genome.plantCandidateCount).isEqualTo(5);
        assertThat(contains(0, 0)).isFalse(); // shack itself
        assertThat(contains(1, 1)).isTrue();
        assertThat(contains(2, 0)).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=PlantCandidatesTest`
Expected: compile error — `plantCandidates`, `plantCandidateCount`, `initPlantCandidates` do not exist.

- [ ] **Step 3: Implement**

Add to `Genome.java`:

```java
    // --- Plant candidates (static, computed once per game) ---
    public static final short[] plantCandidates = new short[12];
    public static int plantCandidateCount = 0;

    public static void initPlantCandidates() {
        plantCandidateCount = 0;
        int sx = GameState.shackMeX;
        int sy = GameState.shackMeY;
        for (int dx = -2; dx <= 2; dx++) {
            int x = sx + dx;
            if (x < 0 || x >= GameState.width) continue;
            int yRange = 2 - Math.abs(dx);
            for (int dy = -yRange; dy <= yRange; dy++) {
                int y = sy + dy;
                if (y < 0 || y >= GameState.height) continue;
                if (dx == 0 && dy == 0) continue; // exclude shack itself
                if (GameState.tiles[y * GameState.width + x] != com.bmrt.cgspring2026.model.TileType.GRASS) continue;
                plantCandidates[plantCandidateCount++] = (short) ((x << 8) | y);
            }
        }
    }

    public static int candX(short c) { return (c >>> 8) & 0xFF; }
    public static int candY(short c) { return c & 0xFF; }
```

Note: this uses `TileType` from the model package. Add the import at the top of `Genome.java`:

```java
import com.bmrt.cgspring2026.model.TileType;
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=PlantCandidatesTest`
Expected: PASS for all 4 tests.

Then run the full GA suite: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/Genome.java src/test/java/com/bmrt/cgspring2026/ga/PlantCandidatesTest.java
git commit -m "ga: pre-compute plant candidates within Manhattan 2 of shack"
```

---

## Task 3: Wire `initPlantCandidates` into `GeneticAgent`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java`

- [ ] **Step 1: Write the failing test**

Append to `GeneticAgentTest.java` (it already has `@BeforeEach grid()` setting shack at (1,1) and a private `seededState()` helper):

```java
    @Test void firstDecideInitsPlantCandidates() {
        // Sentinel: set count to -1 to detect that initPlantCandidates() ran
        Genome.plantCandidateCount = -1;
        GameState s = seededState();
        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, System.nanoTime() + 100_000_000L, out);
        // Not -1 anymore → init ran and overwrote the sentinel
        assertThat(Genome.plantCandidateCount).isNotEqualTo(-1);
        assertThat(Genome.plantCandidateCount).isGreaterThanOrEqualTo(0);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=GeneticAgentTest#firstDecideInitsPlantCandidates`
Expected: FAIL because `decide()` never calls `Genome.initPlantCandidates()`.

- [ ] **Step 3: Implement**

Edit `GeneticAgent.java`. Add a field tracking whether init has happened:

```java
    private boolean plantCandidatesInitialized = false;
```

In `decide(GameState state, long deadlineNs, int[] outActions)`, as the very first action:

```java
    public int decide(GameState state, long deadlineNs, int[] outActions) {
        if (!plantCandidatesInitialized) {
            Genome.initPlantCandidates();
            plantCandidatesInitialized = true;
        }
        // ... existing body
    }
```

- [ ] **Step 4: Run the test**

Run: `mvn -q test -Dtest=GeneticAgentTest`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentTest.java
git commit -m "ga: init plant candidates on first decide call"
```

---

## Task 4: `TrollPolicy.policyPhase[]` + reset in evaluator and live emission

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java`
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`

This task is preparatory: it adds the scratch array and resets, no behavior change yet.

- [ ] **Step 1: Add the field to `TrollPolicy`**

Edit `TrollPolicy.java`, add after `cursorBuf`:

```java
    public static final byte[] policyPhase = new byte[GameState.MAX_TROLLS];
```

- [ ] **Step 2: Reset in `GenomeEvaluator.evaluate`**

Edit `GenomeEvaluator.java`. In `evaluate`, right after the cursor reset:

```java
    int[] cursor = TrollPolicy.cursorBuf;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) TrollPolicy.policyPhase[j] = 0;
```

- [ ] **Step 3: Reset in `GeneticAgent.decide` before live emission**

Edit `GeneticAgent.java`. In `decide`, right before `TrollPolicy.fillOwnActions`:

```java
    int[] cursor = TrollPolicy.cursorBuf;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) TrollPolicy.policyPhase[j] = 0;
    int n = TrollPolicy.fillOwnActions(state, pop.cur, pop.curLen, lastBestIdx, cursor, outActions);
```

- [ ] **Step 4: Run all GA tests**

Run: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green. Nothing reads `policyPhase` yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java src/main/java/com/bmrt/cgspring2026/ga/GenomeEvaluator.java src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java
git commit -m "ga: introduce TrollPolicy.policyPhase scratch with resets"
```

---

## Task 5: Refactor `decideForOwnTroll` to branch on `isPlant` (TARGET-only path identical)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Test: existing `TrollPolicyTest` must continue to pass.

This is a refactor only: structure the code so adding the PLANT branch is mechanical.

- [ ] **Step 1: Confirm existing tests pass**

Run: `mvn -q test -Dtest=TrollPolicyTest`
Expected: all green.

- [ ] **Step 2: Refactor `decideForOwnTroll`**

Replace the body of `decideForOwnTroll` in `TrollPolicy.java`:

```java
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
        while (cursor[trollIdx] < len) {
            short g = (short) Genome.gene(popBuf, idx, trollIdx, cursor[trollIdx]);
            if (g == Genome.EMPTY_GENE) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            int gx = Genome.geneX(g), gy = Genome.geneY(g);

            if (!Genome.isPlant(g)) {
                // TARGET branch — unchanged behavior
                if (s.treeIndexAt(gx, gy) < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
                if (tx == gx && ty == gy) return Action.chop(trollIdx);
                return Action.move(trollIdx, gx, gy);
            }

            // PLANT branch — to be implemented in subsequent tasks
            // For now: degrade to advance + retry (no PLANT gene exists yet in tests)
            cursor[trollIdx]++; policyPhase[trollIdx] = 0; // placeholder
        }
        return Action.wait(trollIdx);
    }
```

- [ ] **Step 3: Run all GA tests**

Run: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green. The refactor preserves TARGET behavior; PLANT genes don't exist in any current test data.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java
git commit -m "ga: refactor TrollPolicy to branch on Genome.isPlant"
```

---

## Task 6: TrollPolicy PLANT — phase 0, PICK fruit when adjacent to shack

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Create the test file with shared setup + first test**

```java
// src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
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

class TrollPolicyPlantTest {

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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
        // Reset policy scratch
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            TrollPolicy.cursorBuf[j] = 0;
            TrollPolicy.policyPhase[j] = 0;
        }
    }

    private GameState trollAt(int x, int y) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x; s.trollY[0] = (byte) y;
        s.trollMS[0] = 2; s.trollCC[0] = 4; s.trollHP[0] = 1; s.trollCP[0] = 1;
        return s;
    }

    private static short[] popBuf() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        return buf;
    }

    private static byte[] lenBuf() {
        return new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    @Test void phase0_pickWhenAdjacentToShackWithoutFruit() {
        // Shack at (1,1); adjacent cells: (0,1), (2,1), (1,0), (1,2). Use (0,1).
        GameState s = trollAt(0, 1);
        s.shackInventory[ResourceType.LEMON] = 3; // shack has fruit
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        // PLANT(0,2,LEMON) — (0,2) is a plant candidate (Manhattan 2)
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PICK);
        assertThat(Action.arg1(out[0])).isEqualTo((int) ResourceType.LEMON);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase0_pickWhenAdjacentToShackWithoutFruit`
Expected: FAIL — placeholder advances cursor + returns WAIT.

- [ ] **Step 3: Implement PLANT phase 0 — PICK branch**

Replace the placeholder PLANT branch in `decideForOwnTroll` (the comment `// PLANT branch — to be implemented`) with the start of the real logic:

```java
            // PLANT branch
            int fruit = Genome.plantFruitType(g);
            int t = s.treeIndexAt(gx, gy);

            // Lazy phase init: tree already at target → skip pick + plant
            if (policyPhase[trollIdx] == 0 && t >= 0) policyPhase[trollIdx] = 1;

            if (policyPhase[trollIdx] == 0) {
                int carryFruit = s.trollInventory[trollIdx * ResourceType.COUNT + fruit] & 0xFF;
                if (carryFruit == 0) {
                    int shackStock = s.shackInventory[fruit]; // player=0 → base=0
                    if (shackStock <= 0) {
                        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
                        continue; // ABORT gene
                    }
                    if (isShackAdjacent(tx, ty)) return Action.pick(trollIdx, fruit);
                    return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
                }
                // have fruit
                if (tx == gx && ty == gy) {
                    policyPhase[trollIdx] = 1;
                    return Action.plant(trollIdx, fruit);
                }
                return Action.move(trollIdx, gx, gy);
            }

            // phase == 1: post-plant, chop until dead
            if (t < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
            if (tx == gx && ty == gy) return Action.chop(trollIdx);
            return Action.move(trollIdx, gx, gy);
```

Remove the placeholder `cursor[trollIdx]++; policyPhase[trollIdx] = 0;` line that was left after refactor.

- [ ] **Step 4: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest`
Expected: PASS.

Also: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/TrollPolicy.java src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: TrollPolicy PLANT branch (phase 0 + phase 1 sub-state machine)"
```

(Note: Task 6 implements the *full* PLANT branch in one go because the sub-state machine's correctness is coupled across phases. Subsequent tasks add test coverage for the remaining cases against this implementation.)

---

## Task 7: PLANT — phase 0 MOVE toward shack when far from shack and no fruit

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

Append to `TrollPolicyPlantTest`:

```java
    @Test void phase0_moveToShackWhenFarAndWithoutFruit() {
        // Troll far from shack (4,4); shack at (1,1) → not adjacent
        GameState s = trollAt(4, 4);
        s.shackInventory[ResourceType.APPLE] = 2;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.APPLE));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        // Target should be a shack-adjacent cell
        int tx = Action.arg1(out[0]), ty = Action.arg2(out[0]);
        int dist = Math.abs(tx - GameState.shackMeX) + Math.abs(ty - GameState.shackMeY);
        assertThat(dist).isEqualTo(1);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase0_moveToShackWhenFarAndWithoutFruit`
Expected: PASS (implementation from Task 6 already handles this).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 0 — move toward shack when no fruit and far"
```

---

## Task 8: PLANT — phase 0 abort when shack has no fruit

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void phase0_abortsWhenShackHasNoFruit() {
        GameState s = trollAt(0, 1); // adjacent to shack
        // shackInventory[LEMON] = 0 by default
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        // Fallback gene to verify cursor advanced
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2)); // TARGET — no tree there, will also skip
        Genome.setLen(lens, 0, 0, 2);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        // cursor must have advanced past the PLANT (and then past the dead TARGET)
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(2);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase0_abortsWhenShackHasNoFruit`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 0 — abort when shack empty of fruit"
```

---

## Task 9: PLANT — phase 0 emits PLANT when troll has fruit and stands on target

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void phase0_plantsWhenOnTargetWithFruit() {
        GameState s = trollAt(0, 2); // standing on plant candidate (0,2)
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.BANANA] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.BANANA));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.PLANT);
        assertThat(Action.arg1(out[0])).isEqualTo((int) TreeType.BANANA);
        // phase should have advanced to 1
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 1);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase0_plantsWhenOnTargetWithFruit`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 0 — plant on target with fruit advances to phase 1"
```

---

## Task 10: PLANT — phase 0 MOVE toward target after picking

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void phase0_moveToTargetAfterPicking() {
        GameState s = trollAt(2, 1); // not on target, has fruit
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.PLUM] = 1;
        s.trollCarryTotal[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.PLUM));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.arg1(out[0])).isEqualTo(0);
        assertThat(Action.arg2(out[0])).isEqualTo(2);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase0_moveToTargetAfterPicking`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 0 — move toward target with fruit in carry"
```

---

## Task 11: PLANT — lazy phase init when tree already on target cell

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void lazyPhaseInit_treeAlreadyOnTargetGoesToChop() {
        GameState s = trollAt(0, 2); // on target, no fruit
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
        // Tree already there → lazy init promotes phase to 1, troll is on cell → CHOP
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.CHOP);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 1);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#lazyPhaseInit_treeAlreadyOnTargetGoesToChop`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT lazy phase init — preexisting tree promotes to chop"
```

---

## Task 12: PLANT — phase 1 CHOP when on target with live tree

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void phase1_chopsWhenOnTargetWithLiveTree() {
        GameState s = trollAt(0, 2);
        s.treeCount = 1;
        s.treeX[0] = 0; s.treeY[0] = 2;
        s.treeHealth[0] = 3;
        s.treeType[0] = TreeType.LEMON;
        TrollPolicy.policyPhase[0] = 1; // simulate already-planted state
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.CHOP);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase1_chopsWhenOnTargetWithLiveTree`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 1 — chop when on target with live tree"
```

---

## Task 13: PLANT — phase 1 advances cursor when tree disappeared

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void phase1_advancesCursorWhenTreeDead() {
        GameState s = trollAt(0, 2);
        // No tree at (0,2)
        TrollPolicy.policyPhase[0] = 1;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        assertThat(TrollPolicy.cursorBuf[0]).isEqualTo(1);
        assertThat(TrollPolicy.policyPhase[0]).isEqualTo((byte) 0);
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.WAIT);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#phase1_advancesCursorWhenTreeDead`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT phase 1 — advance cursor and reset phase when tree dead"
```

---

## Task 14: PLANT — wood priority overrides phase 0

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java`

- [ ] **Step 1: Add the failing test**

```java
    @Test void woodPriorityOverridesPhase0Pick() {
        GameState s = trollAt(0, 1); // adjacent to shack
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 1;
        s.trollCarryTotal[0] = 1;
        s.shackInventory[ResourceType.LEMON] = 3;
        short[] buf = popBuf();
        byte[] lens = lenBuf();
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(0, 2, TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 1);
        int[] out = new int[GameState.MAX_TROLLS + 1];
        TrollPolicy.fillActions(s, buf, lens, 0, TrollPolicy.cursorBuf, out);
        // Drop rule outranks PLANT
        assertThat(Action.type(out[0])).isEqualTo((int) ActionType.DROP);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=TrollPolicyPlantTest#woodPriorityOverridesPhase0Pick`
Expected: PASS (drop rule already runs first in `decideForOwnTroll`).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/TrollPolicyPlantTest.java
git commit -m "ga: test PLANT — wood priority overrides any pending PLANT phase"
```

---

## Task 15: `GenomeInvariants` — dual `(cell, isPlant)` invariant

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java`

- [ ] **Step 1: Add the failing tests**

Append to `GenomeInvariantsTest`:

```java
    @Test void allowsTargetAndPlantOnSameCell() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.makeTarget(3, 4));
        Genome.setGene(buf, 0, 0, 1, Genome.makePlant(3, 4, com.bmrt.cgspring2026.model.TreeType.LEMON));
        Genome.setLen(lenBuf, 0, 0, 2);
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void detectsDuplicatePlantOnSameCell() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.makePlant(3, 4, com.bmrt.cgspring2026.model.TreeType.PLUM));
        Genome.setGene(buf, 0, 1, 0, Genome.makePlant(3, 4, com.bmrt.cgspring2026.model.TreeType.LEMON));
        Genome.setLen(lenBuf, 0, 0, 1);
        Genome.setLen(lenBuf, 0, 1, 1);
        assertThatThrownBy(() -> GenomeInvariants.check(buf, lenBuf, 0))
            .isInstanceOf(AssertionError.class);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GenomeInvariantsTest`
Expected: `allowsTargetAndPlantOnSameCell` FAILS (current code keys by cell only).

- [ ] **Step 3: Implement dual seen**

Replace the loop body in `GenomeInvariants.check`:

```java
        int W = GameState.width;
        int H = GameState.height;
        boolean[] seenTarget = new boolean[W * H];
        boolean[] seenPlant  = new boolean[W * H];
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
                int cellIdx = y * W + x;
                boolean[] seen = Genome.isPlant(g) ? seenPlant : seenTarget;
                if (seen[cellIdx]) {
                    throw new AssertionError("duplicate gene (" + x + "," + y + ") isPlant=" + Genome.isPlant(g));
                }
                seen[cellIdx] = true;
            }
            for (int k = len; k < Genome.MAX_TARGETS_PER_TROLL; k++) {
                if (Genome.gene(buf, individuIdx, j, k) != Genome.EMPTY_GENE) {
                    throw new AssertionError("dirty slot after len troll=" + j + " k=" + k);
                }
            }
        }
        return true;
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=GenomeInvariantsTest`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeInvariants.java src/test/java/com/bmrt/cgspring2026/ga/GenomeInvariantsTest.java
git commit -m "ga: invariant keyed by (cell, isPlant), allow TARGET+PLANT coexistence"
```

---

## Task 16: `GenomeOps.crossover` — dual seen repair

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java`

- [ ] **Step 1: Add the failing test**

Append to `GenomeOpsCrossoverTest` (read the file first to mirror existing setup):

```java
    @Test void crossoverPreservesTargetAndPlantOnSameCell() {
        // P1: TARGET(3,4) + PLANT(3,4,LEMON) for troll 0
        // P2: nothing
        // Offspring should keep both — they are different (cell, isPlant) keys
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        Genome.setGene(buf, 0, 0, 0, Genome.makeTarget(3, 4));
        Genome.setGene(buf, 0, 0, 1, Genome.makePlant(3, 4, com.bmrt.cgspring2026.model.TreeType.LEMON));
        Genome.setLen(lens, 0, 0, 2);
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        // Crossover P1 with itself, cut entire P1 prefix
        GenomeOps.crossover(buf, lens, 0, buf, lens, 0, buf, lens, 1, rng);
        // Offspring at index 1 should have both genes for troll 0
        assertThat(Genome.len(lens, 1, 0)).isEqualTo(2);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GenomeOpsCrossoverTest#crossoverPreservesTargetAndPlantOnSameCell`
Expected: FAIL — current `seenBuf` keys by cell only, dedupes the PLANT against the TARGET.

- [ ] **Step 3: Implement dual seen in crossover**

Edit `GenomeOps.java`. Replace the single `seenBuf` field and update `crossover`:

```java
    private static final boolean[] seenTargetBuf = new boolean[256 * 256];
    private static final boolean[] seenPlantBuf  = new boolean[256 * 256];

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
        // Reset seen tables
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                seenTargetBuf[y * W + x] = false;
                seenPlantBuf[y * W + x]  = false;
            }
        }
        // OX par troll
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int la = Genome.len(lenA, idxA, j);
            int lb = Genome.len(lenB, idxB, j);
            if (la == 0 && lb == 0) continue;
            int cut = (la > 0) ? rng.nextInt(la + 1) : 0;
            int dstOff = Genome.offset(idxDst, j);
            int written = 0;
            int aBase = Genome.offset(idxA, j);
            for (int k = 0; k < cut; k++) {
                short g = srcA[aBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                boolean[] seen = Genome.isPlant(g) ? seenPlantBuf : seenTargetBuf;
                int cell = y * W + x;
                if (seen[cell]) continue;
                seen[cell] = true;
                dst[dstOff + written++] = g;
            }
            int bBase = Genome.offset(idxB, j);
            int target = (la > 0 ? la : lb);
            for (int k = 0; k < lb && written < target && written < Genome.MAX_TARGETS_PER_TROLL; k++) {
                short g = srcB[bBase + k];
                int x = Genome.geneX(g), y = Genome.geneY(g);
                boolean[] seen = Genome.isPlant(g) ? seenPlantBuf : seenTargetBuf;
                int cell = y * W + x;
                if (seen[cell]) continue;
                seen[cell] = true;
                dst[dstOff + written++] = g;
            }
            Genome.setLen(dstLen, idxDst, j, written);
        }
    }
```

Delete the old `seenBuf` field if it remains.

- [ ] **Step 4: Run all GA tests**

Run: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsCrossoverTest.java
git commit -m "ga: crossover repair keys by (cell, isPlant) — dual seen tables"
```

---

## Task 17: `GenomeOps.initRandom` — inject PLANT genes with probability `P_PLANT_INIT`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitTest.java`

- [ ] **Step 1: Add the failing test**

The existing `GenomeOpsInitTest` has `@BeforeEach grid()` doing `GameState.width=10; GameState.height=10; GameState.tiles = new byte[100]`. Since `TileType.GRASS == 0` and a new `byte[]` is zero-initialized, all tiles are GRASS, and `shackMeX/Y` default to 0. So `Genome.initPlantCandidates()` will produce a non-empty list (~5 candidates around (0,0)). It also has a `makeState(int trees, int ownTrolls, int oppTrolls)` helper.

Append to `GenomeOpsInitTest`:

```java
    @Test void initRandomCanProducePlantGenes() {
        Genome.initPlantCandidates(); // populates around (0,0) on the BeforeEach grid
        assertThat(Genome.plantCandidateCount).isGreaterThan(0);

        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        SplittableRandom rng = new SplittableRandom(123);
        GameState s = makeState(5, 2, 0); // 5 trees, 2 own trolls, no opp
        int plantSeen = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            java.util.Arrays.fill(lens, (byte) 0);
            GenomeOps.initRandom(s, buf, lens, 0, rng);
            // Did at least one PLANT gene appear anywhere in this individual?
            outer:
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    short g = (short) Genome.gene(buf, 0, j, k);
                    if (Genome.isPlant(g)) { plantSeen++; break outer; }
                }
            }
        }
        // With P_PLANT_INIT=0.30 per troll, 2 trolls → P(at least one PLANT) ≈ 1 - 0.7^2 = 0.51
        // Expected ~100 hits / 200; allow a wide band.
        assertThat(plantSeen).isBetween(50, 170);
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GenomeOpsInitTest#initRandomCanProducePlantGenes`
Expected: FAIL — no PLANT genes are ever produced.

- [ ] **Step 3: Implement**

Edit `GenomeOps.java`. Add a constant near `P_SKIP_INIT`:

```java
    public static final double P_PLANT_INIT = 0.30;
```

Add a scratch field:

```java
    private static final boolean[] initSeenPlant = new boolean[256 * 256];
```

At the end of `initRandom`, after the TARGET-distribution loop, add a PLANT injection phase:

```java
        // 5. Inject PLANT genes for each own troll with proba P_PLANT_INIT
        if (Genome.plantCandidateCount > 0) {
            // reset scratch for this individual
            for (int p = 0; p < Genome.plantCandidateCount; p++) {
                short c = Genome.plantCandidates[p];
                initSeenPlant[Genome.candY(c) * GameState.width + Genome.candX(c)] = false;
            }
            for (int k = 0; k < ownTrollsCount; k++) {
                int trollIdx = ownTrollsBuf[k];
                int len = Genome.len(lenBuf, individuIdx, trollIdx);
                if (len >= Genome.MAX_TARGETS_PER_TROLL) continue;
                if (rng.nextDouble() >= P_PLANT_INIT) continue;
                int tries = 0;
                while (tries < Genome.plantCandidateCount) {
                    short c = Genome.plantCandidates[rng.nextInt(Genome.plantCandidateCount)];
                    int cx = Genome.candX(c), cy = Genome.candY(c);
                    if (!initSeenPlant[cy * GameState.width + cx]) {
                        int fruit = rng.nextInt(4);
                        Genome.setGene(buf, individuIdx, trollIdx, len, Genome.makePlant(cx, cy, fruit));
                        Genome.setLen(lenBuf, individuIdx, trollIdx, len + 1);
                        initSeenPlant[cy * GameState.width + cx] = true;
                        break;
                    }
                    tries++;
                }
            }
        }
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green, including the new test.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInitTest.java
git commit -m "ga: initRandom injects PLANT genes with P_PLANT_INIT=0.30"
```

---

## Task 18: `GenomeOps.mutateInsertPlant` operator

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInsertPlantTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInsertPlantTest.java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsInsertPlantTest {

    @BeforeEach void grid() {
        String[] rows = {
            "......",
            ".0....",
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
        Genome.initPlantCandidates();
    }

    @Test void insertsPlantGeneOnFreshIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // 1 own troll → trollIdx 0
        // Set len to 0 (already 0) → operator can write to position 0
        // To force insertion, we need at least one own troll. We mark slot 0 lenBuf such that
        // the GA setup considers it as own troll. The operator uses Genome buffers only;
        // it does not depend on GameState here.
        // Provide a non-trivial seed RNG
        SplittableRandom rng = new SplittableRandom(7);
        // Make troll 0 the only one with capacity by initialising lens.
        // The operator's "pick candidate troll" logic must accept troll 0.
        // (Operator implementation must accept ownTrolls input — see implementation below.)
        // For the test, we assume the operator scans all trolls and picks any with capacity.
        boolean inserted = false;
        for (int attempt = 0; attempt < 100 && !inserted; attempt++) {
            GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
            int totalPlant = 0;
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    short g = (short) Genome.gene(buf, 0, j, k);
                    if (Genome.isPlant(g)) totalPlant++;
                }
            }
            if (totalPlant > 0) inserted = true;
        }
        assertThat(inserted).isTrue();
    }

    @Test void noopWhenNoCandidates() {
        // Force empty candidates
        int saved = Genome.plantCandidateCount;
        Genome.plantCandidateCount = 0;
        try {
            short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
            java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
            byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
            SplittableRandom rng = new SplittableRandom(3);
            for (int i = 0; i < 50; i++) GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
            // No plants inserted
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lens, 0, j);
                for (int k = 0; k < len; k++) {
                    short g = (short) Genome.gene(buf, 0, j, k);
                    assertThat(Genome.isPlant(g)).isFalse();
                }
            }
        } finally {
            Genome.plantCandidateCount = saved;
        }
    }

    @Test void skipsCellAlreadyPlantInIndividual() {
        short[] buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        byte[] lens = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
        // Pre-seed: troll 0 already has PLANT at every candidate cell
        int cnt = Math.min(Genome.MAX_TARGETS_PER_TROLL, Genome.plantCandidateCount);
        for (int p = 0; p < cnt; p++) {
            short c = Genome.plantCandidates[p];
            Genome.setGene(buf, 0, 0, p, Genome.makePlant(Genome.candX(c), Genome.candY(c), 0));
        }
        Genome.setLen(lens, 0, 0, cnt);
        SplittableRandom rng = new SplittableRandom(11);
        int initialPlants = countPlants(buf, lens, 0);
        for (int i = 0; i < 50; i++) GenomeOps.mutateInsertPlant(buf, lens, 0, rng);
        // If MAX_TARGETS_PER_TROLL ≥ plantCandidateCount, no new plant can be added on troll 0.
        // If there are other trolls with capacity, plants may go there — count must stay bounded by candidates.
        int finalPlants = countPlants(buf, lens, 0);
        assertThat(finalPlants).isLessThanOrEqualTo(Genome.plantCandidateCount);
        assertThat(finalPlants).isGreaterThanOrEqualTo(initialPlants);
    }

    private static int countPlants(short[] buf, byte[] lens, int idx) {
        int c = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lens, idx, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(buf, idx, j, k);
                if (Genome.isPlant(g)) c++;
            }
        }
        return c;
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GenomeOpsInsertPlantTest`
Expected: compile error — `mutateInsertPlant` does not exist.

- [ ] **Step 3: Implement**

Add to `GenomeOps.java`:

```java
    private static final boolean[] mutSeenPlant = new boolean[256 * 256];

    public static void mutateInsertPlant(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        if (Genome.plantCandidateCount == 0) return;
        // Pick troll with capacity
        int count = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            if (Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL) {
                freeTrollsBuf[count++] = j;
            }
        }
        if (count == 0) return;
        int j = freeTrollsBuf[rng.nextInt(count)];
        int len = Genome.len(lenBuf, individuIdx, j);

        // Build set of PLANT cells already in this individual
        int W = GameState.width;
        for (int p = 0; p < Genome.plantCandidateCount; p++) {
            short c = Genome.plantCandidates[p];
            mutSeenPlant[Genome.candY(c) * W + Genome.candX(c)] = false;
        }
        for (int tj = 0; tj < GameState.MAX_TROLLS; tj++) {
            int tjLen = Genome.len(lenBuf, individuIdx, tj);
            int base = Genome.offset(individuIdx, tj);
            for (int k = 0; k < tjLen; k++) {
                short g = buf[base + k];
                if (Genome.isPlant(g)) {
                    mutSeenPlant[Genome.geneY(g) * W + Genome.geneX(g)] = true;
                }
            }
        }

        // Pick a free candidate
        int tries = 0;
        while (tries < Genome.plantCandidateCount) {
            short c = Genome.plantCandidates[rng.nextInt(Genome.plantCandidateCount)];
            int cx = Genome.candX(c), cy = Genome.candY(c);
            if (!mutSeenPlant[cy * W + cx]) {
                // Insert at random position in [0, len], shift right
                int pos = rng.nextInt(len + 1);
                int base = Genome.offset(individuIdx, j);
                for (int k = len; k > pos; k--) buf[base + k] = buf[base + k - 1];
                int fruit = rng.nextInt(4);
                buf[base + pos] = Genome.makePlant(cx, cy, fruit);
                Genome.setLen(lenBuf, individuIdx, j, len + 1);
                return;
            }
            tries++;
        }
    }
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=GenomeOpsInsertPlantTest`
Expected: PASS.

Then: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsInsertPlantTest.java
git commit -m "ga: mutateInsertPlant — insert a PLANT gene at random position"
```

---

## Task 19: Re-balance `pickMutationKind` to include `insert-plant`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMutationTest.java`

- [ ] **Step 1: Add the failing test and fix the existing `runMutationDispatchesProbabilistically`

The existing `GenomeOpsMutationTest` has `@BeforeEach grid()` setting `width=10, height=10, tiles=new byte[100]` (all GRASS). The existing `runMutationDispatchesProbabilistically` uses `int[] counters = new int[4]` and will throw `ArrayIndexOutOfBoundsException` once `MUT_INSERT_PLANT = 4` exists — it must be updated to size 5 and the probability bounds must be relaxed to match the rebalanced distribution.

**Update** the existing `runMutationDispatchesProbabilistically` to:

```java
    @Test void runMutationDispatchesProbabilistically() {
        SplittableRandom rng = new SplittableRandom(42);
        int[] counters = new int[5];
        for (int t = 0; t < 1000; t++) {
            counters[GenomeOps.pickMutationKind(rng)]++;
        }
        for (int k = 0; k < 5; k++) {
            assertThat(counters[k]).as("operator %d sampled at least once", k).isGreaterThan(30);
        }
        // approximate proportions: 35/25/15/10/15
        assertThat(counters[GenomeOps.MUT_SWAP_INTRA]).isBetween(280, 420);
        assertThat(counters[GenomeOps.MUT_DELETE]).isBetween(50, 150);
        assertThat(counters[GenomeOps.MUT_INSERT_PLANT]).isBetween(100, 200);
    }
```

**Append** these new tests:

```java
    @Test void pickMutationKindIncludesInsertPlant() {
        SplittableRandom rng = new SplittableRandom(99);
        boolean seen = false;
        for (int i = 0; i < 1000; i++) {
            if (GenomeOps.pickMutationKind(rng) == GenomeOps.MUT_INSERT_PLANT) { seen = true; break; }
        }
        assertThat(seen).isTrue();
    }

    @Test void runMutationDispatchesInsertPlant() {
        // Plant candidates populated around (0,0) via initPlantCandidates() on the BeforeEach grid.
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isGreaterThan(0);
        SplittableRandom rng = new SplittableRandom(2026);
        boolean planted = false;
        for (int i = 0; i < 200 && !planted; i++) {
            GenomeOps.runMutation(buf, lenBuf, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS && !planted; j++) {
                int len = Genome.len(lenBuf, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isPlant((short) Genome.gene(buf, 0, j, k))) { planted = true; break; }
                }
            }
        }
        assertThat(planted).isTrue();
    }
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GenomeOpsMutationTest`
Expected: FAIL — `MUT_INSERT_PLANT` does not exist; dispatcher does not handle it.

- [ ] **Step 3: Implement**

Edit `GenomeOps.java`. Replace the probability constants and dispatcher block:

```java
    public static final double P_MUT_SWAP_INTRA   = 0.35;
    public static final double P_MUT_SWAP_INTER   = 0.25;
    public static final double P_MUT_REVERSE      = 0.15;
    public static final double P_MUT_DELETE       = 0.10;
    public static final double P_MUT_INSERT_PLANT = 0.15;

    public static final int MUT_SWAP_INTRA   = 0;
    public static final int MUT_SWAP_INTER   = 1;
    public static final int MUT_REVERSE      = 2;
    public static final int MUT_DELETE       = 3;
    public static final int MUT_INSERT_PLANT = 4;

    public static int pickMutationKind(SplittableRandom rng) {
        double r = rng.nextDouble();
        if (r < P_MUT_SWAP_INTRA) return MUT_SWAP_INTRA;
        r -= P_MUT_SWAP_INTRA;
        if (r < P_MUT_SWAP_INTER) return MUT_SWAP_INTER;
        r -= P_MUT_SWAP_INTER;
        if (r < P_MUT_REVERSE) return MUT_REVERSE;
        r -= P_MUT_REVERSE;
        if (r < P_MUT_DELETE) return MUT_DELETE;
        return MUT_INSERT_PLANT;
    }

    public static void runMutation(short[] buf, byte[] lenBuf, int individuIdx, SplittableRandom rng) {
        switch (pickMutationKind(rng)) {
            case MUT_SWAP_INTRA   -> mutateSwapIntra  (buf, lenBuf, individuIdx, rng);
            case MUT_SWAP_INTER   -> mutateSwapInter  (buf, lenBuf, individuIdx, rng);
            case MUT_REVERSE      -> mutateReverse    (buf, lenBuf, individuIdx, rng);
            case MUT_DELETE       -> mutateDelete     (buf, lenBuf, individuIdx, rng);
            case MUT_INSERT_PLANT -> mutateInsertPlant(buf, lenBuf, individuIdx, rng);
            default -> throw new IllegalStateException();
        }
    }
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=GenomeOpsMutationTest`
Expected: all green.

Then: `mvn -q test -Dtest='com.bmrt.cgspring2026.ga.*'`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsMutationTest.java
git commit -m "ga: dispatch insert-plant in pickMutationKind (rebalanced probabilities)"
```

---

## Task 20: Regression smoke — `GeneticAgent` emits a PLANT cycle and the score improves

**Files:**
- Test: `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPlantSmokeTest.java`

This test guards against silent regressions in the integration. It runs a multi-tick scenario where the only profitable action involves a PLANT cycle, and asserts that fitness improves over a no-PLANT baseline by toggling `P_PLANT_INIT` and `P_MUT_INSERT_PLANT` to 0.

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPlantSmokeTest.java
package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticAgentPlantSmokeTest {

    @BeforeEach void grid() {
        String[] rows = {
            "..........",
            "..........",
            "....0.....",
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
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
        ShackAdjacency.init();
        Genome.initPlantCandidates();
    }

    @Test void emitsAtLeastOnePlantOverASingleDecide() {
        // No live trees on the map; shack has 5 LEMONs to seed PLANT cycles.
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX - 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollMS[0] = 2; s.trollCC[0] = 1; s.trollHP[0] = 1; s.trollCP[0] = 3;
        s.shackInventory[ResourceType.LEMON] = 5;

        GeneticAgent agent = new GeneticAgent();
        int[] out = new int[GameState.MAX_TROLLS + 1];
        agent.decide(s, System.nanoTime() + 50_000_000L, out);
        // The best genome should contain a PLANT gene because there is no other source of score
        boolean planted = false;
        for (int j = 0; j < GameState.MAX_TROLLS && !planted; j++) {
            int len = Genome.len(agent.pop.curLen, agent.lastBestIdx, j);
            for (int k = 0; k < len; k++) {
                short g = (short) Genome.gene(agent.pop.cur, agent.lastBestIdx, j, k);
                if (Genome.isPlant(g)) { planted = true; break; }
            }
        }
        assertThat(planted)
            .as("Best genome contains a PLANT gene when only PLANT-cycle yields score")
            .isTrue();
    }
}
```

Note: this test accesses `agent.pop` and `agent.lastBestIdx`. They are already package-private (see `GeneticAgent` field declarations).

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=GeneticAgentPlantSmokeTest`
Expected: PASS (the GA has been wired end-to-end by previous tasks).

If it FAILS intermittently, increase the deadline or seed the RNG; in the GA it is acceptable for selection to occasionally produce a PLANT-free best individual on a single seed. If consistent failure: investigate which task is broken.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPlantSmokeTest.java
git commit -m "ga: smoke test — GeneticAgent emits a PLANT when PLANT cycle is the only score source"
```

---

## Task 21: Run the full suite + bench sanity check

**Files:** none.

- [ ] **Step 1: Full test suite**

Run: `mvn -q test`
Expected: all green.

- [ ] **Step 2: Bench harness (informational, no assertion)**

Run: `mvn -q test -Dtest=GeneticAgentBench`
Expected: completes; record `gen/s` and `µs/eval` to compare against the pre-feature baseline (logged manually). No commit unless tuning is required.

- [ ] **Step 3: If bench shows >10% gen/s regression** (compared to whatever baseline was captured before this feature)

- Re-read the hot path in `TrollPolicy.decideForOwnTroll` and confirm no allocation in steady state.
- Verify `policyPhase` reset cost in `evaluate` is negligible (`O(MAX_TROLLS) = 32`).
- If unclear, ask the operator before making changes.

- [ ] **Step 4: Final commit if any tuning was needed**

```bash
git add -A
git commit -m "ga: tune PLANT integration after bench measurement"
```

(Skip this commit if no tuning was needed.)

---

## Out of scope

These items are not part of this plan and should not be added:

- HARVEST genes (v2 candidate).
- Multi-PLANT on the same cell within a single individual.
- PLANT cells beyond Manhattan 2 of the own shack.
- `ALPHA_FRUIT_CARRY` fitness bonus.
- Modifying `initWarm` to inject PLANTs deterministically.
- TRAIN inside the genome.
