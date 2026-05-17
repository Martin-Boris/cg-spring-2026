# Design — Actions HARVEST dans le GA

**Date** : 2026-05-17  
**Branch** : ga-v2  
**Scope** : Ajout des gènes HARVEST au génome GA (sélection, init, mutations, fitness)

---

## Contexte

Le bot possède déjà des gènes CUT (couper un arbre → porter du bois → drop) et PLANT (aller chercher une graine → planter → couper). L'objectif est d'ajouter une troisième famille de gènes : **HARVEST** — se rendre sur un arbre de taille 4, récolter ses fruits, retourner au shack adjacent, déposer.

Les arbres de taille 4 produisent jusqu'à 3 fruits avec un cooldown. Chaque fruit rapporte 1 point au shack (via DROP). La récolte est conditionnée par `trollHP` (harvest power) du troll.

---

## Encodage du gène HARVEST dans `Genome.java`

### Layout du short 16 bits

```
bit 15 = PLANT flag  (0x8000)  — existant
bit 14 = HARVEST flag (0x4000) — nouveau
bits 8-12 = x (5 bits, X_MASK=0x1F, X_SHIFT=8)
bits 0-7  = y (8 bits)
```

Les cartes CG ont une largeur ≤ 32, donc x tient dans 5 bits. Bit 14 est libre pour les gènes CUT existants (x < 32 → bit 14 du short = bit 6 de x = 0).

### Nouvelles méthodes dans `Genome`

```java
private static final int HARVEST_FLAG_MASK = 0x4000;

public static short makeHarvest(int x, int y) {
    return (short)(HARVEST_FLAG_MASK | ((x & X_MASK) << X_SHIFT) | (y & 0xFF));
}

public static boolean isHarvest(short g) {
    return (g & HARVEST_FLAG_MASK) != 0 && (g & PLANT_FLAG_MASK) == 0;
}
```

### Tableau des candidats harvest

```java
public static final short[] harvestCandidates = new short[MAX_TREES];
public static int harvestCandidateCount = 0;

public static void initHarvestCandidates(GameState state) {
    harvestCandidateCount = 0;
    for (int t = 0; t < state.treeCount; t++) {
        if ((state.treeSize[t] & 0xFF) == 4 && state.treeHealth[t] > 0) {
            harvestCandidates[harvestCandidateCount++] =
                encode(state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
        }
    }
}
```

`initHarvestCandidates` est appelé au même endroit que `initPlantCandidates` (chaque tour, avant le lancement du GA).

---

## Politique d'exécution dans `TrollPolicy.java`

### Branchement HARVEST dans `decideForOwnTroll`

Inséré dans la boucle `while (cursor < len)`, **avant** le branchement PLANT/CUT existant :

```java
if (Genome.isHarvest(g)) {
    int gx = Genome.geneX(g), gy = Genome.geneY(g);
    int treeIdx = s.treeIndexAt(gx, gy);

    // Arbre invalide (mort, trop petit) → skip
    if (treeIdx < 0 || (s.treeSize[treeIdx] & 0xFF) < 4 || s.treeHealth[treeIdx] <= 0) {
        cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue;
    }

    // Carrying fruits → retour au shack
    int fruitCarry = 0;
    int invBase = trollIdx * ResourceType.COUNT;
    for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
        fruitCarry += s.trollInventory[invBase + r] & 0xFF;

    if (fruitCarry > 0) {
        if (isShackAdjacent(tx, ty)) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0;
            return Action.drop(trollIdx);
        }
        return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
    }

    // Sur l'arbre
    if (tx == gx && ty == gy) {
        if ((s.treeFruits[treeIdx] & 0xFF) == 0) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; // skip si vide
        }
        return Action.harvest(trollIdx);
    }
    // En route vers l'arbre
    return Action.move(trollIdx, gx, gy);
}
```

### Invariants respectés

- `policyPhase` n'est pas nécessaire pour HARVEST (l'état `fruitCarry > 0` distingue les phases).
- Le check `wood > 0` en tête de fonction reste inchangé et couvre les trolls bûcherons.
- Le `fruitCarry` local n'interfère pas avec les seeds portés par les trolls PLANT (le branchement HARVEST n'est atteint que si le gène courant est HARVEST).

---

## Init population dans `GenomeOps.java`

### Étape 6 dans `initRandom`

Après l'injection des gènes PLANT (étape 5) :

```java
public static final double P_HARVEST_INIT = 0.30;
private static final boolean[] initSeenHarvest = new boolean[256 * 256];

// Étape 6 : Injecter gènes HARVEST pour trolls avec HP > 0
if (Genome.harvestCandidateCount > 0) {
    // Reset seen
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        initSeenHarvest[(Genome.candY(c)) * GameState.width + Genome.candX(c)] = false;
    }
    for (int k = 0; k < ownTrollsCount; k++) {
        int trollIdx = ownTrollsBuf[k];
        if ((state.trollHP[trollIdx] & 0xFF) == 0) continue;
        int len = Genome.len(lenBuf, individuIdx, trollIdx);
        if (len >= Genome.MAX_TARGETS_PER_TROLL) continue;
        if (rng.nextDouble() >= P_HARVEST_INIT) continue;
        int tries = 0;
        while (tries < Genome.harvestCandidateCount) {
            short c = Genome.harvestCandidates[rng.nextInt(Genome.harvestCandidateCount)];
            int cx = Genome.candX(c), cy = Genome.candY(c);
            if (!initSeenHarvest[cy * GameState.width + cx]) {
                Genome.setGene(buf, individuIdx, trollIdx, len,
                               Genome.makeHarvest(cx, cy));
                Genome.setLen(lenBuf, individuIdx, trollIdx, len + 1);
                initSeenHarvest[cy * GameState.width + cx] = true;
                break;
            }
            tries++;
        }
    }
}
```

---

## Mutations dans `GenomeOps.java`

### Nouvelle mutation `mutateInsertHarvest`

Symétrique à `mutateInsertCut`, mais :
1. Filtre les trolls avec `HP > 0`
2. Insère un `makeHarvest(cx, cy)` sur un candidat absent du génome

```java
private static final boolean[] mutSeenHarvest = new boolean[256 * 256];

public static void mutateInsertHarvest(GameState state, short[] buf, byte[] lenBuf,
                                       int individuIdx, SplittableRandom rng) {
    if (Genome.harvestCandidateCount == 0) return;
    // Pick troll avec HP > 0 et de la place
    int count = 0;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) {
        if ((state.trollHP[j] & 0xFF) > 0
            && Genome.len(lenBuf, individuIdx, j) < Genome.MAX_TARGETS_PER_TROLL)
            freeTrollsBuf[count++] = j;
    }
    if (count == 0) return;
    int j = freeTrollsBuf[rng.nextInt(count)];
    int len = Genome.len(lenBuf, individuIdx, j);
    int W = GameState.width;
    // Reset + marquer les candidats déjà présents
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        mutSeenHarvest[Genome.candY(c) * W + Genome.candX(c)] = false;
    }
    for (int tj = 0; tj < GameState.MAX_TROLLS; tj++) {
        int tjLen = Genome.len(lenBuf, individuIdx, tj);
        int base = Genome.offset(individuIdx, tj);
        for (int k = 0; k < tjLen; k++) {
            short g = buf[base + k];
            if (Genome.isHarvest(g))
                mutSeenHarvest[Genome.geneY(g) * W + Genome.geneX(g)] = true;
        }
    }
    // Insérer un candidat non vu
    int tries = 0;
    while (tries < Genome.harvestCandidateCount) {
        short c = Genome.harvestCandidates[rng.nextInt(Genome.harvestCandidateCount)];
        int cx = Genome.candX(c), cy = Genome.candY(c);
        if (!mutSeenHarvest[cy * W + cx]) {
            int pos = rng.nextInt(len + 1);
            int base = Genome.offset(individuIdx, j);
            for (int k = len; k > pos; k--) buf[base + k] = buf[base + k - 1];
            buf[base + pos] = Genome.makeHarvest(cx, cy);
            Genome.setLen(lenBuf, individuIdx, j, len + 1);
            return;
        }
        tries++;
    }
}
```

### Suppression des gènes HARVEST

La mutation `mutateDelete` existante suffit : elle supprime aléatoirement n'importe quel gène, y compris HARVEST. Pas de mutation delete dédiée.

### Rebalancement de `pickMutationKind`

```java
public static final double P_MUT_SWAP_INTRA      = 0.22;
public static final double P_MUT_SWAP_INTER      = 0.18;
public static final double P_MUT_REVERSE         = 0.09;
public static final double P_MUT_DELETE          = 0.09;
public static final double P_MUT_INSERT_PLANT    = 0.13;
public static final double P_MUT_INSERT_CUT      = 0.19;
public static final double P_MUT_INSERT_HARVEST  = 0.10;  // nouveau

public static final int MUT_INSERT_HARVEST = 6;
```

*(Valeurs exactes à calibrer via fine-tuning — l'ordre de grandeur est l'important.)*

---

## Fitness dans `GenomeEvaluator.java`

```java
public static final double ALPHA_WOOD_CARRY  = 2.0;  // existant
public static final double ALPHA_FRUIT_CARRY = 2.0;  // nouveau

private static double fitness(GameState finalState) {
    int scoreMe = finalState.score(0);
    int scoreOpp = finalState.score(1);
    int woodCarryMe = 0;
    int fruitCarryMe = 0;
    for (int i = 0; i < finalState.trollCount; i++) {
        if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
        int base = i * ResourceType.COUNT;
        woodCarryMe  += finalState.trollInventory[base + ResourceType.WOOD] & 0xFF;
        for (int r = ResourceType.PLUM; r <= ResourceType.BANANA; r++)
            fruitCarryMe += finalState.trollInventory[base + r] & 0xFF;
    }
    return (scoreMe - scoreOpp)
         + ALPHA_WOOD_CARRY  * woodCarryMe
         + ALPHA_FRUIT_CARRY * fruitCarryMe;
}
```

**Justification de `ALPHA_FRUIT_CARRY = 2.0`** : 1 fruit = 1 point au shack. Même calibrage que le bois (qui vaut 4 points mais a aussi `ALPHA=2.0`). Valeur légèrement optimiste pour encourager l'exploration harvest ; à ajuster via fine-tuning.

---

## Résumé des fichiers modifiés

| Fichier | Changements |
|---|---|
| `Genome.java` | `HARVEST_FLAG_MASK`, `makeHarvest()`, `isHarvest()`, `harvestCandidates[]`, `harvestCandidateCount`, `initHarvestCandidates()` |
| `GenomeOps.java` | `P_HARVEST_INIT`, `initSeenHarvest[]`, étape 6 dans `initRandom`, `mutSeenHarvest[]`, `mutateInsertHarvest()`, `MUT_INSERT_HARVEST`, probas rebalancées dans `pickMutationKind` + `runMutation` |
| `TrollPolicy.java` | Branchement `isHarvest(g)` dans `decideForOwnTroll` |
| `GenomeEvaluator.java` | `ALPHA_FRUIT_CARRY`, calcul `fruitCarryMe` dans `fitness()` |

---

## Ce qui n'est PAS dans ce scope

- `initWarm` : pas de gènes HARVEST dans l'init greedy (les candidats harvest ne sont pas connus de GreedyAgent).
- `initFromPrevBest` : les gènes HARVEST sont transférés automatiquement (même logique que les gènes CUT — ils encodent une position x,y valide tant que l'arbre existe).
- `crossover` : les gènes HARVEST sont traités avec `seenTargetBuf` (même table que CUT) — deux trolls ne ciblent pas le même arbre harvest dans un offspring. *(À revoir si on veut autoriser le harvest coopératif.)*
