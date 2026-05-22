# Design — Refonte du train push : focus early-game sur harvest / mine / plant-only

**Date :** 2026-05-22
**Branche :** ga-v3
**Scope :** Avant le tour `TRAIN_PUSH_TURN_CUTOFF` (= 150), le bot ne cible plus d'arbres pour CUT, ne chop pas après PLANT, et la fitness pousse à amener des fruitiers proches du shack. Comportement post-cutoff inchangé.

---

## Contexte

Le design [`2026-05-17-mine-iron-train-push-design.md`](2026-05-17-mine-iron-train-push-design.md) a introduit le gène MINE et le bonus `trainPush` pour débloquer TRAIN avant le tour 150. En pratique, le bot continue à dépenser une part importante de ses cycles sur du CUT (revenu bois immédiat) au détriment de l'investissement (planter des fruitiers proches, miner de l'IRON). La pression évolutive sur 25 ticks d'horizon ne suffit pas à internaliser le retour différé.

**Objectif :** **forcer** structurellement le bot à investir avant le cutoff en supprimant les gènes CUT de la pop early-game et en récompensant explicitement la présence d'arbres PLUM / LEMON / APPLE proches du shack allié.

---

## Approche

**Gating local** : chaque fonction concernée par le passage early-game vérifie `state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF`. Aucun flag global, aucune signature modifiée. Pattern déjà en place dans le code (`mutateInsertMine` ligne 512, étape 7 de `initRandom`).

5 fichiers / 6 zones de changement. Aucune nouvelle classe.

---

## Section 1 — Fitness shaping : bonus near-tree avant cutoff

**Fichier :** `GenomeEvaluator.java`

### Nouvelle constante

```java
public static final double ALPHA_NEAR_TREE = 2.0;
```

Bonus binaire **par type de fruit** : `+ALPHA_NEAR_TREE` si ≥1 arbre vivant de ce type à distance Manhattan ≤ 5 du shack allié. Max = `3 × ALPHA_NEAR_TREE` = 6.0 (PLUM + LEMON + APPLE).

### Bloc dans `fitness(GameState finalState)`

Inséré avant le `return`, en parallèle du `trainPush` :

```java
double nearTreeBonus = 0.0;
if (finalState.turn < TRAIN_PUSH_TURN_CUTOFF) {
    boolean hasPlum = false, hasLemon = false, hasApple = false;
    int sx = GameState.shackMeX, sy = GameState.shackMeY;
    for (int t = 0; t < finalState.treeCount; t++) {
        if (finalState.treeHealth[t] <= 0) continue;
        byte type = finalState.treeType[t];
        if (type == TreeType.BANANA) continue;
        int dx = (finalState.treeX[t] & 0xFF) - sx;
        int dy = (finalState.treeY[t] & 0xFF) - sy;
        if (Math.abs(dx) + Math.abs(dy) > 5) continue;
        if      (type == TreeType.PLUM  && !hasPlum)  hasPlum  = true;
        else if (type == TreeType.LEMON && !hasLemon) hasLemon = true;
        else if (type == TreeType.APPLE && !hasApple) hasApple = true;
        if (hasPlum && hasLemon && hasApple) break;
    }
    int typesCovered = (hasPlum ? 1 : 0) + (hasLemon ? 1 : 0) + (hasApple ? 1 : 0);
    nearTreeBonus = ALPHA_NEAR_TREE * typesCovered;
}
```

### Retour

```java
return (scoreMe - scoreOpp)
     + ALPHA_WOOD_CARRY  * woodCarryMe
     + ALPHA_FRUIT_CARRY * fruitCarryMe
     + ALPHA_IRON_CARRY  * ironCarryMe
     + ALPHA_TRAIN_PUSH  * trainPush
     + nearTreeBonus;
```

### Justifications

- **Évalué sur `finalState`** : si le génome plante un PLUM proche dans le rollout, le bonus apparaît. La pression évolutive favorise les génomes qui amènent un fruitier proche dans l'horizon.
- **Court-circuit `break`** une fois les 3 types trouvés.
- **BANANA exclu** explicitement (hors palier TRAIN).
- **Zéro alloc** dans le hot path.
- **Manhattan** : choisi pour rapidité. Risque marginal : arbre techniquement inaccessible (obstacle) mais à dist ≤ 5 récompensé. Acceptable en V1.

---

## Section 2 — `initRandom` : étapes 3-4 (distribution arbres → CUT) gated

**Fichier :** `GenomeOps.java`, méthode `initRandom`

### Changement

Les étapes 3-4 actuelles construisent `shuffleBuf[]` (arbres vivants) puis distribuent ces cellules comme gènes CUT. Elles sont la **seule source** de gènes CUT dans `initRandom`. On les wrappe dans un gate :

```java
// 3. Récupérer arbres vivants + 4. distribuer (UNIQUEMENT post-cutoff)
if (state.turn >= GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) {
    int treeCount = 0;
    for (int t = 0; t < state.treeCount; t++) {
        if (state.treeHealth[t] > 0) {
            shuffleBuf[treeCount++] = Genome.encode(
                state.treeX[t] & 0xFF, state.treeY[t] & 0xFF);
        }
    }
    for (int i = treeCount - 1; i > 0; i--) {
        int j = rng.nextInt(i + 1);
        short tmp = shuffleBuf[i]; shuffleBuf[i] = shuffleBuf[j]; shuffleBuf[j] = tmp;
    }
    for (int i = 0; i < treeCount; i++) {
        if (rng.nextDouble() < P_SKIP_INIT) continue;
        int freeCount = 0;
        for (int k = 0; k < ownTrollsCount; k++) {
            int trollIdx = ownTrollsBuf[k];
            if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;
            if (Genome.len(lenBuf, individuIdx, trollIdx) < Genome.MAX_TARGETS_PER_TROLL) {
                freeTrollsBuf[freeCount++] = trollIdx;
            }
        }
        if (freeCount == 0) break;
        int chosenTroll = freeTrollsBuf[rng.nextInt(freeCount)];
        int len = Genome.len(lenBuf, individuIdx, chosenTroll);
        Genome.setGene(buf, individuIdx, chosenTroll, len, shuffleBuf[i]);
        Genome.setLen(lenBuf, individuIdx, chosenTroll, len + 1);
    }
}
```

### Conséquences

- **Pré-cutoff** : `initRandom` génère uniquement PLANT (étape 5), HARVEST (étape 6), MINE (étape 7).
- **Post-cutoff** : comportement actuel intégral.
- **Diversité pré-cutoff** : moins de gènes par individu en moyenne (perte de la source CUT). Compensé par les 3 étapes restantes + `initWarm` (HARVEST+MINE) + `initFromPrevBest` (lui aussi sans CUT).
- **`P_SKIP_INIT`** inchangé.

---

## Section 3 — `initWarm` : split `initWarmEarly` / `initWarmLate`

**Fichier :** `GenomeOps.java`, méthode `initWarm`

### Stratégie

Avant cutoff, le pool de cibles devient `harvestCandidates ∪ ironCandidates`. Le round-robin par couche est préservé. Encodage du gène : `makeHarvest(x,y)` ou `makeMine(x,y)` selon la nature de la cellule.

Le corps existant (lignes 174-210) devient `initWarmLate` (renommage privé). On ajoute `initWarmEarly` privé. Le routing :

```java
public static void initWarm(GameState state, short[] buf, byte[] lenBuf, int individuIdx) {
    // 1-3 : reset + collect own trolls + init positions (inchangé)
    int base = Genome.offset(individuIdx, 0);
    for (int k = 0; k < Genome.SLOTS_PER_GENOME; k++) buf[base + k] = Genome.EMPTY_GENE;
    for (int j = 0; j < GameState.MAX_TROLLS; j++) Genome.setLen(lenBuf, individuIdx, j, 0);

    int ownCount = 0;
    for (int i = 0; i < state.trollCount; i++) {
        if ((state.trollPlayer[i] & 0xFF) == 0) ownTrollsBuf[ownCount++] = i;
    }
    if (ownCount == 0) return;

    for (int k = 0; k < ownCount; k++) {
        int troll = ownTrollsBuf[k];
        warmCurX[troll] = state.trollX[troll] & 0xFF;
        warmCurY[troll] = state.trollY[troll] & 0xFF;
    }

    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) {
        initWarmEarly(state, buf, lenBuf, individuIdx, ownCount);
    } else {
        initWarmLate(state, buf, lenBuf, individuIdx, ownCount);
    }
}
```

### `initWarmLate`

Corps actuel (boucle round-robin par couche sur `state.treeX/Y` filtré par `treeHealth>0`), inchangé. Utilise `warmTreeTakenBuf[]`.

### `initWarmEarly`

Pool plat (X, Y, type) construit à partir de `harvestCandidates` + `ironCandidates` :

```java
// Scratch statique (taille = MAX_TREES + MAX_IRON_CANDIDATES)
private static final int    WARM_EARLY_CAP = GameState.MAX_TREES + Genome.MAX_IRON_CANDIDATES;
private static final short[]   warmEarlyX    = new short[WARM_EARLY_CAP];
private static final short[]   warmEarlyY    = new short[WARM_EARLY_CAP];
private static final byte[]    warmEarlyType = new byte [WARM_EARLY_CAP];  // 0=HARVEST, 1=MINE
private static final boolean[] warmEarlyTaken = new boolean[WARM_EARLY_CAP];

private static void initWarmEarly(GameState state, short[] buf, byte[] lenBuf,
                                  int individuIdx, int ownCount) {
    int poolN = 0;
    for (int h = 0; h < Genome.harvestCandidateCount; h++) {
        short c = Genome.harvestCandidates[h];
        warmEarlyX[poolN]    = (short)(Genome.candX(c) & 0xFF);
        warmEarlyY[poolN]    = (short)(Genome.candY(c) & 0xFF);
        warmEarlyType[poolN] = 0;
        poolN++;
    }
    for (int i = 0; i < Genome.ironCandidateCount; i++) {
        short c = Genome.ironCandidates[i];
        warmEarlyX[poolN]    = (short)(Genome.candX(c) & 0xFF);
        warmEarlyY[poolN]    = (short)(Genome.candY(c) & 0xFF);
        warmEarlyType[poolN] = 1;
        poolN++;
    }
    for (int i = 0; i < poolN; i++) warmEarlyTaken[i] = false;
    if (poolN == 0) return;

    for (int kLayer = 0; kLayer < Genome.MAX_TARGETS_PER_TROLL; kLayer++) {
        boolean anyAssigned = false;
        for (int kT = 0; kT < ownCount; kT++) {
            int troll = ownTrollsBuf[kT];
            if ((state.trollCP[troll] & 0xFF) == 0) continue;
            int cx = warmCurX[troll], cy = warmCurY[troll];
            int hp = state.trollHP[troll] & 0xFF;

            int bestIdx = -1, bestDist = PathTable.UNREACHABLE;
            for (int i = 0; i < poolN; i++) {
                if (warmEarlyTaken[i]) continue;
                if (warmEarlyType[i] == 0 && hp == 0) continue;  // HARVEST exige HP>0
                int px = warmEarlyX[i] & 0xFF, py = warmEarlyY[i] & 0xFF;
                int d = PathTable.distance(cx, cy, px, py);
                if (d == PathTable.UNREACHABLE) continue;
                if (d < bestDist) { bestDist = d; bestIdx = i; }
            }
            if (bestIdx >= 0) {
                int bx = warmEarlyX[bestIdx] & 0xFF, by = warmEarlyY[bestIdx] & 0xFF;
                short gene = (warmEarlyType[bestIdx] == 0)
                        ? Genome.makeHarvest(bx, by)
                        : Genome.makeMine(bx, by);
                Genome.setGene(buf, individuIdx, troll, kLayer, gene);
                Genome.setLen(lenBuf, individuIdx, troll, kLayer + 1);
                warmEarlyTaken[bestIdx] = true;
                warmCurX[troll] = bx;
                warmCurY[troll] = by;
                anyAssigned = true;
            }
        }
        if (!anyAssigned) break;
    }
}
```

### Notes

- **HARVEST sur troll HP=0** : exclu (cohérent avec étape 6 de `initRandom`, `mutateInsertHarvest`).
- **MINE sur troll CP=0** : filtré par le `continue` round-robin (premier check par troll).
- **Pool=0** : early return, génome reste vide. Acceptable (1 individu sur POP_SIZE=20).
- **Allocation** : aucune. Scratchs statiques.

---

## Section 4 — `initFromPrevBest` : filtre des gènes CUT pré-cutoff

**Fichier :** `GenomeOps.java`, méthode `initFromPrevBest`

### Changement

Ajout d'un filtre avant le test `treeIndexAt`, après le court-circuit MINE :

```java
for (int k = 0; k < prevLen; k++) {
    short g = prevBuf[srcOff + k];
    if (g == Genome.EMPTY_GENE) continue;

    if (Genome.isMine(g)) {
        dstBuf[dstOff + written++] = g;
        continue;
    }

    // Pré-cutoff : pas de CUT
    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF && Genome.isCut(g)) {
        continue;
    }

    int gx = Genome.geneX(g);
    int gy = Genome.geneY(g);
    int t = state.treeIndexAt(gx, gy);
    if (t < 0) continue;
    if (state.treeHealth[t] <= 0) continue;
    dstBuf[dstOff + written++] = g;
}
```

### Notes

- **PLANT/HARVEST préservés** (l'existant garde tout sauf MINE via `treeIndexAt` — bug noté dans le design `2026-05-17` ligne 250, hors scope).
- **Tour 150 pile** : à T=150, `state.turn < CUTOFF` est faux → on garde tout (mais le prevBest issu de T=149 ne contient pas de CUT, donc no-op à ce tour précis).

---

## Section 5 — `mutateInsertCut` : early return pré-cutoff

**Fichier :** `GenomeOps.java`, méthode `mutateInsertCut` (ligne 420)

### Changement

Une ligne en tête, calquée sur `mutateInsertMine` ligne 512 :

```java
public static void mutateInsertCut(GameState state, short[] buf, byte[] lenBuf,
                                   int individuIdx, SplittableRandom rng) {
    if (state.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF) return;
    // ... reste inchangé
}
```

### Pas de rebalancement de `pickMutationKind`

Pré-cutoff perd 17% de mutations (no-op MUT_INSERT_CUT). Post-cutoff perd 8% (no-op MUT_INSERT_MINE). Trade-off explicite : on accepte la perte d'efficacité mutation pour préserver la simplicité de `pickMutationKind`. Si profilage révèle un problème de convergence pré-cutoff, on rebalance dans un second design.

---

## Section 6 — `TrollPolicy` : PLANT sans chop pré-cutoff

**Fichier :** `TrollPolicy.java`, méthode `decideForOwnTroll`, bloc PLANT (lignes 150-175)

### Décisions

Pré-cutoff :
1. **Phase 0 + arbre déjà présent sur la case PLANT** : `cursor++` (skip gène entièrement). Pas de transition vers phase 1.
2. **Phase 0 + plant action exécutée** : `cursor++` au lieu de `policyPhase = 1`. Skip phase 2.
3. **Phase 1** : inatteignable par construction. Code laissé en place pour le post-cutoff.

### Implémentation

```java
int fruit = Genome.plantFruitType(g);
int t = s.treeIndexAt(gx, gy);
boolean earlyGame = s.turn < GenomeEvaluator.TRAIN_PUSH_TURN_CUTOFF;

if (policyPhase[trollIdx] == 0 && t >= 0) {
    if (earlyGame) {
        cursor[trollIdx]++; policyPhase[trollIdx] = 0;
        continue;
    }
    policyPhase[trollIdx] = 1;
}

if (policyPhase[trollIdx] == 0) {
    int carryFruit = s.trollInventory[trollIdx * ResourceType.COUNT + fruit] & 0xFF;
    if (carryFruit == 0) {
        int shackStock = s.shackInventory[fruit];
        if (shackStock <= 0) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0;
            continue;
        }
        if (isShackAdjacent(tx, ty)) return Action.pick(trollIdx, fruit);
        return Action.move(trollIdx, closestShackAdjX(tx, ty), closestShackAdjY(tx, ty));
    }
    if (tx == gx && ty == gy) {
        if (earlyGame) {
            cursor[trollIdx]++; policyPhase[trollIdx] = 0;
        } else {
            policyPhase[trollIdx] = 1;
        }
        return Action.plant(trollIdx, fruit);
    }
    return Action.move(trollIdx, gx, gy);
}

// Phase 1 (inatteignable si earlyGame, conservée pour post-cutoff)
if (t < 0) { cursor[trollIdx]++; policyPhase[trollIdx] = 0; continue; }
if (tx == gx && ty == gy) return Action.chop(trollIdx);
return Action.move(trollIdx, gx, gy);
```

### Notes

- **`earlyGame`** : variable locale, calculée une fois par appel.
- **Intent rescue** (lignes 65-80 du fichier actuel) non touché. Reste cohérent avec PLANT pré-cutoff.
- **Transition à T=150** : un troll qui était en `policyPhase=0` sur un PLANT pré-cutoff arrive à T=150 toujours en phase 0. Si arbre sur la case → bloc (A) transitionne en phase 1 → chop. Comportement post-cutoff normal.
- **`policyPhase=1` héritée** : impossible à entrer pré-cutoff. Si elle existait via prevBest (impossible aussi car le cursor/phase est stash/restore par troll, mais pré-cutoff jamais activé), le code phase 1 prend la main et chop. Inutile en pratique.

---

## Récapitulatif des fichiers modifiés

| Fichier | Changements |
|---|---|
| `GenomeEvaluator.java` | `ALPHA_NEAR_TREE` ; bloc `nearTreeBonus` dans `fitness()` ; ajout au retour |
| `GenomeOps.java` | `initRandom` étapes 3-4 gated `turn >= CUTOFF` ; `initWarm` split en `initWarmEarly`/`initWarmLate` + scratchs `warmEarlyX/Y/Type/Taken` ; `initFromPrevBest` filtre CUT pré-cutoff ; `mutateInsertCut` early return pré-cutoff |
| `TrollPolicy.java` | Variable locale `earlyGame` ; bloc PLANT bifurqué |

`Genome.java` et `GeneticAgent.java` **inchangés**.

---

## Ce qui n'est PAS dans ce scope

- **Rebalancement de `pickMutationKind`** : on accepte les no-op (INSERT_CUT pré-cutoff, INSERT_MINE post-cutoff).
- **Tuning de `ALPHA_NEAR_TREE`** : démarre à 2.0, à fine-tuner après validation match.
- **Bug `initFromPrevBest` qui filtre les PLANT via `treeIndexAt`** : déjà noté dans `2026-05-17-mine-iron-train-push-design.md` ligne 250.
- **Effet bord post-cutoff** : la pop entre dans le régime post-cutoff sans gène CUT. Mutations + étapes 3-4 d'`initRandom` réactivées reconstruisent progressivement. Pas de "rampe" explicite — la pression GA s'en charge.
- **Distance PathTable pour le bonus near-tree** : Manhattan choisi en V1.
- **Bonus near-tree pour BANANA** : exclu par décision (palier TRAIN n'inclut pas BANANA).

---

## Risques & validation attendue

1. **Régression score pré-cutoff** : sans CUT, le bot doit-il sous-performer en bois early ? La logique : avant tour 150, le bot privilégie l'investissement (mining + plantation + harvest fruit) sur le revenu immédiat (chop bois). Le `trainPush` + `nearTreeBonus` doit dominer la perte d'ALPHA_WOOD_CARRY non-réalisé. À valider en match local (≥ 3 parties sur cartes variées).
2. **Transition à T=150** : la pop arrive sans CUT. Les premiers CUT apparaissent au bout de quelques générations via `mutateInsertCut` + `initRandom` réactivés. Risque de "trou" de quelques tours. À mesurer.
3. **Bonus near-tree double-récompense** : un PLUM proche planté rapporte `nearTreeBonus` ET, s'il devient mature dans l'horizon, le score natif. Pas un bug — c'est la pression évolutive voulue.
4. **`initWarmEarly` pool vide** : si `harvestCandidateCount + ironCandidateCount == 0`, génome vide. Acceptable.
5. **Performance** : `nearTreeBonus` ajoute `treeCount` (max ~30) opérations par appel `fitness`. Négligeable.

---

## Ordre d'implémentation suggéré

1. **Section 1** (fitness `nearTreeBonus`) — isolée, testable unitairement
2. **Section 5** (`mutateInsertCut` early return) — 1 ligne
3. **Section 4** (`initFromPrevBest` filtre CUT) — 3 lignes
4. **Section 2** (`initRandom` gate étapes 3-4) — wrap dans un `if`
5. **Section 6** (TrollPolicy PLANT skip) — branchement `earlyGame`
6. **Section 3** (`initWarm` split early/late) — plus gros refactor, fait en dernier

Chaque étape compile et passe les tests existants indépendamment.
