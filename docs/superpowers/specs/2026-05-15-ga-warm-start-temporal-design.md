---
title: GA — Warm-start temporel (seed du best du tour précédent)
date: 2026-05-15
status: design validé
---

# GA — Warm-start temporel

## Contexte

`GeneticAgent` reconstruit toute sa population à chaque tour. Aujourd'hui :

- Slot 0 = `GenomeOps.initWarm` (heuristique déterministe round-robin "plus proche par couche").
- Slots 1..19 = `GenomeOps.initRandom` (assignation aléatoire avec skip 30 %).

Cette init ne porte aucune mémoire du tour précédent. Symptôme observé en partie : un troll qui s'était déjà engagé vers un arbre `T` au tour `N-1` peut se voir réassigner `T` à un autre troll au tour `N` à cause du round-robin de `initWarm` (les positions courantes des trolls changent, l'ordre de sélection change, et la cible bascule). Le troll initial a alors perdu plusieurs ticks de déplacement.

Le warm-start "structurel" (`initWarm`) optimise la cohérence intra-tour mais pas la **cohérence inter-tours**. C'est ce manquant que cette spec adresse.

## Objectif

Ajouter une **persistance temporelle** : sauvegarder le génome du meilleur individu à la fin de chaque `decide`, puis le réinjecter au slot 0 de la population au tour suivant après nettoyage des arbres morts.

Non-objectifs :

- Ne PAS supprimer `initWarm` — il reste comme seed structurel au slot 1.
- Ne PAS modifier sélection / crossover / mutation / évaluation / `TrollPolicy`.
- Ne PAS introduire d'allocation dans le hot path.
- Ne PAS stocker plus d'un génome (top-N) — un seul suffit pour traiter le symptôme.

## Décisions de conception

1. **Stash en champ d'instance** de `GeneticAgent` (`prevBestBuf` + `prevBestLen` + flag `hasPrevBest`). Pas de réutilisation des buffers `Population` (fragile vis-à-vis de `swap`/`reset`).
2. **Allocation slot** : slot 0 = prev-best nettoyé ; slot 1 = `initWarm` ; slots 2..19 = `initRandom`. Au tour 0 (`hasPrevBest = false`), fallback : slot 0 = `initWarm`, slots 1..19 = `initRandom` (comportement actuel).
3. **Nettoyage = compactage simple** : on parcourt chaque troll, on retire les gènes dont l'arbre n'existe plus à la cellule (`state.treeIndexAt(gx, gy) < 0`), on garde l'ordre des survivants. Aucune complétion heuristique (sinon on mélange deux signaux et on annule la diversification apportée par `initRandom`).
4. **Stash en fin de `decide`**, juste après le calcul de `lastBestIdx`. Le génome stashé est exactement celui qui pilote les actions du tour courant.
5. **Déterministe**, aucun RNG impliqué dans `initFromPrevBest`.

## Architecture

### Fichiers touchés

- `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
  Ajout de la méthode statique `initFromPrevBest(...)`.

- `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
  Ajout des champs `prevBestBuf`, `prevBestLen`, `hasPrevBest`. Modification de `decide` (stash en fin de méthode) et de `initPopulation` (branchement selon `hasPrevBest`).

- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsPrevBestTest.java` *(nouveau)*
  Tests JUnit 5 sur `initFromPrevBest`.

- `src/test/java/com/bmrt/cgspring2026/ga/GeneticAgentPrevBestTest.java` *(nouveau)*
  Tests d'intégration sur la persistance entre `decide` consécutifs. Nécessite `prevBestBuf`/`prevBestLen` package-private.

### Signature

```java
public static void initFromPrevBest(GameState state,
                                    short[] prevBuf,    // taille SLOTS_PER_GENOME
                                    byte[]  prevLenBuf, // taille MAX_TROLLS
                                    short[] dstBuf,
                                    byte[]  dstLenBuf,
                                    int     individuIdx);
```

Le stash est lu comme un buffer "à plat" (offset par troll `j` = `j * MAX_TARGETS_PER_TROLL`). La destination est lue/écrite via les helpers `Genome.offset` / `Genome.setLen`.

### Nouveaux champs `GeneticAgent`

```java
final short[] prevBestBuf = new short[Genome.SLOTS_PER_GENOME];
final byte[]  prevBestLen = new byte[GameState.MAX_TROLLS];
boolean       hasPrevBest = false;
```

Visibilité **package-private** (et non `private`) pour permettre l'inspection directe par `GeneticAgentPrevBestTest` (tests 8-10) sans introduire de getters publics. Cohérent avec le style des autres champs visibles du package (`pop` est déjà package-private).

## Algorithme `initFromPrevBest`

```
initFromPrevBest(state, prevBuf, prevLenBuf, dstBuf, dstLenBuf, individuIdx):
  // 1. Reset du segment de destination
  base = Genome.offset(individuIdx, 0)
  for k in 0..SLOTS_PER_GENOME-1: dstBuf[base + k] = EMPTY_GENE
  for j in 0..MAX_TROLLS-1:       Genome.setLen(dstLenBuf, individuIdx, j, 0)

  // 2. Compactage troll par troll
  for j in 0..MAX_TROLLS-1:
    prevLen = prevLenBuf[j] & 0xFF
    written = 0
    srcOff = j * MAX_TARGETS_PER_TROLL
    dstOff = Genome.offset(individuIdx, j)
    for k in 0..prevLen-1:
      g = prevBuf[srcOff + k]
      if g == EMPTY_GENE: continue                  // défensif
      gx = Genome.geneX(g); gy = Genome.geneY(g)
      t = state.treeIndexAt(gx, gy)
      if t < 0: continue                            // cellule plus un arbre vivant
      if state.treeHealth[t] <= 0: continue         // défensif (treeIndexAt couvre déjà)
      dstBuf[dstOff + written++] = g
    Genome.setLen(dstLenBuf, individuIdx, j, written)
```

## Intégration dans `GeneticAgent`

### `decide(state, deadlineNs, outActions)`

À la fin, après `lastBestIdx = argmax(pop.curFit); lastBestFitness = pop.curFit[lastBestIdx];` :

```java
System.arraycopy(pop.cur,    Genome.offset(lastBestIdx, 0),
                 prevBestBuf, 0, Genome.SLOTS_PER_GENOME);
System.arraycopy(pop.curLen, Genome.lenOffset(lastBestIdx, 0),
                 prevBestLen, 0, GameState.MAX_TROLLS);
hasPrevBest = true;
```

### `initPopulation(state)`

```java
if (hasPrevBest) {
    GenomeOps.initFromPrevBest(state, prevBestBuf, prevBestLen,
                               pop.cur, pop.curLen, 0);
    GenomeOps.initWarm(state, pop.cur, pop.curLen, 1);
    for (int i = 2; i < Genome.POP_SIZE; i++) {
        GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
    }
} else {
    GenomeOps.initWarm(state, pop.cur, pop.curLen, 0);
    for (int i = 1; i < Genome.POP_SIZE; i++) {
        GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
    }
}
```

## Invariants

L'individu produit au slot 0 satisfait `GenomeInvariants.check` :

- `len[j] ∈ [0, MAX_TARGETS_PER_TROLL]` : `written` borné par `prevLen ≤ MAX_TARGETS_PER_TROLL`.
- Pas de gène vide dans `[0, len[j])` : filtrage avant écriture, `written++` couplé.
- Pas de gène actif au-delà de `len[j]` : reset complet du segment au début.
- Unicité d'arbre par individu : le génome source l'avait, le compactage ne crée pas de doublon.
- Coordonnées valides : héritées du génome source, jamais réécrites.

## Edge cases

| Cas | Comportement |
|---|---|
| Turn 0 (`hasPrevBest = false`) | Fallback : slot 0 = `initWarm`, slots 1..19 = `initRandom`. Comportement actuel inchangé. |
| Tous les arbres du stash sont morts | Pour chaque troll, `len[j] = 0`. Slot 0 devient un individu vide. Slot 1 (`initWarm`) reste un seed dense. |
| Un troll spécifique avait `len = 0` dans le stash | Reste à `0` côté destination. |
| Le tout premier gène d'un troll a été choppé au tour N-1 | Cas central du symptôme : il est retiré, le troll part directement sur le 2e gène. |
| Deux gènes consécutifs morts au milieu de la séquence | Tous retirés, séquence compactée contiguë. |
| Le nombre de trolls own a changé entre tours | Hors spec (les trolls ne meurent jamais — voir CLAUDE.md memory). Pas de garde. |
| `prevLen[j] > MAX_TARGETS_PER_TROLL` | Ne peut pas arriver (le génome source satisfaisait l'invariant). Pas de garde. |

## Coût

Par appel `initFromPrevBest` :

- Reset segment : `SLOTS_PER_GENOME + MAX_TROLLS = 64 + 4 = 68` writes.
- Compactage : ≤ `MAX_TROLLS × MAX_TARGETS_PER_TROLL = 60` itérations, chaque itération = 1 `treeIndexAt` (O(1)) + quelques array accesses.

Total ≈ 150 opérations. Négligeable face au budget 45 ms.

Le stash en fin de `decide` = 2 `System.arraycopy` (64 shorts + 4 bytes) ≈ 100 ns.

**Aucune allocation hot-path.** Tous les buffers sont des champs d'instance final pré-alloués.

## Tests

### `GenomeOpsPrevBestTest.java` (style minimaliste, nom = spec)

1. `initFromPrevBest_compactsDeadTreeInMiddle` — prevBuf troll 0 = `[T_alive, T_dead, T_alive2]` → `len=2`, ordre `[T_alive, T_alive2]`.
2. `initFromPrevBest_dropsFirstGeneWhenTargetChopped` — prevBuf = `[T_dead, T_alive]` → `len=1`, gène `[T_alive]`.
3. `initFromPrevBest_keepsTailIntact` — prevBuf = `[T1, T2, T3]` tous vivants → `len=3`, ordre préservé.
4. `initFromPrevBest_emptyWhenAllTreesDead` — prevBuf = `[T_dead, T_dead]` → `len=0`, segment `EMPTY_GENE`.
5. `initFromPrevBest_emptyPrevLenProducesEmptySegment` — `prevLen[j]=0` → `len[j]=0`.
6. `initFromPrevBest_satisfiesInvariants` — `GenomeInvariants.check` passe sur scénario réaliste.
7. `initFromPrevBest_doesNotTouchOtherIndividuals` — slot 1 préalablement rempli reste intact après écriture au slot 0.

### `GeneticAgentPrevBestTest.java`

8. `decide_stashesBestGenomeBetweenTurns` — après un `decide`, `prevBestBuf`/`prevBestLen` reflètent le génome de `lastBestIdx`.
9. `decide_secondTurn_seedsSlot0FromPrevBest` — deux `decide` consécutifs ; au début du tour 2, le slot 0 reflète le compactage du best du tour 1.
10. `decide_firstTurn_doesNotUsePrevBest` — nouveau `GeneticAgent`, slot 0 du premier tour = `initWarm` seul (`hasPrevBest=false` initialement).

### Tests existants

Tous doivent continuer à passer sans modification (notamment `ga: smoke regression test`, `ga: bench harness mid-game`).

## Hors-scope

- Stash multi-individus (top-N) — extension possible si la mono-mémoire stagne.
- Complétion heuristique du seed compacté (option B écartée pendant le brainstorming).
- Modification de l'élitisme top-1 — l'élite reste choisie sur fitness.
- Garde anti-corruption sur `len[j]` du stash — l'invariant amont le garantit.
- Déduplication slot 0 / slot 1 si les deux convergent — coût rare, diversité des 18 random suffit.

## Critères de succès

1. Compilation OK, tests existants passent.
2. 10 nouveaux tests passent.
3. `ga: smoke regression test` continue à donner `fitness_GA ≥ fitness_greedy`.
4. Sur le bench mi-game (40 ms), à scénario identique, la fitness du best au tour 0 (juste après `initPopulation` + première évaluation) est `≥` à celle observée sans seed temporel. À mesurer manuellement.
5. Pas de régression sur le compteur `gen/s` (overhead < 1 % du budget).
6. Diff lisible : aucune allocation hot-path ajoutée (vérifiable par revue).

## Risques résiduels

- **Diversité** : 1 slot random en moins (18 au lieu de 19). À surveiller via fitness moyen vs max sur le bench. Si trop convergent : pousser les `P_MUT_*` dans une future itération.
- **Génome stagnant** : si le prev-best est mauvais, on le réinjecte. L'élitisme top-1 le sortira dès qu'un meilleur émerge. Pas de mécanisme correctif explicite — non nécessaire.
