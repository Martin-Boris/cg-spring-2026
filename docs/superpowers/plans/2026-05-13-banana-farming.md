# Banana Farming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** Ajouter un module `BananaFarmer` qui fait farmer un troll LOCAL adjacent au shack via un cycle PICK → PLANT → CHOP → DROP de 4 tours, gated par une détection de menace ennemie ≤ 3 tours.

**Architecture :** Nouvelle classe statique `ai/BananaFarmer.java` encapsulant la machine à états (4 états inférés stateless). `GreedyAi` initialise un `int[1] bananaBudget` au début de `decide` et hook `BananaFarmer.plan(...)` pour les trolls LOCAL avant la branche carryFull existante. Pas de modification de `TargetSelector`, `RoleAssigner`, `TreeZoning`, `TrainPlanner`, `Action`, modèle.

**Tech Stack :** Java 21, Maven, JUnit Jupiter 5.6.3, AssertJ 3.18.1.

**Spec source :** `docs/superpowers/specs/2026-05-13-banana-farming-design.md`.

---

## Vue d'ensemble des fichiers

**À créer (main) :**
- `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java` — classe finale statique exposant `static Action plan(Troll, GameState, int[], Set<Long>)`.

**À créer (test) :**
- `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java` — tests unitaires de la machine à états et de `isSafeToFarm`.

**À modifier (main) :**
- `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java` — initialisation du `bananaBudget` dans `decide`, signature et appel ajoutés dans `decideForTroll`.

**À modifier (test) :**
- `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java` — ajout de tests d'intégration pour le cycle banana farming.

**À régénérer :**
- `Player.java` (racine du repo) — via `FileBuilder` après modifications (fichier de soumission CG inlinant toutes les classes).

---

## Contexte technique

### Référentiel des constantes du modèle

- `ResourceType.BANANA.ordinal() == 3`
- `ResourceType.WOOD.ordinal() == 5`
- `Troll.carry` est `int[6]` indexé par `ResourceType.ordinal()`.
- `troll.carryTotal()` somme tout le carry.
- `Action.Pick(int trollId, ResourceType type)`, `Action.Plant(int trollId, TreeType type)`, `Action.Chop(int trollId)`, `Action.Drop(int trollId)`.
- Adjacence shack : `Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) == 1`.

### Convention de clé `assignedTrees`

Identique à `TargetSelector.key` : `(long) tree.y * state.width + tree.x`.

### Pattern test existant

Les tests AI construisent un `GameState` via un helper `openMap(width, height, myX, myY, oppX, oppY)` qui pose `GRASS` partout et place les deux shacks. On reprendra le même pattern dans `BananaFarmerTest`. AssertJ pour les assertions.

### Pré-requis dans les tests pour le farming

Pour qu'un troll soit candidat au farming, il faut :
- `troll.player == 0`
- `troll.x/y` adjacent à `state.myShackX/Y` (Manhattan = 1)
- `troll.carryCapacity >= 1`

Pour FARM_PICK il faut **en plus** :
- `state.myShackInv[BANANA] >= 1` (initialise `bananaBudget[0]`)
- Aucun troll ennemi avec `movementSpeed > 0` à `Manhattan/movementSpeed <= 3` de la case.

---

## Task 1 : Squelette de `BananaFarmer` retournant toujours `null`

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Create: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Écrire un test qui exige l'existence de `BananaFarmer.plan`**

Créer `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java` avec :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class BananaFarmerTest {

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
    void returns_null_when_troll_not_adjacent_to_shack() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        Troll t = troll(0, 5, 1, 5);
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
    }
}
```

- [ ] **Step 2: Lancer le test, vérifier l'échec (compilation)**

Run: `mvn -q -Dtest=BananaFarmerTest#returns_null_when_troll_not_adjacent_to_shack test`
Expected: **FAIL** — `cannot find symbol class BananaFarmer`.

- [ ] **Step 3: Créer le squelette `BananaFarmer` qui retourne toujours null**

Créer `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java` avec :

```java
package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Troll;

import java.util.Set;

public final class BananaFarmer {

    private BananaFarmer() {
    }

    public static Action plan(Troll troll,
                              GameState state,
                              int[] bananaBudget,
                              Set<Long> assignedTrees) {
        return null;
    }
}
```

- [ ] **Step 4: Relancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=BananaFarmerTest#returns_null_when_troll_not_adjacent_to_shack test`
Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): scaffold BananaFarmer returning null"
```

---

## Task 2 : Implémenter FARM_DROP

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter le test FARM_DROP**

Ajouter dans `BananaFarmerTest` :

```java
@Test
void drops_when_adjacent_to_shack_and_carrying_wood() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.WOOD.ordinal()] = 1;
    s.trolls.add(t);
    int[] budget = {0};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Drop(0));
}
```

- [ ] **Step 2: Lancer le test, vérifier l'échec**

Run: `mvn -q -Dtest=BananaFarmerTest#drops_when_adjacent_to_shack_and_carrying_wood test`
Expected: **FAIL** — `expecting Drop(0) but was null`.

- [ ] **Step 3: Implémenter préconditions globales + FARM_DROP**

Remplacer le corps de `BananaFarmer.plan` par :

```java
public static Action plan(Troll troll,
                          GameState state,
                          int[] bananaBudget,
                          Set<Long> assignedTrees) {
    if (troll.player != 0) {
        return null;
    }
    if (troll.carryCapacity <= 0) {
        return null;
    }
    if (Math.abs(troll.x - state.myShackX) + Math.abs(troll.y - state.myShackY) != 1) {
        return null;
    }

    int woodIdx = com.bmrt.cgspring2026.model.ResourceType.WOOD.ordinal();
    if (troll.carry[woodIdx] >= 1) {
        return new Action.Drop(troll.id);
    }

    return null;
}
```

- [ ] **Step 4: Relancer les tests, vérifier qu'ils passent tous**

Run: `mvn -q -Dtest=BananaFarmerTest test`
Expected: **PASS** (2/2).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): BananaFarmer FARM_DROP state"
```

---

## Task 3 : Implémenter FARM_CHOP avec marquage `assignedTrees`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter le test FARM_CHOP**

Ajouter dans `BananaFarmerTest` (et l'import si manquant : `com.bmrt.cgspring2026.model.Tree`, `com.bmrt.cgspring2026.model.TreeType`) :

```java
@Test
void chops_size_zero_banana_under_troll_and_marks_assigned() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(7, 6, 1, 5);
    s.trolls.add(t);
    Tree banana = new Tree();
    banana.type = TreeType.BANANA;
    banana.x = 6;
    banana.y = 1;
    banana.size = 0;
    banana.health = 1;
    s.trees.add(banana);
    int[] budget = {0};
    java.util.Set<Long> assigned = new HashSet<>();

    Action a = BananaFarmer.plan(t, s, budget, assigned);

    assertThat(a).isEqualTo(new Action.Chop(7));
    assertThat(assigned).contains((long) 1 * 10 + 6);
}
```

- [ ] **Step 2: Lancer le test, vérifier l'échec**

Run: `mvn -q -Dtest=BananaFarmerTest#chops_size_zero_banana_under_troll_and_marks_assigned test`
Expected: **FAIL** — `expecting Chop(7) but was null`.

- [ ] **Step 3: Ajouter FARM_CHOP après FARM_DROP**

Dans `BananaFarmer.plan`, **après** le bloc FARM_DROP et **avant** `return null`, ajouter :

```java
for (com.bmrt.cgspring2026.model.Tree tree : state.trees) {
    if (tree.x == troll.x && tree.y == troll.y
            && tree.type == com.bmrt.cgspring2026.model.TreeType.BANANA
            && tree.size == 0) {
        assignedTrees.add((long) tree.y * state.width + tree.x);
        return new Action.Chop(troll.id);
    }
}
```

- [ ] **Step 4: Relancer tous les tests, vérifier qu'ils passent**

Run: `mvn -q -Dtest=BananaFarmerTest test`
Expected: **PASS** (3/3).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): BananaFarmer FARM_CHOP state with assigned tree marking"
```

---

## Task 4 : Implémenter FARM_PLANT

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter trois tests pour FARM_PLANT**

Ajouter dans `BananaFarmerTest` :

```java
@Test
void plants_banana_when_carrying_exactly_one_banana_and_tile_empty() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 1;
    s.trolls.add(t);
    int[] budget = {0};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Plant(0, TreeType.BANANA));
}

@Test
void does_not_plant_when_carry_mixed_with_other_fruit() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 1;
    t.carry[com.bmrt.cgspring2026.model.ResourceType.PLUM.ordinal()] = 1;
    s.trolls.add(t);
    int[] budget = {0};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isNull();
}

@Test
void does_not_plant_when_tile_already_has_tree() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 1;
    s.trolls.add(t);
    Tree existing = new Tree();
    existing.type = TreeType.PLUM;
    existing.x = 6;
    existing.y = 1;
    existing.size = 2;
    s.trees.add(existing);
    int[] budget = {0};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isNull();
}
```

- [ ] **Step 2: Lancer ces tests, vérifier que le premier échoue**

Run: `mvn -q -Dtest=BananaFarmerTest#plants_banana_when_carrying_exactly_one_banana_and_tile_empty test`
Expected: **FAIL** — `expecting Plant(0, BANANA) but was null`.

Run: `mvn -q -Dtest=BananaFarmerTest#does_not_plant_when_carry_mixed_with_other_fruit test`
Expected: **PASS** (l'état actuel retourne déjà `null`, comportement attendu, mais le test garantit la non-régression après implémentation).

Run: `mvn -q -Dtest=BananaFarmerTest#does_not_plant_when_tile_already_has_tree test`
Expected: **PASS** (idem, sera vrai-positif après implémentation).

- [ ] **Step 3: Ajouter FARM_PLANT après FARM_CHOP**

Dans `BananaFarmer.plan`, **après** la boucle FARM_CHOP et **avant** `return null`, ajouter :

```java
int bananaIdx = com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal();
if (troll.carry[bananaIdx] == 1 && troll.carryTotal() == 1) {
    boolean tileFree = true;
    for (com.bmrt.cgspring2026.model.Tree tree : state.trees) {
        if (tree.x == troll.x && tree.y == troll.y) {
            tileFree = false;
            break;
        }
    }
    if (tileFree) {
        return new Action.Plant(troll.id, com.bmrt.cgspring2026.model.TreeType.BANANA);
    }
}
```

- [ ] **Step 4: Relancer tous les tests, vérifier qu'ils passent**

Run: `mvn -q -Dtest=BananaFarmerTest test`
Expected: **PASS** (6/6).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): BananaFarmer FARM_PLANT state"
```

---

## Task 5 : Implémenter `isSafeToFarm` (méthode privée testée via FARM_PICK)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

NB : `isSafeToFarm` est implémenté à ce tour mais testé indirectement via le comportement FARM_PICK de Task 6. On l'ajoute maintenant pour que Task 6 ait juste à câbler l'état PICK. Tests de menace explicites dans Task 7.

- [ ] **Step 1: Ajouter la méthode privée `isSafeToFarm` dans `BananaFarmer`**

À la fin de `BananaFarmer`, juste avant la dernière accolade de la classe :

```java
static boolean isSafeToFarm(int tileX, int tileY, GameState state) {
    for (Troll e : state.trolls) {
        if (e.player == 0) {
            continue;
        }
        if (e.movementSpeed <= 0) {
            continue;
        }
        int d = Math.abs(e.x - tileX) + Math.abs(e.y - tileY);
        int reach = (d + e.movementSpeed - 1) / e.movementSpeed;
        if (reach <= 3) {
            return false;
        }
    }
    return true;
}
```

NB : visibilité **package-private** (pas `private`) pour pouvoir être testée directement par `BananaFarmerTest`.

- [ ] **Step 2: Ajouter un test direct sur `isSafeToFarm` avec aucun ennemi**

Ajouter dans `BananaFarmerTest` :

```java
@Test
void is_safe_to_farm_when_no_enemy() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);

    assertThat(BananaFarmer.isSafeToFarm(6, 1, s)).isTrue();
}
```

- [ ] **Step 3: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=BananaFarmerTest#is_safe_to_farm_when_no_enemy test`
Expected: **PASS**.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): BananaFarmer isSafeToFarm helper"
```

---

## Task 6 : Implémenter FARM_PICK avec décrément du budget

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter trois tests pour FARM_PICK**

Ajouter dans `BananaFarmerTest` :

```java
@Test
void picks_banana_when_empty_adjacent_and_budget_available() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    s.trolls.add(t);
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Pick(0, com.bmrt.cgspring2026.model.ResourceType.BANANA));
    assertThat(budget[0]).isZero();
}

@Test
void does_not_pick_when_budget_zero() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    s.trolls.add(t);
    int[] budget = {0};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isNull();
    assertThat(budget[0]).isZero();
}

@Test
void does_not_pick_when_carry_not_empty() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.PLUM.ordinal()] = 1;
    s.trolls.add(t);
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isNull();
    assertThat(budget[0]).isOne();
}
```

- [ ] **Step 2: Lancer le premier test, vérifier l'échec**

Run: `mvn -q -Dtest=BananaFarmerTest#picks_banana_when_empty_adjacent_and_budget_available test`
Expected: **FAIL** — `expecting Pick(0, BANANA) but was null`.

- [ ] **Step 3: Ajouter FARM_PICK après FARM_PLANT**

Dans `BananaFarmer.plan`, **après** le bloc FARM_PLANT et **avant** `return null`, ajouter :

```java
if (troll.carryTotal() == 0 && bananaBudget[0] >= 1 && isSafeToFarm(troll.x, troll.y, state)) {
    bananaBudget[0]--;
    return new Action.Pick(troll.id, com.bmrt.cgspring2026.model.ResourceType.BANANA);
}
```

- [ ] **Step 4: Relancer tous les tests, vérifier qu'ils passent**

Run: `mvn -q -Dtest=BananaFarmerTest test`
Expected: **PASS** (10/10).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/BananaFarmer.java src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "feat(ai): BananaFarmer FARM_PICK state with budget decrement"
```

---

## Task 7 : Tests de menace ennemie (FARM_PICK gating)

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter trois tests de menace**

Ajouter dans `BananaFarmerTest` :

```java
private Troll enemy(int id, int x, int y, int speed) {
    Troll t = new Troll();
    t.id = id;
    t.player = 1;
    t.x = x;
    t.y = y;
    t.movementSpeed = speed;
    return t;
}

@Test
void does_not_pick_when_enemy_reach_is_three_turns() {
    // distance 6, speed 2 => reach = 3 => unsafe
    GameState s = openMap(20, 4, 5, 1, 15, 2);
    Troll t = troll(0, 6, 1, 5);
    s.trolls.add(t);
    s.trolls.add(enemy(99, 12, 1, 2));
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isNull();
    assertThat(budget[0]).isOne();
}

@Test
void picks_when_enemy_reach_is_four_turns() {
    // distance 7, speed 2 => reach = 4 => safe
    GameState s = openMap(20, 4, 5, 1, 15, 2);
    Troll t = troll(0, 6, 1, 5);
    s.trolls.add(t);
    s.trolls.add(enemy(99, 13, 1, 2));
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Pick(0, com.bmrt.cgspring2026.model.ResourceType.BANANA));
}

@Test
void ignores_enemy_with_zero_movement_speed() {
    // Adjacent enemy but movementSpeed=0 => not a threat
    GameState s = openMap(20, 4, 5, 1, 15, 2);
    Troll t = troll(0, 6, 1, 5);
    s.trolls.add(t);
    s.trolls.add(enemy(99, 7, 1, 0));
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Pick(0, com.bmrt.cgspring2026.model.ResourceType.BANANA));
}
```

- [ ] **Step 2: Lancer ces tests, vérifier qu'ils passent**

Run: `mvn -q -Dtest=BananaFarmerTest test`
Expected: **PASS** (13/13). Les trois nouveaux tests passent grâce à `isSafeToFarm` déjà implémenté en Task 5.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "test(ai): cover enemy threat gating in BananaFarmer"
```

---

## Task 8 : Test de priorité d'état (DROP > CHOP > PLANT > PICK)

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java`

- [ ] **Step 1: Ajouter un test de priorité**

Ajouter dans `BananaFarmerTest` :

```java
@Test
void drop_takes_priority_over_chop_when_both_apply() {
    // Troll carries wood AND stands on a size-0 banana — DROP must win.
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    Troll t = troll(0, 6, 1, 5);
    t.carry[com.bmrt.cgspring2026.model.ResourceType.WOOD.ordinal()] = 1;
    s.trolls.add(t);
    Tree banana = new Tree();
    banana.type = TreeType.BANANA;
    banana.x = 6;
    banana.y = 1;
    banana.size = 0;
    banana.health = 1;
    s.trees.add(banana);
    int[] budget = {1};

    Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

    assertThat(a).isEqualTo(new Action.Drop(0));
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=BananaFarmerTest#drop_takes_priority_over_chop_when_both_apply test`
Expected: **PASS** (ordre déjà respecté par l'implémentation).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ai/BananaFarmerTest.java
git commit -m "test(ai): verify BananaFarmer state priority ordering"
```

---

## Task 9 : Hook `BananaFarmer` dans `GreedyAi`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

- [ ] **Step 1: Ajouter un test d'intégration : LOCAL adjacent + 1 banana en stock → PICK**

Ajouter dans `GreedyAiTest` :

```java
@Test
void local_troll_adjacent_picks_banana_when_stock_available() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 1;
    s.trolls.add(troll(0, 6, 1, 5));

    List<Action> actions = new GreedyAi().decide(s);

    assertThat(actions).containsExactly(
            new Action.Pick(0, com.bmrt.cgspring2026.model.ResourceType.BANANA));
}
```

- [ ] **Step 2: Lancer le test, vérifier l'échec**

Run: `mvn -q -Dtest=GreedyAiTest#local_troll_adjacent_picks_banana_when_stock_available test`
Expected: **FAIL** — l'action retournée sera un `Move` (RandomWalk fallback car pas d'arbre).

- [ ] **Step 3: Modifier `GreedyAi.decide` pour initialiser `bananaBudget`**

Dans `src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java`, dans la méthode `decide`, juste après le tri `myTrolls.sort(...)` et avant la création de `assignedTrees` (ou regrouper) :

Remplacer le bloc :
```java
Set<Long> assignedTrees = new HashSet<>();
List<Action> actions = new ArrayList<>(myTrolls.size() + 1);
for (Troll t : myTrolls) {
    actions.add(decideForTroll(t, roles.get(t.id), state, assignedTrees));
}
```

Par :
```java
Set<Long> assignedTrees = new HashSet<>();
int[] bananaBudget = { state.myShackInv[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] };
List<Action> actions = new ArrayList<>(myTrolls.size() + 1);
for (Troll t : myTrolls) {
    actions.add(decideForTroll(t, roles.get(t.id), state, assignedTrees, bananaBudget));
}
```

- [ ] **Step 4: Modifier la signature et le corps de `decideForTroll`**

Remplacer la méthode `decideForTroll` par :

```java
private Action decideForTroll(Troll troll, Role role, GameState state,
                              Set<Long> assignedTrees, int[] bananaBudget) {
    if (role == Role.LOCAL) {
        Action farm = BananaFarmer.plan(troll, state, bananaBudget, assignedTrees);
        if (farm != null) {
            return farm;
        }
    }
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
```

- [ ] **Step 5: Lancer tous les tests, vérifier qu'ils passent**

Run: `mvn -q test`
Expected: **PASS** (tous les tests précédents + le nouveau).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/GreedyAi.java src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java
git commit -m "feat(ai): wire BananaFarmer into GreedyAi decision loop"
```

---

## Task 10 : Test d'intégration — partage du budget banana entre 2 LOCAL

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

- [ ] **Step 1: Ajouter le test multi-troll**

Ajouter dans `GreedyAiTest` :

```java
@Test
void two_local_trolls_share_one_banana_budget() {
    // Shack at (5,1). Adjacent walkable tiles: (4,1), (6,1), (5,0)→OOB if h=4? Use (5,2).
    // Map 10x4, two LOCAL trolls adjacent, only 1 banana in stock → first by id picks, second falls back.
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 1;
    s.trolls.add(troll(0, 4, 1, 5));
    s.trolls.add(troll(1, 6, 1, 5));

    List<Action> actions = new GreedyAi().decide(s);

    // RoleAssigner with 2 trolls picks one LEADER (highest score, tie-break lowest id).
    // Both trolls have identical stats → LEADER = id 0, LOCAL = id 1.
    // LEADER (id 0) goes to TargetSelector → null tree → RandomWalk.Move
    // LOCAL (id 1) is adjacent → PICK BANANA.
    assertThat(actions).hasSize(2);
    assertThat(actions).contains(
            new Action.Pick(1, com.bmrt.cgspring2026.model.ResourceType.BANANA));
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=GreedyAiTest#two_local_trolls_share_one_banana_budget test`
Expected: **PASS**.

- [ ] **Step 3: Ajouter un test pour le cas budget=0**

```java
@Test
void no_pick_when_banana_stock_is_zero() {
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 0;
    s.trolls.add(troll(0, 6, 1, 5));

    List<Action> actions = new GreedyAi().decide(s);

    assertThat(actions).hasSize(1);
    assertThat(actions.get(0)).isNotInstanceOf(Action.Pick.class);
}
```

- [ ] **Step 4: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=GreedyAiTest#no_pick_when_banana_stock_is_zero test`
Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java
git commit -m "test(ai): cover budget sharing and zero-stock fallback in GreedyAi"
```

---

## Task 11 : Test d'intégration — cycle complet PICK→PLANT→CHOP→DROP

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

- [ ] **Step 1: Ajouter le test du cycle (4 décisions simulées sur des `GameState` distincts)**

Ajouter dans `GreedyAiTest` :

```java
@Test
void full_farming_cycle_emits_expected_actions_per_turn() {
    int bananaIdx = com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal();
    int woodIdx = com.bmrt.cgspring2026.model.ResourceType.WOOD.ordinal();
    GreedyAi ai = new GreedyAi();

    // Turn N: empty troll adjacent, 1 banana in stock → PICK.
    GameState s1 = openMap(10, 4, 5, 1, 8, 2);
    s1.myShackInv[bananaIdx] = 1;
    s1.trolls.add(troll(0, 6, 1, 5));
    assertThat(ai.decide(s1)).containsExactly(
            new Action.Pick(0, com.bmrt.cgspring2026.model.ResourceType.BANANA));

    // Turn N+1: troll now carries 1 banana, no tree on tile → PLANT.
    GameState s2 = openMap(10, 4, 5, 1, 8, 2);
    Troll t2 = troll(0, 6, 1, 5);
    t2.carry[bananaIdx] = 1;
    s2.trolls.add(t2);
    assertThat(ai.decide(s2)).containsExactly(
            new Action.Plant(0, TreeType.BANANA));

    // Turn N+2: tree appears under troll, size 0 → CHOP.
    GameState s3 = openMap(10, 4, 5, 1, 8, 2);
    s3.trolls.add(troll(0, 6, 1, 5));
    Tree b = new Tree();
    b.type = TreeType.BANANA;
    b.x = 6;
    b.y = 1;
    b.size = 0;
    b.health = 1;
    s3.trees.add(b);
    assertThat(ai.decide(s3)).containsExactly(new Action.Chop(0));

    // Turn N+3: troll carries 1 wood → DROP.
    GameState s4 = openMap(10, 4, 5, 1, 8, 2);
    Troll t4 = troll(0, 6, 1, 5);
    t4.carry[woodIdx] = 1;
    s4.trolls.add(t4);
    assertThat(ai.decide(s4)).containsExactly(new Action.Drop(0));
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=GreedyAiTest#full_farming_cycle_emits_expected_actions_per_turn test`
Expected: **PASS**.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java
git commit -m "test(ai): assert full 4-turn banana farming cycle"
```

---

## Task 12 : Test de non-régression — LEADER ne farm pas

**Files:**
- Modify: `src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java`

- [ ] **Step 1: Ajouter le test LEADER**

Ajouter dans `GreedyAiTest` :

```java
@Test
void leader_does_not_farm_even_when_adjacent_and_empty() {
    // Single troll → RoleAssigner returns LOCAL (single-troll edge case),
    // so we need at least 2 trolls with distinct stats to elect a LEADER.
    // Troll id=0 has the highest stats → LEADER.
    GameState s = openMap(10, 4, 5, 1, 8, 2);
    s.myShackInv[com.bmrt.cgspring2026.model.ResourceType.BANANA.ordinal()] = 5;
    Troll leader = troll(0, 6, 1, 10);
    leader.movementSpeed = 5;
    leader.chopPower = 5;
    Troll local = troll(1, 1, 1, 1);
    s.trolls.add(leader);
    s.trolls.add(local);
    // Provide one mature tree far away so LEADER targets it instead of random-walking.
    s.trees.add(tree(8, 1, 4));

    List<Action> actions = new GreedyAi().decide(s);

    // The LEADER (id=0) action must NOT be a Pick.
    Action leaderAction = actions.stream()
            .filter(a -> a instanceof Action.Move m && m.trollId() == 0
                    || a instanceof Action.Pick p && p.trollId() == 0
                    || a instanceof Action.Chop c && c.trollId() == 0)
            .findFirst()
            .orElseThrow();
    assertThat(leaderAction).isNotInstanceOf(Action.Pick.class);
}
```

- [ ] **Step 2: Lancer le test, vérifier qu'il passe**

Run: `mvn -q -Dtest=GreedyAiTest#leader_does_not_farm_even_when_adjacent_and_empty test`
Expected: **PASS** (LEADER skip le hook, va vers l'arbre à (8,1)).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bmrt/cgspring2026/ai/GreedyAiTest.java
git commit -m "test(ai): verify LEADER role bypasses banana farming hook"
```

---

## Task 13 : Test de non-régression — anciens tests `GreedyAi` toujours verts

**Files:** (lecture seule)

- [ ] **Step 1: Lancer la suite complète**

Run: `mvn -q test`
Expected: **PASS** sur 100% des tests, incluant :
- `GreedyAiTest#chops_when_on_tree_tile` ✓
- `GreedyAiTest#drops_when_carry_full_and_adjacent` ✓
- `GreedyAiTest#moves_toward_shack_when_full_and_not_adjacent` ✓
- `GreedyAiTest#random_walks_when_no_trees` ✓
- `GreedyAiTest#emits_train_in_addition_to_troll_action_at_turn_1` ✓
- Tous les `BananaFarmerTest` ✓
- Tous les `TargetSelectorTest`, `RoleAssignerTest`, `TrainPlannerTest`, `TreeZoningTest`, `RandomWalkTest` ✓

Si un test échoue : arrêter, analyser, corriger sans modifier le test (sauf si le test était erroné).

- [ ] **Step 2: Aucun commit nécessaire si tous verts**

Le tour est passe-passe : juste valider l'état avant de régénérer le Player.java.

---

## Task 14 : Régénérer `Player.java` pour la soumission CG

**Files:**
- Modify: `Player.java` (racine)

- [ ] **Step 1: Lancer `FileBuilder` pour régénérer le Player.java de soumission**

Run: `mvn -q compile exec:java -Dexec.mainClass=com.bmrt.cgspring2026.builder.FileBuilder -Dexec.args=src/main/java/com/bmrt/cgspring2026/Player.java`

Expected: le `Player.java` à la racine du repo est mis à jour et inclut désormais la classe `BananaFarmer` en classe interne.

- [ ] **Step 2: Vérifier la présence de `BananaFarmer` dans Player.java**

Run: `grep -n "class BananaFarmer" Player.java`
Expected: au moins une ligne `private static class BananaFarmer {` ou similaire.

- [ ] **Step 3: Vérifier que le fichier compile**

Run: `mvn -q compile`
Expected: succès.

- [ ] **Step 4: Commit**

```bash
git add Player.java
git commit -m "chore(builder): regenerate Player.java with BananaFarmer"
```

---

## Self-Review (réalisé en écrivant le plan)

**Couverture spec :**
- §1 (objectif) → Tasks 1-9 implémentent le cycle complet.
- §2 (architecture, `BananaFarmer` + hook GreedyAi) → Tasks 1, 9.
- §3 (préconditions globales) → Task 2 (étape 3).
- §4 (4 états dans l'ordre) → Tasks 2 (DROP), 3 (CHOP), 4 (PLANT), 6 (PICK).
- §5 (isSafeToFarm + Manhattan + ennemi speed=0) → Tasks 5, 7.
- §6 (intégration GreedyAi, budget) → Task 9.
- §7 (conflit d'arbre, marquage assignedTrees) → Task 3.
- §8 (cas limites) → couverts par Tasks 2-7 + intégration Tasks 9-12.
- §9 (tests TDD) → entièrement matérialisés sur Tasks 1-12.
- §10 (hors scope) → respecté (pas de BFS menace, pas de RoleAssigner modifié, pas d'optim cycle restant).

**Placeholders :** aucun.

**Cohérence des signatures :** `BananaFarmer.plan(Troll, GameState, int[], Set<Long>)` identique partout. `isSafeToFarm(int, int, GameState)` identique partout. Indices ordinaux explicites (pas de magic numbers : on utilise `.ordinal()`).

**Régénération Player.java :** Task 14 finalise. Sans elle, la soumission CG ne contiendrait pas `BananaFarmer`.
