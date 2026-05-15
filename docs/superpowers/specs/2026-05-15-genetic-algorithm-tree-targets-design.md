# Design — Algorithme génétique pour cibles d'arbres

**Date** : 2026-05-15
**Statut** : Validé (à reviewer)
**Scope** : v1 — décision stratégique haut niveau, focus chop/drop, init aléatoire, sans simulation adverse fine.

## 1. Contexte & objectif

Le bot CG Spring 2026 (Troll Farm) utilise actuellement un agent greedy (`GreedyAgent`) qui assigne à chaque troll l'arbre vivant le plus proche encore libre. Cette stratégie myope ignore les conséquences à 5-30 tours et donne des plans sous-optimaux dès que plusieurs trolls coexistent ou que des chemins se croisent.

**Objectif** : remplacer la décision d'assignation par un algorithme génétique qui optimise, pour chaque tour, **l'ordre des cibles d'arbres assignées à chaque troll** sur un horizon de simulation court.

**Hors scope v1** :
- Décision MOVE/CHOP/DROP fine (reste implicite via une troll-policy mirror du greedy)
- Décisions PLANT, MINE, HARVEST, PICK
- TRAIN — reste géré par `GreedyAgent.maybeTrain` au tour 0
- Warm-start non-aléatoire de population
- GA persistant entre tours
- Simulation adverse plus fine que greedy

## 2. Décisions de design validées

| Choix | Valeur |
|-------|--------|
| Couverture des arbres dans le génome | Partition partielle (longueur variable par troll, certains arbres jamais assignés) |
| Horizon de simulation | 25 tours (constante tuneable) |
| Troll-policy implicite | Mirror du greedy (wood>0 → drop; sinon → MOVE/CHOP target courant; fin de liste → WAIT) |
| Modélisation adversaire dans la simu | Greedy (notre GreedyAgent appliqué côté player=1) |
| Re-planning | GA fraîche à chaque tour (init aléatoire) |
| Périmètre GA | Uniquement targets d'arbres |
| Fitness | `(scoreMe - scoreOpp) + 2.0 × Σ trollInv[wood]` |
| Référence d'un arbre | Coords `(x,y)` packées dans un `short` |
| Budget | Time budget : 40 ms / tour (10 ms marge), 900 ms tour 1 |
| Sélection | Tournoi binaire |
| Élitisme | Top-1 |
| Ratios | 70% crossover (OX par troll + repair), 30% mutation |
| Mutations | 40% swap-intra, 30% swap-inter, 20% reverse-segment, 10% delete |
| Représentation mémoire | Pool global plat ping-pong (2 buffers `short[]`) |

## 3. Architecture

### 3.1 Nouveaux composants

```
com.bmrt.cgspring2026.ga/
├── Genome.java          — constantes + accès statiques (offset, gene, len)
├── Population.java      — 2 buffers ping-pong (cur/nxt) + lengths + fitness
├── GeneticAgent.java    — entry point, boucle évolutive, deadline
├── GenomeEvaluator.java — simulation forward + calcul fitness
└── TrollPolicy.java     — traduction génome → actions par tick
```

### 3.2 Pipeline par tour

```
Player.main
  → state.readTurn(in)
  → agent.decide(state, deadline, outActions)
       ├── snapshot state (1 copyFrom)
       ├── init random population dans cur
       ├── évalue cur
       ├── while (nanoTime < deadline):
       │     ├── élitisme top-1 → nxt[0]
       │     ├── pour i=1..POP_SIZE-1:
       │     │     ├── 70% : tournoi×2 → crossover → nxt[i]
       │     │     └── 30% : tournoi → clone + mutation → nxt[i]
       │     ├── évalue nxt[1..]
       │     └── swap cur ↔ nxt
       ├── best = argmax(curFit)
       ├── TrollPolicy.fillActions(state, cur, curLen, best, freshCursor, outActions)
       └── si turn == 0 : ajouter GreedyAgent.maybeTrain
  → output actions + MSG (gen, fit, t)
```

## 4. Représentation du génome

### 4.1 Encodage

Un gène = `short` packant les coords de l'arbre cible :
```
short g = (short) (((x & 0xFF) << 8) | (y & 0xFF))
```
Slot vide = `-1`. Coord stable (vs index qui se compacte). Lookup tree à la simu via `state.treeIndexAt(x, y)`.

### 4.2 Constantes

```java
public static final int POP_SIZE              = 48;
public static final int MAX_TARGETS_PER_TROLL = 16;
public static final int SLOTS_PER_GENOME      = GameState.MAX_TROLLS * MAX_TARGETS_PER_TROLL; // 512
```

### 4.3 Layout mémoire (dans Population)

```java
short[]  bufA = new short[POP_SIZE * SLOTS_PER_GENOME];   // 48 × 512 × 2 = 48 KB
short[]  bufB = new short[POP_SIZE * SLOTS_PER_GENOME];
byte[]   lenA = new byte[POP_SIZE * GameState.MAX_TROLLS]; // 48 × 32 = 1.5 KB
byte[]   lenB = new byte[POP_SIZE * GameState.MAX_TROLLS];
double[] fitA = new double[POP_SIZE];
double[] fitB = new double[POP_SIZE];
short[]  cur, nxt;       // pointeurs sur bufA/bufB
byte[]   curLen, nxtLen;
double[] curFit, nxtFit;
```

Empreinte totale ≈ **54 KB**.

### 4.4 Accesseurs (arithmétique pure)

```java
static int offset(int i, int j) { return i * SLOTS_PER_GENOME + j * MAX_TARGETS_PER_TROLL; }
static int len(byte[] lenBuf, int i, int j) { return lenBuf[i * GameState.MAX_TROLLS + j] & 0xFF; }
static int gene(short[] buf, int i, int j, int k) { return buf[offset(i, j) + k]; }
```

### 4.5 Invariant d'unicité

Pour tout individu `i` du pool actif, aucun `(x,y)` n'apparaît dans deux gènes distincts (toutes positions et tous trolls confondus). Les opérateurs doivent préserver cet invariant.

## 5. Opérateurs

### 5.1 Initialisation aléatoire

1. Collecter `short[] availableTrees` = `(x,y)` des arbres vivants présents sur la carte.
2. Collecter `int[] ownTrollIndices` = indices des trolls `player=0` (seuls eux reçoivent des assignations).
3. Shuffle Fisher-Yates de `availableTrees` via un `SplittableRandom` partagé.
4. Pour chaque arbre dans l'ordre shuffle :
   - Avec proba `P_SKIP_INIT` (0.30) : ignorer (partition partielle, arbre jamais visité).
   - Sinon : tirer un troll au hasard parmi `ownTrollIndices` dont `len < MAX_TARGETS_PER_TROLL`. Si aucun n'a de slot libre, ignorer l'arbre.
5. Écrire `len[]` final pour chaque troll de l'individu.

### 5.2 Mutations

Une mutation par offspring, opérateur tiré dans le tableau multinomial ci-dessous :

| Opérateur | Proba | Effet |
|-----------|-------|-------|
| `swap-intra` | 0.40 | Swap 2 positions au sein d'un même troll |
| `swap-inter` | 0.30 | Swap 2 positions entre 2 trolls différents |
| `reverse-segment` | 0.20 | Reverse `[a,b]` au sein d'un troll |
| `delete` | 0.10 | Supprime une target (shift gauche, `len--`) |

Tous les opérateurs préservent l'unicité globale (aucun n'introduit de doublon).

**Pas d'opérateur d'insertion en v1** — la diversité provient de l'init partielle (≈70% des arbres) + des `delete`. À revisiter en v2 si convergence prématurée observée.

### 5.3 Crossover : OX-style par troll + repair global

Pour deux parents P1, P2 → offspring O :

1. Pour chaque troll `j` :
   - Tirer `k ∈ [0, len_P1[j]]`.
   - `O[j][0..k]` ← `P1[j][0..k]`.
   - Compléter avec les gènes de `P2[j]` non présents dans `O[j]` jusqu'à `len_P1[j]` ou épuisement.
2. **Repair global** : `boolean[] seen` (taille `width × height`) marque tous les `(x,y)` placés ; parcourir O dans l'ordre des trolls et retirer (shift gauche) tout doublon.

`seen` est un scratch buffer permanent.

### 5.4 Sélection — tournoi binaire

```java
int a = rng.nextInt(POP_SIZE);
int b = rng.nextInt(POP_SIZE);
return (fit[a] >= fit[b]) ? a : b;
```

### 5.5 Élitisme top-1

Avant de générer les offspring, le best individu courant est copié dans `nxt[0]` (1× arraycopy `SLOTS_PER_GENOME` + 1× arraycopy `MAX_TROLLS` + 1 affectation `double`). Sa fitness est reportée → pas de réévaluation.

## 6. Évaluation (fitness)

### 6.1 Scratch state unique

Un seul `GameState scratch` alloué dans `GeneticAgent`. Réutilisé pour toutes les évaluations. Chaque éval :
```java
scratch.copyFrom(state);
```

### 6.2 Boucle d'évaluation

```java
static double evaluate(GameState scratch, GameState source,
                       short[] popBuf, byte[] popLen, int idx,
                       int[] actionBuf) {
    scratch.copyFrom(source);
    int[] cursor = TrollPolicy.cursorBuf;
    for (int j = 0; j < scratch.trollCount; j++) cursor[j] = 0;
    for (int t = 0; t < HORIZON; t++) {
        int n = TrollPolicy.fillActions(scratch, popBuf, popLen, idx, cursor, actionBuf);
        Simulator.tick(scratch, actionBuf, n);
    }
    return fitness(scratch, source);
}
```

### 6.3 TrollPolicy — traduction génome → actions

Pour chaque troll `j` de player=0 :
1. Avancer `cursor[j]` tant que le gène à cette position pointe vers un arbre disparu (`treeIndexAt == -1`) ou vide.
2. Si `wood > 0` : MOVE vers la case shack-adjacente la plus proche, ou DROP si déjà adjacent.
3. Sinon, si `cursor[j] < len[j]` (target valide) :
   - Si troll sur la case de l'arbre → CHOP.
   - Sinon → MOVE vers `(treeX, treeY)`.
4. Sinon (liste épuisée et wood=0) → WAIT.

Pour chaque troll `j` de player=1 (adversaire) :
- `GreedyAgent.decideForOpponent(scratch, j, oppTreeTakenBuf)` (refactor mineur : extraire la logique greedy adversaire avec son propre buffer `tree-taken`).

### 6.4 Formule de fitness

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

`ALPHA_WOOD_CARRY = 2.0` : 1 wood porté ≈ 50% de sa valeur déposée (4 points), encourage à terminer le cycle drop.

### 6.5 Coût estimé

| Étape | Coût | Commentaire |
|-------|------|-------------|
| `copyFrom` | ~5-10 µs | 12-15 arraycopy primitifs |
| 1 tick simu | ~30-60 µs | Après les optims hot-loop récentes (T4-T8) |
| 1 éval (25 ticks) | ~0.75-1.5 ms | À mesurer via benchmark |
| Init pop (48 évals) | ~50-70 ms | OK sur tour 1 (budget 900 ms) |
| 1 génération (47 offspring) | ~35-70 ms | Probablement 0-1 génération/tour au pire |

Leviers si trop lent (mesurés via benchmark) :
- Réduire `HORIZON` à 20 ou 15.
- Réduire `POP_SIZE` à 32 ou 24.
- Vérifier les hot-paths du simulator pour optims supplémentaires.

## 7. Intégration `Player.main`

```java
GameState state    = new GameState();
GameState scratch  = new GameState();
GeneticAgent agent = new GeneticAgent(scratch);
int[] actionBuf    = new int[GameState.MAX_TROLLS + 1];
StringBuilder sb   = new StringBuilder();
boolean firstTurn  = true;

while (true) {
    long start = System.nanoTime();
    state.readTurn(in);
    long deadline = start + (firstTurn ? GeneticAgent.INIT_BUDGET_NS
                                       : GeneticAgent.TURN_BUDGET_NS);
    int n = agent.decide(state, deadline, actionBuf);
    firstTurn = false;
    // ... format output, ajouter MSG verbeux ...
    state.turn++;
}
```

Le MSG inclut `gen=` (nb de générations effectives), `fit=` (best fitness), `t=` (temps écoulé). Crucial pour fine-tuning.

`GreedyAgent` est conservé : `decideForOpponent` y reste, et un rollback complet à l'agent greedy nécessite 1 ligne de modification dans `Player.main`.

## 8. Tests

| Test | Périmètre |
|------|-----------|
| `GenomeTest` | encoding/decoding short, offset, accessors |
| `GenomeInitTest` | unicité globale + respect de `P_SKIP_INIT` |
| `MutationTest` | chaque opérateur préserve unicité et longueur attendue |
| `CrossoverTest` | OX par troll + repair → unicité, longueur ≤ parent |
| `PopulationTest` | élitisme top-1 reporté correctement |
| `TrollPolicyTest` | wood>0 → drop / target valide → chop ou move / fin → wait |
| `GenomeEvaluatorTest` | fitness positive après chop+drop dans un setup minimal |
| `GeneticAgentTest` | respect du deadline ; convergence monotone |
| `GeneticAgentRegressionTest` | fitness moyen GA ≥ greedy sur 5 cartes fixées (smoke test) |

## 9. Invariants & debug

`GenomeInvariants.check(buf, len, idx)` vérifie :
1. Pas de doublon `(x,y)` dans l'individu.
2. `len[j] ∈ [0, MAX_TARGETS_PER_TROLL]` pour chaque troll.
3. Slots après `len[j]` valent `-1`.
4. Tous les `(x,y)` sont in-bounds.

Appelé sous `assert` après chaque opérateur — zéro coût en prod.

## 10. Benchmark perf

`src/test/java/.../bench/GeneticAgentBench.java` :
- Fixture mid-game réaliste.
- Mesure générations / 40 ms, µs / évaluation, µs / tick simulation.
- Pas de JMH ; warmup 1000 itérations + mesure sur 5000, `System.nanoTime()`.
- Sortie console pour fine-tuning.

## 11. Constantes tuneables

Toutes regroupées en haut de `GeneticAgent` :

```java
public static final int    POP_SIZE              = 48;
public static final int    HORIZON               = 25;
public static final int    MAX_TARGETS_PER_TROLL = 16;
public static final double ALPHA_WOOD_CARRY      = 2.0;
public static final long   TURN_BUDGET_NS        = 40_000_000L;
public static final long   INIT_BUDGET_NS        = 900_000_000L;
public static final double P_CROSSOVER           = 0.70;
public static final double P_SKIP_INIT           = 0.30;
public static final double P_MUT_SWAP_INTRA      = 0.40;
public static final double P_MUT_SWAP_INTER      = 0.30;
public static final double P_MUT_REVERSE         = 0.20;
public static final double P_MUT_DELETE          = 0.10;
```

## 12. Hors scope v1 (à reporter en v2)

- Warm-start non-aléatoire (init basée sur greedy ou heuristique de proximité).
- Simulation adverse plus fine (GA adverse, heuristique multi-tours).
- GA persistant cross-tour (seeding depuis pop précédente).
- Inclusion de TRAIN/PLANT dans le génome.
- Opérateur d'insertion en mutation (si convergence prématurée détectée).
- Tuning automatique via la skill `fine-tune`.
