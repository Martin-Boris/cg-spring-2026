# Static Pathfinding Precomputation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Précalculer **tous les plus courts chemins entre cases GRASS** au tour 1 (budget 1 s) et exposer une lecture O(1) des distances et des étapes intermédiaires, afin que la simulation MOVE du GA soit un simple lookup mémoire.

**Architecture:**
- BFS depuis chaque case GRASS (4-connectivité, obstacles = WATER/ROCK/IRON/SHACK).
- Indexation **compacte** : on numérote 0..N-1 uniquement les cases praticables (N ≤ ~200, fits in unsigned `byte`).
- Stockage symétrique : `paths[a][b]` et `paths[b][a]` **partagent la même référence `byte[]`** (path stocké de `min(a,b)` vers `max(a,b)` ; lecture inversée pour l'autre sens).
- Buffers BFS pré-alloués une fois et réutilisés : zéro allocation par BFS.

**Tech Stack:** Java 21, JUnit 5, AssertJ. Code dans `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java`. Aucune dépendance externe.

**Why this design:**
- N ≤ 242 cases max → BFS×N = O(N²) ≈ 50 000 ops, largement < 1 s.
- Chemin stocké comme `byte[]` (cell ids unsigned) → max ~33 cases → empreinte totale ~500 KB.
- Reverse-trick demandé par l'utilisateur : on évite de calculer `b→a` (BFS asymétrique) et on évite de stocker 2× les chemins.
- Compact (vs raw `y*W+x`) : ~30 % de mémoire en moins, cohérent avec le `model.md` (« empreinte mémoire compacte »).

**Anti-goals (hors scope) :**
- Trolls dynamiques sur la trajectoire : la précomputation ignore les autres trolls (la simu GA accepte cette approximation, en cohérence avec le pathfinding boîte noire de l'arbitre).
- MOVE vers une case non-GRASS : l'appelant cible toujours une case walkable (le brief prévoit que CG choisit la GRASS la plus proche en direction de la cible — cette logique reste côté appelant).

---

## File Structure

```
src/main/java/com/bmrt/cgspring2026/
  pathfinding/
    PathTable.java          ← état statique (N, mapping, dist, paths) + init() + lecture
src/test/java/com/bmrt/cgspring2026/
  pathfinding/
    PathTableTest.java      ← un seul fichier de tests (suffisant à la taille du module)
src/main/java/com/bmrt/cgspring2026/
  Player.java               ← appelle PathTable.init() après GameState.readInit()
```

`PathTable` est une classe **finale, non instanciable, à champs statiques** : la carte est figée pour la partie (cf. `GameState` qui suit la même convention). Les buffers BFS sont aussi statiques pour rester partagés entre les BFS de la phase init.

---

## Test Grid (utilisée dans tous les tests)

Grille 4×3 :
```
....   row 0
.##.   row 1   (1,1) et (2,1) = ROCK, infranchissables
....   row 2
```

Cases walkable indexées en row-major :
```
(0,0)=0  (1,0)=1  (2,0)=2  (3,0)=3
(0,1)=4                    (3,1)=5
(0,2)=6  (1,2)=7  (2,2)=8  (3,2)=9
```
N=10. Distance min (0,0)→(3,2) = 5 (un chemin valide : (0,0)→(0,1)→(0,2)→(1,2)→(2,2)→(3,2)).

Helper de tests pour fabriquer cette carte sans passer par `Scanner` :

```java
private static void loadTestGrid() {
    GameState.width = 4;
    GameState.height = 3;
    GameState.tiles = new byte[]{
        TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
        TileType.GRASS, TileType.ROCK,  TileType.ROCK,  TileType.GRASS,
        TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
    };
}
```

---

## Task 1 : Indexation compacte des cases walkable

**Files:**
- Create: `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java`
- Test: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java`

**Responsibility:** balayer `GameState.tiles`, attribuer un id 0..N-1 à chaque case GRASS, remplir les tableaux de mapping.

- [ ] **Step 1 : Écrire le test (RED)**

Créer `PathTableTest.java` :

```java
package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableTest {

    private static void loadTestGrid() {
        GameState.width = 4;
        GameState.height = 3;
        GameState.tiles = new byte[]{
            TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
            TileType.GRASS, TileType.ROCK,  TileType.ROCK,  TileType.GRASS,
            TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS,
        };
    }

    @Test void indexCellsAssigns10WalkableCells() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.N).isEqualTo(10);
    }

    @Test void indexCellsAssignsRowMajorIds() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellId(0, 0)).isEqualTo(0);
        assertThat(PathTable.cellId(3, 0)).isEqualTo(3);
        assertThat(PathTable.cellId(0, 1)).isEqualTo(4);
        assertThat(PathTable.cellId(3, 1)).isEqualTo(5);
        assertThat(PathTable.cellId(3, 2)).isEqualTo(9);
    }

    @Test void indexCellsMarksObstaclesUnreachable() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellId(1, 1)).isEqualTo(0xFF);
        assertThat(PathTable.cellId(2, 1)).isEqualTo(0xFF);
    }

    @Test void cellXYReverseLookup() {
        loadTestGrid();
        PathTable.indexCells();
        assertThat(PathTable.cellX[5] & 0xFF).isEqualTo(3);
        assertThat(PathTable.cellY[5] & 0xFF).isEqualTo(1);
    }
}
```

- [ ] **Step 2 : Run, expect compile error / FAIL**

```
mvn -q -Dtest=PathTableTest test
```

Expected : compile error `cannot find symbol class PathTable`.

- [ ] **Step 3 : Implémenter le squelette + indexCells (GREEN)**

Créer `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java` :

```java
package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;

public final class PathTable {

    public static final int UNREACHABLE = 0xFF;

    public static int N;
    public static byte[] cellIdAt;     // [W*H] -> id 0..N-1, 0xFF si non walkable
    public static byte[] cellX;        // [N] x de la case d'id i
    public static byte[] cellY;        // [N] y de la case d'id i

    static void indexCells() {
        int W = GameState.width;
        int H = GameState.height;
        cellIdAt = new byte[W * H];
        java.util.Arrays.fill(cellIdAt, (byte) 0xFF);
        N = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (GameState.tileAt(x, y) == TileType.GRASS) N++;
            }
        }
        cellX = new byte[N];
        cellY = new byte[N];
        int id = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (GameState.tileAt(x, y) == TileType.GRASS) {
                    cellIdAt[y * W + x] = (byte) id;
                    cellX[id] = (byte) x;
                    cellY[id] = (byte) y;
                    id++;
                }
            }
        }
    }

    public static int cellId(int x, int y) {
        return cellIdAt[y * GameState.width + x] & 0xFF;
    }

    private PathTable() {}
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=PathTableTest test
```

Expected : 4 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java
git commit -m "pathtable: compact walkable cell indexing"
```

---

## Task 2 : BFS depuis une source unique

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java` (ajout des buffers BFS, méthode `bfs(int src)`)
- Modify: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java`

**Responsibility:** depuis un id de cellule source, remplir `bfsDist[N]` (distance) et `bfsPrev[N]` (prédecesseur, -1 si racine ou non atteint). Buffers réutilisés entre BFS.

- [ ] **Step 1 : Écrire les tests (RED)**

Ajouter à `PathTableTest.java` :

```java
@Test void bfsFromCornerComputesDistances() {
    loadTestGrid();
    PathTable.indexCells();
    PathTable.allocateBfsBuffers();
    PathTable.bfs(0); // source = (0,0)
    // (0,0)=0, (1,0)=1, (2,0)=2, (3,0)=3 distances 0,1,2,3
    assertThat(PathTable.bfsDist[0]).isEqualTo(0);
    assertThat(PathTable.bfsDist[1]).isEqualTo(1);
    assertThat(PathTable.bfsDist[2]).isEqualTo(2);
    assertThat(PathTable.bfsDist[3]).isEqualTo(3);
    // (0,1)=4 dist 1, (3,1)=5 dist 4 (contourne via (3,0))
    assertThat(PathTable.bfsDist[4]).isEqualTo(1);
    assertThat(PathTable.bfsDist[5]).isEqualTo(4);
    // (3,2)=9 dist 5
    assertThat(PathTable.bfsDist[9]).isEqualTo(5);
}

@Test void bfsRecordsPredecessors() {
    loadTestGrid();
    PathTable.indexCells();
    PathTable.allocateBfsBuffers();
    PathTable.bfs(0);
    assertThat(PathTable.bfsPrev[0]).isEqualTo(-1); // source
    assertThat(PathTable.bfsPrev[1]).isEqualTo(0);  // (1,0) ← (0,0)
    assertThat(PathTable.bfsPrev[4]).isEqualTo(0);  // (0,1) ← (0,0)
}

@Test void bfsResetsBetweenCalls() {
    loadTestGrid();
    PathTable.indexCells();
    PathTable.allocateBfsBuffers();
    PathTable.bfs(0);
    PathTable.bfs(9); // source = (3,2)
    assertThat(PathTable.bfsDist[9]).isEqualTo(0);
    assertThat(PathTable.bfsDist[0]).isEqualTo(5);
    assertThat(PathTable.bfsPrev[9]).isEqualTo(-1);
}
```

- [ ] **Step 2 : Run, expect FAIL**

```
mvn -q -Dtest=PathTableTest test
```

Expected : compile error sur `allocateBfsBuffers`, `bfs`, `bfsDist`, `bfsPrev`.

- [ ] **Step 3 : Ajouter buffers + BFS (GREEN)**

Dans `PathTable.java`, ajouter sous les champs existants :

```java
public  static int[] bfsDist;   // distance depuis la dernière source BFS, UNREACHABLE sinon
public  static int[] bfsPrev;   // prédecesseur dans l'arbre BFS, -1 si racine ou non atteint
private static int[] queue;

private static final int[] DX = { 1, -1, 0,  0 };
private static final int[] DY = { 0,  0, 1, -1 };

static void allocateBfsBuffers() {
    queue   = new int[N];
    bfsDist = new int[N];
    bfsPrev = new int[N];
}

static void bfs(int src) {
    java.util.Arrays.fill(bfsDist, UNREACHABLE);
    java.util.Arrays.fill(bfsPrev, -1);
    int W = GameState.width;
    int H = GameState.height;
    bfsDist[src] = 0;
    int head = 0, tail = 0;
    queue[tail++] = src;
    while (head < tail) {
        int cur = queue[head++];
        int cx = cellX[cur] & 0xFF;
        int cy = cellY[cur] & 0xFF;
        int d  = bfsDist[cur];
        for (int k = 0; k < 4; k++) {
            int nx = cx + DX[k];
            int ny = cy + DY[k];
            if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
            int nid = cellIdAt[ny * W + nx] & 0xFF;
            if (nid == UNREACHABLE) continue;
            if (bfsDist[nid] != UNREACHABLE) continue;
            bfsDist[nid] = d + 1;
            bfsPrev[nid] = cur;
            queue[tail++] = nid;
        }
    }
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=PathTableTest test
```

Expected : 7 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java
git commit -m "pathtable: bfs from single source with reusable buffers"
```

---

## Task 3 : All-pairs avec stockage symétrique partagé

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java`

**Responsibility:** méthode `init()` qui orchestre `indexCells` + `allocateBfsBuffers` + BFS depuis chaque source. Pour chaque paire `(a, b)` avec `a < b`, on reconstruit le chemin `a → b` (longueur = `bfsDist[b] + 1`) et on l'**assigne aux deux slots** `paths[a][b]` et `paths[b][a]` (même référence `byte[]`). La distance est aussi stockée symétriquement dans `dist[a][b]` et `dist[b][a]`.

**Convention de lecture :** `paths[a][b]` stocke toujours le chemin de `min(a,b)` vers `max(a,b)`. Pour lire `b → a` quand `b > a`, il faut **lire le tableau à l'envers**. Cette convention est encapsulée dans le Read API (Task 4).

- [ ] **Step 1 : Écrire les tests (RED)**

Ajouter à `PathTableTest.java` :

```java
@Test void initFillsDistMatrixSymmetrically() {
    loadTestGrid();
    PathTable.init();
    int dAB = PathTable.dist[0][9] & 0xFF;
    int dBA = PathTable.dist[9][0] & 0xFF;
    assertThat(dAB).isEqualTo(5);
    assertThat(dBA).isEqualTo(5);
}

@Test void initSelfDistanceIsZero() {
    loadTestGrid();
    PathTable.init();
    for (int i = 0; i < PathTable.N; i++) {
        assertThat(PathTable.dist[i][i] & 0xFF).isEqualTo(0);
    }
}

@Test void initSharesPathReferenceForReversePair() {
    loadTestGrid();
    PathTable.init();
    // même référence d'objet pour (a,b) et (b,a)
    assertThat(PathTable.paths[0][9]).isSameAs(PathTable.paths[9][0]);
    assertThat(PathTable.paths[3][7]).isSameAs(PathTable.paths[7][3]);
}

@Test void initStoresPathFromMinToMax() {
    loadTestGrid();
    PathTable.init();
    byte[] p = PathTable.paths[0][9];
    assertThat(p.length).isEqualTo(6); // dist 5 + 1
    assertThat(p[0] & 0xFF).isEqualTo(0); // commence par min(0,9)=0
    assertThat(p[p.length - 1] & 0xFF).isEqualTo(9); // finit par max(0,9)=9
}

@Test void initPathIsContiguousNeighbours() {
    loadTestGrid();
    PathTable.init();
    byte[] p = PathTable.paths[0][9];
    for (int i = 1; i < p.length; i++) {
        int a = p[i - 1] & 0xFF;
        int b = p[i] & 0xFF;
        int dx = Math.abs((PathTable.cellX[a] & 0xFF) - (PathTable.cellX[b] & 0xFF));
        int dy = Math.abs((PathTable.cellY[a] & 0xFF) - (PathTable.cellY[b] & 0xFF));
        assertThat(dx + dy).isEqualTo(1); // 4-connexité
    }
}

@Test void initSelfPathIsSingleton() {
    loadTestGrid();
    PathTable.init();
    byte[] p = PathTable.paths[3][3];
    assertThat(p.length).isEqualTo(1);
    assertThat(p[0] & 0xFF).isEqualTo(3);
}
```

- [ ] **Step 2 : Run, expect FAIL**

```
mvn -q -Dtest=PathTableTest test
```

Expected : compile error sur `init`, `dist`, `paths`.

- [ ] **Step 3 : Implémenter `init` + reconstruction (GREEN)**

Dans `PathTable.java`, ajouter en haut sous les champs `cellY` :

```java
public static byte[][]   dist;    // [N][N] distance, UNREACHABLE si non connecté
public static byte[][][] paths;   // [N][N] -> chemin partagé symétriquement
```

Ajouter ces méthodes :

```java
public static void init() {
    indexCells();
    allocateBfsBuffers();
    dist  = new byte[N][N];
    paths = new byte[N][N][];
    for (int src = 0; src < N; src++) {
        bfs(src);
        dist[src][src]  = 0;
        paths[src][src] = new byte[]{ (byte) src };
        for (int dst = src + 1; dst < N; dst++) {
            if (bfsDist[dst] == UNREACHABLE) {
                dist[src][dst] = (byte) UNREACHABLE;
                dist[dst][src] = (byte) UNREACHABLE;
                continue;
            }
            byte[] p = reconstruct(src, dst);
            paths[src][dst] = p;
            paths[dst][src] = p;             // référence partagée — lecture inversée pour dst→src
            byte d = (byte) (p.length - 1);
            dist[src][dst] = d;
            dist[dst][src] = d;
        }
    }
}

private static byte[] reconstruct(int src, int dst) {
    int len = bfsDist[dst] + 1;
    byte[] path = new byte[len];
    int cur = dst;
    for (int i = len - 1; i >= 0; i--) {
        path[i] = (byte) cur;
        cur = bfsPrev[cur];
    }
    return path;
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=PathTableTest test
```

Expected : 13 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java
git commit -m "pathtable: all-pairs with symmetric path sharing"
```

---

## Task 4 : Read API (id et coordonnées, lecture inversée transparente)

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java`
- Modify: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java`

**Responsibility:** exposer une lecture O(1) qui cache le sens de stockage. Trois usages :
1. `distance(fromId, toId)` → distance (UNREACHABLE si non connectés).
2. `stepAlong(fromId, toId, k)` → cell id à l'étape `k` du chemin `from → to` (k clampé à `distance`).
3. Surcharge par coordonnées `(fx, fy, tx, ty)` pour le confort d'appel depuis `GameState` (qui stocke `trollX/trollY` en `byte`).

**Inverse-read math :** si `from <= to`, le chemin stocké est dans le bon sens, on lit `p[k]`. Sinon, `from > to` → on lit à l'envers `p[len - 1 - k]`. Aucune copie, aucune allocation.

- [ ] **Step 1 : Écrire les tests (RED)**

Ajouter à `PathTableTest.java` :

```java
@Test void distanceById() {
    loadTestGrid();
    PathTable.init();
    assertThat(PathTable.distance(0, 9)).isEqualTo(5);
    assertThat(PathTable.distance(9, 0)).isEqualTo(5);
    assertThat(PathTable.distance(3, 3)).isEqualTo(0);
}

@Test void distanceByCoord() {
    loadTestGrid();
    PathTable.init();
    assertThat(PathTable.distance(0, 0, 3, 2)).isEqualTo(5);
    assertThat(PathTable.distance(3, 2, 0, 0)).isEqualTo(5);
}

@Test void stepAlongForwardReadsPathDirectly() {
    loadTestGrid();
    PathTable.init();
    // from=0 (id 0) to=9 (id 9), forward read
    assertThat(PathTable.stepAlong(0, 9, 0)).isEqualTo(0); // étape 0 = source
    assertThat(PathTable.stepAlong(0, 9, 5)).isEqualTo(9); // étape 5 = destination
}

@Test void stepAlongReverseReadsPathBackward() {
    loadTestGrid();
    PathTable.init();
    // from=9 to=0 : doit reconstituer le chemin inverse sans nouvelle allocation
    assertThat(PathTable.stepAlong(9, 0, 0)).isEqualTo(9); // étape 0 = source = 9
    assertThat(PathTable.stepAlong(9, 0, 5)).isEqualTo(0); // étape 5 = destination = 0
    // les étapes intermédiaires doivent être les MÊMES que (0→9) lues à l'envers
    int forwardStep1 = PathTable.stepAlong(0, 9, 1);
    int reverseStep4 = PathTable.stepAlong(9, 0, 4);
    assertThat(forwardStep1).isEqualTo(reverseStep4);
}

@Test void stepAlongClampsBeyondDistance() {
    loadTestGrid();
    PathTable.init();
    // si on demande une étape > distance, on retourne la destination
    assertThat(PathTable.stepAlong(0, 9, 99)).isEqualTo(9);
    assertThat(PathTable.stepAlong(9, 0, 99)).isEqualTo(0);
}

@Test void stepAlongSelfReturnsSelf() {
    loadTestGrid();
    PathTable.init();
    assertThat(PathTable.stepAlong(7, 7, 0)).isEqualTo(7);
    assertThat(PathTable.stepAlong(7, 7, 3)).isEqualTo(7);
}

@Test void stepAlongByCoordReturnsCoord() {
    loadTestGrid();
    PathTable.init();
    // de (0,0) vers (3,2), ms=2 : on doit avancer de 2 cases
    int after = PathTable.stepAlong(0, 0, 3, 2, 2);
    int x = after % GameState.width;
    int y = after / GameState.width;
    // étape 2 sur le chemin (0,0)→(3,2) : doit être à distance 2 de (0,0)
    int dx = Math.abs(x - 0);
    int dy = Math.abs(y - 0);
    assertThat(dx + dy).isEqualTo(2);
}
```

- [ ] **Step 2 : Run, expect FAIL**

```
mvn -q -Dtest=PathTableTest test
```

Expected : compile error sur les nouvelles surcharges.

- [ ] **Step 3 : Implémenter le Read API (GREEN)**

Ajouter dans `PathTable.java` :

```java
public static int distance(int fromId, int toId) {
    return dist[fromId][toId] & 0xFF;
}

public static int distance(int fx, int fy, int tx, int ty) {
    int W = GameState.width;
    int from = cellIdAt[fy * W + fx] & 0xFF;
    int to   = cellIdAt[ty * W + tx] & 0xFF;
    return dist[from][to] & 0xFF;
}

/** Cell id à l'étape `k` du chemin `from → to` ; `k` est clampé à la distance. */
public static int stepAlong(int fromId, int toId, int k) {
    byte[] p = paths[fromId][toId];
    int last = p.length - 1;
    if (k > last) k = last;
    if (fromId <= toId) {
        return p[k] & 0xFF;
    }
    return p[last - k] & 0xFF;
}

/** Surcharge par coords : retourne le **raw cell index** `y*W + x` de la case atteinte. */
public static int stepAlong(int fx, int fy, int tx, int ty, int k) {
    int W = GameState.width;
    int from = cellIdAt[fy * W + fx] & 0xFF;
    int to   = cellIdAt[ty * W + tx] & 0xFF;
    int destId = stepAlong(from, to, k);
    return (cellY[destId] & 0xFF) * W + (cellX[destId] & 0xFF);
}
```

- [ ] **Step 4 : Run, expect PASS**

```
mvn -q -Dtest=PathTableTest test
```

Expected : 20 tests verts.

- [ ] **Step 5 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/pathfinding/PathTable.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableTest.java
git commit -m "pathtable: read api with transparent reverse-read"
```

---

## Task 5 : Intégration au tour 1 dans `Player.java`

**Files:**
- Modify: `src/main/java/com/bmrt/cgspring2026/Player.java`
- Test: `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableInitBudgetTest.java`

**Responsibility:** appeler `PathTable.init()` une seule fois après `GameState.readInit(in)`. Vérifier que sur une carte taille max (22×11 avec ~80 % de GRASS) le coût reste < 1 s avec une marge confortable.

- [ ] **Step 1 : Écrire le test de budget (RED)**

Créer `src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableInitBudgetTest.java` :

```java
package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableInitBudgetTest {

    @Test void initFitsInOneSecondOnMaxMap() {
        // Carte max : 22 × 11 entièrement GRASS = 242 cases (worst case all-pairs).
        GameState.width = 22;
        GameState.height = 11;
        GameState.tiles = new byte[22 * 11];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);

        // warm-up JIT
        PathTable.init();

        long start = System.nanoTime();
        PathTable.init();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // budget tour 1 = 1000 ms ; on s'autorise 200 ms ici (large marge pour le reste : I/O, autres init)
        assertThat(elapsedMs).isLessThan(200L);
        assertThat(PathTable.N).isEqualTo(242);
    }
}
```

- [ ] **Step 2 : Run, expect FAIL au compile (PathTable existe déjà donc PASS si la perf tient)**

```
mvn -q -Dtest=PathTableInitBudgetTest test
```

Si le test passe directement, OK. S'il échoue sur la perf, c'est l'occasion d'optimiser la BFS (mais à N=242 on est bien en dessous).

- [ ] **Step 3 : Brancher l'init dans `Player.java` (GREEN)**

Modifier `src/main/java/com/bmrt/cgspring2026/Player.java`. Le `main` actuel est :

```java
public static void main(String[] args) {
    Scanner in = new Scanner(System.in);
    GameState.readInit(in);
    GameState state = new GameState();

    while (true) {
        long start = System.nanoTime();
        state.readTurn(in);
        ...
    }
}
```

Ajouter l'import et l'appel à `PathTable.init()` **avant** la boucle (mais on veut mesurer le temps incluant readTurn du tour 1, donc on fait l'init hors boucle après readInit) :

```java
import com.bmrt.cgspring2026.pathfinding.PathTable;
...
public static void main(String[] args) {
    Scanner in = new Scanner(System.in);
    GameState.readInit(in);
    PathTable.init();
    GameState state = new GameState();

    while (true) {
        long start = System.nanoTime();
        state.readTurn(in);
        ...
    }
}
```

> Note : le budget de **1000 ms du premier tour** englobe la lecture des inputs **du tour 1** (`readTurn`), pas seulement `readInit`. L'init du PathTable se fait avant `readTurn`, ce qui laisse au CG le temps qu'il a déjà alloué pour la lecture. Pour respecter strictement le budget, on appelle `init()` après `readInit`. Si à l'usage CG mesure le tour à partir de `readInit`, le budget reste bien tenu (init < 200 ms d'après le test).

- [ ] **Step 4 : Run, expect PASS sur tous les tests**

```
mvn -q test
```

Expected : tous les tests verts (anciens + nouveaux PathTable + budget).

- [ ] **Step 5 : Vérification quality-gate avant commit**

```
mvn package -q
grep -rn "System\.out\.print" src/main/java/com/bmrt/cgspring2026/ | grep -v Player.java | grep -v builder/
```

Expected : compile OK ; aucun `System.out` parasite (le seul autorisé est l'output d'action dans `Player.java`).

- [ ] **Step 6 : Commit**

```
git add src/main/java/com/bmrt/cgspring2026/Player.java \
        src/test/java/com/bmrt/cgspring2026/pathfinding/PathTableInitBudgetTest.java
git commit -m "player: precompute path table at init"
```

---

## Récapitulatif structure finale

```
PathTable                       (statique, non instanciable)
├── N                           int          ← nb de cases walkable
├── cellIdAt[W*H]               byte         ← raw → walkable id, 0xFF si obstacle
├── cellX[N], cellY[N]          byte         ← walkable id → coords
├── dist[N][N]                  byte         ← distances, UNREACHABLE = 0xFF
├── paths[N][N]                 byte[]       ← chemin shared min→max ; lire à l'envers si from > to
├── bfsDist, bfsPrev, queue     int[N]       ← buffers BFS réutilisés
└── API
    ├── init()                              ← une seule fois au tour 1
    ├── distance(id, id) / (x,y,x,y)
    └── stepAlong(id, id, k) / (x,y,x,y,k)
```

**Empreinte mémoire estimée (carte max 242 cases tout GRASS, N=242) :**
- `paths` : 242×242 = 58 564 références (~470 KB) ; octets stockés ~ 242×241/2 × dist_moyenne(~10) ≈ 290 KB → total ~760 KB.
- `dist` : 242×242 = 58 564 octets ≈ 60 KB.
- `cellIdAt`, `cellX`, `cellY`, buffers BFS : négligeable (< 5 KB).
- **Total ≈ 800 KB**, bien sous la limite mémoire CG (256 MB).

**Coût CPU init estimé :**
- 242 BFS × O(N) = ~ 60 000 visites de cases.
- Reconstruction : 242×241/2 ≈ 29 000 paths × longueur moyenne ~10 ≈ 290 000 écritures de byte.
- Sur JVM warmée : largement < 50 ms. Test budget 200 ms = grosse marge.

---

## Self-Review (run inline)

**1. Spec coverage :**
- ✅ « tous les plus courts chemins entre deux cases » → `paths[N][N]`.
- ✅ « structure d'objet pour optimiser la lecture » → lecture O(1) via `dist`/`paths`, pas de calcul à la volée.
- ✅ « chargement au tour 1 » → `PathTable.init()` appelé après `readInit`, budget vérifié par test.
- ✅ « optimiser data structure et calcul » → SoA byte[], BFS O(N), buffers réutilisés.
- ✅ « path a→b ⇒ pas de calcul b→a » → reverse-trick : référence partagée + lecture inversée.

**2. Placeholder scan :** aucun TODO/TBD. Toutes les méthodes ont un corps complet.

**3. Type consistency :**
- `PathTable.cellId(x, y)` retourne `int` (0..N-1 ou 0xFF), cohérent dans tous les tests.
- `bfsDist` / `bfsPrev` sont `int[]` (pas `byte`) car on stocke -1 dans `bfsPrev` (sentinelle) ; les distances finales sont rétrécies en `byte` lors de l'écriture dans `dist[][]`.
- `paths[a][b]` partage **strictement** la même référence `byte[]` que `paths[b][a]` (vérifié par `isSameAs`).
- `UNREACHABLE = 0xFF` cohérent entre `cellIdAt` (sentinelle non-walkable) et `dist` (sentinelle non-connecté).

OK, plan complet et cohérent.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-14-static-pathfinding.md`. Two execution options :**

**1. Subagent-Driven (recommended)** — je dispatche un sous-agent frais par tâche, review entre les tâches, itération rapide.

**2. Inline Execution** — exécution des tâches dans cette session avec checkpoints pour review.

**Which approach ?**
