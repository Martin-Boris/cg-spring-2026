# TrainPlanner Decoupled Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** Refactor `TrainPlanner.plan` pour dimensionner indépendamment `moveSpeed/carryCapacity/chopPower` sur leur propre budget de ressource et supprimer le fallback `Train(v,v,0,0)` qui spawnait un troll inutile.

**Architecture :** Refonte locale d'une seule classe (`ai/TrainPlanner.java`) + ses tests (`ai/TrainPlannerTest.java`). Algorithme : pour chaque stat, fonction `maxAffordable(stock, n)` retourne le plus grand `v ∈ [1..V_MAX]` tel que `stock ≥ n + v²` (0 sinon). Émettre `Train(vSpeed, vCarry, 0, vChop)` ssi les trois valeurs sont ≥ 1, sinon `null`. Pas de modification d'API publique (toujours `Action.Train plan(GameState)`).

**Tech Stack :** Java 21, Maven, JUnit Jupiter 5.6.3, AssertJ 3.18.1.

**Spec source :** `docs/superpowers/specs/2026-05-12-greedy-chopper-design.md` §5 et §11.

---

## Vue d'ensemble des fichiers

**À modifier (main) :**
- `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java` — réécriture de `plan(GameState)` et ajout d'une méthode privée `maxAffordable(int stock, int n)`.

**À modifier (test) :**
- `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java` — suppression de `falls_back_to_zero_chop_when_no_iron_at_turn_1`, ajout de 5 nouveaux tests, conservation des 3 autres.

**À régénérer :**
- `Player.java` (racine du repo) — via `FileBuilder` après modification de `TrainPlanner` (le `Player.java` à la racine est le fichier de soumission CG, inlinant toutes les classes).

**Aucune autre classe touchée** : `GreedyAi`, `RoleAssigner`, `TargetSelector`, `Action.Train` (record) etc. restent inchangées.

---

## Contexte technique

### Coût d'une stat (rappel du brief)

Pour un attribut de valeur `v` chez un joueur qui possède `n` trolls, le coût TRAIN est `n + v²` ressources du type associé :
- `movementSpeed` → PLUM
- `carryCapacity` → LEMON
- `chopPower` → IRON
- `harvestPower` → APPLE (toujours 0 en V1 → coût `n + 0 = n` APPLE)

`harvestPower = 0` reste forcé par le pari "rush wood" de la V1. Le coût `n` en APPLE n'est PAS vérifié dans l'algo actuel (on suppose que APPLE est toujours disponible en quantité `n`). **Ne pas ajouter cette vérification** : c'est volontaire et hors scope (le brief n'a pas demandé ce contrôle).

### Modèle `GameState`

Les tests construisent un `GameState` directement (constructeurs sans args, champs publics). Lecture des stocks via `state.myShackInv[ResourceType.PLUM.ordinal()]` etc. Le helper `stateWithTurn(turn, plums, lemons, iron)` existe déjà dans `TrainPlannerTest` ; on le réutilise tel quel.

### Comportement actuel à conserver

- `state.turn != 1` → return `null` (politique "1 seul TRAIN, tour 1").
- Comptage `n` via boucle sur `state.trolls` filtré sur `player == 0`.
- `V_MAX = 4` (constante publique de la classe).

### Comportement actuel à supprimer

- La seconde boucle qui retourne `Train(v, v, 0, 0)` quand IRON est insuffisant (lignes 29-34 du fichier actuel). Disparait complètement.
- Le couplage des trois stats sur un même `v` descendant (boucle unique 23-28). Remplacé par trois calculs indépendants.

---

## Task 1 : Supprimer le fallback `chopPower=0`

**Files :**
- Modify : `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java:38-45` (remplacer le test du fallback)
- Modify : `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java:29-34` (supprimer la 2ᵉ boucle)

- [ ] **Step 1 : Remplacer le test `falls_back_to_zero_chop_when_no_iron_at_turn_1` par `returns_null_when_chop_power_zero`**

Ouvrir `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java`. Repérer le bloc :

```java
    @Test
    void falls_back_to_zero_chop_when_no_iron_at_turn_1() {
        GameState s = stateWithTurn(1, 5, 5, 0);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 2, 0, 0));
    }
```

Le remplacer par :

```java
    @Test
    void returns_null_when_chop_power_zero() {
        GameState s = stateWithTurn(1, 5, 5, 0);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
```

(Mêmes entrées que l'ancien test, assertion inversée : on n'émet plus de TRAIN dégradé quand IRON est insuffisant.)

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue (RED)**

Run : `mvn -Dtest=TrainPlannerTest#returns_null_when_chop_power_zero test`

Expected : FAIL. La 2ᵉ boucle de `TrainPlanner.plan` retourne actuellement `Train(2, 2, 0, 0)` pour ces inputs ; le test attend `null`.

- [ ] **Step 3 : Supprimer la seconde boucle dans `TrainPlanner.plan`**

Ouvrir `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java`. Le corps actuel de `plan` :

```java
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
        for (int v = V_MAX; v >= 1; v--) {
            int cost = n + v * v;
            if (plums >= cost && lemons >= cost) {
                return new Action.Train(v, v, 0, 0);
            }
        }
        return null;
    }
```

Le remplacer par (retire la 2ᵉ boucle, garde le reste tel quel pour cette étape) :

```java
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
        return null;
    }
```

- [ ] **Step 4 : Relancer la suite TrainPlanner**

Run : `mvn -Dtest=TrainPlannerTest test`

Expected : tous les tests passent. Vérifier en particulier `returns_null_when_chop_power_zero` PASS (assertion null OK car la 2ᵉ boucle n'existe plus) et `emits_max_affordable_train_at_turn_1` PASS (`stateWithTurn(1, 5, 5, 5)` → `Train(2,2,0,2)` via la 1ʳᵉ boucle, inchangé).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java \
        src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java
git commit -m "refactor(ai): drop TRAIN fallback that spawned 0-chop trolls"
```

---

## Task 2 : Découpler le calcul par stat

**Files :**
- Modify : `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java` (ajouter 2 tests à la fin de la classe)
- Modify : `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java:14-32` (réécrire `plan` + ajouter `maxAffordable`)

- [ ] **Step 1 : Ajouter le test `decouples_stats_with_asymmetric_budgets`**

Dans `TrainPlannerTest.java`, ajouter à la fin de la classe (avant le `}` final) :

```java
    @Test
    void decouples_stats_with_asymmetric_budgets() {
        // PLUM=20, LEMON=20, IRON=2 avec n=1
        //   maxAffordable(20, 1) = 4 (1 + 16 = 17 <= 20)
        //   maxAffordable(2,  1) = 1 (1 + 1  = 2  <= 2)
        GameState s = stateWithTurn(1, 20, 20, 2);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(4, 4, 0, 1));
    }
```

- [ ] **Step 2 : Ajouter le test `picks_different_v_per_stat`**

Toujours dans `TrainPlannerTest.java`, ajouter juste après le test précédent :

```java
    @Test
    void picks_different_v_per_stat() {
        // n=1, PLUM=5 → v=2 (1+4=5), LEMON=11 → v=3 (1+9=10), IRON=2 → v=1 (1+1=2)
        GameState s = stateWithTurn(1, 5, 11, 2);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isEqualTo(new Action.Train(2, 3, 0, 1));
    }
```

- [ ] **Step 3 : Lancer les deux nouveaux tests pour vérifier qu'ils échouent (RED)**

Run : `mvn "-Dtest=TrainPlannerTest#decouples_stats_with_asymmetric_budgets+TrainPlannerTest#picks_different_v_per_stat" test`

Expected : FAIL pour les deux. Avec l'implémentation actuelle (issue de Task 1) :
- `(20, 20, 2, n=1)` → 1ʳᵉ boucle ne match qu'à `v=1` (coût `1+1=2 ≤ 2` côté IRON) donc retourne `Train(1,1,0,1)` au lieu de `Train(4,4,0,1)`.
- `(5, 11, 2, n=1)` → idem, retourne `Train(1,1,0,1)` au lieu de `Train(2,3,0,1)`.

- [ ] **Step 4 : Réécrire `plan` avec découplage par stat**

Dans `TrainPlanner.java`, remplacer le corps de `plan` (la version issue de Task 1) par :

```java
    public static Action.Train plan(GameState state) {
        if (state.turn != 1) {
            return null;
        }
        int n = countMyTrolls(state);
        int vSpeed = maxAffordable(state.myShackInv[ResourceType.PLUM.ordinal()], n);
        int vCarry = maxAffordable(state.myShackInv[ResourceType.LEMON.ordinal()], n);
        int vChop = maxAffordable(state.myShackInv[ResourceType.IRON.ordinal()], n);

        if (vChop == 0) {
            return null;
        }
        return new Action.Train(vSpeed, vCarry, 0, vChop);
    }

    private static int maxAffordable(int stock, int n) {
        for (int v = V_MAX; v >= 1; v--) {
            if (stock >= n + v * v) {
                return v;
            }
        }
        return 0;
    }
```

> Note : on garde la garde `vChop == 0` uniquement à cette étape — les gardes sur `vCarry` et `vSpeed` seront ajoutées en Task 3 (driven by their own RED tests).

La méthode privée `countMyTrolls(GameState state)` (lignes 38-46 du fichier original) reste inchangée — la conserver telle quelle.

- [ ] **Step 5 : Relancer toute la suite TrainPlanner**

Run : `mvn -Dtest=TrainPlannerTest test`

Expected : tous les tests existants + les deux nouveaux passent.

Vérifications mentales :
- `emits_max_affordable_train_at_turn_1` : `(5,5,5,n=1)` → vSpeed=vCarry=vChop=2 → `Train(2,2,0,2)` ✓ (inchangé).
- `returns_null_when_chop_power_zero` : `(5,5,0)` → vChop=0 → null ✓.
- `returns_null_when_resources_insufficient_at_turn_1` : `(1,1,1,n=1)` → vSpeed=vCarry=vChop=0 (besoin ≥ 2) → null via la garde `vChop==0` ✓.
- `returns_null_after_turn_1_even_with_resources` : `state.turn=2` → null direct ✓.

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java \
        src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java
git commit -m "refactor(ai): decouple TRAIN stats per resource budget"
```

---

## Task 3 : Garde-fous `vCarry == 0` et `vSpeed == 0`

**Files :**
- Modify : `src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java` (ajouter 2 tests)
- Modify : `src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java` (étendre la garde)

- [ ] **Step 1 : Ajouter le test `returns_null_when_carry_capacity_zero`**

Dans `TrainPlannerTest.java`, ajouter à la fin (avant le `}` final) :

```java
    @Test
    void returns_null_when_carry_capacity_zero() {
        // PLUM=5, LEMON=0 (insuffisant pour v=1), IRON=5
        //   vSpeed=2, vCarry=0, vChop=2 → troll incapable de porter le wood
        GameState s = stateWithTurn(1, 5, 0, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
```

- [ ] **Step 2 : Ajouter le test `returns_null_when_move_speed_zero`**

Dans `TrainPlannerTest.java`, juste après :

```java
    @Test
    void returns_null_when_move_speed_zero() {
        // PLUM=0, LEMON=5, IRON=5
        //   vSpeed=0, vCarry=2, vChop=2 → troll immobile
        GameState s = stateWithTurn(1, 0, 5, 5);

        Action.Train train = TrainPlanner.plan(s);

        assertThat(train).isNull();
    }
```

- [ ] **Step 3 : Lancer les deux nouveaux tests pour vérifier qu'ils échouent (RED)**

Run : `mvn "-Dtest=TrainPlannerTest#returns_null_when_carry_capacity_zero+TrainPlannerTest#returns_null_when_move_speed_zero" test`

Expected : FAIL pour les deux. L'implémentation issue de Task 2 :
- `(5, 0, 5, n=1)` → vSpeed=2, vCarry=0, vChop=2 → retourne `Train(2,0,0,2)` au lieu de `null`.
- `(0, 5, 5, n=1)` → vSpeed=0, vCarry=2, vChop=2 → retourne `Train(0,2,0,2)` au lieu de `null`.

- [ ] **Step 4 : Étendre la garde-fou**

Dans `TrainPlanner.java`, modifier la condition de retour anticipé de `plan`. Remplacer :

```java
        if (vChop == 0) {
            return null;
        }
        return new Action.Train(vSpeed, vCarry, 0, vChop);
```

par :

```java
        if (vSpeed == 0 || vCarry == 0 || vChop == 0) {
            return null;
        }
        return new Action.Train(vSpeed, vCarry, 0, vChop);
```

- [ ] **Step 5 : Relancer toute la suite TrainPlanner**

Run : `mvn -Dtest=TrainPlannerTest test`

Expected : tous les tests passent.

Décompte attendu : **8 tests, tous PASS** dans `TrainPlannerTest` :
- `emits_max_affordable_train_at_turn_1` (existant)
- `returns_null_when_resources_insufficient_at_turn_1` (existant)
- `returns_null_after_turn_1_even_with_resources` (existant)
- `returns_null_when_chop_power_zero` (Task 1)
- `decouples_stats_with_asymmetric_budgets` (Task 2)
- `picks_different_v_per_stat` (Task 2)
- `returns_null_when_carry_capacity_zero` (Task 3)
- `returns_null_when_move_speed_zero` (Task 3)

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/com/bmrt/cgspring2026/ai/TrainPlanner.java \
        src/test/java/com/bmrt/cgspring2026/ai/TrainPlannerTest.java
git commit -m "refactor(ai): guard TRAIN against zero speed/carry stats"
```

---

## Task 4 : Suite complète + régénération `Player.java` + commit

**Files :**
- Run : `mvn test` (toutes suites)
- Run : `FileBuilder.main` sur `Player.java`
- Modify si diff : `Player.java` (racine du repo)

- [ ] **Step 1 : Lancer toute la suite de tests du projet**

Run : `mvn test`

Expected : BUILD SUCCESS, 0 failure, 0 error. Vérifier en particulier que `GreedyAiTest`, `TargetSelectorTest`, etc. passent toujours — aucune autre classe ne dépend du comportement supprimé (la 2ᵉ boucle de `TrainPlanner` n'était appelée nulle part ailleurs).

Si une autre suite casse, lire le diagnostic : aucun test ne devrait dépendre du fallback `Train(v,v,0,0)`, mais il faut vérifier. Si on découvre une dépendance, créer une tâche ad-hoc pour adapter le test consommateur (sinon le bot perd silencieusement la cohérence avec le spec).

- [ ] **Step 2 : Régénérer `Player.java` via FileBuilder**

`Player.java` à la racine du repo est le fichier de soumission CodinGame, généré par `FileBuilder` à partir de `src/main/java/com/bmrt/cgspring2026/Player.java`. Toute modif d'une classe inlinée (dont `TrainPlanner`) doit être propagée dans ce fichier.

Run :

```bash
mvn -q compile exec:java -Dexec.mainClass="com.bmrt.cgspring2026.builder.FileBuilder" -Dexec.args="src/main/java/com/bmrt/cgspring2026/Player.java"
```

Si `exec-maven-plugin` n'est pas configuré dans `pom.xml`, fallback :

```bash
mvn -q compile && java -cp target/classes com.bmrt.cgspring2026.builder.FileBuilder src/main/java/com/bmrt/cgspring2026/Player.java
```

Expected : la commande termine sans erreur. Le fichier `Player.java` (racine du repo) est réécrit avec la nouvelle version de `TrainPlanner` inlinée.

- [ ] **Step 3 : Vérifier le diff de `Player.java`**

Run : `git diff Player.java`

Expected : le diff montre :
- Suppression du bloc `for (int v = V_MAX; v >= 1; v--) { ... return new Action.Train(v, v, 0, 0); ... }`.
- Suppression du couplage `plums >= cost && lemons >= cost && iron >= cost` dans la 1ʳᵉ boucle.
- Ajout de la méthode `maxAffordable(int stock, int n)`.
- Ajout de la garde `if (vSpeed == 0 || vCarry == 0 || vChop == 0) return null;`.
- Calculs `vSpeed`, `vCarry`, `vChop` séparés.

Aucune autre classe du `Player.java` ne doit être modifiée par ce regen (sauf reformatage déterministe de FileBuilder).

- [ ] **Step 4 : Commit**

```bash
git add Player.java
git commit -m "chore(builder): regenerate Player.java with decoupled TrainPlanner"
```

Si `Player.java` n'a pas changé (cas improbable) : sauter ce commit, vérifier que FileBuilder a bien été exécuté.

---

## Self-Review (effectuée par l'auteur du plan)

**1. Spec coverage** :

| Élément spec §5/§11 | Tâche correspondante |
|---|---|
| Algorithme : `maxAffordable` par stat | Task 2, Step 4 |
| Garde-fou : null si `vChop=0` | Task 1 (suppression fallback) + Task 2 Step 4 (garde initiale) |
| Garde-fou : null si `vCarry=0` | Task 3, Step 4 |
| Garde-fou : null si `vSpeed=0` | Task 3, Step 4 |
| Test 10 inchangé (`emits_max_affordable_train_at_turn_1`) | Vérifié dans Task 2 Step 5 |
| Test 11 (`decouples_stats_with_asymmetric_budgets`) | Task 2, Step 1 |
| Test 11b (`picks_different_v_per_stat`) | Task 2, Step 2 |
| Test 12 (`returns_null_when_chop_power_zero`) | Task 1, Step 1 |
| Test 12a (`returns_null_when_carry_capacity_zero`) | Task 3, Step 1 |
| Test 12b (`returns_null_when_move_speed_zero`) | Task 3, Step 2 |
| Test 12c (`returns_null_when_all_resources_insufficient`) | Test existant `returns_null_when_resources_insufficient_at_turn_1` (1,1,1) reste valide après Task 2 |
| Test 12d (`returns_null_after_turn_1`) | Test existant `returns_null_after_turn_1_even_with_resources` (inchangé) |
| `V_MAX = 4` conservé | Champ existant non touché |
| Politique "1 seul TRAIN au tour 1" | Garde `state.turn != 1` conservée Task 2 Step 4 |
| Régénération `Player.java` | Task 4 |

Couverture complète, aucune lacune.

**2. Placeholder scan** : aucun TBD / TODO. Tous les code blocks sont complets.

**3. Type consistency** : signatures cohérentes — `plan(GameState) → Action.Train`, `maxAffordable(int, int) → int`, `countMyTrolls(GameState) → int`. Toutes les références à `Action.Train(int, int, int, int)` respectent l'ordre `moveSpeed, carryCapacity, harvestPower, chopPower` du record déclaré dans `Action.java:62`. Les indexes `ResourceType.PLUM/LEMON/IRON.ordinal()` correspondent aux setters utilisés dans `stateWithTurn` (test helper existant).

**4. Décompte final des tests TrainPlannerTest** :
- Conservés : `emits_max_affordable_train_at_turn_1`, `returns_null_when_resources_insufficient_at_turn_1`, `returns_null_after_turn_1_even_with_resources` (3).
- Remplacé : `falls_back_to_zero_chop_when_no_iron_at_turn_1` → `returns_null_when_chop_power_zero` (1).
- Ajoutés : `decouples_stats_with_asymmetric_budgets`, `picks_different_v_per_stat`, `returns_null_when_carry_capacity_zero`, `returns_null_when_move_speed_zero` (4).
- **Total : 8 tests** (cohérent avec l'attente "7 tests" du Task 3 Step 5 — correction : j'avais omis `returns_null_after_turn_1_even_with_resources`. Le bon décompte est 8, à lire 8 dans Task 3 Step 5).

→ Correction inline appliquée : voir Task 3 Step 5 "**8 tests, tous PASS**".
