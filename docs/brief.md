# Brief — Troll Farm (CG Spring 2026, Wood League / Bronze)

## Contexte & objectif

Jeu CodinGame opposant deux joueurs sur une grille rectangulaire. Chaque joueur contrôle une **meute de trolls** (1 au
départ) ainsi qu'un **shack** (cabane).

Objectif : marquer plus de points que l'adversaire en **300 tours** en déposant des ressources dans son shack :

### Barème de scoring (par unité déposée)

| Ressource | Points / unité | Rôle stratégique                                             |
|-----------|----------------|--------------------------------------------------------------|
| PLUM      | **1**          | Score + TRAIN movementSpeed                                  |
| LEMON     | **1**          | Score + TRAIN carryCapacity                                  |
| APPLE     | **1**          | Score + TRAIN harvestPower                                   |
| BANANA    | **1**          | Score (pas d'usage TRAIN)                                    |
| **WOOD**  | **4**          | **Score uniquement — 4× plus rentable au point qu'un fruit** |
| IRON      | **0**          | **Aucun point** — sert seulement à TRAIN chopPower           |

**Implication directe** : 1 wood = 4 fruits côté score. Couper un arbre adulte (size 4) = **16 points** en une action.
Le WOOD est la ressource de score la plus dense ; IRON ne sert que de matière première pour TRAIN.

Les ressources servent à :

- **Entraîner** de nouveaux trolls (4 attributs paramétrables une fois pour toutes).
- **Planter** de nouveaux arbres pour générer plus de fruits.

## Déroulement d'un tour

### Lecture des inputs (chaque tour)

1. Inventaire du joueur : `plums lemons apples bananas iron wood`
2. Inventaire adversaire : `plums lemons apples bananas iron wood`
3. `treeCount` puis `treeCount` lignes d'arbres.
4. `trollsCount` puis `trollsCount` lignes de trolls (les nôtres + ceux de l'adversaire).

### Production des outputs

Une seule ligne contenant **N commandes séparées par `;`**. Une commande par troll possédé. **TRAIN est une commande
SUPPLÉMENTAIRE** : elle s'ajoute en plus des actions de troll, sans consommer le tour d'un troll existant. On peut donc
émettre `MOVE 0 5 5; CHOP 1; TRAIN 1 1 1 0` dans le même tour. `MSG` est également indépendant des trolls.

### Ordre de résolution interne de l'arbitre (un même tour)

1. **MOVE** des trolls
2. **HARVEST**
3. **PLANT**
4. **CHOP**
5. **PICK**
6. **TRAIN**
7. **DROP**
8. **MINE**
9. **Croissance** des arbres (décrément des cooldowns, croissance/fructification à 0)

Les actions du même type s'exécutent **simultanément**. En cas de partage d'arbre (HARVEST/CHOP simultanés), les
ressources sont distribuées **un par un** entre les deux trolls jusqu'à épuisement ; **le dernier objet peut être
dupliqué**.

## Inputs

### Initialisation (une seule fois)

| Variable               | Type   | Description                                                                                   |
|------------------------|--------|-----------------------------------------------------------------------------------------------|
| `width`                | int    | Largeur de la grille (`= 2 * height`)                                                         |
| `height`               | int    | Hauteur (8 ≤ height ≤ 11), donc 16 ≤ width ≤ 22                                               |
| Grille (height lignes) | String | width caractères : `.` GRASS, `~` WATER, `#` ROCK, `+` IRON, `0` mon SHACK, `1` shack adverse |

### Par tour

| Variable                                                                                                                           | Type           | Description                                              |
|------------------------------------------------------------------------------------------------------------------------------------|----------------|----------------------------------------------------------|
| `plums lemons apples bananas iron wood` (×2 lignes)                                                                                | 6 int          | Inventaire moi puis adversaire                           |
| `treeCount`                                                                                                                        | int            | Nombre d'arbres présents                                 |
| `type x y size health fruits cooldown`                                                                                             | String + 6 int | Pour chaque arbre. `type` ∈ {PLUM, LEMON, APPLE, BANANA} |
| `trollsCount`                                                                                                                      | int            | Nombre de trolls (les deux joueurs)                      |
| `id player x y movementSpeed carryCapacity harvestPower chopPower carryPlum carryLemon carryApple carryBanana carryIron carryWood` | 14 valeurs     | Pour chaque troll. `player` = 0 (moi) / 1 (adversaire)   |

## Actions / Outputs

Une commande par ligne de sortie, multiples commandes séparées par `;`.

| Commande                                               | Effet                                                                                                                                                                                                         |
|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MOVE id x y`                                          | Déplace le troll `id` vers (x,y). Pathfinding géré par l'arbitre : il **contourne** les obstacles (WATER, ROCK, IRON, SHACK, autre troll allié). Si la cible est hors de portée / non praticable, va vers la case GRASS accessible la plus proche en direction de la cible. Distance parcourue ≤ `movementSpeed`. |
| `HARVEST id`                                           | Récolte sur l'arbre situé sur la case du troll. Quantité = min(harvestPower, fruits, carryCapacity libre).                                                                                                    |
| `PLANT id type`                                        | Plante un arbre `type` sur la case du troll. Coûte 1 fruit du type indiqué. Doit être sur GRASS sans arbre.                                                                                                   |
| `CHOP id`                                              | Coupe l'arbre sur la case. Inflige `chopPower` dégâts. À 0 PV, l'arbre est détruit et le troll récupère `WOOD = size` (cap à carryCapacity libre, surplus perdu).                                             |
| `PICK id type`                                         | Prend 1 objet (fruit) du type indiqué dans le shack. Le troll doit être sur une **case GRASS adjacente (H/V)** au shack — on ne peut pas se tenir sur le shack lui-même.                                      |
| `DROP id`                                              | Le troll **sur une case adjacente (H/V)** à son shack y dépose **toutes** ses ressources. Le shack étant un obstacle, on dépose depuis la case voisine.                                                       |
| `MINE id`                                              | Récolte du fer si une **case IRON est adjacente (H/V)** au troll. Le troll reste sur sa case GRASS (la case IRON est un obstacle infranchissable). Gain = min(chopPower, carryCapacity libre). Ressource infinie. |
| `TRAIN moveSpeed carryCapacity harvestPower chopPower` | Crée un nouveau troll au shack avec ces attributs (immuables). Coût décrit ci-dessous. **Action supplémentaire : ne consomme pas le tour d'un troll existant**, peut être combinée avec une action par troll. |
| `WAIT`                                                 | Ne rien faire.                                                                                                                                                                                                |
| `MSG text`                                             | Affiche un message dans le replay.                                                                                                                                                                            |

### Coûts de TRAIN

Soit `n` = nombre de trolls actuels du joueur. Pour chaque attribut `a` de valeur `v` :

- Coût = `n + v²` ressources du type associé.
- Type de ressource :
    - `movementSpeed` → PLUM
    - `carryCapacity` → LEMON
    - `harvestPower` → APPLE
    - `chopPower` → IRON

Exemple (n=2) : TRAIN 2 3 1 0 coûte 6 PLUM, 11 LEMON, 3 APPLE, 2 IRON.

## Règles & contraintes

### Carte

- `width = 2 * height`, `8 ≤ height ≤ 11`. Grille de 128 à 242 cases.
- Cases : GRASS (`.`), WATER (`~`), ROCK (`#`), IRON (`+`), SHACK (`0`/`1`).
- **Seules les cases GRASS sont praticables** par les trolls. WATER, ROCK, IRON et SHACK sont des **obstacles** qu'il
  faut **contourner** via le pathfinding (un troll ne peut ni traverser ni s'arrêter sur ces cases).
- **Adjacence = case adjacente en 4-connexité (H/V)** : nord, sud, est, ouest. Pas de diagonale. Deux cases sont
  adjacentes si `|dx| + |dy| = 1`. Les interactions « adjacentes » (DROP / PICK / TRAIN au shack, MINE sur IRON)
  s'effectuent **depuis une case GRASS** voisine immédiate de l'obstacle ciblé, **sans jamais marcher dessus**.
- Une case peut contenir **au max 1 troll par équipe** (les deux trolls adverses peuvent cohabiter sur la même case).
- **Swap pendant MOVE** : deux trolls (alliés comme adverses) peuvent **échanger leurs positions** dans le même tour
  (croisement autorisé). La seule contrainte est qu'**à la fin du tour, chaque troll occupe sa propre case** (pas de
  collision finale entre trolls d'une même équipe sur la même case GRASS).

### Arbres

| Type   | Cooldown normal | Cooldown près de l'eau | Health (taille 1→4) |
|--------|-----------------|------------------------|---------------------|
| PLUM   | 8               | 3                      | 6 / 8 / 10 / 12     |
| LEMON  | 8               | 3                      | 6 / 8 / 10 / 12     |
| APPLE  | 9               | 2                      | 11 / 14 / 17 / 20   |
| BANANA | 6               | 4                      | 3 / 4 / 5 / 6       |

- Taille max = **4**. Quand le cooldown atteint 0, l'arbre **grandit** (size++), ou **produit 1 fruit** s'il est à
  taille 4.
- Max **3 fruits** par arbre.
- Adjacent à WATER (H/V) → cooldown réduit.
- Croissance après un dégât : la health remontée correspond à la **différence** entre les deux paliers de taille.

### Trolls

- 4 attributs définis à la création, **non modifiables** : `movementSpeed`, `carryCapacity`, `harvestPower`,
  `chopPower`.
- Carry inventory par type : PLUM, LEMON, APPLE, BANANA, IRON, WOOD.

### Temps

- Tour 1 : ≤ **1000 ms**
- Tours suivants : ≤ **50 ms**
- Tolérance : on perd si on dépasse de plus de 50 ms 3 fois, ou si une fois de plus.

### Fin de partie

- 300 tours écoulés.
- Un joueur peut imposer la victoire en ne faisant rien (mort programmée de l'adversaire ?).
- **10 tours consécutifs sans aucun arbre** sur la carte.

## Entités clés

### Tile (case de la grille)

- Coordonnées `(x, y)` (0-indexées, y du haut vers le bas).
- Type ∈ {GRASS, WATER, ROCK, IRON, SHACK_ME, SHACK_OPP}.
- Adjacence : **4-connexité (H/V) — case voisine en haut/bas/gauche/droite, jamais diagonale**.
- Praticable (le troll peut s'y déplacer ou s'y arrêter) : **GRASS uniquement**.
- Obstacles à contourner : WATER, ROCK, IRON, SHACK_ME, SHACK_OPP. Le pathfinding doit les éviter.

### Tree (arbre)

- `type`, `x`, `y`, `size` ∈ [1..4], `health`, `fruits` ∈ [0..3], `cooldown`.
- Plant sur GRASS uniquement.

### Troll

- `id`, `player` (0 moi / 1 adv), `(x,y)`.
- Stats : `movementSpeed`, `carryCapacity`, `harvestPower`, `chopPower`.
- Inventaire porté : 6 compteurs (plum, lemon, apple, banana, iron, wood). La somme ≤ carryCapacity.

### Shack

- 1 par joueur, position fixe. Position fournie dans la carte initiale (`0` / `1`).
- **Le shack est un obstacle infranchissable** : aucun troll ne peut marcher dessus ou s'y arrêter (ni le sien ni
  l'adverse). Le pathfinding doit le contourner.
- Sert de stockage (inventaire global du joueur) et de point de spawn pour les nouveaux trolls (les nouveaux trolls
  apparaissent sur une case GRASS adjacente au shack, le shack lui-même restant non praticable).
- Interactions (DROP/PICK) nécessitent que le troll soit sur une **case GRASS adjacente (H/V)** au shack. TRAIN ne
  requiert pas d'adjacence : il fait spawn directement le nouveau troll auprès du shack.

## Conditions de victoire / défaite

### Victoire

- Score final strictement supérieur à celui de l'adversaire.
- **Formule de score** : `score = plums_déposés + lemons_déposés + apples_déposés + bananas_déposés + 4 × wood_déposé`.
  IRON = 0 point (utile uniquement pour TRAIN chopPower).

### Défaite

- Score inférieur à l'adversaire.
- Timeout (cf. règle de tolérance).
- Commande non reconnue / invalide.

## Pièges & cas limites identifiés

- **Carry overflow lors d'un CHOP** : si `wood produit > carryCapacity libre`, le **surplus est perdu** (≠ DROP
  forcé). → couper en ayant déposé d'abord.
- **PLANT coopératif** : 2 trolls plantant le **même type** sur la même case → les deux perdent une graine, **un seul
  arbre est planté**. Types différents → rien ne se passe (graines conservées ?). À vérifier.
- **Dernier objet dupliqué** : en HARVEST/CHOP partagé, **les deux trolls obtiennent** la dernière ressource →
  exploitable pour gagner 1 fruit/wood "gratuit".
- **PICK = 1 seul objet** par tour : la phase de chargement depuis le shack est lente.
- **DROP vide tout** : pas de drop sélectif. Penser à PICK ensuite si on veut replanter.
- **MOVE imprécis** : pathfinding boîte noire → ne pas supposer une trajectoire. Vérifier qu'on arrive bien à
  destination (chemin libre, distance ≤ movementSpeed).
- **Une case = 1 troll par équipe (en fin de tour)** : risque de collision finale entre trolls alliés. Ne pas planifier
  deux trolls sur la même destination en fin de tour. En revanche, **le croisement (swap) est autorisé pendant la phase
  MOVE** : deux trolls peuvent échanger leur case dans le même tour tant que chacun finit sur sa propre case.
- **TRAIN coût croissant** : `n + v²`. Doubler les trolls double presque le coût de base, et les hauts stats sont
  quadratiquement chers. Privilégier des trolls avec quelques attributs forts plutôt que beaucoup d'unités max-stat.
- **Cooldown près de l'eau** : APPLE passe de 9 à 2 (×4.5 plus rapide). Eau prioritaire pour le placement de PLANT.
- **BANANA** : cooldown 6 normal mais 4 près de l'eau (faible gain). Health très basse → coupe rapide (intéressant pour
  WOOD farm avec faible chopPower).
- **WOOD = size de l'arbre coupé** → plus rentable de couper un arbre adulte (size 4 = 4 wood × 4 pts = **16 pts** en un
  CHOP). Comparé à 1 HARVEST de 3 fruits = 3 pts, le WOOD est massivement plus dense en score.
- **IRON = 0 point** : ne miner que pour préparer un TRAIN avec chopPower > 0. Tout IRON déposé "au cas où" gèle des
  slots d'inventaire shack sans bénéfice direct.
- **Arbitrage fruit vs wood** : un fruit récupéré coûte 1 HARVEST (souvent rapide, en série sur un arbre mature) ; un
  WOOD coûte CHOP × (health/chopPower) tours mais détruit la source. **Toujours équilibrer wood farming et plantation
  pour ne pas tarir la carte** (rappel : 10 tours sans arbres = défaite/fin).
- **Tour 1 budget 1000 ms** : utilisable pour pré-calculer distances, zones, scoring de cases.
- **Inventaire shack pour TRAIN** : seules les ressources du shack comptent (pas celles portées par les trolls).
  Toujours DROP avant de TRAIN.
- **TRAIN est gratuit en "slot d'action"** : on peut TRAIN ET faire bouger/récolter tous les trolls existants dans le
  même tour. Donc, dès qu'on a les ressources et un bon ROI, on TRAIN sans arbitrage avec une autre action.
- **MINE adjacent (≠ même case)** : différent de HARVEST/CHOP qui exigent **même case** que l'arbre. MINE se fait
  depuis une case GRASS **adjacente (H/V)** à la case IRON.
- **IRON non praticable** : on ne peut pas marcher dessus, juste l'exploiter depuis une case GRASS adjacente. La case
  IRON est un **obstacle** que le pathfinding doit contourner.
- **SHACK non praticable** : idem, les deux shacks sont des obstacles que les trolls (alliés comme adverses) doivent
  contourner. Toute interaction avec un shack se fait depuis une case GRASS adjacente.
- **Adjacence = H/V uniquement** : pas de diagonale. Une case n'est adjacente qu'à 4 voisines max (moins sur les bords
  de carte). Toujours vérifier `|dx|+|dy| == 1` pour qualifier une interaction « adjacente ».
- **Fin par absence d'arbres pendant 10 tours** : si on rase tout sans replanter, partie courte → planter est
  stratégique pour prolonger la collecte de l'adversaire si on est en tête.

## Questions en suspens

- **Attributs du troll initial** : non précisés dans l'énoncé. Probablement `1/1/1/1`. À confirmer via parsing du
  premier tour.
- **Taille d'un arbre planté** : probablement size=1 avec full health (6/6/11/3 selon le type). À confirmer.
- **Cooldown initial à la plantation** : valeur de départ (cooldown plein ? 0 ?).
- **PLANT avec types différents simultanés** : graines conservées ou perdues ? L'énoncé dit « rien ne se passe » → à
  interpréter comme conservation.
- **Spawn TRAIN si toutes les cases GRASS adjacentes au shack sont occupées** : que se passe-t-il ? (Skip du TRAIN ?
  Spawn en file d'attente ? À tester.) Le shack lui-même restant non praticable, le nouveau troll doit apparaître sur
  une case GRASS voisine.
- **Croissance/dégâts** : si on chop pendant qu'un arbre grandit dans le même tour, l'ordre dit
  MOVE→HARVEST→PLANT→CHOP→...→Grow → on attaque la health **avant** la croissance.
- **WAIT explicite obligatoire** ? Suffit-il de ne rien écrire pour un troll ? L'usage classique CG est qu'on doit
  produire une commande par troll, à confirmer.
- **Plusieurs TRAIN par tour** ? L'énoncé ne précise pas, mais comme c'est une action supplémentaire indépendante des
  trolls, on pourrait théoriquement émettre `TRAIN ...; TRAIN ...`. À confirmer (et `n` est-il recalculé entre les
  deux ?). En pratique le coût grimpe vite donc rarement utile.
