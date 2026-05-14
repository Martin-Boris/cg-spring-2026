# Greedy Heuristic Agent — Chop-and-Drop Baseline

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter un agent décisionnel **gourmand, stateless et zéro-allocation** qui, à chaque tour, produit une action par troll allié selon la stratégie "chop l'arbre le plus proche, puis dépose le wood au shack". Au tour 1 uniquement, ajoute un TRAIN avec les valeurs max possibles pour `movementSpeed`, `carryCapacity` et `chopPower` (en sacrifiant `harvestPower = 0`). Cet agent doit pouvoir être appelé **plusieurs centaines de milliers de fois dans la boucle de fitness du futur GA** sans pression mémoire ni recalculs inutiles.

**Architecture:**
- **`Action`** (déjà squelette dans `action/Action.java`) : encoder/décoder un `int` par action ; `toCommand(int, GameState)` produit la string CG. Aucune allocation par action (la `String` produite uniquement pour l'output final).
- **`ShackAdjacency`** (nouveau) : précalcule au tour 1 la liste (≤ 4) des cases GRASS adjacentes au shack allié.
- **`GreedyAgent`** (nouveau) : classe `final` non instanciable, buffers `static`. Méthode `decide(GameState, int[] outActions)` qui remplit le buffer et retourne le nombre d'actions. **Stateless entre appels** : aucune mémorisation troll → arbre cross-tour ; chaque appel reconstruit l'affectation à partir du `GameState`. C'est essentiel pour la réutilisation dans la simu GA (qui appelle l'agent sur des états arbitraires).
- **Logique par troll** (dérivée du `GameState`, pas de FSM persistante) :
  - `inventory[WOOD] > 0` → chemin retour : DROP si adjacent au shack, sinon MOVE vers le `ShackAdjacency` le plus proche.
  - sinon → chemin chop : CHOP si déjà sur l'arbre cible, sinon MOVE vers cet arbre. Si plus aucun arbre libre, WAIT.
- **Affectation arbre↔troll** : greedy déterministe — on itère les trolls alliés dans l'ordre de leur index interne, chacun pioche l'arbre **non encore réservé** dont la distance `PathTable.distance` est minimale. `boolean[MAX_TREES] treeTaken` réinitialisé en début de `decide()`.
- **TRAIN tour 1** : `ms`, `cc`, `cp` maximisés indépendamment via `maxV(resource, n) = floor(sqrt(resource - n))` ; `hp = 0`. Émis uniquement si **toutes** les ressources (PLUM, LEMON, APPLE, IRON) couvrent le coût total — sinon skip silencieux. `n` = nombre de trolls alliés courants (lu dans le `GameState`).

**Tech Stack:** Java 21, JUnit 5, AssertJ. Code sous `com.bmrt.cgspring2026.action` (Action) et `com.bmrt.cgspring2026.greedy` (nouveau). Aucune dépendance externe nouvelle.

**Why this design:**
- **Stateless** : l'agent ne porte aucun champ d'instance ; tout le scratch est dans des `static` de longueur `MAX_TREES`/`MAX_TROLLS`. → Le GA peut l'invoquer sur n'importe quel état simulé sans purge ni reset.
- **Zéro allocation par appel** : `decide()` n'alloue rien. `Action.toCommand()` alloue une `String` (inévitable pour `println`) mais n'est appelée qu'en production, pas dans la simu GA.
- **O(trolls × trees) par tour** : avec ~10 trolls et ~80 arbres max, ~800 lookups `PathTable.distance` (qui est lui-même O(1)). Largement sous 1 ms.
- **Aucune duplication d'info statique** : `ShackAdjacency` se branche sur la même convention que `PathTable` (rangée au tour 1, partagée pour la partie entière).
- **Encoding `int` réutilisable** : le génome GA = `int[]` ; les builders/decoders d'`Action` sont donc l'infra commune greedy ↔ GA.

**Anti-goals (hors scope) :**
- Pas de PLANT, pas de HARVEST, pas de PICK, pas de MINE dans la décision (le greedy se limite à chop/drop conformément à la spec utilisateur).
- Pas de re-target intelligent inter-tours (oscillations possibles si deux trolls se "volent" leur cible — toléré).
- Pas de gestion fine du carry overflow lors d'un chop (si `wood > carryFree`, le surplus est perdu côté arbitre ; on accepte).
- Pas de planification multi-tours.
- Pas de communication via `MSG`.
- Pas de prise en compte de l'adversaire (anti-blocking, etc.) — focus pur sur sa propre récolte.

---

## File Structure

```
src/main/java/com/bmrt/cgspring2026/
  action/
    Action.java                  ← complète les TODO actuels (encode/decode/toCommand)
    ActionType.java              ← inchangé
  greedy/
    ShackAdjacency.java          ← nouveau, init() statique
    GreedyAgent.java             ← nouveau, decide() statique
  Player.java                    ← intègre l'agent (remplace le placeholder WAIT)

src/test/java/com/bmrt/cgspring2026/
  action/
    ActionTest.java              ← encode/decode/toCommand
  greedy/
    ShackAdjacencyTest.java
    GreedyAgentTrainTest.java    ← TRAIN tour 1 (max stats + skip si insuffisant)
    GreedyAgentDecideTest.java   ← per-troll decisions sur petites grilles
```

Chaque classe est `final`, non instanciable (constructeur privé) — convention déjà utilisée pour `GameState`, `PathTable`. Pas d'instances : tout `static`.

---

## Test fixture commune

Helper de tests (à dupliquer dans les fichiers concernés, comme déjà fait pour `loadTestGrid()` dans `PathTableTest`) :

```java
private static void loadGrid(String... rows) {
    GameState.height = rows.length;
    GameState.width  = rows[0].length();
    GameState.tiles  = new byte[GameState.width * GameState.height];
    for (int y = 0; y < GameState.height; y++) {
        String r = rows[y];
        for (int x = 0; x < GameState.width; x++) {
            byte t = TileType.fromChar(r.charAt(x));
            GameState.tiles[y * GameState.width + x] = t;
            if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
            if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
        }
    }
}
```

Grille principale utilisée dans plusieurs tasks :

```
"......"   row 0
".0...."   row 1  (shack moi à (1,1))
"......"   row 2
"...1.."   row 3  (shack adverse à (3,3))
"......"   row 4
```

W=6, H=5. Cases GRASS adjacentes au shack moi : (0,1), (2,1), (1,0), (1,2).

---

## Task 1 : Encodage et décodage d'une action sur `int`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/action/Action.java` (remplir tous les `UnsupportedOperationException`)
- Create: `src/test/java/com/bmrt/cgspring2026/action/ActionTest.java`

**Responsibility:** transformer chaque action en un `int` 32 bits selon l'encodage défini dans `docs/model.md`, et fournir les décodeurs symétriques + le formatage CG via `toCommand`.

**Encodage rappel** (cf. model.md §Actions) :
- Standard : `[arg2:8][arg1:8][trollIdx:8][type:8]`
- TRAIN    : `[cp:6][hp:6][cc:6][ms:6][type:8]` (type en byte le moins significatif)

Le placement du low byte = `type` est imposé pour que `Action.type(action)` soit un simple `action & 0xFF` ; idem `trollIdx(action)` = `(action >>> 8) & 0xFF`. C'est le point d'entrée des décisions dans la simu GA → doit être l'opération la plus rapide possible.

- [ ] **Step 1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/action/ActionTest.java` :

```java
package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.model.TreeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTest {

    private static void loadGridForOutput() {
        GameState.height = 3;
        GameState.width  = 4;
        GameState.tiles  = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
    }

    @Test void waitEncodesTypeAndTrollIdx() {
        int a = Action.wait(5);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.trollIdx(a)).isEqualTo(5);
    }

    @Test void moveEncodesXAndY() {
        int a = Action.move(3, 17, 9);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(3);
        assertThat(Action.arg1(a)).isEqualTo(17);
        assertThat(Action.arg2(a)).isEqualTo(9);
    }

    @Test void harvestEncodesTypeAndTrollIdx() {
        int a = Action.harvest(2);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.HARVEST);
        assertThat(Action.trollIdx(a)).isEqualTo(2);
    }

    @Test void plantEncodesTreeType() {
        int a = Action.plant(0, TreeType.APPLE);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.PLANT);
        assertThat(Action.arg1(a)).isEqualTo((int) TreeType.APPLE);
    }

    @Test void chopEncodesTypeAndTrollIdx() {
        int a = Action.chop(4);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.CHOP);
        assertThat(Action.trollIdx(a)).isEqualTo(4);
    }

    @Test void pickEncodesResourceType() {
        int a = Action.pick(1, ResourceType.LEMON);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.PICK);
        assertThat(Action.arg1(a)).isEqualTo((int) ResourceType.LEMON);
    }

    @Test void dropEncodesTypeAndTrollIdx() {
        int a = Action.drop(0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.DROP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void mineEncodesTypeAndTrollIdx() {
        int a = Action.mine(7);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MINE);
        assertThat(Action.trollIdx(a)).isEqualTo(7);
    }

    @Test void trainEncodesFourStatsOnSixBits() {
        int a = Action.train(3, 5, 0, 4);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.TRAIN);
        assertThat(Action.trainMS(a)).isEqualTo(3);
        assertThat(Action.trainCC(a)).isEqualTo(5);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(4);
    }

    @Test void trainPreservesMaxSixBitValue() {
        int a = Action.train(63, 63, 63, 63);
        assertThat(Action.trainMS(a)).isEqualTo(63);
        assertThat(Action.trainCC(a)).isEqualTo(63);
        assertThat(Action.trainHP(a)).isEqualTo(63);
        assertThat(Action.trainCP(a)).isEqualTo(63);
    }

    // --- toCommand : utilise trollId externe (CG) lu dans state.trollId[trollIdx] ---

    private static GameState stateWithTrolls() {
        GameState s = new GameState();
        s.trollCount = 2;
        s.trollId[0] = (byte) 7;
        s.trollId[1] = (byte) 12;
        return s;
    }

    @Test void toCommandWaitUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.wait(0), stateWithTrolls())).isEqualTo("WAIT 7");
    }

    @Test void toCommandMoveUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.move(1, 3, 2), stateWithTrolls())).isEqualTo("MOVE 12 3 2");
    }

    @Test void toCommandChopUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.chop(1), stateWithTrolls())).isEqualTo("CHOP 12");
    }

    @Test void toCommandDropUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.drop(0), stateWithTrolls())).isEqualTo("DROP 7");
    }

    @Test void toCommandHarvestUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.harvest(0), stateWithTrolls())).isEqualTo("HARVEST 7");
    }

    @Test void toCommandMineUsesExternalId() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.mine(1), stateWithTrolls())).isEqualTo("MINE 12");
    }

    @Test void toCommandPlantUsesTreeName() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.plant(0, TreeType.BANANA), stateWithTrolls())).isEqualTo("PLANT 7 BANANA");
    }

    @Test void toCommandPickUsesResourceName() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.pick(0, ResourceType.APPLE), stateWithTrolls())).isEqualTo("PICK 7 APPLE");
    }

    @Test void toCommandTrainEmitsFourStats() {
        loadGridForOutput();
        assertThat(Action.toCommand(Action.train(3, 2, 0, 1), stateWithTrolls())).isEqualTo("TRAIN 3 2 0 1");
    }
}
```

- [ ] **Step 2 : Run, expect FAIL**

```
mvn -q -Dtest=ActionTest test
```

Expected : tous les tests échouent avec `UnsupportedOperationException("TODO")`.

- [ ] **Step 3 : Implémenter les builders / decoders (GREEN)**

Remplacer le contenu de `src/main/java/com/bmrt/cgspring2026/action/Action.java` :

```java
package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TreeType;

public final class Action {

    // --- Encodage standard : [arg2:8][arg1:8][trollIdx:8][type:8] ---

    private static int pack(int type, int trollIdx, int arg1, int arg2) {
        return (type & 0xFF)
             | ((trollIdx & 0xFF) << 8)
             | ((arg1     & 0xFF) << 16)
             | ((arg2     & 0xFF) << 24);
    }

    public static int wait(int trollIdx)                       { return pack(ActionType.WAIT,    trollIdx, 0, 0); }
    public static int move(int trollIdx, int x, int y)         { return pack(ActionType.MOVE,    trollIdx, x, y); }
    public static int harvest(int trollIdx)                    { return pack(ActionType.HARVEST, trollIdx, 0, 0); }
    public static int plant(int trollIdx, int treeType)        { return pack(ActionType.PLANT,   trollIdx, treeType, 0); }
    public static int chop(int trollIdx)                       { return pack(ActionType.CHOP,    trollIdx, 0, 0); }
    public static int pick(int trollIdx, int resourceType)     { return pack(ActionType.PICK,    trollIdx, resourceType, 0); }
    public static int drop(int trollIdx)                       { return pack(ActionType.DROP,    trollIdx, 0, 0); }
    public static int mine(int trollIdx)                       { return pack(ActionType.MINE,    trollIdx, 0, 0); }

    // --- Encodage TRAIN : [cp:6][hp:6][cc:6][ms:6][type:8] ---

    public static int train(int ms, int cc, int hp, int cp) {
        return (ActionType.TRAIN & 0xFF)
             | ((ms & 0x3F) << 8)
             | ((cc & 0x3F) << 14)
             | ((hp & 0x3F) << 20)
             | ((cp & 0x3F) << 26);
    }

    // --- Décodeurs ---

    public static int type(int action)     { return action & 0xFF; }
    public static int trollIdx(int action) { return (action >>> 8)  & 0xFF; }
    public static int arg1(int action)     { return (action >>> 16) & 0xFF; }
    public static int arg2(int action)     { return (action >>> 24) & 0xFF; }

    public static int trainMS(int action) { return (action >>> 8)  & 0x3F; }
    public static int trainCC(int action) { return (action >>> 14) & 0x3F; }
    public static int trainHP(int action) { return (action >>> 20) & 0x3F; }
    public static int trainCP(int action) { return (action >>> 26) & 0x3F; }

    // --- Formatage CG ---

    private static final String[] TREE_NAMES     = { "PLUM", "LEMON", "APPLE", "BANANA" };
    private static final String[] RESOURCE_NAMES = { "PLUM", "LEMON", "APPLE", "BANANA", "IRON", "WOOD" };

    public static String toCommand(int action, GameState state) {
        int t = type(action);
        if (t == ActionType.TRAIN) {
            return "TRAIN " + trainMS(action) + " " + trainCC(action) + " " + trainHP(action) + " " + trainCP(action);
        }
        int idx = trollIdx(action);
        int externalId = state.trollId[idx] & 0xFF;
        return switch (t) {
            case ActionType.WAIT    -> "WAIT "    + externalId;
            case ActionType.MOVE    -> "MOVE "    + externalId + " " + arg1(action) + " " + arg2(action);
            case ActionType.HARVEST -> "HARVEST " + externalId;
            case ActionType.PLANT   -> "PLANT "   + externalId + " " + TREE_NAMES[arg1(action)];
            case ActionType.CHOP    -> "CHOP "    + externalId;
            case ActionType.PICK    -> "PICK "    + externalId + " " + RESOURCE_NAMES[arg1(action)];
            case ActionType.DROP    -> "DROP "    + externalId;
            case ActionType.MINE    -> "MINE "    + externalId;
            default -> throw new IllegalStateException("unknown action type " + t);
        };
    }

    private Action() {}
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=ActionTest test
```

Expected : tous les tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/action/Action.java \
        src/test/java/com/bmrt/cgspring2026/action/ActionTest.java
git commit -m "action: implement int encoding, decoding and CG output formatting"
```

---

## Task 2 : Précalcul des cases adjacentes au shack allié

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/greedy/ShackAdjacency.java`
- Create: `src/test/java/com/bmrt/cgspring2026/greedy/ShackAdjacencyTest.java`

**Responsibility:** au tour 1, scanner les 4 voisins H/V de `GameState.shackMeX/Y`, retenir uniquement ceux de type GRASS, les stocker dans un petit tableau partagé. Permet ensuite à `GreedyAgent` de trouver en O(≤4) la case adjacente la plus proche d'un troll (via `PathTable.distance`).

- [ ] **Step 1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/greedy/ShackAdjacencyTest.java` :

```java
package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShackAdjacencyTest {

    private static void loadGrid(String... rows) {
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

    @Test void fourNeighboursAllGrass() {
        loadGrid(
            "......",
            ".0....",
            "......",
            "...1..",
            "......"
        );
        ShackAdjacency.init();
        assertThat(ShackAdjacency.count).isEqualTo(4);
        // contient (0,1), (2,1), (1,0), (1,2) — ordre N,S,E,W, peu importe le quel
        int mask = 0;
        for (int i = 0; i < ShackAdjacency.count; i++) {
            int x = ShackAdjacency.x[i] & 0xFF;
            int y = ShackAdjacency.y[i] & 0xFF;
            if (x == 0 && y == 1) mask |= 1;
            if (x == 2 && y == 1) mask |= 2;
            if (x == 1 && y == 0) mask |= 4;
            if (x == 1 && y == 2) mask |= 8;
        }
        assertThat(mask).isEqualTo(0xF);
    }

    @Test void cornerShackHasFewerNeighbours() {
        loadGrid(
            "0#....",   // shack à (0,0), rock à (1,0) → seul (0,1) reste
            "......",
            ".....1"
        );
        ShackAdjacency.init();
        assertThat(ShackAdjacency.count).isEqualTo(1);
        assertThat(ShackAdjacency.x[0] & 0xFF).isEqualTo(0);
        assertThat(ShackAdjacency.y[0] & 0xFF).isEqualTo(1);
    }

    @Test void waterAndShackOppAreExcluded() {
        loadGrid(
            "..~...",    // (2,0) WATER
            "..01..",    // shack moi (2,1) — adversaire est case (3,1) → exclu car SHACK_OPP
            "..#..."     // (2,2) ROCK
        );
        ShackAdjacency.init();
        // voisins de (2,1) : (1,1)=GRASS ✓, (3,1)=SHACK_OPP ✗, (2,0)=WATER ✗, (2,2)=ROCK ✗
        assertThat(ShackAdjacency.count).isEqualTo(1);
        assertThat(ShackAdjacency.x[0] & 0xFF).isEqualTo(1);
        assertThat(ShackAdjacency.y[0] & 0xFF).isEqualTo(1);
    }
}
```

- [ ] **Step 2 : Run, expect FAIL (compile)**

```
mvn -q -Dtest=ShackAdjacencyTest test
```

Expected : compile error `cannot find symbol class ShackAdjacency`.

- [ ] **Step 3 : Implémenter (GREEN)**

Créer `src/main/java/com/bmrt/cgspring2026/greedy/ShackAdjacency.java` :

```java
package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class ShackAdjacency {

    // Capacité fixe = 4 (au plus 4 voisins H/V). Indexable en byte.
    public static final byte[] x = new byte[4];
    public static final byte[] y = new byte[4];
    public static int count;

    private static final int[] DX = { 1, -1, 0,  0 };
    private static final int[] DY = { 0,  0, 1, -1 };

    public static void init() {
        count = 0;
        int sx = GameState.shackMeX;
        int sy = GameState.shackMeY;
        for (int k = 0; k < 4; k++) {
            int nx = sx + DX[k];
            int ny = sy + DY[k];
            if (nx < 0 || nx >= GameState.width)  continue;
            if (ny < 0 || ny >= GameState.height) continue;
            if (GameState.tileAt(nx, ny) != TileType.GRASS) continue;
            x[count] = (byte) nx;
            y[count] = (byte) ny;
            count++;
        }
    }

    private ShackAdjacency() {}
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=ShackAdjacencyTest test
```

Expected : 3 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/greedy/ShackAdjacency.java \
        src/test/java/com/bmrt/cgspring2026/greedy/ShackAdjacencyTest.java
git commit -m "greedy: precompute shack-adjacent grass tiles at init"
```

---

## Task 3 : TRAIN max stats au tour 1

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` (squelette + `maybeTrain`)
- Create: `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentTrainTest.java`

**Responsibility:** fournir une méthode `static int maybeTrain(GameState s)` qui retourne `-1` si aucun TRAIN n'est affordable, sinon l'`int` encodé `Action.train(ms, cc, 0, cp)` avec :
- `n` = nombre de trolls alliés (player == 0) actuellement présents.
- `ms = max v ≥ 1 tel que n + v² ≤ shackInventory[PLUM]` (référé impose ms ≥ 1).
- `cc = max v ≥ 0 tel que n + v² ≤ shackInventory[LEMON]`.
- `hp = 0` (on ne harvest pas, mais le coût `n + 0² = n APPLE` est tout de même payé).
- `cp = max v ≥ 0 tel que n + v² ≤ shackInventory[IRON]`.

Conditions pour émettre :
- PLUM disponible doit couvrir au moins `n + 1` (ms ≥ 1 obligatoire côté arbitre).
- LEMON ≥ n, APPLE ≥ n, IRON ≥ n (chacune doit couvrir le coût même avec `v = 0`).

Si l'une des conditions est violée → retourner `-1`.

**Pourquoi cette politique :** la spec utilisateur demande "maximise carry, chop, speed". On fixe donc `hp = 0` (gaspillage minimal d'APPLE) et on essaie de pousser les trois autres au max indépendamment. La maximisation est triviale puisque chaque stat ne consomme que sa propre ressource : aucun arbitrage à faire.

- [ ] **Step 1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentTrainTest.java` :

```java
package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAgentTrainTest {

    private static GameState stateWithSingleTroll(int plum, int lemon, int apple, int iron) {
        // grille minimale
        GameState.width  = 4;
        GameState.height = 3;
        GameState.tiles  = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.shackInventory[ResourceType.PLUM]   = plum;
        s.shackInventory[ResourceType.LEMON]  = lemon;
        s.shackInventory[ResourceType.APPLE]  = apple;
        s.shackInventory[ResourceType.IRON]   = iron;
        return s;
    }

    @Test void noTrainWhenPlumBelowMinForMs1() {
        // n=1, besoin de PLUM >= 1 + 1² = 2 pour ms=1. Ici PLUM=1 → skip.
        GameState s = stateWithSingleTroll(1, 5, 5, 5);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test void noTrainWhenLemonInsufficient() {
        // n=1, besoin de LEMON >= 1 pour cc=0. Ici LEMON=0 → skip.
        GameState s = stateWithSingleTroll(5, 0, 5, 5);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test void noTrainWhenAppleInsufficient() {
        // n=1, besoin de APPLE >= 1 pour hp=0. Ici APPLE=0 → skip.
        GameState s = stateWithSingleTroll(5, 5, 0, 5);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test void noTrainWhenIronInsufficient() {
        // n=1, besoin de IRON >= 1 pour cp=0. Ici IRON=0 → skip.
        GameState s = stateWithSingleTroll(5, 5, 5, 0);
        assertThat(GreedyAgent.maybeTrain(s)).isEqualTo(-1);
    }

    @Test void minimalTrainWhenJustAffordable() {
        // n=1, PLUM=2 (ms=1), LEMON=1 (cc=0), APPLE=1 (hp=0), IRON=1 (cp=0).
        GameState s = stateWithSingleTroll(2, 1, 1, 1);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.TRAIN);
        assertThat(Action.trainMS(a)).isEqualTo(1);
        assertThat(Action.trainCC(a)).isEqualTo(0);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(0);
    }

    @Test void maximumIndependentlyPerStat() {
        // n=1.
        // PLUM=10 → ms max tel que 1+v² ≤ 10 → v=3 (1+9=10).
        // LEMON=5 → cc max : 1+v² ≤ 5 → v=2 (1+4=5).
        // APPLE=1 → hp = 0 (on ne maximise pas hp).
        // IRON=17 → cp max : 1+v² ≤ 17 → v=4 (1+16=17).
        GameState s = stateWithSingleTroll(10, 5, 1, 17);
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.trainMS(a)).isEqualTo(3);
        assertThat(Action.trainCC(a)).isEqualTo(2);
        assertThat(Action.trainHP(a)).isEqualTo(0);
        assertThat(Action.trainCP(a)).isEqualTo(4);
    }

    @Test void costAccountsForCurrentTrollCount() {
        // n=2 (deux trolls alliés déjà présents).
        // PLUM=10 → ms max : 2+v² ≤ 10 → v=2 (2+4=6, 2+9=11>10 KO).
        GameState.width  = 4;
        GameState.height = 3;
        GameState.tiles  = new byte[12];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);
        GameState s = new GameState();
        s.trollCount = 3;
        s.trollPlayer[0] = 0;  // allié
        s.trollPlayer[1] = 0;  // allié
        s.trollPlayer[2] = 1;  // adverse → ne compte pas dans n
        s.shackInventory[ResourceType.PLUM]  = 10;
        s.shackInventory[ResourceType.LEMON] = 10;
        s.shackInventory[ResourceType.APPLE] = 10;
        s.shackInventory[ResourceType.IRON]  = 10;
        int a = GreedyAgent.maybeTrain(s);
        assertThat(Action.trainMS(a)).isEqualTo(2);  // n=2 utilisé
    }
}
```

- [ ] **Step 2 : Run, expect FAIL (compile)**

```
mvn -q -Dtest=GreedyAgentTrainTest test
```

Expected : compile error `cannot find symbol class GreedyAgent`.

- [ ] **Step 3 : Implémenter `GreedyAgent.maybeTrain` (GREEN)**

Créer `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` :

```java
package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;

public final class GreedyAgent {

    /**
     * Renvoie l'int encodé d'une action TRAIN max ms/cc/cp (hp=0) ou {@code -1}
     * si l'inventaire shack ne couvre pas le coût minimal.
     */
    public static int maybeTrain(GameState s) {
        int n = countOwnTrolls(s);
        int plum  = s.shackInventory[ResourceType.PLUM];
        int lemon = s.shackInventory[ResourceType.LEMON];
        int apple = s.shackInventory[ResourceType.APPLE];
        int iron  = s.shackInventory[ResourceType.IRON];

        // arbitre impose ms >= 1 → coût PLUM >= n + 1
        if (plum  < n + 1) return -1;
        if (lemon < n)     return -1;
        if (apple < n)     return -1;
        if (iron  < n)     return -1;

        int ms = maxV(plum,  n, 1);  // min 1
        int cc = maxV(lemon, n, 0);  // min 0
        int cp = maxV(iron,  n, 0);  // min 0
        return Action.train(ms, cc, 0, cp);
    }

    /** Plus grand v >= floor tel que n + v² <= resource. Pré-condition : resource >= n + floor². */
    private static int maxV(int resource, int n, int floor) {
        int v = floor;
        while ((long) (n + (v + 1) * (v + 1)) <= resource) v++;
        return v;
    }

    private static int countOwnTrolls(GameState s) {
        int n = 0;
        for (int i = 0; i < s.trollCount; i++) {
            if (s.trollPlayer[i] == 0) n++;
        }
        return n;
    }

    private GreedyAgent() {}
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=GreedyAgentTrainTest test
```

Expected : 6 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java \
        src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentTrainTest.java
git commit -m "greedy: train max stats with per-resource independent maximisation"
```

---

## Task 4 : Décision par troll (DROP / CHOP / MOVE / WAIT)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` (ajout `decideForTroll`)
- Create: `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java`

**Responsibility:** fournir `static int decideForTroll(GameState s, int trollIdx, int treeIdx)` qui retourne l'`int` action pour ce troll allié, **étant donné** un index d'arbre cible déjà calculé (peut être `-1` si aucun arbre n'a été assigné). La logique :

1. Si `trollInventory[trollIdx*6 + WOOD] > 0` (le troll porte du bois) :
   - si position troll est l'une des cases de `ShackAdjacency` → `DROP` ;
   - sinon → `MOVE` vers la case `ShackAdjacency` la plus proche (`PathTable.distance` la plus petite). En cas d'égalité, prendre la première rencontrée (déterministe).
2. Sinon (pas de bois) :
   - si `treeIdx == -1` → `WAIT` ;
   - si position troll == `(treeX[treeIdx], treeY[treeIdx])` → `CHOP` ;
   - sinon → `MOVE` vers `(treeX[treeIdx], treeY[treeIdx])`.

L'assignation de `treeIdx` est faite à l'étage supérieur (Task 5).

- [ ] **Step 1 : Écrire le test (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java` :

```java
package com.bmrt.cgspring2026.greedy;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyAgentDecideTest {

    @BeforeEach void setUpGrid() {
        // 6x5, shack moi en (1,1), adverse en (3,3), tout GRASS sinon.
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

    private static GameState stateWithTroll(int x, int y, int wood) {
        GameState s = new GameState();
        s.trollCount = 1;
        s.trollPlayer[0] = 0;
        s.trollX[0] = (byte) x;
        s.trollY[0] = (byte) y;
        s.trollMS[0] = 2;
        s.trollCC[0] = 4;
        s.trollHP[0] = 1;
        s.trollCP[0] = 1;
        s.trollInventory[ResourceType.WOOD] = (byte) wood;
        return s;
    }

    // --- Chemin retour (wood > 0) ---

    @Test void dropWhenStandingOnShackAdjacent() {
        // troll à (0,1) = case adjacente au shack (1,1)
        GameState s = stateWithTroll(0, 1, 3);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.DROP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void moveTowardsClosestShackAdjacentWhenCarryingWood() {
        // troll à (5,4), shack à (1,1). Cases adj : (0,1)(2,1)(1,0)(1,2).
        // PathTable.distance depuis (5,4) → (2,1)=6, (1,2)=5, (1,0)=7, (0,1)=7
        // → la plus proche est (1,2).
        GameState s = stateWithTroll(5, 4, 2);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
        assertThat(Action.arg1(a)).isEqualTo(1);
        assertThat(Action.arg2(a)).isEqualTo(2);
    }

    // --- Chemin chop (wood == 0) ---

    @Test void waitWhenNoTreeAvailable() {
        GameState s = stateWithTroll(2, 2, 0);
        int a = GreedyAgent.decideForTroll(s, 0, -1);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.WAIT);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void chopWhenStandingOnTargetTree() {
        GameState s = stateWithTroll(3, 2, 0);
        s.treeCount = 1;
        s.treeX[0] = 3;
        s.treeY[0] = 2;
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.CHOP);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
    }

    @Test void moveTowardsTargetTreeWhenNotOnIt() {
        GameState s = stateWithTroll(0, 0, 0);
        s.treeCount = 1;
        s.treeX[0] = 5;
        s.treeY[0] = 4;
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE);
        assertThat(Action.trollIdx(a)).isEqualTo(0);
        assertThat(Action.arg1(a)).isEqualTo(5);
        assertThat(Action.arg2(a)).isEqualTo(4);
    }

    @Test void carryingWoodPriorityOverTreeAssignment() {
        // troll porte du wood + a une assignation : doit quand même partir au shack
        GameState s = stateWithTroll(5, 4, 1);
        s.treeCount = 1;
        s.treeX[0] = 5;
        s.treeY[0] = 4; // pile sur l'arbre
        int a = GreedyAgent.decideForTroll(s, 0, 0);
        assertThat(Action.type(a)).isEqualTo((int) ActionType.MOVE); // pas CHOP
    }
}
```

- [ ] **Step 2 : Run, expect FAIL (compile)**

```
mvn -q -Dtest=GreedyAgentDecideTest test
```

Expected : compile error sur `decideForTroll`.

- [ ] **Step 3 : Implémenter `decideForTroll` (GREEN)**

Ajouter dans `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` (avant le constructeur privé) :

```java
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
```

(Adapter les imports en début de fichier.)

Puis ajouter ces méthodes :

```java
public static int decideForTroll(GameState s, int trollIdx, int treeIdx) {
    int tx = s.trollX[trollIdx] & 0xFF;
    int ty = s.trollY[trollIdx] & 0xFF;
    int wood = s.trollInventory[trollIdx * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;

    if (wood > 0) {
        if (isShackAdjacent(tx, ty)) {
            return Action.drop(trollIdx);
        }
        int dropX = ShackAdjacency.x[0] & 0xFF;
        int dropY = ShackAdjacency.y[0] & 0xFF;
        int bestDist = PathTable.distance(tx, ty, dropX, dropY);
        for (int i = 1; i < ShackAdjacency.count; i++) {
            int cx = ShackAdjacency.x[i] & 0xFF;
            int cy = ShackAdjacency.y[i] & 0xFF;
            int d  = PathTable.distance(tx, ty, cx, cy);
            if (d < bestDist) {
                bestDist = d;
                dropX = cx;
                dropY = cy;
            }
        }
        return Action.move(trollIdx, dropX, dropY);
    }

    if (treeIdx < 0) {
        return Action.wait(trollIdx);
    }
    int treeX = s.treeX[treeIdx] & 0xFF;
    int treeY = s.treeY[treeIdx] & 0xFF;
    if (tx == treeX && ty == treeY) {
        return Action.chop(trollIdx);
    }
    return Action.move(trollIdx, treeX, treeY);
}

private static boolean isShackAdjacent(int x, int y) {
    for (int i = 0; i < ShackAdjacency.count; i++) {
        if ((ShackAdjacency.x[i] & 0xFF) == x && (ShackAdjacency.y[i] & 0xFF) == y) return true;
    }
    return false;
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=GreedyAgentDecideTest test
```

Expected : 6 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java \
        src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java
git commit -m "greedy: per-troll decision (drop / chop / move / wait)"
```

---

## Task 5 : Assignation greedy des arbres + `decide` top-level + intégration `Player.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` (ajout `decide` + `pickClosestFreeTree`)
- Modify: `src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java` (tests d'assignation + tour-1)
- Modify: `src/main/java/com/bmrt/cgspring2026/Player.java`

**Responsibility:** méthode `static int decide(GameState s, int[] outActions)` qui orchestre l'ensemble.

Algorithme :
1. `count = 0`.
2. Si `s.turn == 0`, calculer `int t = maybeTrain(s)`. Si `t != -1`, `outActions[count++] = t`.
3. Réinitialiser `treeTaken[0..treeCount[ = false`.
4. Pour chaque troll allié dans l'ordre d'index interne :
   - `treeIdx = pickClosestFreeTree(s, trollIdx)` (`-1` si aucun arbre libre).
   - Si `treeIdx >= 0`, marquer `treeTaken[treeIdx] = true`.
   - `outActions[count++] = decideForTroll(s, trollIdx, treeIdx)`.
5. Retourner `count`.

`pickClosestFreeTree` itère sur `s.treeCount` arbres, ignore les `treeTaken`, et choisit celui à la plus petite `PathTable.distance` depuis `(trollX, trollY)`. En cas d'égalité, plus petit index gagne.

**Intégration dans `Player.java`** :
- Allouer un seul `int[] actionBuf = new int[GameState.MAX_TROLLS + 1]` (le `+1` pour le TRAIN potentiel).
- Allouer un seul `StringBuilder` (réutilisé chaque tour avec `setLength(0)`).
- À chaque tour : `int n = GreedyAgent.decide(state, actionBuf);` puis concaténer `Action.toCommand(actionBuf[i], state)` séparés par `;`.

- [ ] **Step 1 : Écrire les tests (RED)**

Ajouter à `GreedyAgentDecideTest.java` :

```java
// --- decide() top-level ---

@Test void decideEmptyMapReturnsWaitOnly() {
    GameState s = stateWithTroll(0, 0, 0);
    s.turn = 5; // pas tour 1, pas de TRAIN
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    int n = GreedyAgent.decide(s, buf);
    assertThat(n).isEqualTo(1);
    assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.WAIT);
}

@Test void decideAssignsClosestFreeTreePerTroll() {
    GameState s = new GameState();
    s.turn = 5;
    s.trollCount = 2;
    s.trollPlayer[0] = 0;
    s.trollPlayer[1] = 0;
    s.trollX[0] = 0; s.trollY[0] = 0;
    s.trollX[1] = 5; s.trollY[1] = 4;
    s.treeCount = 2;
    s.treeX[0] = 0; s.treeY[0] = 4;   // proche du troll 0
    s.treeX[1] = 5; s.treeY[1] = 0;   // proche du troll 1
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    int n = GreedyAgent.decide(s, buf);
    assertThat(n).isEqualTo(2);
    // troll 0 → MOVE vers (0,4)
    assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.MOVE);
    assertThat(Action.arg1(buf[0])).isEqualTo(0);
    assertThat(Action.arg2(buf[0])).isEqualTo(4);
    // troll 1 → MOVE vers (5,0)
    assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.MOVE);
    assertThat(Action.arg1(buf[1])).isEqualTo(5);
    assertThat(Action.arg2(buf[1])).isEqualTo(0);
}

@Test void decideDoesNotReassignSameTreeTwice() {
    // deux trolls à la même distance d'un arbre unique → seul le premier (index 0) le prend
    GameState s = new GameState();
    s.turn = 5;
    s.trollCount = 2;
    s.trollPlayer[0] = 0;
    s.trollPlayer[1] = 0;
    s.trollX[0] = 0; s.trollY[0] = 2;
    s.trollX[1] = 4; s.trollY[1] = 2;
    s.treeCount = 1;
    s.treeX[0] = 2; s.treeY[0] = 2;
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    GreedyAgent.decide(s, buf);
    // troll 0 vise l'arbre
    assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.MOVE);
    assertThat(Action.arg1(buf[0])).isEqualTo(2);
    assertThat(Action.arg2(buf[0])).isEqualTo(2);
    // troll 1 n'a plus rien → WAIT
    assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.WAIT);
}

@Test void decideIgnoresEnemyTrolls() {
    GameState s = new GameState();
    s.turn = 5;
    s.trollCount = 2;
    s.trollPlayer[0] = 1;  // adversaire
    s.trollPlayer[1] = 0;  // allié
    s.trollX[1] = 0; s.trollY[1] = 0;
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    int n = GreedyAgent.decide(s, buf);
    // une seule action (pour le troll allié, idx interne = 1)
    assertThat(n).isEqualTo(1);
    assertThat(Action.trollIdx(buf[0])).isEqualTo(1);
}

@Test void decideEmitsTrainAtTurn0WhenAffordable() {
    GameState s = new GameState();
    s.turn = 0;
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.trollX[0] = 0; s.trollY[0] = 0;
    s.shackInventory[ResourceType.PLUM]  = 2;
    s.shackInventory[ResourceType.LEMON] = 1;
    s.shackInventory[ResourceType.APPLE] = 1;
    s.shackInventory[ResourceType.IRON]  = 1;
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    int n = GreedyAgent.decide(s, buf);
    // tour 0 + 1 troll → 2 actions, TRAIN en premier
    assertThat(n).isEqualTo(2);
    assertThat(Action.type(buf[0])).isEqualTo((int) ActionType.TRAIN);
    assertThat(Action.type(buf[1])).isEqualTo((int) ActionType.WAIT);
}

@Test void decideSkipsTrainAfterTurn0() {
    GameState s = new GameState();
    s.turn = 1;
    s.trollCount = 1;
    s.trollPlayer[0] = 0;
    s.shackInventory[ResourceType.PLUM]  = 10;
    s.shackInventory[ResourceType.LEMON] = 10;
    s.shackInventory[ResourceType.APPLE] = 10;
    s.shackInventory[ResourceType.IRON]  = 10;
    int[] buf = new int[GameState.MAX_TROLLS + 1];
    int n = GreedyAgent.decide(s, buf);
    assertThat(n).isEqualTo(1);
    assertThat(Action.type(buf[0])).isNotEqualTo((int) ActionType.TRAIN);
}
```

- [ ] **Step 2 : Run, expect FAIL (compile)**

```
mvn -q -Dtest=GreedyAgentDecideTest test
```

Expected : compile error sur `GreedyAgent.decide`.

- [ ] **Step 3 : Implémenter `decide` + `pickClosestFreeTree` (GREEN)**

Ajouter dans `src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java` :

```java
private static final boolean[] treeTaken = new boolean[GameState.MAX_TREES];

public static int decide(GameState s, int[] outActions) {
    int count = 0;
    if (s.turn == 0) {
        int trainAction = maybeTrain(s);
        if (trainAction != -1) {
            outActions[count++] = trainAction;
        }
    }
    java.util.Arrays.fill(treeTaken, 0, s.treeCount, false);
    for (int i = 0; i < s.trollCount; i++) {
        if (s.trollPlayer[i] != 0) continue;
        int treeIdx = pickClosestFreeTree(s, i);
        if (treeIdx >= 0) treeTaken[treeIdx] = true;
        outActions[count++] = decideForTroll(s, i, treeIdx);
    }
    return count;
}

private static int pickClosestFreeTree(GameState s, int trollIdx) {
    int tx = s.trollX[trollIdx] & 0xFF;
    int ty = s.trollY[trollIdx] & 0xFF;
    int best = -1;
    int bestDist = Integer.MAX_VALUE;
    for (int t = 0; t < s.treeCount; t++) {
        if (treeTaken[t]) continue;
        int d = PathTable.distance(tx, ty, s.treeX[t] & 0xFF, s.treeY[t] & 0xFF);
        if (d == PathTable.UNREACHABLE) continue;
        if (d < bestDist) {
            bestDist = d;
            best = t;
        }
    }
    return best;
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=GreedyAgentDecideTest test
```

Expected : 12 tests verts dans ce fichier (6 anciens + 6 nouveaux).

- [ ] **Step 5 : Brancher l'agent dans `Player.java`**

Remplacer le contenu de `src/main/java/com/bmrt/cgspring2026/Player.java` :

```java
package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
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

        GameState state = new GameState();
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb = new StringBuilder();

        while (true) {
            long start = System.nanoTime();
            state.readTurn(in);

            int n = GreedyAgent.decide(state, actionBuf);
            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            System.out.println(sb);

            System.err.println("turn=" + state.turn + " elapsed=" + (System.nanoTime() - start) + "ns");
            state.turn++;
        }
    }
}
```

- [ ] **Step 6 : Vérifier la compilation complète + tous les tests**

```
mvn -q test
```

Expected : tous les tests verts (PathTable + GameState + Action + ShackAdjacency + GreedyAgent).

```
mvn -q package
```

Expected : build successful.

- [ ] **Step 7 : Vérifier l'absence de System.out parasites**

```
grep -rn "System\.out\.print" src/main/java/com/bmrt/cgspring2026/ | grep -v Player.java | grep -v builder/
```

Expected : aucune ligne (seul `Player.main` écrit sur stdout pour CG).

- [ ] **Step 8 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/greedy/GreedyAgent.java \
        src/test/java/com/bmrt/cgspring2026/greedy/GreedyAgentDecideTest.java \
        src/main/java/com/bmrt/cgspring2026/Player.java
git commit -m "greedy: full decide() with closest-free-tree assignment and Player wiring"
```

---

## Récapitulatif structure finale

```
Action                           (int packing primitive, statique)
├── wait / move / harvest / plant / chop / pick / drop / mine
├── train(ms, cc, hp, cp)         ← encodage 4×6 bits
├── type / trollIdx / arg1 / arg2
├── trainMS / trainCC / trainHP / trainCP
└── toCommand(action, state)      ← seul allocateur (String) ; appelé uniquement en sortie

ShackAdjacency                   (static, ≤ 4 entrées)
├── x[4], y[4], count
└── init()                        ← scanne les 4 voisins du shack moi, garde GRASS

GreedyAgent                      (static, stateless, zero-alloc)
├── maybeTrain(s) → int           ← TRAIN max ms/cc/cp, hp=0, -1 si non affordable
├── decideForTroll(s, idx, tree)  ← drop / chop / move / wait
├── decide(s, buf) → count        ← tour 0 : TRAIN ; puis une action par troll allié
└── pickClosestFreeTree(s, idx)   ← greedy, marque treeTaken[]

Player.main
├── GameState.readInit / PathTable.init / ShackAdjacency.init (une fois)
└── boucle : readTurn → decide → toCommand → println    (zéro alloc en steady state hors String d'output)
```

**Coût mémoire de l'agent :**
- `treeTaken` : `boolean[128]` = 128 octets.
- `ShackAdjacency.x/y` : 8 octets.
- Aucune autre allocation persistante.

**Coût CPU par tour (steady state) :**
- `maybeTrain` : O(1) sauf au tour 0.
- `decide` : O(trollCount × treeCount) × O(1) `PathTable.distance` ≈ ~10 × ~80 = 800 ops + ~10 × O(4) pour les drops. << 1 µs.

**Aptitude au GA :**
- Aucune allocation par appel (le `int[]` est fourni par l'appelant).
- Lecture pure du `GameState` ; aucune mutation, aucun champ d'instance.
- `treeTaken` est un buffer `static` réutilisable mais **réinitialisé en début de `decide`** — donc safe pour appels successifs depuis la simu GA tant que les appels ne sont pas concurrents (le GA est typiquement mono-thread CodinGame).

---

## Self-Review (run inline)

**1. Spec coverage :**
- ✅ "au tour 1 toujours faire un train avec stats max" → `maybeTrain` appelé seulement si `s.turn == 0`, max sur ms/cc/cp indépendamment.
- ✅ "stats qui maximise carry capacity, powerchop et speed" → ms, cc, cp maximisés ; hp = 0.
- ✅ "calculer la valeur max possible pour chaque stats en fonction des ressources disponibles" → `maxV(resource, n, floor)` itère jusqu'au plafond.
- ✅ "chaque troll focus l'arbre le plus prêt" → `pickClosestFreeTree` via `PathTable.distance` (chemin réel, pas Manhattan).
- ✅ "couper (chop) puis ramener (WOOD)" → branche `wood > 0` du `decideForTroll` qui redirige vers le shack.
- ✅ "ramener sur une case adjacente du shack et déposer (DROP)" → `ShackAdjacency` + `Action.drop` lorsque sur la bonne case.
- ✅ "un arbre ne peut être target que une fois et par un seul troll" → `treeTaken[]` marqué dès l'assignation.
- ✅ "algo le plus optimisé possible pour éviter de perdre du temps et de la ressource mémoire dans un GA" → static + zero-alloc + O(trolls × trees) avec lookups O(1).

**2. Placeholder scan :** aucun TODO/TBD. Tous les corps de méthodes sont complets, toutes les structures de tests sont concrètes.

**3. Type consistency :**
- `Action.type / arg1 / arg2 / trollIdx / trainXX` renvoient tous `int` (valeur dans [0, 255] ou [0, 63]) — cohérent dans tous les tests.
- `ShackAdjacency.x / y` sont `byte[]` (cohérent avec la convention SoA du modèle) ; chaque lecture passe par `& 0xFF`.
- `GreedyAgent.maybeTrain` renvoie `-1` (int) en cas d'échec, sinon un `int` action valide — cohérent avec `decide` qui teste `!= -1`.
- `pickClosestFreeTree` renvoie `-1` si aucun arbre, sinon index dans `[0, treeCount[` — cohérent avec `decideForTroll(s, idx, treeIdx)` qui teste `treeIdx < 0`.
- `treeTaken` est `boolean[MAX_TREES]` partagé entre appels mais réinitialisé en `Arrays.fill(treeTaken, 0, s.treeCount, false)` (taille variable, ne touche pas au-delà).
- `count` dans `decide` ≤ `MAX_TROLLS + 1` → c'est exactement la taille du `actionBuf` alloué dans `Player.main`.

**4. Cohérence avec le code existant :**
- Imports : `PathTable.distance`, `GameState.tileAt`, `TileType.GRASS` — tous présents dans le code actuel (vérifié dans `Read`).
- Pas de modification de `GameState`, `PathTable`, `TileType`, `ResourceType`, `TreeType`, `ActionType` — seules extensions : `Action.java` (squelette → corps).
- Convention `final class` + constructeur privé : identique à `GameState`, `PathTable`, `TileType`, etc.

OK, plan complet et cohérent.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-14-greedy-heuristic-agent.md`. Two execution options :**

**1. Subagent-Driven (recommended)** — je dispatche un sous-agent frais par tâche, review entre les tâches, itération rapide.

**2. Inline Execution** — exécution des tâches dans cette session avec checkpoints pour review.

**Which approach ?**
