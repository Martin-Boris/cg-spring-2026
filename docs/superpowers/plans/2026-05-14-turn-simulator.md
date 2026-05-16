# Turn Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a zero-allocation, mutable, in-place `Simulator.tick(GameState, int[] actions, int n)` that advances a `GameState` by one turn applying any combination of actions from any troll/player, mirroring the reference behaviour of `com.bmrt.cgspring2026.gamesourcecode.Board#tick`.

**Architecture:** A single utility class `simulation.Simulator` exposes static methods, one per priority phase, called in fixed order from `tick(...)`. Each phase scans the input `actions[]` linearly, filters by `Action.type`, and mutates the `GameState` SoA arrays directly. Scratch buffers (move resolution, target lists, dead-tree bitmap) are lazy-initialised static fields sized for max grid / max trolls. After the action phases, plants are ticked (`cooldown--`, growth, fruit production) and dead trees compacted via swap-with-last. Validation mirrors the engine's parse-time guards, applied at apply-time on the SoA fields.

**Tech Stack:** Java 21, Maven, JUnit 5.6.3 + AssertJ 3.18.1 (existing test infrastructure).

---

## Phase ordering (mirrors `Task.getTaskPriority()` in the reference engine)

| Order | Phase    | Notes                                                                                  |
|-------|----------|----------------------------------------------------------------------------------------|
| 1     | MOVE     | Per-player conflict resolution: single targets, then cycle swaps, then blocking.       |
| 2     | HARVEST  | Group by cell; fruit-by-fruit distribution; last fruit duplicated to all trolls.       |
| 3     | PLANT    | Group by cell; if all agree on type, all lose seed, one tree spawned.                  |
| 4     | CHOP     | Group by cell; damage; if killed, distribute wood round-robin (last wood duplicated).  |
| 5     | PICK     | Independent; sequential; depletes shack stock.                                         |
| 6     | TRAIN    | Independent; cost deduction, shack-occupied guard, new troll spawned at shack cell.    |
| 7     | DROP     | Independent; move ALL of troll inventory to shack.                                     |
| 8     | MINE     | Independent; +min(chopPower, free capacity) IRON.                                      |
| 10    | WAIT     | No-op (intentional skip).                                                              |
| post  | Plant tick + dead-tree compaction + `state.turn++`.                                    |

WAIT actions have no apply-step. Unspecified troll indices (no entry in `actions[]`) are equivalent to WAIT.

---

## File Structure

- **Create:** `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java` — all phase methods + scratch buffers + `tick(...)` entry point. Package `com.bmrt.cgspring2026.simulation`.
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorDropTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMineTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPickTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorHarvestTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorChopTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTrainTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java`
- **Create:** `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTickIntegrationTest.java`

No modification of existing files. `FileBuilder` already scans `src/main/java` recursively; the new package will be included automatically when bundling.

---

## Conventions used in this plan

- **Grid coords:** `x` is column (0..width-1), `y` is row (0..height-1). `width = 2 * height`.
- **`(byte) X & 0xFF`:** unsigned read of a byte-array slot (positions, counts, ids).
- **Test helper `loadGrid(String[] rows)`:** every test file copies this private static helper to set `GameState.width/height/tiles` and shack positions. We repeat the code in every task (DRY violated intentionally — engineers may read tasks out of order).
- **`PathTable.init()`:** only required for MOVE tests. Other tests skip it.
- **mvn invocation:** all tests run via `mvn -q -Dtest=ClassName test`.

---

### Task 1: Bootstrap `Simulator` skeleton + turn counter

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java`

- [ ] **Step 1: Write the failing test (turn counter increments)**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPlantTickTest {

    @BeforeEach void grid() {
        String[] rows = {"........", "...0....", "........", "....1...", "........", "........", "........", "........"};
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

    @Test void tickIncrementsTurnCounter() {
        GameState s = new GameState();
        s.turn = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.turn).isEqualTo(6);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SimulatorPlantTickTest#tickIncrementsTurnCounter test`
Expected: COMPILATION FAILURE — class `Simulator` does not exist.

- [ ] **Step 3: Create the Simulator skeleton**

Create `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;

public final class Simulator {

    private Simulator() {}

    public static void tick(GameState s, int[] actions, int n) {
        s.turn++;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=SimulatorPlantTickTest#tickIncrementsTurnCounter test`
Expected: PASS (1 test passed).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java
git commit -m "simulator: skeleton class + turn counter increment"
```

---

### Task 2: Plant tick — cooldown decrement + growth + fruit production

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java`

**Spec recap (from `gamesourcecode.Plant#tick(true)`):**
1. If `cooldown > 0`, decrement.
2. If after step 1 `cooldown == 0` AND `health > 0`:
   - If `size < 4`: `size++`, `health += DELTA_HEALTH[type]`, `cooldown = growthCooldown(type, near_water)`.
   - Else if `fruits < 3`: `fruits++`, `cooldown = growthCooldown(type, near_water)`.
3. `growthCooldown(type, nearWater) = COOLDOWN_NORMAL[type] - (nearWater ? boost[type] : 0)`. Water boosts (matching `Constants.PLANT_WATER_COOLDOWN_BOOST`): PLUM=5, LEMON=5, APPLE=7, BANANA=2.
4. "Near water" = any of 4 H/V neighbours is `TileType.WATER`.

- [ ] **Step 1: Write the failing tests**

Append to `SimulatorPlantTickTest.java`:

```java
    @Test void plantTickDecrementsCooldown() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 8;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 4);
        assertThat(s.treeSize[0]).isEqualTo((byte) 2);
    }

    @Test void plantTickGrowsWhenCooldownReachesZero() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 8;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        // cooldown 1->0, size 2->3, health 8 + DELTA(2) = 10, cooldown reset to 8 (no water nearby)
        assertThat(s.treeSize[0]).isEqualTo((byte) 3);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 10);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 8);
    }

    @Test void plantTickProducesFruitWhenSizeFour() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 4;
        s.treeHealth[0]   = 20;
        s.treeFruits[0]   = 1;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeSize[0]).isEqualTo((byte) 4);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 9);
    }

    @Test void plantTickCapsFruitsAtThree() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 4;
        s.treeHealth[0]   = 12;
        s.treeFruits[0]   = 3;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 3);
        // No growth, no fruit, cooldown decremented to 0 and stays (no growth branch taken)
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 0);
    }

    @Test void plantTickUsesWaterBoostCooldown() {
        // place water at (5,6) so tree at (5,5) is near water
        GameState.tiles[6 * GameState.width + 5] = TileType.WATER;
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0]     = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[0]        = 5; s.treeY[0] = 5;
        s.treeSize[0]     = 2;
        s.treeHealth[0]   = 14;
        s.treeFruits[0]   = 0;
        s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeSize[0]).isEqualTo((byte) 3);
        // APPLE near water: 9 - 7 = 2
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 2);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorPlantTickTest test`
Expected: FAIL — only `tickIncrementsTurnCounter` passes; the four new tests fail because tick does not touch trees.

- [ ] **Step 3: Implement plant tick**

Replace `Simulator.java` with:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;

public final class Simulator {

    private static final byte[] WATER_BOOST = { 5, 5, 7, 2 };

    private Simulator() {}

    public static void tick(GameState s, int[] actions, int n) {
        plantTick(s);
        s.turn++;
    }

    static void plantTick(GameState s) {
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeCooldown[i] > 0) s.treeCooldown[i]--;
            if (s.treeCooldown[i] == 0 && s.treeHealth[i] > 0) {
                int type = s.treeType[i] & 0xFF;
                if (s.treeSize[i] < 4) {
                    s.treeSize[i]++;
                    int newSize = s.treeSize[i] & 0xFF;
                    int delta = TreeType.HEALTH_BY_SIZE[type][newSize - 1]
                              - (newSize >= 2 ? TreeType.HEALTH_BY_SIZE[type][newSize - 2] : 0);
                    // Match reference: health += constant DELTA per type, not derived from size table
                    s.treeHealth[i] += deltaHealth(type);
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                } else if (s.treeFruits[i] < 3) {
                    s.treeFruits[i]++;
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                }
            }
        }
    }

    private static byte deltaHealth(int treeType) {
        // PLUM=2, LEMON=2, APPLE=3, BANANA=1 (mirrors Constants.PLANT_DELTA_HEALTH)
        switch (treeType) {
            case TreeType.PLUM:   return 2;
            case TreeType.LEMON:  return 2;
            case TreeType.APPLE:  return 3;
            case TreeType.BANANA: return 1;
            default: throw new IllegalStateException();
        }
    }

    private static byte growthCooldown(GameState s, int treeIdx, int treeType) {
        int base = TreeType.COOLDOWN_NORMAL[treeType] & 0xFF;
        int x = s.treeX[treeIdx] & 0xFF;
        int y = s.treeY[treeIdx] & 0xFF;
        if (nearWater(x, y)) base -= WATER_BOOST[treeType] & 0xFF;
        return (byte) base;
    }

    private static boolean nearWater(int x, int y) {
        return tileEquals(x + 1, y, TileType.WATER)
            || tileEquals(x - 1, y, TileType.WATER)
            || tileEquals(x, y + 1, TileType.WATER)
            || tileEquals(x, y - 1, TileType.WATER);
    }

    private static boolean tileEquals(int x, int y, byte type) {
        if (x < 0 || x >= GameState.width || y < 0 || y >= GameState.height) return false;
        return GameState.tiles[y * GameState.width + x] == type;
    }
}
```

(The local variable `delta` is unused — the reference uses a flat constant delta per type. Remove it before commit.)

Cleaned-up `plantTick` body (drop unused locals):

```java
    static void plantTick(GameState s) {
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeCooldown[i] > 0) s.treeCooldown[i]--;
            if (s.treeCooldown[i] == 0 && s.treeHealth[i] > 0) {
                int type = s.treeType[i] & 0xFF;
                if (s.treeSize[i] < 4) {
                    s.treeSize[i]++;
                    s.treeHealth[i] += deltaHealth(type);
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                } else if (s.treeFruits[i] < 3) {
                    s.treeFruits[i]++;
                    s.treeCooldown[i] = growthCooldown(s, i, type);
                }
            }
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorPlantTickTest test`
Expected: PASS (5 tests passed).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java
git commit -m "simulator: plant tick (cooldown, growth, fruit, water boost)"
```

---

### Task 3: Dead-tree compaction after plant tick

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java`

**Spec recap:** at the end of `Board.tick`, `plants = plants.stream().filter(p -> !p.isDead())...`. Dead = `health <= 0`. We compact the SoA arrays by swap-with-last (no allocation). Order is not preserved.

- [ ] **Step 1: Write the failing tests**

Append to `SimulatorPlantTickTest.java`:

```java
    @Test void deadTreesRemovedAfterTick() {
        GameState s = new GameState();
        s.treeCount = 3;
        // tree 0: alive
        s.treeType[0] = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0] = 1; s.treeY[0] = 1;
        s.treeSize[0] = 4; s.treeHealth[0] = 12; s.treeFruits[0] = 0; s.treeCooldown[0] = 5;
        // tree 1: dead
        s.treeType[1] = com.bmrt.cgspring2026.model.TreeType.LEMON;
        s.treeX[1] = 2; s.treeY[1] = 2;
        s.treeSize[1] = 1; s.treeHealth[1] = 0; s.treeFruits[1] = 0; s.treeCooldown[1] = 0;
        // tree 2: alive
        s.treeType[2] = com.bmrt.cgspring2026.model.TreeType.APPLE;
        s.treeX[2] = 3; s.treeY[2] = 3;
        s.treeSize[2] = 4; s.treeHealth[2] = 20; s.treeFruits[2] = 1; s.treeCooldown[2] = 5;
        Simulator.tick(s, new int[0], 0);
        assertThat(s.treeCount).isEqualTo(2);
        // The remaining 2 trees are the two alive ones (order may differ due to swap-with-last)
        boolean foundPlum = false, foundApple = false;
        for (int i = 0; i < s.treeCount; i++) {
            if (s.treeType[i] == com.bmrt.cgspring2026.model.TreeType.PLUM)  foundPlum  = true;
            if (s.treeType[i] == com.bmrt.cgspring2026.model.TreeType.APPLE) foundApple = true;
        }
        assertThat(foundPlum).isTrue();
        assertThat(foundApple).isTrue();
    }

    @Test void deadTreeDoesNotTickInSameTurn() {
        GameState s = new GameState();
        s.treeCount = 1;
        s.treeType[0] = com.bmrt.cgspring2026.model.TreeType.PLUM;
        s.treeX[0] = 1; s.treeY[0] = 1;
        s.treeSize[0] = 2; s.treeHealth[0] = 0; s.treeFruits[0] = 0; s.treeCooldown[0] = 1;
        Simulator.tick(s, new int[0], 0);
        // Tree compacted away; count becomes 0.
        assertThat(s.treeCount).isEqualTo(0);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorPlantTickTest test`
Expected: FAIL on the two new tests — `treeCount` stays at 3 / 1.

- [ ] **Step 3: Implement compaction**

In `Simulator.java`, modify `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void compactDeadTrees(GameState s) {
        int i = 0;
        while (i < s.treeCount) {
            if (s.treeHealth[i] <= 0) {
                int last = s.treeCount - 1;
                if (i != last) {
                    s.treeType[i]     = s.treeType[last];
                    s.treeX[i]        = s.treeX[last];
                    s.treeY[i]        = s.treeY[last];
                    s.treeSize[i]     = s.treeSize[last];
                    s.treeHealth[i]   = s.treeHealth[last];
                    s.treeFruits[i]   = s.treeFruits[last];
                    s.treeCooldown[i] = s.treeCooldown[last];
                }
                s.treeCount--;
            } else {
                i++;
            }
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorPlantTickTest test`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTickTest.java
git commit -m "simulator: compact dead trees after plant tick"
```

---

### Task 4: DROP phase

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorDropTest.java`

**Spec recap (from `DropTask`):** parse-time guards: troll has non-empty inventory, troll on cell adjacent to its own shack (`|dx|+|dy| <= 1`, the shack cell itself counts because `cell.isNearShack` returns true for the shack cell, but in practice trolls aren't on shacks). Apply: transfer ALL troll inventory items to `player.inventory`, zero troll inventory.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorDropTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorDropTest {

    @BeforeEach void grid() {
        String[] rows = {"........", "...0....", "........", "....1...", "........", "........", "........", "........"};
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

    @Test void dropTransfersAllItemsAdjacentToOwnShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1); // adjacent east
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 10;
        s.trollInventory[ResourceType.PLUM] = 3;
        s.trollInventory[ResourceType.WOOD] = 2;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
        assertThat(s.shackInventory[ResourceType.WOOD]).isEqualTo(2);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.trollInventory[ResourceType.WOOD]).isEqualTo((byte) 0);
    }

    @Test void dropDoesNothingWhenNotNearShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 6; s.trollY[0] = 6; // far from shack
        s.trollInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(0);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 3);
    }

    @Test void dropDoesNothingWhenInventoryEmpty() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        for (int r = 0; r < ResourceType.COUNT; r++) {
            assertThat(s.shackInventory[r]).isEqualTo(0);
        }
    }

    @Test void dropForPlayerOneUsesOppShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 1;
        s.trollX[0] = (byte) (GameState.shackOppX + 1);
        s.trollY[0] = (byte) GameState.shackOppY;
        s.trollInventory[ResourceType.APPLE] = 4;
        int[] acts = { Action.drop(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.COUNT + ResourceType.APPLE]).isEqualTo(4);
        assertThat(s.shackInventory[ResourceType.APPLE]).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorDropTest test`
Expected: FAIL — DROP not implemented (shack inventory stays 0, troll inventory unchanged).

- [ ] **Step 3: Implement DROP phase**

In `Simulator.java`, modify `tick` and add the phase + helpers:

```java
import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.ResourceType;
```

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyDrops(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyDrops(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.DROP) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            if (!trollNearOwnShack(s, idx)) continue;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            if (total == 0) continue;
            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
            for (int r = 0; r < ResourceType.COUNT; r++) {
                s.shackInventory[shackBase + r] += s.trollInventory[invBase + r] & 0xFF;
                s.trollInventory[invBase + r] = 0;
            }
        }
    }

    static boolean trollNearOwnShack(GameState s, int trollIdx) {
        int player = s.trollPlayer[trollIdx] & 0xFF;
        int sx = (player == 0) ? GameState.shackMeX  : GameState.shackOppX;
        int sy = (player == 0) ? GameState.shackMeY  : GameState.shackOppY;
        int tx = s.trollX[trollIdx] & 0xFF;
        int ty = s.trollY[trollIdx] & 0xFF;
        int d  = Math.abs(tx - sx) + Math.abs(ty - sy);
        return d <= 1;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorDropTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorDropTest.java
git commit -m "simulator: DROP phase (transfer troll inventory to shack)"
```

---

### Task 5: MINE phase

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMineTest.java`

**Spec recap (from `MineTask` + `Unit.mine()`):** parse-time guards: troll has a H/V-adjacent `IRON` tile, has `chopPower > 0`, has free capacity. Apply: loop `for i in 0..chopPower: if freeCapacity > 0, +1 IRON`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMineTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorMineTest {

    @BeforeEach void grid() {
        // iron at (3,3), grass everywhere else
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.tiles[3 * 6 + 3] = TileType.IRON;
        GameState.shackMeX  = 0; GameState.shackMeY  = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    @Test void mineAdjacentToIronAddsChopPowerIron() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 2; // north of iron
        s.trollCP[0] = 3;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 3);
    }

    @Test void mineRespectsCarryCapacity() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 2; s.trollY[0] = 3;
        s.trollCP[0] = 5;
        s.trollCC[0] = 2;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 2);
    }

    @Test void mineFailsWhenNotAdjacentToIron() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 0; s.trollY[0] = 0;
        s.trollCP[0] = 3;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 0);
    }

    @Test void mineFailsWhenChopPowerZero() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 2;
        s.trollCP[0] = 0;
        s.trollCC[0] = 10;
        int[] acts = { Action.mine(0) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.IRON]).isEqualTo((byte) 0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorMineTest test`
Expected: FAIL — MINE not implemented.

- [ ] **Step 3: Implement MINE phase**

In `Simulator.java`, modify `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyTrains(s, actions, n);     // placeholder (next tasks fill these)
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }
```

Wait — keep the order strict to the priority list. The final order is established in Task 11. For now, just append `applyMines` AFTER `applyDrops` (DROP=priority 7, MINE=priority 8). Add empty stubs for earlier phases will be filled in later tasks.

Revised `tick` body for this task (just DROP + MINE among action phases):

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }
```

Add the MINE method:

```java
    static void applyMines(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.MINE) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int cp = s.trollCP[idx] & 0xFF;
            if (cp == 0) continue;
            if (!adjacentToIron(s, idx)) continue;
            int cc = s.trollCC[idx] & 0xFF;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            int free = cc - total;
            int gain = Math.min(cp, free);
            if (gain <= 0) continue;
            s.trollInventory[invBase + ResourceType.IRON] += gain;
        }
    }

    static boolean adjacentToIron(GameState s, int trollIdx) {
        int x = s.trollX[trollIdx] & 0xFF;
        int y = s.trollY[trollIdx] & 0xFF;
        return tileAtOrNone(x + 1, y) == TileType.IRON
            || tileAtOrNone(x - 1, y) == TileType.IRON
            || tileAtOrNone(x, y + 1) == TileType.IRON
            || tileAtOrNone(x, y - 1) == TileType.IRON;
    }

    static byte tileAtOrNone(int x, int y) {
        if (x < 0 || x >= GameState.width || y < 0 || y >= GameState.height) return -1;
        return GameState.tiles[y * GameState.width + x];
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorMineTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMineTest.java
git commit -m "simulator: MINE phase (+iron when adjacent to iron tile)"
```

---

### Task 6: PICK phase

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPickTest.java`

**Spec recap (from `PickTask`):** parse-time guards: troll near own shack, has free capacity, shack has stock of the asked type. Apply: sequentially over actions, decrement shack 1, increment troll 1. If stock runs out mid-batch, later PICKs fail silently.

PICK type is `arg1` in the action encoding (resource type). Only fruits (PLUM..BANANA) are valid plant types per source; but in our SoA we accept any resource — the engine validates at parse time. To be safe, accept any resource 0..5.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPickTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPickTest {

    @BeforeEach void grid() {
        String[] rows = {"........", "...0....", "........", "....1...", "........", "........", "........", "........"};
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

    @Test void pickMovesOnePlumFromShackToTroll() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 5;
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(2);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void pickFailsWhenStockEmpty() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 5;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void pickFailsWhenNotNearShack() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 6; s.trollY[0] = 6;
        s.trollCC[0] = 5;
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
    }

    @Test void pickFailsWhenCarryFull() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollCC[0] = 1;
        s.trollInventory[ResourceType.WOOD] = 1; // full
        s.shackInventory[ResourceType.PLUM] = 3;
        int[] acts = { Action.pick(0, ResourceType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(3);
    }

    @Test void twoPicksDepleteStock() {
        GameState s = new GameState();
        s.trollCount = 2;
        for (int t = 0; t < 2; t++) {
            s.trollPlayer[t] = 0;
            s.trollX[t] = (byte) (GameState.shackMeX + (t == 0 ? 1 : -1));
            s.trollY[t] = (byte) GameState.shackMeY;
            s.trollCC[t] = 5;
        }
        s.shackInventory[ResourceType.LEMON] = 1;
        int[] acts = { Action.pick(0, ResourceType.LEMON), Action.pick(1, ResourceType.LEMON) };
        Simulator.tick(s, acts, 2);
        assertThat(s.shackInventory[ResourceType.LEMON]).isEqualTo(0);
        // first picker gets the lemon, second is a no-op
        assertThat(s.trollInventory[ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorPickTest test`
Expected: FAIL — PICK not implemented.

- [ ] **Step 3: Implement PICK phase**

In `Simulator.java`, add the phase and wire it (PICK is priority 5, must come BEFORE TRAIN/DROP/MINE):

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyPicks(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyPicks(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.PICK) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int type = Action.arg1(a);
            if (type < 0 || type >= ResourceType.COUNT) continue;
            if (!trollNearOwnShack(s, idx)) continue;
            int cc = s.trollCC[idx] & 0xFF;
            int invBase = idx * ResourceType.COUNT;
            int total = 0;
            for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
            if (total >= cc) continue;
            int shackBase = (s.trollPlayer[idx] & 0xFF) * ResourceType.COUNT;
            if (s.shackInventory[shackBase + type] <= 0) continue;
            s.shackInventory[shackBase + type]--;
            s.trollInventory[invBase + type]++;
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorPickTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPickTest.java
git commit -m "simulator: PICK phase (1 resource per call, stock-depleting)"
```

---

### Task 7: HARVEST phase (single + shared trees)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorHarvestTest.java`

**Spec recap (from `HarvestTask` + `Unit.harvest(power)`):**
- Parse-time guards: troll on a tree cell, tree has fruits, troll has hp & free capacity.
- Apply: group HARVEST tasks by cell. For each cell with a tree, for `power` in 1..3: break if tree.fruits == 0; for each troll in cell-group: if `power > hp`: skip; if full: skip; else `troll.inv[treeType]++` and `if tree.fruits > 0: tree.fruits--` (last-fruit-duplicated emerges from the order of decrement after increment).

**Iteration over actions[]:** we need to group HARVEST actions by `(trollX, trollY)`. Approach: linear scan, for each HARVEST action look up the tree at troll's cell; if no tree, skip. Then for each `(treeIdx, set of trolls)`, run the harvest distribution.

To avoid allocations, we use a fixed-size scratch array of "troll indices that share this tree" + a "processed tree bitmap":

```java
private static final boolean[] harvestTreeProcessed = new boolean[GameState.MAX_TREES];
private static final int[] harvestTrollBuf = new int[GameState.MAX_TROLLS];
```

For each action in scan order: if HARVEST, find tree index. If `harvestTreeProcessed[treeIdx]` is true, skip (we already processed this tree). Otherwise: collect all HARVEST actions targeting same tree (linear scan again), apply distribution, mark processed.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorHarvestTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorHarvestTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    private static int placeTree(GameState s, int x, int y, int type, int size, int fruits, int cooldown) {
        int i = s.treeCount++;
        s.treeType[i] = (byte) type;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeSize[i] = (byte) size;
        s.treeHealth[i] = TreeType.HEALTH_BY_SIZE[type][size - 1];
        s.treeFruits[i] = (byte) fruits;
        s.treeCooldown[i] = (byte) cooldown;
        return i;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int hp, int cc) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollHP[i] = (byte) hp;
        s.trollCC[i] = (byte) cc;
        return i;
    }

    @Test void harvestSingleTrollOneFruit() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 2, 9);
        int t = placeTroll(s, 0, 3, 3, 1, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
    }

    @Test void harvestSingleTrollMatchesHarvestPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 3, 9);
        int t = placeTroll(s, 0, 3, 3, 2, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 2);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
    }

    @Test void harvestSharedTreeLastFruitDuplicated() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.LEMON, 4, 1, 9);
        int t1 = placeTroll(s, 0, 3, 3, 1, 5);
        int t2 = placeTroll(s, 1, 3, 3, 1, 5);
        int[] acts = { Action.harvest(t1), Action.harvest(t2) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 0);
    }

    @Test void harvestFailsWhenNoTreeOnCell() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 2, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void harvestStopsAtCapacity() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 3, 9);
        int t = placeTroll(s, 0, 3, 3, 3, 1);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
    }

    @Test void harvestFailsWithZeroHarvestPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 2, 9);
        int t = placeTroll(s, 0, 3, 3, 0, 5);
        int[] acts = { Action.harvest(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorHarvestTest test`
Expected: FAIL — HARVEST not implemented.

- [ ] **Step 3: Implement HARVEST phase**

In `Simulator.java`:

```java
    private static final boolean[] harvestTreeProcessed = new boolean[GameState.MAX_TREES];
    private static final int[]     harvestSharedTrolls  = new int[GameState.MAX_TROLLS];
```

Add HARVEST first in `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyHarvests(s, actions, n);
        applyPicks(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyHarvests(GameState s, int[] actions, int n) {
        for (int t = 0; t < s.treeCount; t++) harvestTreeProcessed[t] = false;
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.HARVEST) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
            int treeIdx = findTreeAt(s, tx, ty);
            if (treeIdx < 0) continue;
            if (harvestTreeProcessed[treeIdx]) continue;
            // collect all HARVEST actions targeting this tree, in actions[] order
            int shared = 0;
            for (int j = i; j < n; j++) {
                int b = actions[j];
                if (Action.type(b) != ActionType.HARVEST) continue;
                int jdx = Action.trollIdx(b);
                if (jdx >= s.trollCount) continue;
                int jx = s.trollX[jdx] & 0xFF, jy = s.trollY[jdx] & 0xFF;
                if (jx == tx && jy == ty) harvestSharedTrolls[shared++] = jdx;
            }
            harvestTreeProcessed[treeIdx] = true;
            int type = s.treeType[treeIdx] & 0xFF;
            for (int power = 1; power <= 3; power++) {
                if (s.treeFruits[treeIdx] == 0) break;
                for (int k = 0; k < shared; k++) {
                    int trollIdx = harvestSharedTrolls[k];
                    int hp = s.trollHP[trollIdx] & 0xFF;
                    if (power > hp) continue;
                    int invBase = trollIdx * ResourceType.COUNT;
                    int cc = s.trollCC[trollIdx] & 0xFF;
                    int total = 0;
                    for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
                    if (total >= cc) continue;
                    s.trollInventory[invBase + type]++;
                    if (s.treeFruits[treeIdx] > 0) s.treeFruits[treeIdx]--;
                }
            }
        }
    }

    static int findTreeAt(GameState s, int x, int y) {
        for (int i = 0; i < s.treeCount; i++) {
            if ((s.treeX[i] & 0xFF) == x && (s.treeY[i] & 0xFF) == y && s.treeHealth[i] > 0) return i;
        }
        return -1;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorHarvestTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorHarvestTest.java
git commit -m "simulator: HARVEST phase (per-cell distribution, last-fruit duplicated)"
```

---

### Task 8: PLANT phase (single + cooperative + contradictory)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTest.java`

**Spec recap (from `PlantTask`):**
- Parse-time guards: troll on grass, no plant on cell, troll has the seed.
- Apply: group by cell. If all tasks on cell ask for the same type: every troll loses 1 seed, exactly ONE tree is created (first task creates; rest see existing). If types differ: nothing happens (no seed loss).
- New tree fields: `size = 0`, `health = FINAL_HEALTH - DELTA * 4`, `fruits = 0`, `cooldown = 0`. For PLUM/LEMON: `12 - 2*4 = 4`. For APPLE: `20 - 3*4 = 8`. For BANANA: `6 - 1*4 = 2`.

Note: the same-turn post-tick will grow the new tree from size 0 → size 1 (since cooldown is 0 at creation).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPlantTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    private static int placeTroll(GameState s, int player, int x, int y) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollCC[i] = 5;
        return i;
    }

    @Test void plantCreatesTreeAndGrowsToSizeOneAfterTick() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // New tree: starts with size=0, health=4 (PLUM final 12 - delta 2 * 4). Tick post-action grows it to size=1, health=6.
        assertThat(s.treeX[0]).isEqualTo((byte) 3);
        assertThat(s.treeY[0]).isEqualTo((byte) 3);
        assertThat(s.treeType[0]).isEqualTo(TreeType.PLUM);
        assertThat(s.treeSize[0]).isEqualTo((byte) 1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 6);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 0);
    }

    @Test void plantFailsWithoutSeed() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3);
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
    }

    @Test void plantFailsOnNonGrass() {
        GameState s = new GameState();
        GameState.tiles[3 * 6 + 3] = TileType.ROCK;
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void plantFailsOnOccupiedCell() {
        GameState s = new GameState();
        // existing tree at (3,3)
        s.treeCount = 1;
        s.treeType[0] = TreeType.LEMON;
        s.treeX[0] = 3; s.treeY[0] = 3;
        s.treeSize[0] = 2;
        s.treeHealth[0] = 8;
        s.treeFruits[0] = 0;
        s.treeCooldown[0] = 9;
        int t = placeTroll(s, 0, 3, 3);
        s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM] = 1;
        int[] acts = { Action.plant(t, TreeType.PLUM) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeType[0]).isEqualTo(TreeType.LEMON);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
    }

    @Test void twoTrollsSameCellSameTypeOnePlantBothLoseSeed() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 3, 3);
        int t2 = placeTroll(s, 1, 3, 3);
        s.trollInventory[t1 * ResourceType.COUNT + ResourceType.APPLE] = 1;
        s.trollInventory[t2 * ResourceType.COUNT + ResourceType.APPLE] = 1;
        int[] acts = { Action.plant(t1, TreeType.APPLE), Action.plant(t2, TreeType.APPLE) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeType[0]).isEqualTo(TreeType.APPLE);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 0);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 0);
    }

    @Test void twoTrollsSameCellDifferentTypesNoPlantNoSeedLoss() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 3, 3);
        int t2 = placeTroll(s, 1, 3, 3);
        s.trollInventory[t1 * ResourceType.COUNT + ResourceType.PLUM]  = 1;
        s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON] = 1;
        int[] acts = { Action.plant(t1, TreeType.PLUM), Action.plant(t2, TreeType.LEMON) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.PLUM]).isEqualTo((byte) 1);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorPlantTest test`
Expected: FAIL — PLANT not implemented.

- [ ] **Step 3: Implement PLANT phase**

Wire PLANT before CHOP/PICK/TRAIN/DROP/MINE. Insert in `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyHarvests(s, actions, n);
        applyPlants(s, actions, n);
        applyPicks(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }
```

Add PLANT and helpers:

```java
    private static final boolean[] plantCellProcessed = new boolean[GameState.MAX_TROLLS];

    static void applyPlants(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) plantCellProcessed[i] = false;
        for (int i = 0; i < n; i++) {
            if (plantCellProcessed[i]) continue;
            int a = actions[i];
            if (Action.type(a) != ActionType.PLANT) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
            // skip if not grass, or already a tree present
            if (GameState.tiles[ty * GameState.width + tx] != TileType.GRASS) continue;
            if (findTreeAt(s, tx, ty) >= 0) continue;
            // collect concurrent PLANT actions on same cell
            int firstType = Action.arg1(a);
            boolean contradictory = false;
            int starti = i;
            int endi = n;
            int sharedCount = 0;
            int[] sharedIdx = harvestSharedTrolls; // reuse buffer
            int[] sharedType = plantTypeBuf;
            for (int j = i; j < n; j++) {
                int b = actions[j];
                if (Action.type(b) != ActionType.PLANT) continue;
                int jdx = Action.trollIdx(b);
                if (jdx >= s.trollCount) continue;
                int jx = s.trollX[jdx] & 0xFF, jy = s.trollY[jdx] & 0xFF;
                if (jx != tx || jy != ty) continue;
                // parse-time: troll must have the seed
                int jtype = Action.arg1(b);
                if (s.trollInventory[jdx * ResourceType.COUNT + jtype] <= 0) continue;
                sharedIdx[sharedCount]  = jdx;
                sharedType[sharedCount] = jtype;
                if (jtype != firstType) contradictory = true;
                sharedCount++;
                plantCellProcessed[j] = true;
            }
            if (sharedCount == 0 || contradictory) continue;
            // all agree; each loses a seed; exactly one tree is created
            int treeType = firstType;
            int newIdx = s.treeCount++;
            s.treeType[newIdx]     = (byte) treeType;
            s.treeX[newIdx]        = (byte) tx;
            s.treeY[newIdx]        = (byte) ty;
            s.treeSize[newIdx]     = 0;
            s.treeHealth[newIdx]   = (byte) initialPlantHealth(treeType);
            s.treeFruits[newIdx]   = 0;
            s.treeCooldown[newIdx] = 0;
            for (int k = 0; k < sharedCount; k++) {
                int jdx = sharedIdx[k];
                int jtype = sharedType[k];
                s.trollInventory[jdx * ResourceType.COUNT + jtype]--;
            }
        }
    }

    private static final int[] plantTypeBuf = new int[GameState.MAX_TROLLS];

    private static int initialPlantHealth(int treeType) {
        // FINAL - DELTA * MAX_SIZE: PLUM=12-8=4, LEMON=12-8=4, APPLE=20-12=8, BANANA=6-4=2
        switch (treeType) {
            case TreeType.PLUM:   return 4;
            case TreeType.LEMON:  return 4;
            case TreeType.APPLE:  return 8;
            case TreeType.BANANA: return 2;
            default: throw new IllegalStateException();
        }
    }
```

Note: `plantCellProcessed` is indexed by action position (0..n-1, n ≤ MAX_TROLLS+1 in practice). Sized to MAX_TROLLS is safe since actions ≤ MAX_TROLLS + a few TRAIN. If exceeded, bump to a larger constant. (`actions` length ≤ `MAX_TROLLS + 1` per `decide()` convention.)

If MAX_TROLLS is not enough, replace size with `GameState.MAX_TROLLS + 8` for safety. Update declaration:

```java
    private static final boolean[] plantCellProcessed = new boolean[GameState.MAX_TROLLS + 8];
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorPlantTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorPlantTest.java
git commit -m "simulator: PLANT phase (cooperative same-type, contradictory abort)"
```

---

### Task 9: CHOP phase (damage + kill + wood distribution + duplication)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorChopTest.java`

**Spec recap (from `ChopTask`):**
- Parse-time guards: tree on troll's cell, troll has chop power.
- Apply: group by cell. Damage = sum of chop powers; clamped at 0 (`health = max(0, health - cp)` per chop, sequentially). If tree dies, distribute wood: `remainingWood = size`. For `i in 0..size`, for each troll in cell-group: if free capacity > 0, `+1 WOOD`, `remainingWood--`; outer loop breaks when `remainingWood <= 0` (but inner loop within a round still runs through all trolls — last wood duplicated when round count exceeds remaining).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorChopTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorChopTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        GameState.shackOppX = 5; GameState.shackOppY = 5;
        GameState.tiles[0] = TileType.SHACK_ME;
        GameState.tiles[5 * 6 + 5] = TileType.SHACK_OPP;
    }

    private static int placeTree(GameState s, int x, int y, int type, int size, int cooldown) {
        int i = s.treeCount++;
        s.treeType[i] = (byte) type;
        s.treeX[i] = (byte) x; s.treeY[i] = (byte) y;
        s.treeSize[i] = (byte) size;
        s.treeHealth[i] = TreeType.HEALTH_BY_SIZE[type][size - 1];
        s.treeFruits[i] = 0;
        s.treeCooldown[i] = (byte) cooldown;
        return i;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int cp, int cc) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollCP[i] = (byte) cp;
        s.trollCC[i] = (byte) cc;
        return i;
    }

    @Test void chopDamagesTreeWithoutKilling() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 9); // health 20
        int t = placeTroll(s, 0, 3, 3, 5, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // 20 - 5 = 15, then post-tick cooldown 9->8, no growth
        assertThat(s.treeHealth[0]).isEqualTo((byte) 15);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 8);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 0);
    }

    @Test void chopKillsTreeAndYieldsWoodEqualsSize() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 9); // health 12
        int t = placeTroll(s, 0, 3, 3, 20, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 4);
    }

    @Test void chopWoodOverflowIsLost() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.APPLE, 4, 9); // size 4
        int t = placeTroll(s, 0, 3, 3, 20, 2);    // capacity only 2
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }

    @Test void chopSharedDistributesLastWoodDuplicated() {
        // size=3 tree, 2 trolls each chopPower 10, capacity 10 -> 2 wood each = 4 distributed (size 3 -> last duplicated)
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.BANANA, 3, 6); // size 3, health 5
        int t1 = placeTroll(s, 0, 3, 3, 10, 10);
        int t2 = placeTroll(s, 1, 3, 3, 10, 10);
        int[] acts = { Action.chop(t1), Action.chop(t2) };
        Simulator.tick(s, acts, 2);
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[t1 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
        assertThat(s.trollInventory[t2 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }

    @Test void chopFailsWithoutChopPower() {
        GameState s = new GameState();
        placeTree(s, 3, 3, TreeType.PLUM, 4, 9);
        int t = placeTroll(s, 0, 3, 3, 0, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 12);
    }

    @Test void chopFailsWhenNoTreeOnCell() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5, 10);
        int[] acts = { Action.chop(t) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollInventory[t * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorChopTest test`
Expected: FAIL — CHOP not implemented.

- [ ] **Step 3: Implement CHOP phase**

Wire CHOP after PLANT (priority 4). Modify `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyHarvests(s, actions, n);
        applyPlants(s, actions, n);
        applyChops(s, actions, n);
        applyPicks(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    private static final boolean[] chopTreeProcessed = new boolean[GameState.MAX_TREES];

    static void applyChops(GameState s, int[] actions, int n) {
        for (int t = 0; t < s.treeCount; t++) chopTreeProcessed[t] = false;
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.CHOP) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            if ((s.trollCP[idx] & 0xFF) == 0) continue;
            int tx = s.trollX[idx] & 0xFF, ty = s.trollY[idx] & 0xFF;
            int treeIdx = findTreeAt(s, tx, ty);
            if (treeIdx < 0) continue;
            if (chopTreeProcessed[treeIdx]) continue;
            // collect concurrent CHOP actions on this tree
            int shared = 0;
            for (int j = i; j < n; j++) {
                int b = actions[j];
                if (Action.type(b) != ActionType.CHOP) continue;
                int jdx = Action.trollIdx(b);
                if (jdx >= s.trollCount) continue;
                if ((s.trollCP[jdx] & 0xFF) == 0) continue;
                if ((s.trollX[jdx] & 0xFF) != tx || (s.trollY[jdx] & 0xFF) != ty) continue;
                harvestSharedTrolls[shared++] = jdx;
            }
            chopTreeProcessed[treeIdx] = true;
            // damage sequentially
            for (int k = 0; k < shared; k++) {
                int dmg = s.trollCP[harvestSharedTrolls[k]] & 0xFF;
                int h = (s.treeHealth[treeIdx] & 0xFF) - dmg;
                s.treeHealth[treeIdx] = (byte) Math.max(h, 0);
            }
            if (s.treeHealth[treeIdx] != 0) continue;
            // distribute wood
            int size = s.treeSize[treeIdx] & 0xFF;
            int remaining = size;
            for (int round = 0; round < size && remaining > 0; round++) {
                for (int k = 0; k < shared; k++) {
                    int trollIdx = harvestSharedTrolls[k];
                    int invBase = trollIdx * ResourceType.COUNT;
                    int cc = s.trollCC[trollIdx] & 0xFF;
                    int total = 0;
                    for (int r = 0; r < ResourceType.COUNT; r++) total += s.trollInventory[invBase + r] & 0xFF;
                    if (total >= cc) continue;
                    s.trollInventory[invBase + ResourceType.WOOD]++;
                    remaining--;
                }
            }
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorChopTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorChopTest.java
git commit -m "simulator: CHOP phase (damage, kill, wood distribution + duplication)"
```

---

### Task 10: TRAIN phase (cost, spawn, shack-block, multi-train)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTrainTest.java`

**Spec recap (from `TrainTask` + `Unit`):**
- Cost: `n + v²` for each of the 4 stats, paid in PLUM/LEMON/APPLE/IRON resources. `n` = current troll count of the player.
- Affordability check uses current shack inventory.
- Apply guard: `getUnitsByCell(player.getShack()).count() > 0` aborts. The newly spawned troll IS on the shack cell, so a SECOND same-turn TRAIN by the same player fails.
- Stat bounds: MS ∈ [1, w*h], CC ∈ [0, 1000], HP ∈ [0, 3], CP ∈ [0, max final health = 20]. Out-of-range → parse fails.
- Spawn: troll position = shack position; inventory zeroed; new unique id = current max id + 1.
- Multiple TRAIN actions per turn allowed — each recomputes `n`. For both players, processed in actions[] order.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTrainTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTrainTest {

    @BeforeEach void grid() {
        GameState.height = 6;
        GameState.width  = 6;
        GameState.tiles  = new byte[36];
        for (int i = 0; i < 36; i++) GameState.tiles[i] = TileType.GRASS;
        GameState.shackMeX = 1; GameState.shackMeY = 1;
        GameState.shackOppX = 4; GameState.shackOppY = 4;
        GameState.tiles[1 * 6 + 1] = TileType.SHACK_ME;
        GameState.tiles[4 * 6 + 4] = TileType.SHACK_OPP;
    }

    private static int placeTroll(GameState s, int player, int x, int y, int id) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollId[i] = (byte) id;
        return i;
    }

    @Test void trainSpawnsTrollAtShackWithStatsAndDeductsCost() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0); // existing troll elsewhere (NOT on shack)
        s.shackInventory[ResourceType.PLUM]  = 5; // need n+v^2 = 1+4 = 5
        s.shackInventory[ResourceType.LEMON] = 2; // 1+1 = 2
        s.shackInventory[ResourceType.APPLE] = 2; // 1+1 = 2
        s.shackInventory[ResourceType.IRON]  = 2; // 1+1 = 2
        int[] acts = { Action.train(2, 1, 1, 1) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(2);
        int newIdx = 1;
        assertThat(s.trollPlayer[newIdx]).isEqualTo((byte) 0);
        assertThat(s.trollX[newIdx] & 0xFF).isEqualTo(GameState.shackMeX);
        assertThat(s.trollY[newIdx] & 0xFF).isEqualTo(GameState.shackMeY);
        assertThat(s.trollMS[newIdx]).isEqualTo((byte) 2);
        assertThat(s.trollCC[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollHP[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollCP[newIdx]).isEqualTo((byte) 1);
        assertThat(s.trollId[newIdx]).isEqualTo((byte) 1);
        // costs deducted
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.LEMON]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.APPLE]).isEqualTo(0);
        assertThat(s.shackInventory[ResourceType.IRON]).isEqualTo(0);
    }

    @Test void trainFailsWhenCannotAfford() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0);
        s.shackInventory[ResourceType.PLUM]  = 4;
        s.shackInventory[ResourceType.LEMON] = 2;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;
        int[] acts = { Action.train(2, 1, 1, 1) }; // costs 5 PLUM
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(4);
    }

    @Test void trainFailsWhenTrollAlreadyOnShack() {
        GameState s = new GameState();
        placeTroll(s, 0, GameState.shackMeX, GameState.shackMeY, 0); // blocks shack
        s.shackInventory[ResourceType.PLUM]  = 5;
        s.shackInventory[ResourceType.LEMON] = 2;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;
        int[] acts = { Action.train(2, 1, 1, 1) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollCount).isEqualTo(1);
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(5);
    }

    @Test void secondTrainSameTurnFailsBecauseFirstOccupiesShack() {
        GameState s = new GameState();
        placeTroll(s, 0, 3, 3, 0);
        s.shackInventory[ResourceType.PLUM]  = 100;
        s.shackInventory[ResourceType.LEMON] = 100;
        s.shackInventory[ResourceType.APPLE] = 100;
        s.shackInventory[ResourceType.IRON]  = 100;
        int[] acts = { Action.train(1, 0, 0, 0), Action.train(1, 0, 0, 0) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollCount).isEqualTo(2);
    }

    @Test void trainForPlayerOneUsesOppInventoryAndShack() {
        GameState s = new GameState();
        placeTroll(s, 1, 3, 3, 0);
        int base = ResourceType.COUNT;
        s.shackInventory[base + ResourceType.PLUM]  = 5;
        s.shackInventory[base + ResourceType.LEMON] = 2;
        s.shackInventory[base + ResourceType.APPLE] = 2;
        s.shackInventory[base + ResourceType.IRON]  = 2;
        // For training player 1, we still emit TRAIN action — but we need a way to know the owning player.
        // Convention: TRAIN action owner is inferred from the next "TRAIN" by ordering — but a single TRAIN action
        // is not tagged with a player. The simulation needs an owner. We adopt: TRAIN affects the player whose
        // resources are sufficient AND who has an empty shack. Tie-break by player 0 first.
        // -> Adapt: in the public API, callers supply TRAIN actions only for their own player. The Simulator
        //          picks player 0 if it can afford & shack free; else player 1.
        int[] acts = { Action.train(1, 0, 0, 0) };
        Simulator.tick(s, acts, 1);
        // Player 0 has 0 PLUM, cannot afford -> player 1's TRAIN runs
        assertThat(s.trollCount).isEqualTo(2);
        assertThat(s.trollPlayer[1]).isEqualTo((byte) 1);
        assertThat(s.trollX[1] & 0xFF).isEqualTo(GameState.shackOppX);
    }
}
```

> **Convention note (read carefully):** TRAIN actions in our encoding (Task 1's `model.md`) carry no explicit player field. The greedy agent only emits TRAIN for player 0. To simulate adversarial states, TRAIN actions need an owner; the simplest convention is: **the first player (0, then 1) that can afford the TRAIN and has a free shack cell becomes the owner**. This matches the GA use case (we simulate hypothetical opponent moves by composing the opponent's own TRAIN action in their action list, processed alongside our own — order in `actions[]` decides the owner via the affordability check, which is deterministic).
>
> The last test enforces this convention. Future extension to a richer encoding (e.g. carry player bit in TRAIN's high bits) is out of scope.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorTrainTest test`
Expected: FAIL — TRAIN not implemented.

- [ ] **Step 3: Implement TRAIN phase**

Wire TRAIN between PICK (5) and DROP (7). Modify `tick`:

```java
    public static void tick(GameState s, int[] actions, int n) {
        applyHarvests(s, actions, n);
        applyPlants(s, actions, n);
        applyChops(s, actions, n);
        applyPicks(s, actions, n);
        applyTrains(s, actions, n);
        applyDrops(s, actions, n);
        applyMines(s, actions, n);
        plantTick(s);
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyTrains(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.TRAIN) continue;
            int ms = Action.trainMS(a);
            int cc = Action.trainCC(a);
            int hp = Action.trainHP(a);
            int cp = Action.trainCP(a);
            // try player 0 first, then player 1
            int player = -1;
            for (int p = 0; p < 2; p++) {
                if (!canAffordTrain(s, p, ms, cc, hp, cp)) continue;
                if (shackOccupied(s, p)) continue;
                player = p; break;
            }
            if (player < 0) continue;
            // deduct costs
            int base = player * ResourceType.COUNT;
            int nUnits = countOwnTrolls(s, player);
            s.shackInventory[base + ResourceType.PLUM]  -= nUnits + ms * ms;
            s.shackInventory[base + ResourceType.LEMON] -= nUnits + cc * cc;
            s.shackInventory[base + ResourceType.APPLE] -= nUnits + hp * hp;
            s.shackInventory[base + ResourceType.IRON]  -= nUnits + cp * cp;
            // spawn troll at shack
            int newIdx = s.trollCount++;
            s.trollPlayer[newIdx] = (byte) player;
            s.trollId[newIdx]     = (byte) (maxTrollId(s) + 1);
            s.trollX[newIdx] = (byte) (player == 0 ? GameState.shackMeX : GameState.shackOppX);
            s.trollY[newIdx] = (byte) (player == 0 ? GameState.shackMeY : GameState.shackOppY);
            s.trollMS[newIdx] = (byte) ms;
            s.trollCC[newIdx] = (byte) cc;
            s.trollHP[newIdx] = (byte) hp;
            s.trollCP[newIdx] = (byte) cp;
            int invBase = newIdx * ResourceType.COUNT;
            for (int r = 0; r < ResourceType.COUNT; r++) s.trollInventory[invBase + r] = 0;
        }
    }

    private static boolean canAffordTrain(GameState s, int player, int ms, int cc, int hp, int cp) {
        int base = player * ResourceType.COUNT;
        int n = countOwnTrolls(s, player);
        return s.shackInventory[base + ResourceType.PLUM]  >= n + ms * ms
            && s.shackInventory[base + ResourceType.LEMON] >= n + cc * cc
            && s.shackInventory[base + ResourceType.APPLE] >= n + hp * hp
            && s.shackInventory[base + ResourceType.IRON]  >= n + cp * cp;
    }

    private static boolean shackOccupied(GameState s, int player) {
        int sx = (player == 0) ? GameState.shackMeX  : GameState.shackOppX;
        int sy = (player == 0) ? GameState.shackMeY  : GameState.shackOppY;
        for (int i = 0; i < s.trollCount; i++) {
            if ((s.trollX[i] & 0xFF) == sx && (s.trollY[i] & 0xFF) == sy) return true;
        }
        return false;
    }

    private static int countOwnTrolls(GameState s, int player) {
        int c = 0;
        for (int i = 0; i < s.trollCount; i++) if ((s.trollPlayer[i] & 0xFF) == player) c++;
        return c;
    }

    private static int maxTrollId(GameState s) {
        int m = -1;
        for (int i = 0; i < s.trollCount; i++) {
            int v = s.trollId[i] & 0xFF;
            if (v > m) m = v;
        }
        return m;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorTrainTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTrainTest.java
git commit -m "simulator: TRAIN phase (cost, spawn at shack, owner inferred by affordability)"
```

---

### Task 11: MOVE phase — simple walkable in-range target

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java`

**Spec recap (from `MoveTask` + `Board.getNextCell`):**
- Compute next cell along BFS path from source to target, capped to `movementSpeed`.
- If target is walkable and reachable in ≤ speed steps → land on target.
- If too far → land on path-step `speed`.
- If unreachable (target on non-walkable or disconnected) → stay in place (simulator simplification; rare in agent output).

We use the pre-computed `PathTable` (already used by the greedy agent). `PathTable.stepAlong(fromId, toId, k)` returns the cell id `k` steps along the path; `k` is clamped to `distance`. For unreachable targets, `PathTable.distance` returns `UNREACHABLE` (0xFF) — treat as stay-in-place.

This task implements the no-conflict case (one troll per cell). Conflicts come in Task 12-13.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorMoveTest {

    @BeforeEach void grid() {
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
    }

    private static int placeTroll(GameState s, int player, int x, int y, int ms) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollMS[i] = (byte) ms;
        return i;
    }

    @Test void moveToReachableWalkableTargetWithinSpeed() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, 4, 3) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollX[t] & 0xFF).isEqualTo(4);
        assertThat(s.trollY[t] & 0xFF).isEqualTo(3);
    }

    @Test void moveCapsAtSpeedAlongPath() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 0, 0, 2);
        int[] acts = { Action.move(t, 5, 0) };
        Simulator.tick(s, acts, 1);
        // 2 steps along path (BFS); destination has distance 5; we go to step 2 along the path.
        assertThat(Math.abs((s.trollX[t] & 0xFF) - 0) + Math.abs((s.trollY[t] & 0xFF) - 0)).isLessThanOrEqualTo(2);
        assertThat((s.trollX[t] & 0xFF) + (s.trollY[t] & 0xFF)).isGreaterThan(0);
    }

    @Test void moveToOwnCellIsNoOp() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, 3, 3) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollX[t] & 0xFF).isEqualTo(3);
        assertThat(s.trollY[t] & 0xFF).isEqualTo(3);
    }

    @Test void moveToShackTileSimulatedAsStayInPlaceOrAdjacent() {
        // The shack is a non-walkable endpoint in PathTable. The reference engine would project to
        // adjacent grass; the optimised simulator either lands on adjacent grass (if step-along reaches it)
        // or stays in place when the path is unresolvable. Both are accepted.
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, GameState.shackOppX, GameState.shackOppY) };
        Simulator.tick(s, acts, 1);
        int finalX = s.trollX[t] & 0xFF;
        int finalY = s.trollY[t] & 0xFF;
        int manhattan = Math.abs(finalX - GameState.shackOppX) + Math.abs(finalY - GameState.shackOppY);
        // Either the troll stayed (manhattan from start = anything) or moved towards: just check tile is walkable.
        assertThat(GameState.tiles[finalY * GameState.width + finalX]).isIn(TileType.GRASS);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorMoveTest test`
Expected: FAIL — MOVE not implemented.

- [ ] **Step 3: Implement MOVE phase (simple version)**

Wire MOVE first in `tick`. Modify `tick`:

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
        compactDeadTrees(s);
        s.turn++;
    }

    static void applyMoves(GameState s, int[] actions, int n) {
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.MOVE) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int tx = Action.arg1(a);
            int ty = Action.arg2(a);
            if (tx < 0 || tx >= GameState.width || ty < 0 || ty >= GameState.height) continue;
            int fromX = s.trollX[idx] & 0xFF;
            int fromY = s.trollY[idx] & 0xFF;
            int speed = s.trollMS[idx] & 0xFF;
            stepTowards(s, idx, fromX, fromY, tx, ty, speed);
        }
    }

    static void stepTowards(GameState s, int trollIdx, int fromX, int fromY, int toX, int toY, int speed) {
        if (fromX == toX && fromY == toY) return;
        int fromId = PathTable.cellId(fromX, fromY);
        int toId   = PathTable.cellId(toX, toY);
        if (fromId == PathTable.UNREACHABLE) return;
        if (toId   == PathTable.UNREACHABLE) return; // simplified: unreachable target -> stay
        int dist = PathTable.distance(fromId, toId);
        if (dist == PathTable.UNREACHABLE) return;
        int k = Math.min(speed, dist);
        if (k == 0) return;
        int rawIdx = PathTable.stepAlong(fromX, fromY, toX, toY, k);
        int newX = rawIdx % GameState.width;
        int newY = rawIdx / GameState.width;
        // refuse to land on a non-walkable cell (e.g., shack endpoint at k=dist)
        if (GameState.tiles[newY * GameState.width + newX] != TileType.GRASS) {
            // step back by 1
            if (k <= 1) return;
            rawIdx = PathTable.stepAlong(fromX, fromY, toX, toY, k - 1);
            newX = rawIdx % GameState.width;
            newY = rawIdx / GameState.width;
            if (GameState.tiles[newY * GameState.width + newX] != TileType.GRASS) return;
        }
        s.trollX[trollIdx] = (byte) newX;
        s.trollY[trollIdx] = (byte) newY;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorMoveTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java
git commit -m "simulator: MOVE phase, simple path-step (no conflicts)"
```

---

### Task 12: MOVE — same-team conflicts (target collision) and swap cycles

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java`
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java`

**Spec recap (from `MoveTask.apply`):** within one player's batch:
1. Compute target cell for every troll that has a MOVE action; trolls without MOVE keep current cell.
2. Mark all current cells as occupied.
3. Skip trolls whose target == current (they "stay put").
4. Iteratively:
   - Find a troll whose target has frequency 1 among remaining targets AND is not occupied → move it (free source, occupy target).
   - If no such single-target move possible: look for cycles (a→b→a, longer cycles) — move them all simultaneously.
   - If still no progress: allow a troll to move into a target with frequency >1 (first wins).
5. Opposing-team trolls do NOT count as obstacles (two trolls of different teams can share a cell).

Implementation strategy: per-player resolution loop.

- [ ] **Step 1: Write the failing tests**

Append to `SimulatorMoveTest.java`:

```java
    @Test void sameTeamTwoTrollsCannotEndOnSameCell() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 2, 3, 5);
        int t2 = placeTroll(s, 0, 4, 3, 5);
        int[] acts = { Action.move(t1, 3, 3), Action.move(t2, 3, 3) };
        Simulator.tick(s, acts, 2);
        // Exactly one of the two trolls reached (3,3); the other stayed put.
        int reached = 0;
        if ((s.trollX[t1] & 0xFF) == 3 && (s.trollY[t1] & 0xFF) == 3) reached++;
        if ((s.trollX[t2] & 0xFF) == 3 && (s.trollY[t2] & 0xFF) == 3) reached++;
        assertThat(reached).isEqualTo(1);
        // The one that didn't reach is still at its original cell
        boolean t1Reached = (s.trollX[t1] & 0xFF) == 3 && (s.trollY[t1] & 0xFF) == 3;
        if (t1Reached) {
            assertThat(s.trollX[t2] & 0xFF).isEqualTo(4);
            assertThat(s.trollY[t2] & 0xFF).isEqualTo(3);
        } else {
            assertThat(s.trollX[t1] & 0xFF).isEqualTo(2);
            assertThat(s.trollY[t1] & 0xFF).isEqualTo(3);
        }
    }

    @Test void sameTeamSwapPositions() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 2, 3, 5);
        int t2 = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t1, 3, 3), Action.move(t2, 2, 3) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollX[t1] & 0xFF).isEqualTo(3);
        assertThat(s.trollX[t2] & 0xFF).isEqualTo(2);
    }

    @Test void differentTeamsCanShareCell() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 2, 3, 5);
        int t2 = placeTroll(s, 1, 4, 3, 5);
        int[] acts = { Action.move(t1, 3, 3), Action.move(t2, 3, 3) };
        Simulator.tick(s, acts, 2);
        assertThat(s.trollX[t1] & 0xFF).isEqualTo(3);
        assertThat(s.trollY[t1] & 0xFF).isEqualTo(3);
        assertThat(s.trollX[t2] & 0xFF).isEqualTo(3);
        assertThat(s.trollY[t2] & 0xFF).isEqualTo(3);
    }

    @Test void stationaryTrollBlocksTeamMate() {
        GameState s = new GameState();
        int t1 = placeTroll(s, 0, 3, 3, 5);
        int t2 = placeTroll(s, 0, 2, 3, 5);
        // t1 has no MOVE -> stays at (3,3); t2 tries to move to (3,3) -> blocked.
        int[] acts = { Action.move(t2, 3, 3) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollX[t1] & 0xFF).isEqualTo(3);
        assertThat(s.trollX[t2] & 0xFF).isEqualTo(2);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SimulatorMoveTest test`
Expected: FAIL — current MOVE applies sequentially without conflict resolution; both trolls might end on (3,3).

- [ ] **Step 3: Implement same-team conflict resolution**

Replace `applyMoves` with a two-pass implementation. First pass: compute each troll's pre-resolved target. Second pass: per player, run the iterative resolver.

```java
    // Scratch: per-troll requested target (or -1 = no move / stationary)
    private static final int[] moveTargetX = new int[GameState.MAX_TROLLS];
    private static final int[] moveTargetY = new int[GameState.MAX_TROLLS];
    private static final boolean[] hasMove = new boolean[GameState.MAX_TROLLS];

    // Per-player resolver scratch
    private static final int[] resolverIdx = new int[GameState.MAX_TROLLS];
    private static final boolean[] resolverDone = new boolean[GameState.MAX_TROLLS];

    static void applyMoves(GameState s, int[] actions, int n) {
        // Reset & compute pre-resolved targets
        for (int i = 0; i < s.trollCount; i++) {
            hasMove[i] = false;
            moveTargetX[i] = s.trollX[i] & 0xFF;
            moveTargetY[i] = s.trollY[i] & 0xFF;
        }
        for (int i = 0; i < n; i++) {
            int a = actions[i];
            if (Action.type(a) != ActionType.MOVE) continue;
            int idx = Action.trollIdx(a);
            if (idx >= s.trollCount) continue;
            int tx = Action.arg1(a), ty = Action.arg2(a);
            if (tx < 0 || tx >= GameState.width || ty < 0 || ty >= GameState.height) continue;
            int fromX = s.trollX[idx] & 0xFF, fromY = s.trollY[idx] & 0xFF;
            int speed = s.trollMS[idx] & 0xFF;
            int[] dest = preResolveTarget(fromX, fromY, tx, ty, speed);
            if (dest == null) continue;
            hasMove[idx] = true;
            moveTargetX[idx] = dest[0];
            moveTargetY[idx] = dest[1];
        }
        // Resolve per player
        for (int player = 0; player < 2; player++) resolvePlayerMoves(s, player);
    }

    // Returns {x,y} or null. Uses PathTable.stepAlong; never lands on non-grass.
    private static final int[] preResolveDest = new int[2];
    private static int[] preResolveTarget(int fromX, int fromY, int toX, int toY, int speed) {
        if (fromX == toX && fromY == toY) return null;
        int fromId = PathTable.cellId(fromX, fromY);
        int toId   = PathTable.cellId(toX, toY);
        if (fromId == PathTable.UNREACHABLE || toId == PathTable.UNREACHABLE) return null;
        int dist = PathTable.distance(fromId, toId);
        if (dist == PathTable.UNREACHABLE) return null;
        int k = Math.min(speed, dist);
        if (k == 0) return null;
        int raw = PathTable.stepAlong(fromX, fromY, toX, toY, k);
        int x = raw % GameState.width, y = raw / GameState.width;
        if (GameState.tiles[y * GameState.width + x] != TileType.GRASS) {
            if (k <= 1) return null;
            raw = PathTable.stepAlong(fromX, fromY, toX, toY, k - 1);
            x = raw % GameState.width; y = raw / GameState.width;
            if (GameState.tiles[y * GameState.width + x] != TileType.GRASS) return null;
        }
        preResolveDest[0] = x;
        preResolveDest[1] = y;
        return preResolveDest;
    }

    private static void resolvePlayerMoves(GameState s, int player) {
        // Collect player's trolls
        int count = 0;
        for (int i = 0; i < s.trollCount; i++) {
            if ((s.trollPlayer[i] & 0xFF) != player) continue;
            resolverIdx[count] = i;
            resolverDone[i] = !hasMove[i] || (moveTargetX[i] == (s.trollX[i] & 0xFF)
                                            && moveTargetY[i] == (s.trollY[i] & 0xFF));
            count++;
        }
        // Mark occupied[] = current positions of all player's trolls (including stationary)
        int W = GameState.width;
        boolean[] occupied = ensureOccupiedBuffer(W * GameState.height);
        for (int i = 0; i < occupied.length; i++) occupied[i] = false;
        for (int k = 0; k < count; k++) {
            int idx = resolverIdx[k];
            occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = true;
        }
        boolean progressed = true;
        boolean allowBlocked = false;
        int[] freq = ensureFreqBuffer(W * GameState.height);
        while (progressed) {
            progressed = false;
            // recompute target frequency among undone trolls
            for (int i = 0; i < freq.length; i++) freq[i] = 0;
            for (int k = 0; k < count; k++) {
                int idx = resolverIdx[k];
                if (resolverDone[idx]) continue;
                freq[moveTargetY[idx] * W + moveTargetX[idx]]++;
            }
            // single-target moves into free cells
            for (int k = 0; k < count; k++) {
                int idx = resolverIdx[k];
                if (resolverDone[idx]) continue;
                int destCell = moveTargetY[idx] * W + moveTargetX[idx];
                if (!occupied[destCell] && (allowBlocked || freq[destCell] == 1)) {
                    occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
                    s.trollX[idx] = (byte) moveTargetX[idx];
                    s.trollY[idx] = (byte) moveTargetY[idx];
                    occupied[destCell] = true;
                    resolverDone[idx] = true;
                    progressed = true;
                    allowBlocked = false;
                }
            }
            if (progressed) continue;
            // cycle detection
            for (int startK = 0; startK < count && !progressed; startK++) {
                int startIdx = resolverIdx[startK];
                if (resolverDone[startIdx]) continue;
                int cur = startIdx;
                int hops = 0;
                int found = -1;
                while (hops <= count) {
                    int destCell = moveTargetY[cur] * W + moveTargetX[cur];
                    // find a troll whose CURRENT cell == destCell, undone, same player
                    int next = -1;
                    for (int k = 0; k < count; k++) {
                        int j = resolverIdx[k];
                        if (resolverDone[j]) continue;
                        if ((s.trollY[j] & 0xFF) * W + (s.trollX[j] & 0xFF) == destCell) { next = j; break; }
                    }
                    if (next < 0) break;
                    if (next == startIdx) { found = hops; break; }
                    cur = next; hops++;
                }
                if (found >= 0) {
                    // execute the cycle
                    int cur2 = startIdx;
                    int[] cycle = cycleBuf;
                    int len = 0;
                    cycle[len++] = cur2;
                    for (int h = 0; h <= found; h++) {
                        int destCell = moveTargetY[cur2] * W + moveTargetX[cur2];
                        int next = -1;
                        for (int k = 0; k < count; k++) {
                            int j = resolverIdx[k];
                            if (resolverDone[j]) continue;
                            if ((s.trollY[j] & 0xFF) * W + (s.trollX[j] & 0xFF) == destCell) { next = j; break; }
                        }
                        if (next < 0 || next == startIdx) break;
                        cycle[len++] = next;
                        cur2 = next;
                    }
                    for (int c = 0; c < len; c++) {
                        int idx = cycle[c];
                        occupied[(s.trollY[idx] & 0xFF) * W + (s.trollX[idx] & 0xFF)] = false;
                    }
                    for (int c = 0; c < len; c++) {
                        int idx = cycle[c];
                        s.trollX[idx] = (byte) moveTargetX[idx];
                        s.trollY[idx] = (byte) moveTargetY[idx];
                        occupied[moveTargetY[idx] * W + moveTargetX[idx]] = true;
                        resolverDone[idx] = true;
                    }
                    progressed = true;
                }
            }
            if (!progressed && !allowBlocked) {
                allowBlocked = true;
                progressed = true; // re-enter loop with relaxed rule
            }
        }
    }

    private static boolean[] occupiedBuf;
    private static int[]     freqBuf;
    private static final int[] cycleBuf = new int[GameState.MAX_TROLLS];

    private static boolean[] ensureOccupiedBuffer(int size) {
        if (occupiedBuf == null || occupiedBuf.length < size) occupiedBuf = new boolean[size];
        return occupiedBuf;
    }
    private static int[] ensureFreqBuffer(int size) {
        if (freqBuf == null || freqBuf.length < size) freqBuf = new int[size];
        return freqBuf;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SimulatorMoveTest test`
Expected: PASS (all 8 tests: 4 from Task 11 + 4 from Task 12).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/simulation/Simulator.java src/test/java/com/bmrt/cgspring2026/simulation/SimulatorMoveTest.java
git commit -m "simulator: MOVE same-team conflict + cycle swap resolution"
```

---

### Task 13: Integration test — multi-phase end-to-end turn

**Files:**
- Test: `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTickIntegrationTest.java`

This task adds NO production code. It exercises a realistic mixed turn (multiple trolls doing different actions) and verifies all phases compose correctly.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTickIntegrationTest.java`:

```java
package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTickIntegrationTest {

    @BeforeEach void grid() {
        String[] rows = {
                "........",
                ".0......",
                "........",
                "....1...",
                "........",
                "........",
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
    }

    @Test void mixedTurnDropTrainHarvestMove() {
        GameState s = new GameState();
        // Troll 0: on shack-adjacent, has 2 PLUM + 1 WOOD to drop
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) (GameState.shackMeX + 1);
        s.trollY[0] = (byte) GameState.shackMeY;
        s.trollMS[0] = 1; s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollCP[0] = 0;
        s.trollId[0] = 0;
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.PLUM] = 2;
        s.trollInventory[0 * ResourceType.COUNT + ResourceType.WOOD] = 1;
        // Troll 1: on tree, harvests
        s.trollPlayer[1] = 0;
        s.trollX[1] = 3; s.trollY[1] = 5;
        s.trollMS[1] = 1; s.trollCC[1] = 5; s.trollHP[1] = 2; s.trollCP[1] = 0;
        s.trollId[1] = 1;
        // Tree under troll 1
        s.treeCount = 1;
        s.treeType[0] = TreeType.APPLE;
        s.treeX[0] = 3; s.treeY[0] = 5;
        s.treeSize[0] = 4; s.treeHealth[0] = 20; s.treeFruits[0] = 3; s.treeCooldown[0] = 5;
        // Shack inventory big enough to TRAIN 1 1 0 0 with n=2 -> needs 3 PLUM, 3 LEMON, 2 APPLE, 2 IRON
        s.shackInventory[ResourceType.PLUM]  = 3;
        s.shackInventory[ResourceType.LEMON] = 3;
        s.shackInventory[ResourceType.APPLE] = 2;
        s.shackInventory[ResourceType.IRON]  = 2;

        int[] acts = {
            Action.harvest(1),
            Action.drop(0),
            Action.train(1, 1, 0, 0),
        };
        Simulator.tick(s, acts, 3);

        // TRAIN runs BEFORE DROP. TRAIN sees PLUM 3, LEMON 3, APPLE 2, IRON 2 -> affordable, spawns troll.
        assertThat(s.trollCount).isEqualTo(3);
        assertThat(s.trollPlayer[2]).isEqualTo((byte) 0);
        assertThat(s.trollX[2] & 0xFF).isEqualTo(GameState.shackMeX);
        // After TRAIN, shack PLUM 0, LEMON 0, APPLE 0, IRON 0.
        // Then DROP adds 2 PLUM + 1 WOOD.
        assertThat(s.shackInventory[ResourceType.PLUM]).isEqualTo(2);
        assertThat(s.shackInventory[ResourceType.WOOD]).isEqualTo(1);

        // Troll 1 harvested 2 fruits (hp=2, fruits=3): inventory=2 apples; tree fruits = 1
        assertThat(s.trollInventory[1 * ResourceType.COUNT + ResourceType.APPLE]).isEqualTo((byte) 2);
        assertThat(s.treeFruits[0]).isEqualTo((byte) 1);
        // tree cooldown decremented (no growth, size already 4 fruits<3 after harvest)
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 4);

        // Score check: 2 PLUM + 4 * 1 WOOD = 6 for player 0
        assertThat(s.score(0)).isEqualTo(2 + 4);
    }

    @Test void plantingThenSameTreeStaysIntactPostTick() {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 4;
        s.trollMS[0] = 1; s.trollCC[0] = 5;
        s.trollId[0] = 0;
        s.trollInventory[ResourceType.BANANA] = 1;
        int[] acts = { Action.plant(0, TreeType.BANANA) };
        Simulator.tick(s, acts, 1);
        assertThat(s.treeCount).isEqualTo(1);
        // BANANA initial health = 6 - 4 = 2; after first tick: size 0->1, health 2+1=3, cooldown set to 6 normal.
        assertThat(s.treeSize[0]).isEqualTo((byte) 1);
        assertThat(s.treeHealth[0]).isEqualTo((byte) 3);
        assertThat(s.treeCooldown[0]).isEqualTo((byte) 6);
    }

    @Test void chopThenHarvestSameTreeImpossibleSameTurn() {
        // HARVEST (priority 2) happens BEFORE CHOP (priority 4).
        // Troll 0 harvests; troll 1 chops; both on same cell.
        GameState s = new GameState();
        s.trollCount = 2;
        s.trollPlayer[0] = 0;
        s.trollX[0] = 3; s.trollY[0] = 5;
        s.trollCC[0] = 5; s.trollHP[0] = 1; s.trollId[0] = 0;
        s.trollPlayer[1] = 0;
        s.trollX[1] = 3; s.trollY[1] = 5;
        s.trollCC[1] = 5; s.trollCP[1] = 100; s.trollId[1] = 1;
        s.treeCount = 1;
        s.treeType[0] = TreeType.LEMON;
        s.treeX[0] = 3; s.treeY[0] = 5;
        s.treeSize[0] = 2; s.treeHealth[0] = 8; s.treeFruits[0] = 1; s.treeCooldown[0] = 5;
        int[] acts = { Action.harvest(0), Action.chop(1) };
        Simulator.tick(s, acts, 2);
        // Harvest succeeds first: troll 0 gets 1 LEMON.
        assertThat(s.trollInventory[0 * ResourceType.COUNT + ResourceType.LEMON]).isEqualTo((byte) 1);
        // Then chop kills the tree; troll 1 gets 2 wood (size 2).
        assertThat(s.treeCount).isEqualTo(0);
        assertThat(s.trollInventory[1 * ResourceType.COUNT + ResourceType.WOOD]).isEqualTo((byte) 2);
    }
}
```

- [ ] **Step 2: Run tests to verify they pass (no production change)**

Run: `mvn -q -Dtest=SimulatorTickIntegrationTest test`
Expected: PASS (3 tests). If anything fails, the simulator phase ordering is wrong — fix `tick(...)` in `Simulator.java` to match: MOVE→HARVEST→PLANT→CHOP→PICK→TRAIN→DROP→MINE→plantTick→compactDeadTrees→turn++.

- [ ] **Step 3: Run the full test suite to verify no regressions**

Run: `mvn -q test`
Expected: PASS — all simulator tests AND all pre-existing tests in `model/`, `action/`, `pathfinding/`, `greedy/`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/simulation/SimulatorTickIntegrationTest.java
git commit -m "simulator: integration tests for multi-phase turns"
```

---

## Acceptance criteria (post-Task 13)

- `mvn test` passes (all simulator tests + all pre-existing tests).
- A turn simulation runs without allocating any objects on the heap (scratch buffers are static, `int[]` actions are passed in by the caller). The `preResolveDest` returning an int[] reference is the only structural cost — kept as a single static `int[2]` array, returned as a borrowed view.
- The phase order in `Simulator.tick` matches the reference engine priority numbers.
- `Simulator.tick(s, new int[]{}, 0)` advances `state.turn` and ticks plants without any other side effects.

## Out-of-scope (deliberate simplifications vs reference engine)

1. **MOVE unreachable-target projection.** The reference engine projects an unreachable target to the nearest reachable cell by Manhattan distance, then walks toward it. Our simulator stays in place when the target is non-walkable / disconnected. Agents in our codebase never emit such moves (they target trees or shack-adjacent grass).
2. **MOVE randomness.** The reference picks one cell at random among equally-good candidates. Our simulator follows the deterministic pre-computed path from `PathTable`, so two simulations on the same state are bit-for-bit identical (required for the GA).
3. **TRAIN player ownership.** The reference parses a per-player command string; we infer the owner by checking affordability+free-shack in the order (player 0, player 1). Callers must respect this convention (don't emit both players' TRAINs in the same call expecting either to win arbitrarily).
4. **MSG / summaries / inputs/outputs.** Not part of the simulation surface.
5. **`hasStalled()` early-end detection.** Not in `tick`; if the GA needs it, expose a separate method.

---

## Self-review notes

- **Spec coverage:** every priority phase from `Task.getTaskPriority()` (1-10) and every action type from `ActionType` is covered by at least one task with tests.
- **Type consistency:** all method names referenced in later tasks (`applyMoves`, `applyHarvests`, `applyPlants`, `applyChops`, `applyPicks`, `applyTrains`, `applyDrops`, `applyMines`, `plantTick`, `compactDeadTrees`, `findTreeAt`, `trollNearOwnShack`, `adjacentToIron`) are introduced in the task that first defines them and reused with the same signature.
- **Placeholders:** none — every step shows the exact code to write or the exact command to run.
- **Test setup duplication:** the grid-loading helper in each test file is duplicated (DRY violated) so each task remains readable on its own; future cleanup is welcome but out of scope.
