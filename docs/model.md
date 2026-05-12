# Modèle — Troll Farm (CG Spring 2026)

## Objectif & contraintes héritées de `algo.md`

- `GameState.copy()` < 1 µs (cible : simu < 2 µs/tour pour permettre ≥ 5 000 simus dans 50 ms).
- Zéro allocation dans la boucle AG.
- Lookups O(1) par tile (troll présent ? arbre présent ?).
- Actions encodées en `int` (chromosome AG = `int[]`).
- Constructeurs `public X()` sans args ; parsing dans méthodes statiques séparées.

## Vue d'ensemble

```
Player
  └── GameState                      (l'état du jeu, mutable, copiable)
        ├── terrain  : byte[]        (grille plate, indexée y*width+x)
        ├── treeByTile : short[]     (-1 ou index dans tableaux d'arbres)
        ├── myTrollByTile, oppTrollByTile : short[]
        ├── arbres   : SoA byte[]    (type, x, y, size, health, fruits, cooldown, alive)
        ├── trolls   : SoA byte[]    (player, x, y, ms, cc, hp, cp, carry[6])
        ├── shackMe, shackOpp : int  (tile)
        ├── invMe, invOpp    : int[6]
        └── turn : int

Statics (constantes uniquement) :
  TileType, Resource, TreeType, ActionType, TreeStats

Actions (méthodes statiques sur int packé)
  encode(...) → int
  type/troll/p1/p2(int) → int
  format(int, state) → String

IAEngine (V1 / V2 / ...) — lit GameState, produit int[] actions, écrit la commande.
```

## Indexation & conventions

- **Tile index** : `tile = y * width + x`. `x ∈ [0, width[`, `y ∈ [0, height[`.
- **`tileCount = width * height`** ≤ 242 (height max 11 → width 22).
- **Ressources** indexées 0..5 dans cet ordre fixe : PLUM, LEMON, APPLE, BANANA, IRON, WOOD. Sert pour les inventaires (porté et shack).
- **Tree types** : PLUM=0, LEMON=1, APPLE=2, BANANA=3 (alignés sur les indices ressources fruits).
- **Player** : 0 = moi, 1 = adversaire.

## Constantes — capacités

| Nom | Valeur | Justification |
|---|---|---|
| `MAX_TILES` | 256 | width×height ≤ 242 |
| `MAX_TREES` | 128 | replantation incluse, large marge |
| `MAX_TROLLS` | 32 | TRAIN coûteux ⇒ ~10-20 en pratique |
| `MAX_HORIZON` | 12 | borne AG |

## Classes

### `GameState` (modèle principal, hot path)

**Constructeur** : `public GameState()` — sans args, allocations différées au premier `readInit`.

#### Champs (mutables, primitifs)

```java
// Carte
public int width, height, tileCount;
public byte[] terrain;          // taille tileCount ; TileType.*
public boolean[] waterAdj;      // taille tileCount ; précalculé une fois
public int shackMeTile, shackOppTile;

// Index par tile (recalculé chaque tour, ou maintenu par apply)
public short[] treeByTile;      // -1 ou index dans treeX/Y/...
public short[] myTrollByTile;   // -1 ou index dans trollX/Y/...
public short[] oppTrollByTile;  // idem (les deux peuvent coexister sur la même case)

// Arbres — SoA, swap-remove sur destruction
public int treeCount;
public byte[] treeType;         // [MAX_TREES]
public byte[] treeX, treeY;
public byte[] treeSize;         // 1..4
public byte[] treeHealth;
public byte[] treeFruits;       // 0..3
public byte[] treeCooldown;

// Trolls — SoA
public int trollCount;
public byte[] trollPlayer;      // [MAX_TROLLS] ; 0/1
public byte[] trollX, trollY;
public byte[] trollMoveSpeed, trollCarryCapacity, trollHarvestPower, trollChopPower;
public byte[] trollCarry;       // [MAX_TROLLS * 6] flat : index = troll*6 + resource
public int[] trollOriginalId;   // [MAX_TROLLS] — id CG fourni par l'arbitre (pour output)

// Inventaires shack
public int[] invMe;             // [6]
public int[] invOpp;            // [6]

// Tour courant
public int turn;
```

#### Méthodes clés

```java
public GameState();

// Parsing — méthodes statiques pour tester sans Scanner
public static void readInit(Scanner in, GameState dst);   // alloue et remplit terrain, shacks, waterAdj
public static void readTurn(Scanner in, GameState dst);   // inventaires, arbres, trolls, indexes

// Copy pour simulation
public void copyFrom(GameState src);   // System.arraycopy zéro alloc

// Helpers
public int score(int player);          // recalcule depuis invMe/invOpp
public int tile(int x, int y);         // y*width + x
public boolean isAdjacent(int tile1, int tile2);
public boolean isWalkable(int tile);   // GRASS uniquement

// Simulation
public void applyTurn(int[] myActions, int[] oppActions);  // résolution dans l'ordre du brief
```

Notes :
- `readInit` est appelée une fois, alloue tous les tableaux (taille connue dès qu'on a `width × height`).
- `readTurn` ne réalloue pas : elle remet les `*ByTile` à -1, écrase `treeCount`/`trollCount`, remplit les SoA.
- `copyFrom` permet de réutiliser un `GameState` pool sans `new` — chaque thread/simu garde son instance.

### `TileType` (constantes byte)

```java
public final class TileType {
    public static final byte GRASS = 0;
    public static final byte WATER = 1;
    public static final byte ROCK  = 2;
    public static final byte IRON  = 3;
    public static final byte SHACK_ME  = 4;
    public static final byte SHACK_OPP = 5;
    private TileType() {}
}
```

### `Resource` (constantes byte)

```java
public final class Resource {
    public static final byte PLUM   = 0;
    public static final byte LEMON  = 1;
    public static final byte APPLE  = 2;
    public static final byte BANANA = 3;
    public static final byte IRON   = 4;
    public static final byte WOOD   = 5;
    public static final int COUNT   = 6;
    private Resource() {}
}
```

### `TreeType` (constantes byte alignées sur les fruits Resource)

```java
public final class TreeType {
    public static final byte PLUM   = 0;
    public static final byte LEMON  = 1;
    public static final byte APPLE  = 2;
    public static final byte BANANA = 3;
    public static final int COUNT   = 4;
    private TreeType() {}
}
```

### `TreeStats` (tables figées du brief)

```java
public final class TreeStats {
    // [type] -> cooldown normal / près de l'eau
    public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
    public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };
    // [type][size-1] -> health max
    public static final byte[][] HEALTH = {
        { 6, 8, 10, 12 },   // PLUM
        { 6, 8, 10, 12 },   // LEMON
        { 11, 14, 17, 20 }, // APPLE
        { 3, 4, 5, 6 }      // BANANA
    };
    public static final int MAX_FRUITS = 3;
    public static final int MAX_SIZE   = 4;
    private TreeStats() {}
}
```

### `ActionType` (constantes byte, alignées sur le bit-packing)

```java
public final class ActionType {
    public static final byte WAIT    = 0;
    public static final byte MOVE    = 1;
    public static final byte HARVEST = 2;
    public static final byte PLANT   = 3;
    public static final byte CHOP    = 4;
    public static final byte PICK    = 5;
    public static final byte DROP    = 6;
    public static final byte MINE    = 7;
    public static final byte TRAIN   = 8;
    public static final byte MSG     = 9;
    private ActionType() {}
}
```

## Actions — encodage `int` packé

Un `int` représente une action. Disposition des 32 bits :

```
[ type:4 | trollIdx:6 | p1:8 | p2:8 | p3:6 ]
```

- `type` (4 bits) : `ActionType.*`
- `trollIdx` (6 bits) : index local du troll dans la SoA, 0..63 (note : pas l'`originalId` CG ; on map au moment de l'output)
- `p1, p2, p3` : payload variable selon le type :

| Action | p1 | p2 | p3 |
|---|---|---|---|
| `WAIT` | — | — | — |
| `MOVE` | targetTile (0..255) | — | — |
| `HARVEST` | — | — | — |
| `CHOP` | — | — | — |
| `MINE` | direction (0..3 N/E/S/W), optionnel — sinon auto-pick | — | — |
| `DROP` | — | — | — |
| `PICK` | fruitType (0..3) | — | — |
| `PLANT` | fruitType (0..3) | — | — |
| `TRAIN` | moveSpeed (0..15) | carryCap (0..15) | (harvest 4b)\|(chop 4b) en p3 |
| `MSG` | — | — | — (le texte se gère hors `int`) |

Helper statique :

```java
public final class Actions {
    public static int encode(int type, int trollIdx, int p1, int p2, int p3);
    public static int type(int packed);
    public static int trollIdx(int packed);
    public static int p1(int packed);
    public static int p2(int packed);
    public static int p3(int packed);
    public static String format(int packed, GameState state);  // utilise trollOriginalId
    private Actions() {}
}
```

`WAIT` est l'élément neutre (`packed == 0`), pratique pour init.

## Parsing des inputs

### `readInit(Scanner, GameState)`

```
width  = nextInt
height = nextInt
tileCount = width*height
allouer tous les tableaux (terrain, *ByTile, treeX/Y/..., trollX/Y/...)
pour y in 0..height-1 :
    ligne = next()
    pour x in 0..width-1 :
        c = ligne[x]
        terrain[y*width+x] = mapping(c)
        si c == '0' : shackMeTile = tile
        si c == '1' : shackOppTile = tile
préCalculer waterAdj[] : true si une voisine H/V est WATER
```

### `readTurn(Scanner, GameState)`

```
reset treeByTile[], myTrollByTile[], oppTrollByTile[] à -1

pour p in [0,1] :
    inv = (p==0 ? invMe : invOpp)
    inv[PLUM]=nextInt; inv[LEMON]=nextInt; inv[APPLE]=nextInt;
    inv[BANANA]=nextInt; inv[IRON]=nextInt; inv[WOOD]=nextInt;

treeCount = nextInt
pour i in 0..treeCount-1 :
    typeStr = next()
    treeType[i]    = parseType(typeStr)
    treeX[i] = nextInt; treeY[i] = nextInt
    treeSize[i]    = nextInt
    treeHealth[i]  = nextInt
    treeFruits[i]  = nextInt
    treeCooldown[i]= nextInt
    treeByTile[treeY[i]*width + treeX[i]] = i

trollCount = nextInt
pour i in 0..trollCount-1 :
    trollOriginalId[i] = nextInt
    trollPlayer[i]     = nextInt
    trollX[i] = nextInt; trollY[i] = nextInt
    trollMoveSpeed[i]     = nextInt
    trollCarryCapacity[i] = nextInt
    trollHarvestPower[i]  = nextInt
    trollChopPower[i]     = nextInt
    pour r in 0..5 : trollCarry[i*6 + r] = nextInt
    tile = trollY[i]*width + trollX[i]
    si trollPlayer[i]==0 : myTrollByTile[tile] = i
    sinon              : oppTrollByTile[tile] = i

turn++
```

## Simulation — `applyTurn(myActions, oppActions)`

Ordre strict (cf. brief) :
1. **MOVE** — résoudre les déplacements (pathfinding simple : on accepte la destination encodée, si invalide → reste sur place ; raffinement plus tard).
2. **HARVEST** — partage si même arbre.
3. **PLANT** — vérifier graines, conflits.
4. **CHOP** — partage si même arbre, surplus de wood perdu.
5. **PICK** — déduire du shack si dispo.
6. **TRAIN** — si ressources shack suffisantes, créer un troll.
7. **DROP** — vider l'inventaire porté vers le shack.
8. **MINE** — gagner fer adjacent.
9. **Croissance** — décrémenter cooldown, grow/fruit si ==0.

Pour V1 (greedy), on n'a pas besoin du simulateur. Il devient critique en V2 (AG).

## Décisions d'architecture

| Décision | Choix retenu | Raison |
|---|---|---|
| Repr. entités | **SoA tableaux parallèles** primitifs (byte[]/int[]) | Copie en O(N) via `arraycopy`, cache-friendly, zéro alloc dans la simu |
| Repr. carte | `byte[]` flat indexé `y*w+x` | Idem perf ; lookup O(1) |
| Lookups par tile | `short[]` `treeByTile`, `myTrollByTile`, `oppTrollByTile` | O(1) au lieu de scan linéaire |
| Repr. actions | `int` packé (helpers statiques) | Chromosome AG natif, zéro alloc, encode/decode trivial |
| Mutabilité | `GameState` mutable + `copyFrom(src)` | Pool d'instances, pas de `new` dans la boucle AG |
| Constructeur | `public GameState()` no-args, alloc dans `readInit` | Testabilité, séparation parsing/construction (cf. CLAUDE.md) |
| Parsing | méthodes `static readInit/readTurn(Scanner, GameState)` | Découple I/O du modèle, facile à mocker en tests |
| Constantes | classes statiques (`TileType`, `Resource`, `TreeType`, `TreeStats`, `ActionType`) | Pas d'enums (alloc, switch lookup) ; constantes inlinées par le JIT |
| Tailles max | `MAX_TREES=128`, `MAX_TROLLS=32` | Bornes confortables vs. règles du brief |
| ID troll | `trollOriginalId[i]` séparé de l'index local | L'AG manipule des index 0..N-1 stables ; la sortie réutilise l'ID CG |

## Ce qui N'est PAS dans le modèle (et pourquoi)

- **Enum `Action` / sealed interface** : remplacé par `int` packé pour perf et compacité (chromosome AG).
- **Classes `Tree`, `Troll`, `Shack`** : remplacées par les SoA. Aucune indirection objet dans le hot path.
- **`Map<...>` ou `List<...>`** : aucun usage dans le state ; uniquement dans le code de log/debug si besoin.
- **Score persistant** : recalculé à la demande depuis `invMe`/`invOpp` (`score(player)`).
- **Historique des tours** : pas stocké ; seul le best plan AG du tour précédent (côté agent, pas dans le state).
- **Distance maps BFS pré-calculées** : calculées par les agents (greedy, AG) à l'init, pas par `GameState` qui reste pur modèle.

## Conséquences sur la structure de fichiers

```
src/main/java/com/bmrt/cgspring2026/
  Player.java
  model/
    GameState.java
    TileType.java
    Resource.java
    TreeType.java
    TreeStats.java
  action/
    ActionType.java
    Actions.java
  iaengine/
    (à venir : GreedyAgent.java, GeneticAgent.java)
```

Chaque classe sera importée depuis `Player.java` (directement ou transitivement) pour que `FileBuilder` puisse les fusionner.
