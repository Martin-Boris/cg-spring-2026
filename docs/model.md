# Modèle — Troll Farm (CG Spring 2026)

## Vue d'ensemble

Modèle conçu pour la **simulation massive** (algorithme génétique). Objectifs :

- **Copie d'état en quelques µs** via `System.arraycopy` sur des tableaux primitifs.
- **Zéro allocation pendant la simu** : tous les buffers sont pré-alloués à taille fixe (`MAX_TREES`, `MAX_TROLLS`).
- **Empreinte mémoire compacte** : `byte[]` partout où les valeurs tiennent sur 7-8 bits → état dynamique ≈ 2-3 KB par
  `GameState` → cache-friendly, pool de centaines d'états sans pression GC.
- **Carte en static** : la grille est figée pour toute la partie, donc partagée par tous les `GameState` et jamais
  copiée.

```
                    ┌──────────────────────────────────────────┐
                    │  GameState (static : carte + dimensions) │
                    │  - tiles[]   width × height              │
                    │  - shackMe / shackOpp positions          │
                    └──────────────────────────────────────────┘
                                       │
                                       │ partagé par toutes les instances
                                       ▼
                    ┌──────────────────────────────────────────┐
                    │  GameState (instance : état dynamique)   │
                    │                                          │
                    │  shackInventory[12]   int[]              │
                    │                                          │
                    │  trees (SoA)          byte[MAX_TREES]    │
                    │    type/x/y/size/health/fruits/cooldown  │
                    │                                          │
                    │  trolls (SoA)         byte[MAX_TROLLS]   │
                    │    id/player/x/y/ms/cc/hp/cp             │
                    │    inventory[MAX_TROLLS × 6]             │
                    └──────────────────────────────────────────┘

         Action (encodée sur 1 int)   ─────►   GameState.apply(int action)
```

## Classes

### GameState

Représente l'état complet du jeu à un instant T. **Structure of Arrays (SoA)** : tableaux primitifs parallèles plutôt
que `Tree[]`/`Troll[]`. Permet `System.arraycopy` sur chaque tableau lors de la copie.

**Champs statiques (carte, immuable pour la partie)** :

| Champ        | Type   | Rôle                                                            |
|--------------|--------|-----------------------------------------------------------------|
| `width`      | int    | Largeur grille (16-22)                                          |
| `height`     | int    | Hauteur grille (8-11)                                           |
| `tiles`      | byte[] | `width*height` cases. Constantes `TileType.GRASS`, `WATER`, ... |
| `shackMeX/Y` | int    | Position du shack allié                                         |
| `shackOppX/Y`| int    | Position du shack adverse                                       |

Accès : `tiles[y * width + x]`.

**Champs d'instance (état dynamique)** :

| Champ              | Type                    | Rôle                                                 |
|--------------------|-------------------------|------------------------------------------------------|
| `turn`             | int                     | Tour courant (0-299)                                 |
| `shackInventory`   | int[12]                 | `[player*6 + resource]` — peut grossir → int         |
| `treeCount`        | int                     | Nombre d'arbres actifs                               |
| `treeType[]`       | byte[MAX_TREES]         | `TreeType.PLUM`, ...                                 |
| `treeX[]`          | byte[MAX_TREES]         | 0-21                                                 |
| `treeY[]`          | byte[MAX_TREES]         | 0-10                                                 |
| `treeSize[]`       | byte[MAX_TREES]         | 1-4                                                  |
| `treeHealth[]`     | byte[MAX_TREES]         | 0-20                                                 |
| `treeFruits[]`     | byte[MAX_TREES]         | 0-3                                                  |
| `treeCooldown[]`   | byte[MAX_TREES]         | 0-9                                                  |
| `trollCount`       | int                     | Nombre de trolls (les deux joueurs)                  |
| `trollId[]`        | byte[MAX_TROLLS]        | id fourni par CG (clé externe → index interne)       |
| `trollPlayer[]`    | byte[MAX_TROLLS]        | 0 ou 1                                               |
| `trollX[]`         | byte[MAX_TROLLS]        |                                                      |
| `trollY[]`         | byte[MAX_TROLLS]        |                                                      |
| `trollMS[]`        | byte[MAX_TROLLS]        | movementSpeed                                        |
| `trollCC[]`        | byte[MAX_TROLLS]        | carryCapacity                                        |
| `trollHP[]`        | byte[MAX_TROLLS]        | harvestPower                                         |
| `trollCP[]`        | byte[MAX_TROLLS]        | chopPower                                            |
| `trollInventory[]` | byte[MAX_TROLLS × 6]    | `[trollIdx*6 + resource]`                            |

**Constantes** :

| Constante    | Valeur | Justification                                                            |
|--------------|--------|--------------------------------------------------------------------------|
| `MAX_TREES`  | 128    | Grille max 242 cases - obstacles ; on n'atteindra jamais 128 arbres.     |
| `MAX_TROLLS` | 32     | Coût TRAIN quadratique → spawn rarement >10 par joueur.                  |

**Constructeur** : `public GameState()` — sans paramètres, alloue tous les tableaux à taille fixe. Aucun parsing dedans.

**Méthodes clés** :

```java
public static void readInit(Scanner in);   // remplit les champs static (carte)
public void readTurn(Scanner in);          // remplit l'état dynamique (1 tour)
public void copyFrom(GameState src);       // System.arraycopy partout — la primitive de simulation
public void apply(int action);             // applique une action encodée → mute this
public int score(int player);              // dérivé : Σ shackInventory + 4 × wood
public int trollIndexById(int id);         // résout id CG → index interne (linéaire, trollCount ≤ ~10)
public int treeIndexAt(int x, int y);      // arbre sur (x,y) ou -1
```

**Indexation par index interne, pas par id** : on travaille avec l'indice de tableau (`trollIdx ∈ [0, trollCount[`),
pas avec le `id` fourni par CG. La conversion id ↔ index se fait en lecture/écriture (parsing input et formatage
output). Cela garantit que toutes les boucles de simu sont des `for (int i = 0; i < trollCount; i++)`.

### TileType (constantes byte)

```java
public static final byte GRASS     = 0;
public static final byte WATER     = 1;
public static final byte ROCK      = 2;
public static final byte IRON      = 3;
public static final byte SHACK_ME  = 4;
public static final byte SHACK_OPP = 5;
```

Pas d'enum : on veut indexer en `byte` sans `ordinal()`.

### ResourceType (constantes byte)

```java
public static final byte PLUM   = 0;
public static final byte LEMON  = 1;
public static final byte APPLE  = 2;
public static final byte BANANA = 3;
public static final byte IRON   = 4;
public static final byte WOOD   = 5;
public static final int  COUNT  = 6;
```

Ordre = ordre de l'input CG. Permet d'utiliser ces indices directement pour `shackInventory` et `trollInventory`.

### TreeType (constantes byte)

```java
public static final byte PLUM   = 0;
public static final byte LEMON  = 1;
public static final byte APPLE  = 2;
public static final byte BANANA = 3;
public static final int  COUNT  = 4;
```

Tables associées (cooldowns, health par taille) en tableaux indexés par type :

```java
public static final byte[] COOLDOWN_NORMAL = { 8, 8, 9, 6 };
public static final byte[] COOLDOWN_WATER  = { 3, 3, 2, 4 };
public static final byte[][] HEALTH_BY_SIZE = {
    { 6, 8, 10, 12 },   // PLUM
    { 6, 8, 10, 12 },   // LEMON
    {11,14, 17, 20 },   // APPLE
    { 3, 4,  5,  6 },   // BANANA
};
```

## Actions

Une action est encodée sur **un seul `int` (32 bits)**. Pas d'allocation, copie/mutation triviales pour le GA.

### Encodage standard (MOVE, HARVEST, PLANT, CHOP, PICK, DROP, MINE, WAIT)

```
 31      24 23      16 15       8 7        0
┌──────────┬──────────┬──────────┬──────────┐
│   arg2   │   arg1   │ trollIdx │   type   │
└──────────┴──────────┴──────────┴──────────┘
   8 bits     8 bits     8 bits     8 bits
```

| Type    | trollIdx | arg1            | arg2          |
|---------|----------|-----------------|---------------|
| MOVE    | idx      | x               | y             |
| HARVEST | idx      | -               | -             |
| PLANT   | idx      | TreeType        | -             |
| CHOP    | idx      | -               | -             |
| PICK    | idx      | ResourceType    | -             |
| DROP    | idx      | -               | -             |
| MINE    | idx      | -               | -             |
| WAIT    | idx      | -               | -             |

### Encodage TRAIN (action sans troll, 4 stats à encoder)

```
 31    26 25    20 19    14 13     8 7        0
┌────────┬────────┬────────┬────────┬──────────┐
│   cp   │   hp   │   cc   │   ms   │ type=TRA │
└────────┴────────┴────────┴────────┴──────────┘
  6 bits   6 bits   6 bits   6 bits     8 bits
```

Stats sur 6 bits → max 63 par stat, largement suffisant (coût quadratique).

### ActionType (constantes byte)

```java
public static final byte WAIT    = 0;
public static final byte MOVE    = 1;
public static final byte HARVEST = 2;
public static final byte PLANT   = 3;
public static final byte CHOP    = 4;
public static final byte PICK    = 5;
public static final byte DROP    = 6;
public static final byte MINE    = 7;
public static final byte TRAIN   = 8;
```

### Helpers Action

```java
// Construction (un int par action)
public static int wait(int trollIdx);
public static int move(int trollIdx, int x, int y);
public static int harvest(int trollIdx);
public static int plant(int trollIdx, int treeType);
public static int chop(int trollIdx);
public static int pick(int trollIdx, int resourceType);
public static int drop(int trollIdx);
public static int mine(int trollIdx);
public static int train(int ms, int cc, int hp, int cp);

// Décodage
public static int type(int action);
public static int trollIdx(int action);
public static int arg1(int action);
public static int arg2(int action);
public static int trainMS(int action);
public static int trainCC(int action);
public static int trainHP(int action);
public static int trainCP(int action);

// Formatage output (id externe CG)
public static String toCommand(int action, GameState s);
```

### Génome GA

Un génome = `int[]` de longueur `HORIZON × (MAX_TROLLS + 1)` : pour chaque tour de l'horizon, une action par troll +
un slot TRAIN. Mutation = bit-flip ou réécriture aléatoire d'un slot. Croisement = swap de slices.

## Parsing des inputs

| Phase | Input CG                                                                          | Code                                                              |
|-------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------|
| Init  | `width height` puis `height` lignes                                               | `GameState.readInit(Scanner)` remplit `tiles`, `shack*`           |
| Tour  | 2 lignes inventaire (× 6)                                                         | `shackInventory[0..5]` puis `shackInventory[6..11]`               |
| Tour  | `treeCount` puis `type x y size health fruits cooldown`                           | Boucle remplit les 7 tableaux `tree*` ; `treeType` = TreeType.PLUM, ... |
| Tour  | `trollsCount` puis `id player x y ms cc hp cp ×6 carry`                           | Boucle remplit les 8 stats + `trollInventory[i*6..i*6+5]`         |

Correspondance grille → `tiles` :

```
'.' → GRASS        '~' → WATER        '#' → ROCK
'+' → IRON         '0' → SHACK_ME     '1' → SHACK_OPP
```

`shackMeX/Y` et `shackOppX/Y` sont extraits pendant le parsing init (scan unique des `'0'` et `'1'`).

## Décisions d'architecture

| Décision                                      | Choix retenu                              | Raison                                                                                                                          |
|-----------------------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| SoA vs AoS                                    | SoA (tableaux parallèles)                 | `System.arraycopy` est un intrinsic JIT, ultra-rapide ; pas de pointeurs ; contigu en mémoire = cache-friendly.                 |
| Allocation des entités                        | Pool fixe (MAX_*)                         | Zéro allocation pendant simu → pas de GC ; copies prédictibles ; pas de `ArrayList.add`.                                        |
| Types primitifs                               | `byte` partout, `int` pour shackInventory | Valeurs ≤ 127 partout sauf inventaire shack qui peut grossir. Compacité × 4 vs int.                                             |
| Carte                                         | Champs static dans `GameState`            | Carte figée pour la partie → 1 seul exemplaire partagé, jamais copié. Accès direct sans indirection.                            |
| Action                                        | `int` encodé                              | Génome GA = `int[]` trivial à muter / croiser ; pas d'allocation par action.                                                    |
| Indexation troll                              | par index interne, pas par id CG          | Permet des boucles compactes `for i in [0, trollCount[`. Conversion id ↔ index uniquement en I/O.                               |
| Constantes (TileType, ResourceType, ...)      | classes avec `static final byte`          | Pas d'enum : accès direct par valeur byte, pas d'`ordinal()`, pas de tableau intermédiaire.                                     |
| `copyFrom(other)` vs `clone()`                | `copyFrom`                                | Évite l'allocation : on prend un état du pool et on y copie. Conforme au patron "object pool" du GA.                            |
| Copie taille fixe vs `trollCount`             | Taille fixe (MAX_*)                       | `arraycopy(128)` vs `arraycopy(8)` est imperceptible ; éliminer la branche est mieux. À reconsidérer si profiling le démontre. |

## Ce qui N'EST PAS dans le modèle (et pourquoi)

- **Score** : pas stocké, dérivé à la demande via `score(player)` (formule connue : `Σ fruits + 4 × wood`).
- **MSG** : output-only, n'appartient pas à l'état du jeu.
- **Pathfinding pré-calculé** : sera ajouté plus tard (matrice de distances W×H × W×H au tour 1) dans une classe à
  part. Pas dans `GameState` car statique.
- **Cooldown initial à la plantation** : valeur à découvrir en jeu (cf. brief — question en suspens). Le modèle stocke
  juste la valeur reçue.
- **Attributs du troll initial** : idem, le modèle ne suppose rien, il lit ce que CG envoie.
- **MSG, ordres de résolution** : la logique de simulation `apply()` les implémente mais ne sont pas des champs.
- **Historique** : un seul état présent ; le GA travaille sur des copies, pas une chaîne d'états.

## Structure de fichiers générée

```
src/main/java/com/bmrt/cgspring2026/
  Player.java                  (existant)
  model/
    GameState.java
    TileType.java
    ResourceType.java
    TreeType.java
  action/
    Action.java
    ActionType.java
  builder/
    FileBuilder.java           (existant)
```
