# Design — Gène MINE IRON + fitness shaping pour débloquer TRAIN

**Date :** 2026-05-17
**Branche :** ga-v2
**Scope :** Ajouter un 4ème type de gène (MINE IRON) au génome GA + une récompense de fitness qui pousse la pop à constituer le stock shack au palier TRAIN, avant un cutoff temporel et un cap de population.

---

## Contexte

`GreedyAgent.maybeTrain` (`GeneticAgent.decide` étape 5) est appelé chaque tour mais ne déclenche jamais TRAIN : il manque chroniquement d'IRON (et souvent le palier exact en PLUM/LEMON/APPLE) dans le shack. Cause racine :

1. Le génome ne code que HARVEST, CUT, PLANT (`Genome.java:13-17`). Aucun gène ne produit l'action MINE → le bot n'extrait pas d'IRON.
2. La fitness (`GenomeEvaluator.fitness`) récompense `score + α·woodCarry + α·fruitCarry`. Rien ne pousse à atteindre **le palier TRAIN exact** sur le stock shack (où 1 unité de plus débloque un nouveau troll). À l'horizon 25 ticks, la pression évolutive privilégie le score immédiat (WOOD/HARVEST) plutôt que la constitution d'un stock d'investissement.

Objectif : permettre au GA d'envoyer un troll miner de l'IRON, et orienter la fitness pour qu'elle valorise les ressources qui rapprochent du prochain palier TRAIN, tout en bornant cet effort dans le temps (pas de mining après tour 150) et en population (désactivé une fois 5 trolls atteints).

---

## Section 1 — Nouveau type de gène MINE dans `Genome.java`

### Layout des shorts 16 bits (après changement)

```
bit15=1                    → PLANT gene  (bits13-14=fruitType, bits8-12=x, bits0-7=y)
bit15=0, bit14=1           → HARVEST gene (bits8-12=x, bits0-7=y)
bit15=0, bit14=0, bit13=1  → MINE gene    (bits8-12=x, bits0-7=y)   ← nouveau
bit15=0, bit14=0, bit13=0  → CUT gene     (bits8-12=x, bits0-7=y)
all bits=1                 → EMPTY_GENE
```

Invariant préservé : `X_MASK = 0x1F` (5 bits) → `x < 32`. Cartes CG ont `width ≤ 22`, donc bit13 du short reste 0 pour tout encoding non-MINE.

### Nouvelles méthodes

```java
private static final int MINE_FLAG_MASK = 0x2000;  // bit13

public static short makeMine(int x, int y) {
    return (short)(MINE_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
}

public static boolean isMine(short g) {
    return (g & 0xE000) == MINE_FLAG_MASK;  // bit15=0, bit14=0, bit13=1
}
```

`geneX(g)` et `geneY(g)` existants fonctionnent tels quels (bits 8-12 pour x, bits 0-7 pour y).

### Tableau des candidats IRON

```java
public static final int MAX_IRON_CANDIDATES = 64;  // borne sup pratique sur 1 carte CG
public static final short[] ironCandidates = new short[MAX_IRON_CANDIDATES];
public static int ironCandidateCount = 0;

public static void initIronCandidates() {
    ironCandidateCount = 0;
    for (int y = 0; y < GameState.height; y++) {
        for (int x = 0; x < GameState.width; x++) {
            if (GameState.tiles[y * GameState.width + x] != TileType.IRON) continue;
            // Garder uniquement celles qui ont au moins 1 voisin GRASS accessible
            if (hasAdjacentGrass(x, y)) {
                ironCandidates[ironCandidateCount++] = encode(x, y);
            }
        }
    }
}
```

Appelé **une seule fois** (init game, statique) dans `GeneticAgent.decide` à côté de `initPlantCandidates` (cellules IRON et terrain sont immuables). `hasAdjacentGrass(x, y)` vérifie qu'au moins une des 4 cases adjacentes est `TileType.GRASS` (sinon la cellule IRON est inminable, on l'exclut des candidats).

---

## Section 2 — Politique MINE dans `TrollPolicy.decideForOwnTroll`

Branchement inséré **entre** `isHarvest` et le bloc CUT implicite (qui reste l'else `!isPlant`).

```java
if (Genome.isMine(g)) {
    int gx = Genome.geneX(g), gy = Genome.geneY(g);

    // 1. Vérif IRON présent (carte immuable → toujours vrai, défensif)
    if (GameState.tileAt(gx, gy) != TileType.IRON) {
        cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
    }
    // 2. Skip si cp == 0 (cohérent avec CUT)
    if ((s.trollCP[trollIdx] & 0xFF) == 0) {
        cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
    }
    // 3. Si carry > 0 ET carry plein → drop d'abord
    int carryTotal = s.trollCarryTotal[trollIdx];
    int cc = s.trollCC[trollIdx] & 0xFF;
    if (carryTotal >= cc) {
        if (isShackAdjacent(tx, ty)) {
            return Action.drop(trollIdx);  // cursor inchangé : on revient miner après drop
        }
        return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
    }
    // 4. Sur cellule grass adjacente à l'IRON → MINE
    if (isAdjacentToCell(tx, ty, gx, gy)) {
        // Si après ce MINE le troll sera plein, on consume le gène
        int gain = Math.min(s.trollCP[trollIdx] & 0xFF, cc - carryTotal);
        if (carryTotal + gain >= cc) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0;
        }
        return Action.mine(trollIdx);
    }
    // 5. Sinon move vers la grass-adj la plus proche
    int[] adj = closestGrassAdjToIron(tx, ty, gx, gy);
    return Action.move(trollIdx, adj[0], adj[1]);
}
```

**Helpers nouveaux** (privés à `TrollPolicy`) :

```java
private static boolean isAdjacentToCell(int x, int y, int targetX, int targetY) {
    return Math.abs(x - targetX) + Math.abs(y - targetY) == 1;
}

// Retourne la cellule GRASS adjacente (4-connexité) à (ix, iy) la plus proche
// de (fromX, fromY) au sens PathTable.distance. Retourne {ix, iy} si aucune
// (cas pathologique exclu par initIronCandidates).
private static int[] closestGrassAdjToIron(int fromX, int fromY, int ix, int iy) {
    int[] dx = {1, -1, 0, 0};
    int[] dy = {0, 0, 1, -1};
    int bestX = ix, bestY = iy, bestD = PathTable.UNREACHABLE;
    for (int k = 0; k < 4; k++) {
        int nx = ix + dx[k], ny = iy + dy[k];
        if (nx < 0 || nx >= GameState.width || ny < 0 || ny >= GameState.height) continue;
        if (GameState.tileAt(nx, ny) != TileType.GRASS) continue;
        int d = PathTable.distance(fromX, fromY, nx, ny);
        if (d == PathTable.UNREACHABLE) continue;
        if (d < bestD) { bestD = d; bestX = nx; bestY = ny; }
    }
    return new int[]{bestX, bestY};
}
```

**Note** : `closestGrassAdjToIron` alloue un `int[2]` à chaque appel. Acceptable car appelé au plus une fois par décision-troll-tour (cold path policy). Si profilage révèle une pression GC, on peut réutiliser un scratch buffer statique.

**Mise à jour du bloc CUT** : remplacer `if (!Genome.isPlant(g))` par `if (Genome.isCut(g))` (équivalent fonctionnel, plus explicite avec MINE en place). Ajout de `Genome.isCut` :

```java
public static boolean isCut(short g) {
    return (g & 0xE000) == 0;  // bit15=0, bit14=0, bit13=0
}
```

---

## Section 3 — Init/mutate avec cutoff temporel dans `GenomeOps.java`

### Nouveaux paramètres (constantes en tête du fichier)

```java
public static final double P_MINE_INIT  = 0.20;
public static final double P_MUT_INSERT_MINE = 0.08;
```

### Re-balancement de `pickMutationKind`

Probas actuelles (somme = 1.0) : SWAP_INTRA 0.22, SWAP_INTER 0.18, REVERSE 0.09, DELETE 0.09, INSERT_PLANT 0.13, INSERT_CUT 0.19, INSERT_HARVEST 0.10.

Nouvelles probas (somme = 1.0) :

```java
public static final double P_MUT_SWAP_INTRA      = 0.20;
public static final double P_MUT_SWAP_INTER      = 0.17;
public static final double P_MUT_REVERSE         = 0.08;
public static final double P_MUT_DELETE          = 0.09;
public static final double P_MUT_INSERT_PLANT    = 0.12;
public static final double P_MUT_INSERT_CUT      = 0.17;
public static final double P_MUT_INSERT_HARVEST  = 0.09;
public static final double P_MUT_INSERT_MINE     = 0.08;
```

### Cutoff temporel

`mutateInsertMine` et l'injection MINE dans `initRandom` sont **no-op** si `state.turn >= GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF`. Pas de gène MINE ne peut entrer dans la population à partir du tour 150. Les gènes MINE déjà présents dans `prevBest` sont **conservés via `initFromPrevBest`** (le mining en cours peut se terminer) mais finiront par mourir naturellement (sélection / mutateDelete).

### Étape 7 dans `initRandom` (après PLANT et HARVEST)

```java
// 7. Injecter gènes MINE (si turn < cutoff)
if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF
    && Genome.ironCandidateCount > 0) {
    int W = GameState.width;
    for (int h = 0; h < Genome.ironCandidateCount; h++) {
        short c = Genome.ironCandidates[h];
        initSeenMine[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int k = 0; k < ownTrollsCount; k++) {
        int trollIdx = ownTrollsBuf[k];
        if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;  // sans cp, pas de mining
        int len = Genome.len(lenBuf, individuIdx, trollIdx);
        if (len >= Genome.MAX_TARGETS_PER_TROLL) continue;
        if (rng.nextDouble() >= P_MINE_INIT) continue;
        int tries = 0;
        while (tries < Genome.ironCandidateCount) {
            short c = Genome.ironCandidates[rng.nextInt(Genome.ironCandidateCount)];
            int cx = Genome.candX(c), cy = Genome.candY(c);
            if (!initSeenMine[cy * W + cx]) {
                Genome.setGene(buf, individuIdx, trollIdx, len, Genome.makeMine(cx, cy));
                Genome.setLen(lenBuf, individuIdx, trollIdx, len + 1);
                initSeenMine[cy * W + cx] = true;
                break;
            }
            tries++;
        }
    }
}
```

### `mutateInsertMine`

Calque exact de `mutateInsertCut`, avec :
- filtre `cp > 0` (au lieu de `cp > 0` aussi — même règle)
- filtre `state.turn < TRAIN_PUSH_TURN_CUTOFF` en tête (early return)
- candidats = `Genome.ironCandidates` (cellules absentes du génome de cet individu)
- encodage via `Genome.makeMine(cx, cy)`
- buffer `mutSeenMine[]` séparé

### `initWarm`

`initWarm` cherche le plus proche arbre vivant. Pas de gène MINE injecté en warm — la chaîne mining/drop/retour ne se mappe pas sur un round-robin par couche d'arbres. Le warm reste focalisé sur l'exploitation des arbres. La diversité MINE vient d'`initRandom` (POP_SIZE-2 individus) et de `mutateInsertMine`.

### `initFromPrevBest`

Préservation transparente : la boucle copie tous les gènes non-EMPTY dont la cible reste valide. Pour un gène MINE, la cible (cellule IRON) est immuable → toujours valide. **Changement requis** : actuellement `initFromPrevBest` ligne 203 fait `int t = state.treeIndexAt(gx, gy)` pour valider les gènes CUT/HARVEST en arbre vivant. MINE doit court-circuiter ce check :

```java
short g = prevBuf[srcOff + k];
if (g == Genome.EMPTY_GENE) continue;
if (Genome.isMine(g)) {
    // Cellule IRON immuable, toujours valide
    dstBuf[dstOff + written++] = g;
    continue;
}
if (Genome.isPlant(g)) {
    // Logique PLANT existante (inchangée)
    ...
}
int t = state.treeIndexAt(Genome.geneX(g), Genome.geneY(g));
if (t < 0 || state.treeHealth[t] <= 0) continue;
dstBuf[dstOff + written++] = g;
```

*(Le code actuel ne traite pas spécifiquement PLANT — il filtre tout via `treeIndexAt`. Or un gène PLANT pointe sur une case shack-adj vide d'arbre. À vérifier : ce filtre élimine-t-il aussi les PLANT ? Si oui, c'est un bug existant orthogonal à ce design. À ne PAS corriger dans ce scope.)*

### `crossover`

Les gènes MINE sont traités comme "target" (même bucket que CUT/HARVEST via `seenTargetBuf`). Aucun changement requis : `Genome.isPlant(g)` retourne false pour MINE → mappé sur `seenTargetBuf`.

### Buffers scratch ajoutés

```java
private static final boolean[] initSeenMine = new boolean[256 * 256];
private static final boolean[] mutSeenMine  = new boolean[256 * 256];
```

---

## Section 4 — Fitness shaping dans `GenomeEvaluator.java`

### Nouvelles constantes

```java
public static final int    TRAIN_PUSH_TURN_CUTOFF = 150;
public static final int    TRAIN_PUSH_TROLL_CAP   = 5;
public static final double ALPHA_TRAIN_PUSH       = 0.5;
public static final double ALPHA_IRON_CARRY       = 0.5;

// Static pour éviter alloc dans le hot path (appelé POP_SIZE × HORIZON × N gens)
private static final int[] TRAIN_RESOURCES = {
    ResourceType.PLUM, ResourceType.LEMON, ResourceType.APPLE, ResourceType.IRON
};
```

### Modification de `fitness(GameState finalState)`

```java
private static double fitness(GameState finalState) {
    int scoreMe  = finalState.score(0);
    int scoreOpp = finalState.score(1);

    int woodCarryMe = 0;
    int fruitCarryMe = 0;
    int ironCarryMe = 0;
    int ownTrolls = 0;
    for (int i = 0; i < finalState.trollCount; i++) {
        if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
        ownTrolls++;
        int base = i * ResourceType.COUNT;
        woodCarryMe  += finalState.trollInventory[base + ResourceType.WOOD] & 0xFF;
        ironCarryMe  += finalState.trollInventory[base + ResourceType.IRON] & 0xFF;
        for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
            fruitCarryMe += finalState.trollInventory[base + r] & 0xFF;
    }

    double trainPush = 0.0;
    if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF && ownTrolls < TRAIN_PUSH_TROLL_CAP) {
        int target = ownTrolls + 1;  // palier pour TRAIN avec stat=1
        for (int k = 0; k < TRAIN_RESOURCES.length; k++) {
            int stock = finalState.shackInventory[TRAIN_RESOURCES[k]];
            trainPush += Math.min(stock, target);
        }
    }

    return (scoreMe - scoreOpp)
         + ALPHA_WOOD_CARRY  * woodCarryMe
         + ALPHA_FRUIT_CARRY * fruitCarryMe
         + ALPHA_IRON_CARRY  * ironCarryMe
         + ALPHA_TRAIN_PUSH  * trainPush;
}
```

**Justifications :**
- `target = ownTrolls + 1` : palier pour TRAIN avec une seule stat à 1 (coût base = `n + 1²`). On ne valorise que la première unité au-delà du palier ; au-delà, le bonus est plat — pas de double-récompense pour des stocks excessifs.
- `ALPHA_IRON_CARRY = 0.5` : équivalent au `ALPHA_FRUIT_CARRY` actuel (0.5). L'IRON porté seul vaut 0 point mais a une valeur instrumentale (déposera bientôt).
- `ALPHA_TRAIN_PUSH = 0.5` : 1 ressource shack dans la zone palier vaut 0.5 fitness en plus de son score natif. Pour un fruit déposé : 1.0 (score) + 0.5 (push) = 1.5. Pour IRON déposé : 0 + 0.5 = 0.5 (juste assez pour valoir le détour avant le cutoff). Valeur à fine-tuner.
- `TRAIN_RESOURCES` est un static final pour éviter toute allocation dans le hot path (`fitness` est appelée POP_SIZE × HORIZON × N générations).

### Conséquence pour `GeneticAgent.evaluatePopulation`

Aucun changement. La fitness shapée est utilisée tel quel ; le `hysteresisBonus` reste additionnel.

---

## Section 5 — `GenomeInvariants.check`

Les gènes MINE passent le check `seenTarget` actuel : `Genome.isPlant(g)` retourne false → mappés dans `seenTarget`. Pas de modification requise.

Effet de bord : un troll ne peut pas avoir simultanément un gène MINE(x,y) ET un gène CUT/HARVEST sur la même (x,y). En pratique impossible (les cellules IRON sont des obstacles, pas d'arbre). Acceptable.

---

## Récapitulatif des fichiers modifiés

| Fichier | Changements |
|---|---|
| `Genome.java` | `MINE_FLAG_MASK`, `makeMine`, `isMine`, `isCut`, `ironCandidates[]`, `ironCandidateCount`, `initIronCandidates` |
| `GenomeOps.java` | `P_MINE_INIT`, `P_MUT_INSERT_MINE`, `MUT_INSERT_MINE`, `mutateInsertMine`, étape 7 dans `initRandom`, `initSeenMine`/`mutSeenMine`, court-circuit MINE dans `initFromPrevBest`, rebalancement `pickMutationKind` |
| `TrollPolicy.java` | Branchement `isMine(g)` dans `decideForOwnTroll`, helpers `isAdjacentToCell` et `closestGrassAdjToIron`, remplacement `!isPlant` par `isCut` |
| `GenomeEvaluator.java` | `TRAIN_PUSH_TURN_CUTOFF`, `TRAIN_PUSH_TROLL_CAP`, `ALPHA_TRAIN_PUSH`, `ALPHA_IRON_CARRY`, `TRAIN_RESOURCES[]`, fitness shaping + `ironCarryMe` |
| `GeneticAgent.java` | Appel `Genome.initIronCandidates()` une fois (à côté de `initPlantCandidates`) |

---

## Ce qui n'est PAS dans ce scope

- **Action MINE dans `Simulator`** : déjà implémentée (`Simulator.applyMines` ligne 505), aucun changement nécessaire.
- **`GreedyAgent.maybeTrain`** : déjà appelé chaque tour, déclenche dès que les ressources suffisent. Aucun changement.
- **Stratégie de tri trolls pour TRAIN dédié au mining** (ex : un troll cp-élevé dédié) : la pression évolutive via fitness shaping + diversité du génome est laissée libre de trouver les optimums.
- **Sélection multi-palier** (ex : pousser à n+2 ou n+3) : on ne valorise que +1 unité au-delà du stock courant. Pas de récompense pour sur-stocker.
- **`initWarm`** : pas de gène MINE injecté en warm. La pression vient d'`initRandom` et des mutations.

---

## Risques & validation attendue

1. **Le mining alloue-t-il un trade-off positif ?** Vérifier en match local (3+ parties) que TRAIN se déclenche au moins une fois avant tour 150 et qu'on atteint 3+ trolls. Sans amélioration mesurable, baisser le palier `cutoff` ou augmenter `ALPHA_TRAIN_PUSH`.
2. **Pas de régression sur le score sans mining** : sur cartes sans IRON proche ou cartes saturées d'arbres, le bonus push doit rester dominé par le score natif. À vérifier sur ≥ 5 cartes variées.
3. **Cohérence cursor sur drop intermédiaire pendant mining** : un troll plein qui drop puis revient miner doit retrouver le gène MINE actif (cursor non incrémenté lors du drop intermédiaire — vérifié par construction Section 2 étape 3).
4. **Performance** : la fitness shape ajoute 4 lookups d'array + 4 min() par appel fitness (~POP_SIZE × 25 ticks × N générations). Coût négligeable sur 44ms budget.
