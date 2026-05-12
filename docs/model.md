# Modèle — Troll Farm (CG Spring 2026)

## Vue d'ensemble

```
GameState
├── grid        : byte[]              — types de tuiles aplatis (height × width)
├── width, height                     — dimensions de la carte
├── myShackX/Y, oppShackX/Y           — positions des shacks (extraites au readInit)
├── myShackInv  : int[6]              — inventaire shack joueur (index = ResourceType.ordinal)
├── oppShackInv : int[6]              — inventaire shack adversaire
├── trees       : ArrayList<Tree>     — taille variable au cours de la partie
├── trolls      : ArrayList<Troll>    — taille variable (TRAIN ajoute des trolls)
└── turn        : int                 — compteur de tours (incrémenté par readTurn)

Tile        (enum) : GRASS, WATER, ROCK, IRON, SHACK_ME, SHACK_OPP
ResourceType(enum) : PLUM, LEMON, APPLE, BANANA, IRON, WOOD          [indices 0..5]
TreeType    (enum) : PLUM, LEMON, APPLE, BANANA                       [indices 0..3]

Action (sealed interface) implémentée par les records :
  Move, Harvest, Plant, Chop, Pick, Drop, Mine, Train, Wait, Msg
```

## Classes

### GameState
État complet du jeu à un instant T. Conçu pour être copié rapidement (simulation).

Champs :
- `int width, height`
- `byte[] grid` — tile.ordinal() par case, indexé par `y * width + x`
- `int myShackX, myShackY, oppShackX, oppShackY`
- `int[] myShackInv = new int[6]`
- `int[] oppShackInv = new int[6]`
- `List<Tree> trees = new ArrayList<>()`
- `List<Troll> trolls = new ArrayList<>()`
- `int turn`

Constructeur : `public GameState()` — sans arguments, tous les champs initialisés par affectation.

Parsing (méthodes statiques séparées) :
- `static void readInit(Scanner in, GameState state)` — lit width, height, et la grille. Renseigne `width/height/grid/myShackX/Y/oppShackX/Y`.
- `static void readTurn(Scanner in, GameState state)` — lit les inventaires, les arbres, les trolls. Vide et remplit `trees` et `trolls`. Incrémente `turn`.

Méthodes clés :
- `Tile tileAt(int x, int y)` — accès typé à `grid`
- `boolean walkable(int x, int y)` — true ssi GRASS
- `GameState copy()` — copie profonde (clone tableaux primitifs, copy trees/trolls)
- `Troll trollById(int id)` — recherche linéaire (peu d'unités)

### Tile (enum)
Valeurs : `GRASS, WATER, ROCK, IRON, SHACK_ME, SHACK_OPP`.

Méthode utilitaire `static Tile from(char c)` :
- `'.'` → GRASS
- `'~'` → WATER
- `'#'` → ROCK
- `'+'` → IRON
- `'0'` → SHACK_ME
- `'1'` → SHACK_OPP

`isWalkable()` : `this == GRASS`.

### ResourceType (enum)
Valeurs ordonnées (et index 0..5) : `PLUM, LEMON, APPLE, BANANA, IRON, WOOD`.

Cet ordre **correspond exactement** à l'ordre de lecture des inventaires dans l'input
(`plums lemons apples bananas iron wood`) et à l'ordre du carry inventory des trolls
(`carryPlum carryLemon carryApple carryBanana carryIron carryWood`).

Méthode `int scorePerUnit()` : `1` pour PLUM/LEMON/APPLE/BANANA, `0` pour IRON, `4` pour WOOD.

### TreeType (enum)
Valeurs : `PLUM, LEMON, APPLE, BANANA`. `static TreeType parse(String s)` pour le parsing.
Méthode `int normalCooldown()` / `int waterCooldown()` / `int healthForSize(int size)`.
Méthode `ResourceType fruit()` (mapping vers la ressource récoltée).

### Tree
Champs publics : `TreeType type; int x, y, size, health, fruits, cooldown;`
Constructeur sans args. `Tree copy()` retourne une copie indépendante.

### Troll
Champs publics :
- `int id, player, x, y`
- `int movementSpeed, carryCapacity, harvestPower, chopPower`
- `int[] carry = new int[6]` — indexé par `ResourceType.ordinal()`

Constructeur sans args. `Troll copy()` retourne une copie indépendante.
Méthode `int carryTotal()` (somme des 6 compteurs).

## Actions

Une `sealed interface Action` avec un seul contrat : `String toCommand()`.

Records implémentant `Action` :
| Action     | Champs                                                                 | Sortie texte                                  |
|------------|------------------------------------------------------------------------|-----------------------------------------------|
| `Move`     | `int trollId, int x, int y`                                            | `MOVE id x y`                                 |
| `Harvest`  | `int trollId`                                                          | `HARVEST id`                                  |
| `Plant`    | `int trollId, TreeType type`                                           | `PLANT id TYPE`                               |
| `Chop`     | `int trollId`                                                          | `CHOP id`                                     |
| `Pick`     | `int trollId, ResourceType type`                                       | `PICK id TYPE`                                |
| `Drop`     | `int trollId`                                                          | `DROP id`                                     |
| `Mine`     | `int trollId`                                                          | `MINE id`                                     |
| `Train`    | `int moveSpeed, int carryCapacity, int harvestPower, int chopPower`    | `TRAIN ms cc hp cp`                           |
| `Wait`     | `int trollId`                                                          | `WAIT id` (selon la convention CG validée)    |
| `Msg`      | `String text`                                                          | `MSG text`                                    |

Un `Turn` regroupe plusieurs `Action` séparées par `;` à la sérialisation finale (responsabilité de la couche IA).

## Parsing des inputs

### `readInit` (1 fois)
```
width height
height lignes de width caractères → byte[] grid + détection des '0' / '1' pour shacks
```

### `readTurn` (chaque tour)
```
ligne 1 : 6 int  → myShackInv[0..5]   (PLUM..WOOD)
ligne 2 : 6 int  → oppShackInv[0..5]
ligne 3 : int treeCount
treeCount lignes : type x y size health fruits cooldown → Tree
ligne suivante : int trollsCount
trollsCount lignes : id player x y ms cc hp cp + 6 carry counters → Troll
```

`readTurn` **vide** `trees` et `trolls` avant remplissage (les listes sont rebâties à chaque tour).
Le `turn` interne est incrémenté en début d'appel.

## Décisions d'architecture

| Décision                              | Choix retenu                                         | Raison                                                                                          |
|---------------------------------------|------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Représentation de la grille           | `byte[]` aplati (tile.ordinal())                     | Clone O(N) très rapide pour `copy()` ; carte ≤ 242 cases                                        |
| Listes d'arbres / trolls              | `ArrayList<Tree>` / `ArrayList<Troll>`               | Taille variable au cours du jeu (TRAIN, CHOP destruction), volumétrie faible (≤ qq dizaines)    |
| Inventaires (shack & carry)           | `int[6]` indexé par `ResourceType.ordinal()`         | Mêmes indices que l'ordre de lecture → parsing en boucle, addition/copie triviales              |
| Position des shacks                   | Champs `int` dans `GameState`, extraits au readInit  | Pas besoin d'objet Shack dédié — position + inventaire suffisent                                |
| `Tile` / `ResourceType` / `TreeType`  | enums                                                | Lisibilité ; conversion via `from(char)` / `parse(String)` ; ordinal stable pour indexation     |
| Action                                | sealed interface + records                           | Type-safe, immuable, lisible ; toCommand() local à chaque variante ; supporte switch exhaustif  |
| Constructeurs sans args               | `GameState()`, `Tree()`, `Troll()`                   | Testabilité (instanciation directe sans Scanner) ; parsing en méthode statique                  |
| `readInit` / `readTurn`               | static, séparés du constructeur                      | Permet d'avoir un GameState créé "à vide" pour les tests / la simulation                        |
| Mutabilité de Tree/Troll              | mutable (champs publics)                             | Permet à la simulation de muter en place après `copy()` sans realloc                            |
| Copie de GameState                    | `copy()` qui clone primitifs et copy entités         | Sera utilisée par la couche IA pour explorer plusieurs branches                                 |

## Ce qui N'est PAS dans le modèle (et pourquoi)

- **Score courant des joueurs** : pas dans l'input. Sera calculé/tracké par la couche IA si nécessaire (somme des DROP × score/unité). Pas une donnée du `GameState` brut.
- **Distance map / pathfinding pré-calculé** : c'est un cache/optim de la couche IA, pas une donnée d'état. Hors scope du modèle.
- **Pré-indexation des arbres par (x,y)** : trop peu nombreux pour justifier une map ; si le besoin émerge en simulation, ce sera une optim ciblée.
- **Cases adjacentes au shack** (cache des cases GRASS voisines pour DROP/PICK) : optim IA, pas modèle.
- **Historique des actions** : pas nécessaire pour décider le tour courant ; la simulation manipule des copies.
- **Coût TRAIN pré-calculé** : facilement dérivable `n + v²` ; helper à mettre dans une couche stratégie, pas dans le modèle.
- **Classe `Shack` dédiée** : 4 ints suffisent (positions + inventaires). Pas de comportement à encapsuler.
