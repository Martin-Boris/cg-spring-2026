---
title: GA — Action PLANT (compound) dans le génome
date: 2026-05-16
status: design validé
---

# GA — Action PLANT (compound) dans le génome

## 1. Contexte & objectif

Le GA actuel (`GeneticAgent`) ne sait optimiser que des **séquences de cibles d'arbres existants** (gènes TARGET). Quand la carte se vide d'arbres rentables, le bot n'a plus de meilleure action que `WAIT`, alors que le brief autorise `PLANT` (1 fruit → 1 arbre) pour créer de nouvelles cibles. Wood = 4 pts/unité, donc même un arbre planté size 1 (chop = 1 wood = 4 pts) rapporte +3 pts net par fruit consommé.

**Objectif v1** : étendre le génome avec un nouveau type de gène **PLANT compound** qui encode le cycle complet `PICK → MOVE → PLANT → CHOP → DROP` sur une cellule cible proche du shack.

**Hors scope v1** :
- HARVEST (récolte de fruits sur arbres mûrs) — possiblement v2.
- TRAIN dans le génome — reste géré au tour 0 par le greedy.
- Multi-PLANT (plusieurs PLANTs sur la même cellule par individu).
- Plant hors zone "max 2 du shack" (e.g. adjacent à water à distance arbitraire).
- Bonus de fitness sur fruits-en-carry (réserve documentée).

## 2. Décisions de design validées

| Choix | Valeur | Justification |
|-------|--------|---------------|
| Type de gène PLANT | Compound (cycle complet pick→plant→chop→drop) | Score direct = signal de fitness fort, convergence rapide |
| Zone de PLANT | Manhattan ≤ 2 du shack, GRASS uniquement | Trajets courts vu carryCap=1, garde l'espace de recherche petit |
| Pré-calcul candidats | One-shot au tour 1, table statique partagée | Pas de revalidation par tick/mutation |
| Encodage gène | `short` 16 bits, flag bit 15 (TARGET=0/PLANT=1) | Backward-compat avec TARGET existant |
| State machine troll | `byte[] policyPhase` par troll, valeurs {0,1} | Distingue pre-plant et post-plant ; reset par évaluation |
| Lazy phase init | Si arbre déjà sur cellule au démarrage du gène → phase=1 | Le PLANT devient un chop sans pick (gracieux) |
| Abort si shack vide | Cursor avance sans WAIT perpétuel | Évite que la simu se fige sur ressources impossibles |
| Invariant unicité | Au plus 1 TARGET et 1 PLANT par cellule | Permet la stratégie chop-then-replant sur cellule pré-shack |
| Mutation `insert-plant` | Nouvel opérateur, proba 0.15 | Mécanisme dédié pour introduire PLANTs en cours d'évolution |
| `P_PLANT_INIT` | 0.30 par troll à `initRandom` | Diversité initiale sans saturer la population |
| Fitness | Inchangée | La formule actuelle capte naturellement le ROI du cycle |

## 3. Architecture

### 3.1 Fichiers impactés

```
src/main/java/com/bmrt/cgspring2026/ga/
├── Genome.java                MOD  (helpers PLANT, accesseurs re-masqués, plantCandidates statiques)
├── GenomeOps.java             MOD  (init avec PLANTs, mutation insert-plant)
├── TrollPolicy.java           MOD  (sub-state machine PLANT + policyPhase[])
├── GenomeEvaluator.java       MOD  (init/reset de policyPhase[] avant simu)
├── GeneticAgent.java          MOD  (init des plantCandidates au premier decide)
└── GenomeInvariants.java      MOD  (invariant (cell, isPlant))

src/test/java/com/bmrt/cgspring2026/ga/
├── PlantCandidatesTest.java        NEW
├── TrollPolicyPlantTest.java       NEW
├── InsertPlantMutationTest.java    NEW
└── (tests existants étendus)       MOD
```

### 3.2 Pipeline inchangé

Le pipeline `Player.main → state.readTurn → agent.decide → output` reste identique. Le seul ajout côté `GeneticAgent` est l'init one-shot de `plantCandidates` au premier `decide`.

## 4. Encodage du gène

### 4.1 Layout `short` (16 bits)

```
bit 15  | bits 13-14 | bits 8-12 | bits 0-7
 flag   | fruitType  |     x     |    y
PLANT   | PLUM..BAN  |   0..21   |  0..10
(1)     |  (0..3)    |  (5 bits) | (8 bits, marge)
```

- **TARGET** : `flag=0, fruitType=0` → equivalent au layout précédent (high byte = x, low byte = y). **Backward-compatible**.
- **PLANT** : `flag=1`, `fruitType ∈ [0,3]`, `(x,y)` = cellule cible.
- **EMPTY_GENE = -1** (0xFFFF) : convention "slots après `len[j]` sont EMPTY" préservée.

### 4.2 Helpers `Genome`

```java
// Constructeurs
static short makeTarget(int x, int y);                          // inchangé
static short makePlant(int x, int y, int fruitType);            // nouveau

// Décodage
static boolean isPlant(short g);
static int     plantFruitType(short g);                          // bits 13-14
static int     geneX(short g);                                   // bits 8-12 (masque adapté)
static int     geneY(short g);                                   // bits 0-7  (inchangé)

// Inchangé : offset(i,j), gene(buf,i,j,k), setLen(...), lenOffset(...), EMPTY_GENE
```

### 4.3 Plant candidates (statique)

```java
public static short[] plantCandidates;     // coord packing : (x << 8) | y — pas un gène
public static int     plantCandidateCount;

public static void initPlantCandidates(GameState state);

// Helpers pour extraire les coords d'une entrée plantCandidates
static int candX(short c) { return (c >> 8) & 0xFF; }
static int candY(short c) { return c & 0xFF; }
```

`plantCandidates` stocke des coords packées (high byte = x, low byte = y), pas des gènes complets — il n'y a ni flag ni fruitType à ce niveau. Le fruit est choisi à l'init/mutation lors de la création du gène PLANT.

**Algorithme** :
```
plantCandidateCount = 0
for x in [shackMeX - 2 .. shackMeX + 2]:
  for y in [shackMeY - 2 .. shackMeY + 2]:
    if out-of-bounds: continue
    if |x - shackMeX| + |y - shackMeY| > 2: continue
    if tiles[y * width + x] != GRASS: continue
    plantCandidates[plantCandidateCount++] = (short) ((x << 8) | y)
```

Au plus 12 entrées (croix Manhattan rayon 2). Appelé une fois au premier `decide` par `GeneticAgent`. La carte étant statique, cette table reste valable toute la partie.

## 5. TrollPolicy — sub-state machine

### 5.1 Nouvel état

```java
// dans TrollPolicy (champs statiques ou via Population)
public static byte[] policyPhase = new byte[GameState.MAX_TROLLS];
```

- Reset à 0 pour tous les trolls **au début de chaque `evaluate()`** (à côté du reset de `cursor[]`).
- Valeurs : `0` = "pre-plant" (need pick + plant), `1` = "post-plant" (need chop until dead).
- Pour les TARGET genes, le champ est **ignoré** (lu mais non utilisé).

### 5.2 Pseudo-code `fillActions` (un troll j, player=0)

```
// === DROP_RULE (priorité absolue) ===
if trollInventory[j*6 + WOOD] > 0:
   if adjacent_to_shack(troll[j]):
      action = DROP(j)
   else:
      action = MOVE(j, nearest_shack_adj_cell)
   return action

// === Liste épuisée ===
if cursor[j] >= len[j]:
   action = WAIT(j); return

short g = gene at cursor[j]

// === TARGET branch ===
if not isPlant(g):
   gx = geneX(g); gy = geneY(g)
   t = state.treeIndexAt(gx, gy)
   if t < 0:
      cursor[j]++; policyPhase[j] = 0;
      retry policy from start (or recurse)
   if troll on (gx, gy):
      action = CHOP(j)
   else:
      action = MOVE(j, gx, gy)
   return

// === PLANT branch ===
gx = geneX(g); gy = geneY(g); fruit = plantFruitType(g)
t = state.treeIndexAt(gx, gy)

// Lazy phase init : arbre deja sur cellule
if policyPhase[j] == 0 and t >= 0:
   policyPhase[j] = 1

if policyPhase[j] == 0:
   // Need fruit + plant
   if trollInventory[j*6 + fruit] == 0:
      if shackInventory[0*6 + fruit] == 0:
         cursor[j]++; policyPhase[j] = 0;
         retry policy                              // ABORT
      if adjacent_to_shack(troll[j]):
         action = PICK(j, fruit)
      else:
         action = MOVE(j, nearest_shack_adj_cell)
      return
   if troll on (gx, gy):
      action = PLANT(j, fruit)
      policyPhase[j] = 1
      return
   action = MOVE(j, gx, gy); return

// policyPhase[j] == 1 : post-plant, chop until dead
if t < 0:
   cursor[j]++; policyPhase[j] = 0;
   retry policy
if troll on (gx, gy):
   action = CHOP(j)
else:
   action = MOVE(j, gx, gy)
return
```

**"retry policy"** = continuer dans la même fonction `fillActions`, sans re-allouer ; en pratique une boucle `while (true)` qui ne sort que via un `return action`. Le nombre de retries par tick est borné par `len[j]` (avancement strict du cursor).

### 5.3 Cas particuliers

| Scénario | Comportement |
|----------|--------------|
| Cellule PLANT a déjà un arbre au démarrage | `lazy phase init` → phase=1, chop direct. Fruit non consommé. |
| Shack vide du fruit demandé | Cursor avance immédiatement. Pas de WAIT perpétuel. |
| Troll porte du wood en phase 0 PLANT | DROP_RULE prioritaire → vide d'abord, puis pick au tick suivant. |
| Cellule PLANT devient arbre pendant la simu (par un autre troll) | Phase reste 0, mais lazy init au tick suivant transforme en phase=1. Le fruit (si déjà picked) reste en carry et sera utilisé pour un futur gène PLANT. |
| Cellule PLANT garde un arbre vivant en fin de horizon | Wood en carry comptabilisé par `ALPHA_WOOD_CARRY` si chop partiel a eu lieu. |

## 6. Init / Mutation / Crossover

### 6.1 `initRandom` étendu

Après allocation des TARGETs (logique actuelle, partition partielle des arbres vivants) :

```
// Scratch table partagée pendant l'init de cet individu (équivalent du seen actuel)
boolean[] seenPlant = scratch(width * height)
reset seenPlant to false

for each troll j with player=0 and len[j] < MAX_TARGETS_PER_TROLL:
   if rng.nextDouble() < P_PLANT_INIT:
      tries = 0
      while tries < plantCandidateCount:
         c = plantCandidates[rng.nextInt(plantCandidateCount)]
         cx = (c >> 8) & 0xFF; cy = c & 0xFF
         if not seenPlant[cy * width + cx]:
            fruit = rng.nextInt(4)
            append PLANT(cx, cy, fruit) to troll j's list
            seenPlant[cy * width + cx] = true
            break
         tries++
```

Le scratch `seenPlant` est réutilisable entre individus (reset au début de chaque init individu).

Notes :
- Pas de check "shack has fruit" : la simulation décide. Évite biais déterministe.
- `initWarm` : **inchangé en v1**. Si la fitness le justifie en mesure, une variante "warm-plant" pourra être ajoutée en v2.
- `initFromPrevBest` : **garde tous les PLANT genes intacts**. Le filtrage "tree-dead" ne s'applique qu'aux TARGETs.

### 6.2 Mutation — nouvel opérateur `insert-plant`

| Opérateur | Proba | Effet |
|-----------|-------|-------|
| `swap-intra` | **0.35** (était 0.40) | Inchangé |
| `swap-inter` | **0.25** (était 0.30) | Inchangé |
| `reverse-segment` | **0.15** (était 0.20) | Inchangé |
| `delete` | **0.10** | Inchangé |
| **`insert-plant`** | **0.15** | Voir ci-dessous |

`insert-plant` :
```
candidate trolls = trolls player=0 with len[j] < MAX_TARGETS_PER_TROLL
if candidate trolls is empty: no-op
j = random candidate troll
position = random in [0, len[j]]
// Pick a plant candidate not yet used as PLANT in this individual
tries = 0
while tries < plantCandidateCount:
   c = plantCandidates[rng.nextInt(plantCandidateCount)]
   if not already PLANT(c.x, c.y) in this individual: break
   tries++
if tries == plantCandidateCount: no-op
fruit = rng.nextInt(4)
shift right slots [position, len[j]) by 1
write PLANT(c.x, c.y, fruit) at position
len[j]++
```

Les autres opérateurs (`swap-*`, `reverse`, `delete`) **fonctionnent sans modification** sur les shorts ; ils permutent/suppriment sans regarder le flag.

### 6.3 Crossover OX — repair avec dédup duale

```java
boolean[] seenTarget = new boolean[width * height];   // ~242 bytes
boolean[] seenPlant  = new boolean[width * height];   // ~242 bytes
```

Repair pendant l'OX : pour chaque gène lu dans l'ordre offspring :
- `key = isPlant(g) ? seenPlant : seenTarget`, idx = `geneY(g) * width + geneX(g)`
- si `key[idx]` est `true` → drop le gène (shift gauche)
- sinon `key[idx] = true`, garder

Reset des deux tables au début de chaque crossover.

**Conséquence** : `[TARGET(c), PLANT(c, fruit)]` survit (clés différentes). `[PLANT(c, A), PLANT(c, B)]` se dédoublonne sur le premier (l'ordre OX préserve P1 d'abord).

## 7. Fitness — inchangée

```java
double fitness(GameState finalState, GameState initialState) {
    int scoreMe  = finalState.score(0);
    int scoreOpp = finalState.score(1);
    int woodCarryMe = 0;
    for (int i = 0; i < finalState.trollCount; i++) {
        if ((finalState.trollPlayer[i] & 0xFF) != 0) continue;
        woodCarryMe += finalState.trollInventory[i * ResourceType.COUNT + ResourceType.WOOD] & 0xFF;
    }
    return (scoreMe - scoreOpp) + ALPHA_WOOD_CARRY * woodCarryMe;
}
```

Le cycle PLANT est valorisé naturellement :
- PICK : shack[fruit] -= 1 → `scoreMe -= 1`.
- PLANT : fruit consommé (déjà sorti du shack).
- CHOP : `wood_in_carry += size` → `+ALPHA_WOOD_CARRY × size`.
- DROP : shack[wood] += size → `scoreMe += 4 × size`.
- Net pour size=1 : `-1 + 4 = +3` pts (deductibles + carryAlpha en cours de horizon).

**Réserve documentée** : si le GA boude les PLANTs à cause du `-1` initial sans contrepartie dans le horizon, ajouter `ALPHA_FRUIT_CARRY × Σ fruits_in_carry(player=0)` en v2. **Pas implémenté en v1.**

## 8. Invariants

`GenomeInvariants.check(buf, len, idx)` vérifie :

1. Pas de doublon `(x, y, isPlant)` dans l'individu (`seenTarget`/`seenPlant` séparées).
2. `len[j] ∈ [0, MAX_TARGETS_PER_TROLL]` pour chaque troll.
3. Slots après `len[j]` valent `EMPTY_GENE`.
4. Pour chaque gène (en lisant uniquement `[0, len[j])`) :
   - `(x, y)` in-bounds (`x ∈ [0, width)`, `y ∈ [0, height)`).
   - Si `isPlant(g)` : `fruitType ∈ [0, 3]` ET `(x, y) ∈ plantCandidates`.

**Note** : l'invariant **ne vérifie pas** qu'un TARGET référence un arbre vivant. Un TARGET dont l'arbre a disparu en cours de simulation reste structurellement valide ; la `TrollPolicy` le saute via `treeIndexAt < 0` en avançant le cursor. L'invariant capture les contraintes structurelles, pas l'état dynamique.

Appelé sous `assert` après chaque opérateur — zéro coût en prod.

## 9. Tests

### 9.1 Nouveaux fichiers

**`PlantCandidatesTest`** :
1. `manhattanDistance_filtersCorrectly` — shack centré, set = croix rayon 2.
2. `excludesNonGrassTiles` — WATER/ROCK/IRON exclus.
3. `excludesShackItself` — shack pas inclus.
4. `emptyWhenShackIsolated` — shack entouré obstacles → set vide.

**`TrollPolicyPlantTest`** :
5. `phase0_picksFruitFromShack` — pas de fruit en carry, shack a stock, troll adjacent → PICK.
6. `phase0_movesToShackWhenNotAdjacent` — pas de fruit, troll au loin → MOVE.
7. `phase0_abortsWhenShackEmpty` — shack vide du fruit demandé → cursor avance.
8. `phase0_plantsWhenOnTarget` — fruit en carry, troll sur cellule → PLANT, phase=1.
9. `lazyPhaseInit_treeAlreadyOnCell` — arbre préexistant → phase=1 sans pick.
10. `phase1_chopsUntilDead` — arbre alive sur cellule → CHOP.
11. `phase1_advancesCursorWhenTreeDead` — arbre dead → cursor++ + phase=0.
12. `postDrop_advancesGene` — wood > 0 + adjacent → DROP, gène complet.
13. `dropRulePriority_evenInPhase0` — wood + phase=0 → DROP avant pick.

**`InsertPlantMutationTest`** :
14. `insertsPlantAtRandomPosition` — shift right correct, len++.
15. `noopWhenNoCandidates` — `plantCandidateCount==0` → no-op silencieux.
16. `noopWhenAllTrollsFull` — `len[j] == MAX_TARGETS_PER_TROLL` ∀j → no-op.
17. `skipsCellAlreadyPlantInIndividual` — pas de doublon (cell, PLANT).
18. `preservesTargetAtSameCell` — autorise PLANT sur cellule déjà target.

### 9.2 Tests existants à étendre

- `GenomeTest` : roundtrip `makePlant`/`isPlant`/`plantFruitType` ; backward-compat TARGET.
- `GenomeInitTest` : `P_PLANT_INIT` respecté statistiquement ; pas de doublon (cell, isPlant).
- `MutationTest` : opérateurs existants n'introduisent pas de doublon (cell, isPlant).
- `CrossoverTest` : `[TARGET(c), PLANT(c, fruit)]` survit au repair (dédup duale).
- `GenomeInvariantsTest` : nouveau invariant.
- `GeneticAgentTest` : pipeline OK avec PLANTs présents.
- `GeneticAgentRegressionTest` : `fitness_GA(avec PLANT) ≥ fitness_GA(sans PLANT)` sur 5 cartes fixées (smoke test).

## 10. Constantes tuneables

```java
public static final double P_PLANT_INIT       = 0.30;
public static final double P_MUT_INSERT_PLANT = 0.15;

// Re-balance mutation distribution (somme = 1.00)
public static final double P_MUT_SWAP_INTRA   = 0.35;
public static final double P_MUT_SWAP_INTER   = 0.25;
public static final double P_MUT_REVERSE      = 0.15;
public static final double P_MUT_DELETE       = 0.10;
// + P_MUT_INSERT_PLANT = 0.15
```

`ALPHA_FRUIT_CARRY` documenté comme réserve, **non ajouté en v1**.

## 11. Coût attendu

- `initPlantCandidates` : O(25) une fois → négligeable.
- TrollPolicy par tick : 1 lookup `treeIndexAt` + 1 lookup `shackInventory` + branches. ~+10 ns / troll / tick par rapport au TARGET-only.
- Mutation `insert-plant` : O(MAX_TARGETS_PER_TROLL) shift + 1-2 tirages. ~100 ns.
- Crossover repair avec dédup duale : 2 resets `boolean[width*height]` + 1 lookup par gène. ~+200 ns par crossover (négligeable face à 1 ms d'évaluation).

**Pas d'allocation hot-path ajoutée.**

## 12. Critères de succès

1. Compilation OK, tests existants passent (modulo extensions documentées).
2. ~18 nouveaux tests passent.
3. `ga: smoke regression test` : `fitness_GA(avec PLANT) ≥ fitness_GA(sans PLANT) - 5%` (tolérance bruit) sur 5 cartes fixées.
4. Bench mid-game : gen/s reste ≥ 90% du baseline sans PLANT (overhead acceptable).
5. Au moins une partie complète tour 1 → tour 300 où le bot émet `≥ 3 PLANT` au total (validation in-vivo que la GA exploite la feature).
6. Diff lisible : pas d'allocation hot-path ajoutée (vérifiable par revue).

## 13. Risques résiduels

| Risque | Mitigation |
|--------|------------|
| GA boude les PLANTs si shack souvent vide | `P_PLANT_INIT` + `P_MUT_INSERT_PLANT` injectent en permanence ; fitness validera ou pas |
| Cycle plant→chop ne finit pas dans horizon=25 | Carry=1 + chop court → cycle ≤ 12 turns, OK ; sinon réserve `ALPHA_FRUIT_CARRY` |
| Shack pas près de l'eau → croissance trop lente | Chop immédiat de size=1 reste rentable (+3 pts net). À surveiller en mesure. |
| Coût TrollPolicy augmente trop | Mesure du bench requise ; fallback : court-circuit `if !isPlant(g)` en début de boucle |
| Régression sur fitness vs greedy | Smoke test obligatoire avant merge |

## 14. Hors-scope v1 (à reporter)

- HARVEST genes (récolte de fruits sur arbres mûrs).
- Multi-PLANT sur même cellule (plusieurs cycles consécutifs).
- PLANT hors zone "max 2 du shack" (e.g. adjacent water à distance arbitraire).
- `ALPHA_FRUIT_CARRY` bonus de fitness.
- Variante `initWarm` qui injecte des PLANTs déterministes.
- TRAIN dans le génome.
