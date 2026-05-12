# Greedy Chopper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construire la V1 du bot CG Spring 2026 sous forme d'algo glouton orienté wood (chop pur, TRAIN unique au tour 1, deux rôles de trolls avec partition Voronoi statique).

**Architecture :** Nouveau package `com.bmrt.cgspring2026.ai` regroupant 6 composants (TreeZoning, RoleAssigner, TargetSelector, TrainPlanner, RandomWalk, GreedyAi) plus 2 enums (Role, Zone). Player.main devient un orchestrateur minimal qui délègue à `GreedyAi.decide`.

**Tech Stack :** Java 21, Maven, JUnit Jupiter 5.6.3, AssertJ 3.18.1.

**Spec source :** `docs/superpowers/specs/2026-05-12-greedy-chopper-design.md`

---

## Vue d'ensemble des fichiers

**À créer (main) :**
- `src/main/java/com/bmrt/cgspring2026/ai/Role.java` — enum (LEADER, LOCAL)
- `src/main/java/com/bmrt/cgspring2026/ai/Zone.java` — enum (MINE, OPP, NEUTRAL, UNREACHABLE)
- `src/main/java/com/bmrt/cgspring2026/ai/TreeZoning.java` — Voronoi statique
- `src/main/java/com/bmrt/cgspring2026/ai/RoleAssigner.java` — étiquetage des trolls
- `src/main/java/com/bmrt/cgspring2026/ai/TargetSelector.java` — choix d'arbre cible
- `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java` — TRAIN unique au tour 1
- `src/main/java/com/bmrt/cgspring2026/ai/RandomWalk.java` — fallback de déplacement
- `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java` — orchestrateur

**À créer (test) :**
- `src/test/java/com/bmrt/cgspring2026/ai/TreeZoningTest.java`
- `src/test/java/com/bmrt/cgspring2026/ai/RoleAssignerTest.java`
- `src/test/java/com/bmrt/cgspring2026/ai/TargetSelectorTest.java`
- `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java`
- `src/test/java/com/bmrt/cgspring2026/ai/RandomWalkTest.java`
- `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

**À modifier :**
- `src/main/java/com/bmrt/cgspring2026/Player.java` — câblage vers `GreedyAi`

---

## Task 1 : Enums Role & Zone

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/Role.java`
- Create : `src/main/java/com/bmrt/cgspring2026/ai/Zone.java`

Ces deux enums sont triviaux (pas de comportement) — pas de test dédié. Ils servent de types de retour pour `RoleAssigner` et `TreeZoning`. On les écrit en premier pour débloquer les tests suivants.

- [ ] **Step 1 : Créer Role.java**

```java
package com.bmrt.cgspring2026.ai;

public enum Role {
    LEADER,
    LOCAL
}
```

- [ ] **Step 2 : Créer Zone.java**

```java
package com.bmrt.cgspring2026.ai;

public enum Zone {
    MINE,
    OPP,
    NEUTRAL,
    UNREACHABLE
}
```

- [ ] **Step 3 : Compile rapide**

Run : `mvn -q compile`
Expected : BUILD SUCCESS.

- [ ] **Step 4 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/Role.java src/main/java/com/bmrt/cgspring2026/ai/Zone.java
git commit -m "feat(ai): add Role and Zone enums"
```

---

## Task 2 : TreeZoning — Voronoi statique BFS

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/TreeZoning.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/TreeZoningTest.java`

API ciblée :
```java
public final class TreeZoning {
    public static TreeZoning precompute(GameState state) { ... }
    public Zone zoneOf(int x, int y) { ... }
}
```

Implémentation : deux BFS uniquement sur cases GRASS, démarrant chacun depuis les voisins GRASS de l'un des shacks (les shacks eux-mêmes ne sont pas marchables).

### TDD cycle 1 — cellule plus proche de mon shack → MINE

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `src/test/java/com/bmrt/cgspring2026/ai/TreeZoningTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreeZoningTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        return s;
    }

    @Test
    void cell_closer_to_my_shack_is_mine_zone() {
        // 8x4 open map, my shack at (1,1), opp shack at (6,2)
        GameState s = openMap(8, 4, 1, 1, 6, 2);

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(2, 1)).isEqualTo(Zone.MINE);
    }
}
```

- [ ] **Step 2 : Lancer le test → FAIL**

Run : `mvn -q test -Dtest=TreeZoningTest`
Expected : FAIL — `TreeZoning` does not exist.

- [ ] **Step 3 : Implémenter le minimum**

Créer `src/main/java/com/bmrt/cgspring2026/ai/TreeZoning.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TreeZoning {

    private static final int INF = Integer.MAX_VALUE;

    private final int width;
    private final int height;
    private final Zone[] zones;

    private TreeZoning(int width, int height, Zone[] zones) {
        this.width = width;
        this.height = height;
        this.zones = zones;
    }

    public static TreeZoning precompute(GameState state) {
        int w = state.width;
        int h = state.height;
        int[] distMine = bfsFromShack(state, state.myShackX, state.myShackY);
        int[] distOpp = bfsFromShack(state, state.oppShackX, state.oppShackY);
        Zone[] zones = new Zone[w * h];
        for (int i = 0; i < zones.length; i++) {
            int dm = distMine[i];
            int dp = distOpp[i];
            if (dm == INF && dp == INF) {
                zones[i] = Zone.UNREACHABLE;
            } else if (dm < dp) {
                zones[i] = Zone.MINE;
            } else if (dm > dp) {
                zones[i] = Zone.OPP;
            } else {
                zones[i] = Zone.NEUTRAL;
            }
        }
        return new TreeZoning(w, h, zones);
    }

    private static int[] bfsFromShack(GameState state, int shackX, int shackY) {
        int w = state.width;
        int h = state.height;
        int[] dist = new int[w * h];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = INF;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        // Seed BFS from GRASS neighbors of the shack (shack itself isn't walkable).
        for (int k = 0; k < 4; k++) {
            int nx = shackX + dx[k];
            int ny = shackY + dy[k];
            if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
                dist[ny * w + nx] = 1;
                queue.add(new int[]{nx, ny});
            }
        }
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cy = cur[1];
            int cd = dist[cy * w + cx];
            for (int k = 0; k < 4; k++) {
                int nx = cx + dx[k];
                int ny = cy + dy[k];
                if (state.walkable(nx, ny) && dist[ny * w + nx] == INF) {
                    dist[ny * w + nx] = cd + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return dist;
    }

    public Zone zoneOf(int x, int y) {
        return zones[y * width + x];
    }
}
```

- [ ] **Step 4 : Relancer le test → PASS**

Run : `mvn -q test -Dtest=TreeZoningTest`
Expected : PASS.

### TDD cycle 2 — équidistance → NEUTRAL ; côté opposé → OPP

- [ ] **Step 5 : Ajouter 2 tests qui doivent passer immédiatement (assertion de robustesse)**

Append dans `TreeZoningTest` :

```java
    @Test
    void cell_closer_to_opp_shack_is_opp_zone() {
        GameState s = openMap(8, 4, 1, 1, 6, 2);

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(5, 2)).isEqualTo(Zone.OPP);
    }

    @Test
    void equidistant_cell_is_neutral_zone() {
        // Symmetric map: shacks at (1,1) and (6,1), middle column equidistant
        GameState s = openMap(8, 3, 1, 1, 6, 1);

        TreeZoning zoning = TreeZoning.precompute(s);

        // (3,1) is at distance 2 from each shack
        assertThat(zoning.zoneOf(3, 1)).isEqualTo(Zone.NEUTRAL);
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=TreeZoningTest`
Expected : PASS (les 3 tests).

### TDD cycle 3 — case isolée par des obstacles → UNREACHABLE

- [ ] **Step 7 : Ajouter le test**

```java
    @Test
    void cell_walled_off_by_rocks_is_unreachable() {
        GameState s = openMap(5, 5, 1, 1, 3, 3);
        // Wall a 1x1 island at (4,4): surround it with ROCK
        s.grid[3 * 5 + 4] = (byte) Tile.ROCK.ordinal();
        s.grid[4 * 5 + 3] = (byte) Tile.ROCK.ordinal();

        TreeZoning zoning = TreeZoning.precompute(s);

        assertThat(zoning.zoneOf(4, 4)).isEqualTo(Zone.UNREACHABLE);
    }
```

- [ ] **Step 8 : Lancer → PASS**

Run : `mvn -q test -Dtest=TreeZoningTest`
Expected : PASS (4 tests).

- [ ] **Step 9 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TreeZoning.java src/test/java/com/bmrt/cgspring2026/ai/TreeZoningTest.java
git commit -m "feat(ai): TreeZoning Voronoi BFS from shacks"
```

---

## Task 3 : RoleAssigner

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/RoleAssigner.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/RoleAssignerTest.java`

API :
```java
public final class RoleAssigner {
    public static Map<Integer, Role> assign(List<Troll> myTrolls) { ... }
}
```

Règle : score = movementSpeed + carryCapacity + chopPower. Argmax = LEADER, autres = LOCAL. Si un seul troll → LOCAL. Tie-break sur id ascendant.

### TDD cycle 1 — un seul troll est LOCAL

- [ ] **Step 1 : Test qui échoue**

Créer `src/test/java/com/bmrt/cgspring2026/ai/RoleAssignerTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignerTest {

    private Troll troll(int id, int ms, int cc, int cp) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.movementSpeed = ms;
        t.carryCapacity = cc;
        t.chopPower = cp;
        return t;
    }

    @Test
    void single_troll_is_local() {
        Map<Integer, Role> roles = RoleAssigner.assign(List.of(troll(0, 1, 1, 1)));

        assertThat(roles).containsExactly(Map.entry(0, Role.LOCAL));
    }
}
```

- [ ] **Step 2 : Lancer → FAIL**

Run : `mvn -q test -Dtest=RoleAssignerTest`
Expected : FAIL (classe inexistante).

- [ ] **Step 3 : Implémenter**

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.Troll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoleAssigner {

    private RoleAssigner() {
    }

    public static Map<Integer, Role> assign(List<Troll> myTrolls) {
        Map<Integer, Role> result = new HashMap<>();
        if (myTrolls.isEmpty()) {
            return result;
        }
        if (myTrolls.size() == 1) {
            result.put(myTrolls.get(0).id, Role.LOCAL);
            return result;
        }
        Troll leader = myTrolls.get(0);
        int leaderScore = score(leader);
        for (int i = 1; i < myTrolls.size(); i++) {
            Troll t = myTrolls.get(i);
            int s = score(t);
            if (s > leaderScore || (s == leaderScore && t.id < leader.id)) {
                leader = t;
                leaderScore = s;
            }
        }
        for (Troll t : myTrolls) {
            result.put(t.id, t == leader ? Role.LEADER : Role.LOCAL);
        }
        return result;
    }

    private static int score(Troll t) {
        return t.movementSpeed + t.carryCapacity + t.chopPower;
    }
}
```

- [ ] **Step 4 : Lancer → PASS**

Run : `mvn -q test -Dtest=RoleAssignerTest`
Expected : PASS.

### TDD cycle 2 — argmax devient LEADER

- [ ] **Step 5 : Test**

Append :

```java
    @Test
    void highest_score_becomes_leader() {
        Troll weak = troll(0, 1, 1, 1);   // score 3
        Troll strong = troll(1, 3, 2, 4); // score 9

        Map<Integer, Role> roles = RoleAssigner.assign(List.of(weak, strong));

        assertThat(roles.get(0)).isEqualTo(Role.LOCAL);
        assertThat(roles.get(1)).isEqualTo(Role.LEADER);
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=RoleAssignerTest`
Expected : PASS (2 tests).

### TDD cycle 3 — tie-break par id ascendant

- [ ] **Step 7 : Test**

```java
    @Test
    void tie_broken_by_smallest_id() {
        Troll a = troll(2, 2, 2, 2); // score 6, id=2
        Troll b = troll(0, 2, 2, 2); // score 6, id=0

        Map<Integer, Role> roles = RoleAssigner.assign(List.of(a, b));

        assertThat(roles.get(0)).isEqualTo(Role.LEADER);
        assertThat(roles.get(2)).isEqualTo(Role.LOCAL);
    }
```

- [ ] **Step 8 : Lancer → PASS**

Run : `mvn -q test -Dtest=RoleAssignerTest`
Expected : PASS (3 tests).

- [ ] **Step 9 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/RoleAssigner.java src/test/java/com/bmrt/cgspring2026/ai/RoleAssignerTest.java
git commit -m "feat(ai): RoleAssigner picks LEADER by stat sum"
```

---

## Task 4 : TargetSelector

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/TargetSelector.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/TargetSelectorTest.java`

API :
```java
public final class TargetSelector {
    public static Tree pickTree(Troll troll, Role role, GameState state,
                                Set<Long> assignedTrees, TreeZoning zoning) { ... }
}
```

Comportement : voir §4 du spec — filtrage zone (selon rôle), filtrage mature-first strict, argmin distance Manhattan, tie-break (y, x).

Clé d'arbre : `y * width + x` (sur `long`).

### TDD cycle 1 — proche & mature pour LOCAL

- [ ] **Step 1 : Créer le test**

Créer `src/test/java/com/bmrt/cgspring2026/ai/TargetSelectorTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSelectorTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        return s;
    }

    private Tree tree(int x, int y, int size) {
        Tree t = new Tree();
        t.type = TreeType.PLUM;
        t.x = x;
        t.y = y;
        t.size = size;
        return t;
    }

    private Troll troll(int id, int x, int y) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        return t;
    }

    @Test
    void local_picks_closest_mature_tree() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(3, 1, 4)); // mature, dist 2
        s.trees.add(tree(2, 1, 4)); // mature, dist 1
        s.trees.add(tree(2, 2, 4)); // mature, dist 2 (tie with first)
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
        assertThat(picked.y).isEqualTo(1);
    }
}
```

- [ ] **Step 2 : Lancer → FAIL**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : FAIL (classe inexistante).

- [ ] **Step 3 : Implémenter**

Créer `src/main/java/com/bmrt/cgspring2026/ai/TargetSelector.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.Troll;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TargetSelector {

    private TargetSelector() {
    }

    public static Tree pickTree(Troll troll, Role role, GameState state,
                                Set<Long> assignedTrees, TreeZoning zoning) {
        if (state.trees.isEmpty()) {
            return null;
        }

        // 1. Filter out already-assigned trees, unless only one tree remains (sharing allowed).
        List<Tree> candidates = new ArrayList<>(state.trees.size());
        boolean lastTree = state.trees.size() == 1;
        for (Tree tree : state.trees) {
            if (lastTree || !assignedTrees.contains(key(tree, state.width))) {
                candidates.add(tree);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // 2. Zone filtering by role with fallback chain.
        List<Tree> zoned;
        if (role == Role.LEADER) {
            zoned = filterByZones(candidates, zoning, Zone.OPP);
            if (zoned.isEmpty()) {
                zoned = filterByZones(candidates, zoning, Zone.OPP, Zone.NEUTRAL);
            }
            if (zoned.isEmpty()) {
                zoned = candidates;
            }
        } else {
            zoned = filterByZones(candidates, zoning, Zone.MINE, Zone.NEUTRAL);
            if (zoned.isEmpty()) {
                zoned = candidates;
            }
        }
        if (lastTree) {
            zoned = candidates;
        }

        // 3. Mature-first strict.
        List<Tree> matures = new ArrayList<>(zoned.size());
        for (Tree tree : zoned) {
            if (tree.size == 4) {
                matures.add(tree);
            }
        }
        List<Tree> pool = matures.isEmpty() ? zoned : matures;

        // 4. Argmin Manhattan distance from troll ; tie-break (y, x).
        Tree best = pool.get(0);
        int bestDist = manhattan(troll, best);
        for (int i = 1; i < pool.size(); i++) {
            Tree cur = pool.get(i);
            int d = manhattan(troll, cur);
            if (d < bestDist
                    || (d == bestDist && cur.y < best.y)
                    || (d == bestDist && cur.y == best.y && cur.x < best.x)) {
                best = cur;
                bestDist = d;
            }
        }
        assignedTrees.add(key(best, state.width));
        return best;
    }

    private static List<Tree> filterByZones(List<Tree> candidates, TreeZoning zoning, Zone... allowed) {
        List<Tree> out = new ArrayList<>();
        for (Tree tree : candidates) {
            Zone z = zoning.zoneOf(tree.x, tree.y);
            for (Zone a : allowed) {
                if (z == a) {
                    out.add(tree);
                    break;
                }
            }
        }
        return out;
    }

    private static int manhattan(Troll troll, Tree tree) {
        return Math.abs(troll.x - tree.x) + Math.abs(troll.y - tree.y);
    }

    private static long key(Tree tree, int width) {
        return (long) tree.y * width + tree.x;
    }
}
```

- [ ] **Step 4 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS.

### TDD cycle 2 — fallback immature

- [ ] **Step 5 : Test**

```java
    @Test
    void local_falls_back_to_immature_when_no_mature() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(3, 1, 2)); // immature, dist 2
        s.trees.add(tree(2, 1, 1)); // immature, dist 1
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
        assertThat(picked.y).isEqualTo(1);
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS (2 tests).

### TDD cycle 3 — assigned trees ignorés

- [ ] **Step 7 : Test**

```java
    @Test
    void assigned_trees_are_skipped() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(2, 1, 4)); // mature, dist 1 — déjà assigné
        s.trees.add(tree(4, 1, 4)); // mature, dist 3
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);
        var assigned = new HashSet<Long>();
        assigned.add((long) 1 * 10 + 2); // key of (2,1)

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, assigned, zoning);

        assertThat(picked.x).isEqualTo(4);
        assertThat(picked.y).isEqualTo(1);
    }
```

- [ ] **Step 8 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS (3 tests).

### TDD cycle 4 — partage du dernier arbre

- [ ] **Step 9 : Test**

```java
    @Test
    void last_remaining_tree_can_be_shared() {
        GameState s = openMap(10, 6, 1, 1, 8, 4);
        s.trees.add(tree(2, 1, 4)); // mature, déjà assigné
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);
        var assigned = new HashSet<Long>();
        assigned.add((long) 1 * 10 + 2);

        Tree picked = TargetSelector.pickTree(t, Role.LOCAL, s, assigned, zoning);

        assertThat(picked).isNotNull();
        assertThat(picked.x).isEqualTo(2);
    }
```

- [ ] **Step 10 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS (4 tests).

### TDD cycle 5 — LEADER cible zone OPP

- [ ] **Step 11 : Test**

```java
    @Test
    void leader_targets_opp_zone_first() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(2, 1, 4)); // côté MINE (proche de (1,1))
        s.trees.add(tree(7, 2, 4)); // côté OPP (proche de (8,2))
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LEADER, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(7);
        assertThat(picked.y).isEqualTo(2);
    }
```

- [ ] **Step 12 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS (5 tests).

### TDD cycle 6 — LEADER fallback hors zone OPP

- [ ] **Step 13 : Test**

```java
    @Test
    void leader_falls_back_when_no_opp_trees() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(2, 1, 4)); // seulement zone MINE
        Troll t = troll(0, 1, 1);
        TreeZoning zoning = TreeZoning.precompute(s);

        Tree picked = TargetSelector.pickTree(t, Role.LEADER, s, new HashSet<>(), zoning);

        assertThat(picked.x).isEqualTo(2);
    }
```

- [ ] **Step 14 : Lancer → PASS**

Run : `mvn -q test -Dtest=TargetSelectorTest`
Expected : PASS (6 tests).

- [ ] **Step 15 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TargetSelector.java src/test/java/com/bmrt/cgspring2026/ai/TargetSelectorTest.java
git commit -m "feat(ai): TargetSelector zone+mature-first greedy pick"
```

---

## Task 5 : TrainPlanner

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java`

API :
```java
public final class TrainPlanner {
    public static final int V_MAX = 4;
    public static Action.Train plan(GameState state) { ... } // null si pas de TRAIN
}
```

Comportement : TRAIN uniquement si `state.turn == 1`. Greedy descendant sur v ∈ [V_MAX..1] : essayer `(v,v,0,v)` puis fallback `(v,v,0,0)`. Sinon null.

### TDD cycle 1 — TRAIN au tour 1, max affordable

- [ ] **Step 1 : Test**

Créer `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainPlannerTest {

    private GameState stateWithTurn(int turn, int plums, int lemons, int iron) {
        GameState s = new GameState();
        s.width = 1;
        s.height = 1;
        s.grid = new byte[]{0};
        s.turn = turn;
        s.myShackInv[ResourceType.PLUM.ordinal()] = plums;
        s.myShackInv[ResourceType.LEMON.ordinal()] = lemons;
        s.myShackInv[ResourceType.IRON.ordinal()] = iron;
        // One ally troll: required for n in cost formula
        Troll t = new Troll();
        t.id = 0;
        t.player = 0;
        s.trolls.add(t);
        return s;
    }

    @Test
    void emits_max_affordable_train_at_turn_1() {
        // n=1, v=2 cost = 1 + 4 = 5 each. v=3 cost = 1 + 9 = 10 each.
        // We give exactly 5 of each -> max v=2 affordable with chopPower.
        GameState s = stateWithTurn(1, 5, 5, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 2, 0, 2));
    }
}
```

- [ ] **Step 2 : Lancer → FAIL**

Run : `mvn -q test -Dtest=TrainPlannerTest`
Expected : FAIL (classe inexistante).

- [ ] **Step 3 : Implémenter**

Créer `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;

public final class TrainPlanner {

    public static final int V_MAX = 4;

    private TrainPlanner() {
    }

    public static Action.Train plan(GameState state) {
        if (state.turn != 1) {
            return null;
        }
        int n = countMyTrolls(state);
        int plums = state.myShackInv[ResourceType.PLUM.ordinal()];
        int lemons = state.myShackInv[ResourceType.LEMON.ordinal()];
        int iron = state.myShackInv[ResourceType.IRON.ordinal()];

        for (int v = V_MAX; v >= 1; v--) {
            int cost = n + v * v;
            if (plums >= cost && lemons >= cost && iron >= cost) {
                return new Action.Train(v, v, 0, v);
            }
        }
        // Fallback: no chopPower if IRON insufficient
        for (int v = V_MAX; v >= 1; v--) {
            int cost = n + v * v;
            if (plums >= cost && lemons >= cost) {
                return new Action.Train(v, v, 0, 0);
            }
        }
        return null;
    }

    private static int countMyTrolls(GameState state) {
        int n = 0;
        for (var t : state.trolls) {
            if (t.player == 0) {
                n++;
            }
        }
        return n;
    }
}
```

- [ ] **Step 4 : Lancer → PASS**

Run : `mvn -q test -Dtest=TrainPlannerTest`
Expected : PASS.

### TDD cycle 2 — fallback sans IRON

- [ ] **Step 5 : Test**

```java
    @Test
    void falls_back_to_zero_chop_when_no_iron_at_turn_1() {
        // n=1, v=2 cost 5 — assez de plum/lemon, zéro iron
        GameState s = stateWithTurn(1, 5, 5, 0);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 2, 0, 0));
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=TrainPlannerTest`
Expected : PASS (2 tests).

### TDD cycle 3 — ressources insuffisantes → null

- [ ] **Step 7 : Test**

```java
    @Test
    void returns_null_when_resources_insufficient_at_turn_1() {
        // n=1, v=1 cost = 1+1 = 2 — on n'a même pas 2 plums
        GameState s = stateWithTurn(1, 1, 1, 1);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
```

- [ ] **Step 8 : Lancer → PASS**

Run : `mvn -q test -Dtest=TrainPlannerTest`
Expected : PASS (3 tests).

### TDD cycle 4 — null après tour 1 même si ressources OK

- [ ] **Step 9 : Test**

```java
    @Test
    void returns_null_after_turn_1_even_with_resources() {
        GameState s = stateWithTurn(2, 100, 100, 100);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
```

- [ ] **Step 10 : Lancer → PASS**

Run : `mvn -q test -Dtest=TrainPlannerTest`
Expected : PASS (4 tests).

- [ ] **Step 11 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java
git commit -m "feat(ai): TrainPlanner emits single TRAIN at turn 1"
```

---

## Task 6 : RandomWalk

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/RandomWalk.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/RandomWalkTest.java`

API :
```java
public final class RandomWalk {
    public static Action.Move pick(Troll troll, GameState state) { ... }
}
```

Comportement : seed = `state.turn * 1000L + troll.id`. Jusqu'à 16 essais d'un offset (dx, dy) dans [-5..5], clamp à la grille, retenir si GRASS et différent de la case actuelle. Fallback ultime : stationner.

### TDD cycle 1 — sortie GRASS valide

- [ ] **Step 1 : Test**

Créer `src/test/java/com/bmrt/cgspring2026/ai/RandomWalkTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomWalkTest {

    private GameState openMap(int w, int h) {
        GameState s = new GameState();
        s.width = w;
        s.height = h;
        s.grid = new byte[w * h];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        return s;
    }

    private Troll troll(int id, int x, int y) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        return t;
    }

    @Test
    void returns_move_to_walkable_cell_different_from_current() {
        GameState s = openMap(10, 10);
        s.turn = 5;
        Troll t = troll(3, 5, 5);

        Action.Move move = RandomWalk.pick(t, s);

        assertThat(move.trollId()).isEqualTo(3);
        assertThat(s.walkable(move.x(), move.y())).isTrue();
        assertThat(move.x() == 5 && move.y() == 5).isFalse();
    }
}
```

- [ ] **Step 2 : Lancer → FAIL**

Run : `mvn -q test -Dtest=RandomWalkTest`
Expected : FAIL.

- [ ] **Step 3 : Implémenter**

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Troll;

import java.util.Random;

public final class RandomWalk {

    private static final int RADIUS = 5;
    private static final int ATTEMPTS = 16;

    private RandomWalk() {
    }

    public static Action.Move pick(Troll troll, GameState state) {
        Random rng = new Random((long) state.turn * 1000L + troll.id);
        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = rng.nextInt(2 * RADIUS + 1) - RADIUS;
            int dy = rng.nextInt(2 * RADIUS + 1) - RADIUS;
            int tx = clamp(troll.x + dx, 0, state.width - 1);
            int ty = clamp(troll.y + dy, 0, state.height - 1);
            if ((tx != troll.x || ty != troll.y) && state.walkable(tx, ty)) {
                return new Action.Move(troll.id, tx, ty);
            }
        }
        return new Action.Move(troll.id, troll.x, troll.y);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
```

- [ ] **Step 4 : Lancer → PASS**

Run : `mvn -q test -Dtest=RandomWalkTest`
Expected : PASS.

### TDD cycle 2 — reproductibilité du seed

- [ ] **Step 5 : Test**

```java
    @Test
    void same_seed_yields_same_destination() {
        GameState s1 = openMap(10, 10);
        s1.turn = 7;
        GameState s2 = openMap(10, 10);
        s2.turn = 7;
        Troll t1 = troll(2, 4, 4);
        Troll t2 = troll(2, 4, 4);

        Action.Move m1 = RandomWalk.pick(t1, s1);
        Action.Move m2 = RandomWalk.pick(t2, s2);

        assertThat(m1).isEqualTo(m2);
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=RandomWalkTest`
Expected : PASS (2 tests).

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/RandomWalk.java src/test/java/com/bmrt/cgspring2026/ai/RandomWalkTest.java
git commit -m "feat(ai): RandomWalk seeded fallback move"
```

---

## Task 7 : GreedyAi — orchestration

**Files :**
- Create : `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java`
- Test : `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

API :
```java
public final class GreedyAi {
    public List<Action> decide(GameState state) { ... }
}
```

Comportement : voir §3 du spec. Le `TreeZoning` est calculé une fois au tour 1 et caché dans une variable d'instance. L'ordre de traitement des trolls : LEADER d'abord, puis les autres triés par id ascendant.

### TDD cycle 1 — CHOP quand sur la case d'un arbre

- [ ] **Step 1 : Test**

Créer `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAiTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        s.turn = 2; // skip TRAIN by default
        return s;
    }

    private Tree tree(int x, int y, int size) {
        Tree t = new Tree();
        t.type = TreeType.PLUM;
        t.x = x;
        t.y = y;
        t.size = size;
        return t;
    }

    private Troll troll(int id, int x, int y, int cc) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        t.carryCapacity = cc;
        return t;
    }

    @Test
    void chops_when_on_tree_tile() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.trees.add(tree(3, 1, 4));
        s.trolls.add(troll(7, 3, 1, 10)); // troll on the same tile as the tree

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(new Action.Chop(7));
    }
}
```

- [ ] **Step 2 : Lancer → FAIL**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : FAIL (classe inexistante).

- [ ] **Step 3 : Implémenter**

Créer `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java` :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.Troll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GreedyAi {

    private TreeZoning zoning;

    public List<Action> decide(GameState state) {
        if (zoning == null) {
            zoning = TreeZoning.precompute(state);
        }

        List<Troll> myTrolls = new ArrayList<>();
        for (Troll t : state.trolls) {
            if (t.player == 0) {
                myTrolls.add(t);
            }
        }

        Action.Train trainAction = TrainPlanner.plan(state);
        Map<Integer, Role> roles = RoleAssigner.assign(myTrolls);

        // Order: LEADER first, then by id ascending.
        myTrolls.sort(Comparator.<Troll>comparingInt(t -> roles.get(t.id) == Role.LEADER ? 0 : 1)
                .thenComparingInt(t -> t.id));

        Set<Long> assignedTrees = new HashSet<>();
        List<Action> actions = new ArrayList<>(myTrolls.size() + 1);
        for (Troll t : myTrolls) {
            actions.add(decideForTroll(t, roles.get(t.id), state, assignedTrees));
        }
        if (trainAction != null) {
            actions.add(trainAction);
        }
        return actions;
    }

    private Action decideForTroll(Troll troll, Role role, GameState state, Set<Long> assignedTrees) {
        if (troll.carryTotal() >= troll.carryCapacity && troll.carryCapacity > 0) {
            if (adjacentToMyShack(troll, state)) {
                return new Action.Drop(troll.id);
            }
            return moveTowardShack(troll, state);
        }
        Tree target = TargetSelector.pickTree(troll, role, state, assignedTrees, zoning);
        if (target == null) {
            return RandomWalk.pick(troll, state);
        }
        if (troll.x == target.x && troll.y == target.y) {
            return new Action.Chop(troll.id);
        }
        return new Action.Move(troll.id, target.x, target.y);
    }

    private static boolean adjacentToMyShack(Troll troll, GameState state) {
        return Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) == 1;
    }

    private static Action.Move moveTowardShack(Troll troll, GameState state) {
        int sx = state.myShackX;
        int sy = state.myShackY;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        int bestX = troll.x;
        int bestY = troll.y;
        int bestDist = Integer.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            int nx = sx + dx[k];
            int ny = sy + dy[k];
            if (!state.walkable(nx, ny)) {
                continue;
            }
            int d = Math.abs(nx - troll.x) + Math.abs(ny - troll.y);
            if (d < bestDist || (d == bestDist && ny < bestY) || (d == bestDist && ny == bestY && nx < bestX)) {
                bestDist = d;
                bestX = nx;
                bestY = ny;
            }
        }
        return new Action.Move(troll.id, bestX, bestY);
    }
}
```

- [ ] **Step 4 : Lancer → PASS**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : PASS.

### TDD cycle 2 — DROP quand plein et adjacent

- [ ] **Step 5 : Test**

```java
    @Test
    void drops_when_carry_full_and_adjacent() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 3); // adjacent au shack à (5,1)
        t.carry[5] = 3; // 3 wood, capacity 3 → plein
        s.trolls.add(t);

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).containsExactly(new Action.Drop(0));
    }
```

- [ ] **Step 6 : Lancer → PASS**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : PASS (2 tests).

### TDD cycle 3 — MOVE vers shack si plein et non adjacent

- [ ] **Step 7 : Test**

```java
    @Test
    void moves_toward_shack_when_full_and_not_adjacent() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 1, 1, 3); // loin du shack
        t.carry[5] = 3;
        s.trolls.add(t);

        List<Action> actions = new GreedyAi().decide(s);

        // 4 voisins walkable du shack : (6,1) (4,1) (5,0) (5,2)
        // Plus proche de (1,1) : (4,1) Manhattan = 3
        assertThat(actions).containsExactly(new Action.Move(0, 4, 1));
    }
```

- [ ] **Step 8 : Lancer → PASS**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : PASS (3 tests).

### TDD cycle 4 — random walk si pas d'arbre

- [ ] **Step 9 : Test**

```java
    @Test
    void random_walks_when_no_trees() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        // no trees
        s.trolls.add(troll(0, 5, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isInstanceOf(Action.Move.class);
    }
```

- [ ] **Step 10 : Lancer → PASS**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : PASS (4 tests).

### TDD cycle 5 — émet TRAIN en plus des actions trolls au tour 1

- [ ] **Step 11 : Test**

```java
    @Test
    void emits_train_in_addition_to_troll_action_at_turn_1() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        s.turn = 1;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.PLUM.ordinal()] = 100;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.LEMON.ordinal()] = 100;
        s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.IRON.ordinal()] = 100;
        s.trees.add(tree(3, 1, 4));
        s.trolls.add(troll(0, 1, 1, 5));

        List<Action> actions = new GreedyAi().decide(s);

        // 1 action troll + 1 TRAIN
        assertThat(actions).hasSize(2);
        assertThat(actions.get(1)).isInstanceOf(Action.Train.class);
    }
```

- [ ] **Step 12 : Lancer → PASS**

Run : `mvn -q test -Dtest=GreedyAiTest`
Expected : PASS (5 tests).

- [ ] **Step 13 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java
git commit -m "feat(ai): GreedyAi orchestrates per-troll decisions + TRAIN"
```

---

## Task 8 : Câblage Player.main

**Files :**
- Modify : `src/main/java/com/bmrt/cgspring2026/Player.java`

Player.main devient un orchestrateur : lit l'init, instancie `GreedyAi`, boucle `readTurn` → `decide` → joindre les actions avec `;` → println. Si aucune action, émettre `MSG idle` pour produire au moins une ligne.

- [ ] **Step 1 : Lire le fichier actuel pour rappel**

Run : `head -n 50 src/main/java/com/bmrt/cgspring2026/Player.java`
Expected : voir le squelette actuel avec le `MOVE id myShackX myShackY` placeholder.

- [ ] **Step 2 : Remplacer le contenu**

Écrire dans `src/main/java/com/bmrt/cgspring2026/Player.java` :

```java
package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.ai.GreedyAi;
import com.bmrt.cgspring2026.model.GameState;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState state = new GameState();
        GameState.readInit(in, state);
        GreedyAi ai = new GreedyAi();

        while (true) {
            GameState.readTurn(in, state);
            List<Action> actions = ai.decide(state);
            String output;
            if (actions.isEmpty()) {
                output = new Action.Msg("idle").toCommand();
            } else {
                output = actions.stream()
                        .map(Action::toCommand)
                        .collect(Collectors.joining(";"));
            }
            System.out.println(output);
        }
    }
}
```

- [ ] **Step 3 : Compile**

Run : `mvn -q compile`
Expected : BUILD SUCCESS.

- [ ] **Step 4 : Lancer toute la suite de tests**

Run : `mvn -q test`
Expected : Tests run : ≥ 20, Failures : 0, Errors : 0.

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/Player.java
git commit -m "feat: wire Player.main to GreedyAi"
```

---

## Task 9 : Vérifier la fusion FileBuilder

**Files :**
- Run : `src/main/java/com/bmrt/cgspring2026/builder/FileBuilder.java`

`FileBuilder` doit produire un `Player.java` autonome à la racine du projet, contenant toutes les classes du package `ai/` inlinées. Comme il scanne aussi le dossier du package racine (`readPackageClasses`), toutes les classes adjacentes sont incluses ; mais le sous-package `ai/` n'est inclus que par les `import com.bmrt.cgspring2026.ai.X;` présents dans `Player.java`. Vérifions.

- [ ] **Step 1 : Lancer FileBuilder**

Run :
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.bmrt.cgspring2026.builder.FileBuilder \
  -Dexec.args=src/main/java/com/bmrt/cgspring2026/Player.java
```

Si `exec-maven-plugin` n'est pas configuré, alternative :
```bash
mvn -q compile
java -cp target/classes com.bmrt.cgspring2026.builder.FileBuilder src/main/java/com/bmrt/cgspring2026/Player.java
```

Expected : fichier `Player.java` créé/écrasé à la racine du projet.

- [ ] **Step 2 : Vérifier que les classes ai sont bien inlinées**

Run : `grep -c "private static" Player.java`
Expected : au moins 8 occurrences (Role, Zone, TreeZoning, RoleAssigner, TargetSelector, TrainPlanner, RandomWalk, GreedyAi, plus les classes model et action).

Run : `grep -E "private static (final )?(class|enum) (GreedyAi|TrainPlanner|TreeZoning)" Player.java`
Expected : 3 lignes correspondant aux 3 classes.

- [ ] **Step 3 : Compile le fichier soumissible**

Run : `javac -d /tmp/cg-check Player.java`
Expected : BUILD SUCCESS (aucune erreur de compilation).

- [ ] **Step 4 : Commit le fichier régénéré**

```bash
git add Player.java
git commit -m "build: regenerate single-file Player.java for CG submission"
```

---

## Task 10 : Smoke test local

**Files :**
- (aucun nouveau)

Vérifier que le bot répond correctement à un input artificiel ne provoque pas de crash.

- [ ] **Step 1 : Créer un fichier d'input de test**

Créer `/tmp/cg-input.txt` avec :

```
10 4
..........
.0.....1..
..........
..........
0 0 0 0 0 0
0 0 0 0 0 0
1
PLUM 3 1 4 12 0 5
1
0 0 1 1 1 1 0 0 0 0 0 0 0 0
```

- [ ] **Step 2 : Lancer le bot et capter la première sortie**

Run :
```bash
mvn -q compile
timeout 5s java -cp target/classes com.bmrt.cgspring2026.Player < /tmp/cg-input.txt | head -n 1
```

Expected : une ligne contenant une commande valide, par exemple `MOVE 0 3 1` (le troll en (1,1) avec un arbre mature en (3,1) doit move vers l'arbre). Pas de stack trace.

- [ ] **Step 3 : Vérifier l'absence d'exception**

Run :
```bash
timeout 5s java -cp target/classes com.bmrt.cgspring2026.Player < /tmp/cg-input.txt 2>&1 | grep -iE "exception|error" || echo "OK no exception"
```

Expected : `OK no exception`.

- [ ] **Step 4 : Aucun commit nécessaire** (smoke test informel)

---

## Self-Review

### Couverture du spec

| Section spec                                        | Task(s) couvrant                  |
|-----------------------------------------------------|-----------------------------------|
| §2 Architecture (8 fichiers ai)                     | Tasks 1-7                         |
| §3 Boucle de décision                               | Task 7 (GreedyAi)                 |
| §4 TargetSelector — règles greedy                   | Task 4 (6 tests TDD)              |
| §5 TrainPlanner — un seul TRAIN au tour 1           | Task 5 (4 tests TDD)              |
| §6 TreeZoning (Voronoi statique)                    | Task 2 (4 tests TDD)              |
| §7 RoleAssigner                                     | Task 3 (3 tests TDD)              |
| §8 Drop & adjacence shack                           | Task 7 cycles 2-3                 |
| §9 RandomWalk                                       | Task 6 (2 tests TDD)              |
| §10 Hors scope                                      | (aucune tâche, c'est intentionnel)|
| §11 Tests (14 cas + 12b)                            | Distribués sur tasks 2-7          |
| §12 Limites connues                                 | (assumées)                        |
| §13 Cibles de performance                           | Task 10 (smoke test)              |
| §14 Plan d'implémentation                           | Ce document                       |

### Recherche de placeholders

Aucun TBD, TODO, "implement later", "similar to task N" repéré.

### Cohérence des types

- `Set<Long>` partout pour `assignedTrees` (Task 4 et Task 7).
- Clé d'arbre : `(long) tree.y * width + tree.x` (Task 4 implem et Task 4 test #3).
- `Map<Integer, Role>` pour `roles` (Task 3 et Task 7).
- `Action.Train` retour de `TrainPlanner.plan` (peut être null).
- `Action.Move` retour de `RandomWalk.pick` (jamais null, fallback statique).

### Test cardinalité

Tests prévus dans la spec (§11) : 14 cas + 12b = 15.

Tests planifiés ici :
- TreeZoning : 4 ✓ (couvre #13 et plus)
- RoleAssigner : 3
- TargetSelector : 6 (couvre #1, #2, #3, #7, #8)
- TrainPlanner : 4 (couvre #10, #11, #12, #12b)
- RandomWalk : 2 (couvre #9 et une garantie déterminisme bonus)
- GreedyAi : 5 (couvre #4, #5, #6, #9-via-fallback, #14-via-cardinalité TRAIN)

Total : 24 tests. Couverture spec OK.

### Note sur le test #7 (LEADER zone OPP)

Le test "leader_targets_opp_zone_first" (Task 4 cycle 5) couvre #7. Pas besoin d'un test dédié dans GreedyAiTest car la logique est dans TargetSelector et déjà testée à ce niveau.

---

Plan complete and saved to `docs/superpowers/plans/2026-05-12-greedy-chopper.md`.

## Execution Handoff

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
