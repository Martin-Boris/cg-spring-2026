---
title: GA — Warm-start de la population initiale
date: 2026-05-15
status: design validé
---

# GA — Warm-start de la population initiale

## Contexte

L'agent génétique (`GeneticAgent`) construit à chaque tour une population de `POP_SIZE = 20` individus via `GenomeOps.initRandom`. Chaque individu code, pour chacun des trolls own, une séquence ordonnée d'au plus `MAX_TARGETS_PER_TROLL = 10` arbres cibles (cellules `x,y` encodées sur un `short`). La contrainte structurelle est qu'un arbre apparaît au plus une fois dans un individu donné (vérifiée par `GenomeInvariants.check`).

L'init actuelle est entièrement aléatoire : pour chaque arbre vivant, on tire (avec un skip 30%) un troll non plein au hasard. Cette init est peu informée — la pop a peu de bons individus de départ et passe une partie du budget évolutif à reconstruire des choses triviales (assigner un arbre proche d'un troll qui en est proche).

L'objectif du warm-start est de **donner à la population un individu seed de qualité** sans dégrader la diversité.

## Objectif

Introduire un individu seed dans la population initiale, construit selon une heuristique **"plus proches arbres par troll, en round-robin"**, en respectant strictement la contrainte d'unicité d'arbre par individu.

Non-objectifs :
- Ne PAS remplacer tous les individus par le seed (perte de diversité fatale au GA).
- Ne PAS modifier sélection / crossover / mutation / évaluation.
- Ne PAS introduire de nouveau type ou de nouveau buffer alloué dans le hot path.

## Décisions de conception

1. **Un seul individu seed**, placé à l'index 0 de la population. Les `POP_SIZE - 1 = 19` autres restent initialisés par `initRandom`.
2. **Construction round-robin par couche** plutôt que greedy par troll en bloc : à la couche `k`, chaque troll prend le meilleur arbre encore libre depuis sa position courante. Évite la famine du dernier troll qu'un greedy bloc induirait.
3. **Position courante par troll** : initialisée à la position du troll, mise à jour à la position de l'arbre venant d'être assigné. Donne une vraie "tournée" cohérente avec ce que TrollPolicy va consommer.
4. **Métrique** : `PathTable.distance(curX, curY, treeX, treeY)`. Les arbres inaccessibles (`UNREACHABLE = 0xFF`) sont ignorés.
5. **Tie-break** : à distance égale, on garde le premier arbre rencontré (treeIndex croissant). Déterministe — pas de dépendance au RNG.
6. **Warm-start à chaque tour**, pas seulement au tour 0 : coût négligeable, bénéfice constant.

## Architecture

### Fichiers touchés

- `src/main/java/com/bmrt/cgspring2026/ga/GenomeOps.java`
  Ajout de la méthode statique `initWarm(GameState state, short[] buf, byte[] lenBuf, int individuIdx)`. Ajout des scratch buffers statiques associés.

- `src/main/java/com/bmrt/cgspring2026/ga/GeneticAgent.java`
  Dans `initPopulation`, l'individu 0 passe par `initWarm`, les autres restent sur `initRandom`.

- `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java` *(nouveau)*
  Tests JUnit 5 couvrant les invariants et un mini scénario déterministe.

### Signature

```java
public static void initWarm(GameState state,
                            short[] buf,
                            byte[] lenBuf,
                            int individuIdx);
```

Pas de paramètre `SplittableRandom` — l'algorithme est déterministe.

### Scratch buffers (alloués une fois, jamais dans le hot path)

```java
private static final boolean[] warmTreeTakenBuf = new boolean[GameState.MAX_TREES];
private static final int[]     warmCurX         = new int[GameState.MAX_TROLLS];
private static final int[]     warmCurY         = new int[GameState.MAX_TROLLS];
```

`ownTrollsBuf` (déjà présent dans `GenomeOps`) est réutilisé.

## Algorithme détaillé

```
initWarm(state, buf, lenBuf, individuIdx):
  // 1. Reset du segment de l'individu
  base = Genome.offset(individuIdx, 0)
  for k in 0..SLOTS_PER_GENOME-1:
    buf[base + k] = EMPTY_GENE
  for j in 0..MAX_TROLLS-1:
    Genome.setLen(lenBuf, individuIdx, j, 0)

  // 2. Construire la liste des trolls own
  ownCount = 0
  for i in 0..state.trollCount-1:
    if (state.trollPlayer[i] & 0xFF) == 0:
      ownTrollsBuf[ownCount++] = i
  if ownCount == 0: return

  // 3. Initialiser positions courantes et état trees pris
  for k in 0..ownCount-1:
    troll = ownTrollsBuf[k]
    warmCurX[troll] = state.trollX[troll] & 0xFF
    warmCurY[troll] = state.trollY[troll] & 0xFF
  for t in 0..state.treeCount-1:
    warmTreeTakenBuf[t] = false

  // 4. Boucle round-robin par couche
  for kLayer in 0..MAX_TARGETS_PER_TROLL-1:
    anyAssigned = false
    for kT in 0..ownCount-1:
      troll = ownTrollsBuf[kT]
      bestTree = -1
      bestDist = UNREACHABLE  // = 0xFF, jamais atteint par un distance valide
      for t in 0..state.treeCount-1:
        if state.treeHealth[t] <= 0: continue
        if warmTreeTakenBuf[t]:      continue
        tx = state.treeX[t] & 0xFF
        ty = state.treeY[t] & 0xFF
        d  = PathTable.distance(warmCurX[troll], warmCurY[troll], tx, ty)
        if d == UNREACHABLE: continue
        if d < bestDist:
          bestDist = d
          bestTree = t
      if bestTree >= 0:
        bx = state.treeX[bestTree] & 0xFF
        by = state.treeY[bestTree] & 0xFF
        Genome.setGene(buf, individuIdx, troll, kLayer, Genome.encode(bx, by))
        Genome.setLen(lenBuf, individuIdx, troll, kLayer + 1)
        warmTreeTakenBuf[bestTree] = true
        warmCurX[troll] = bx
        warmCurY[troll] = by
        anyAssigned = true
    if not anyAssigned: break
```

### Intégration dans `GeneticAgent.initPopulation`

```java
private void initPopulation(GameState state) {
    GenomeOps.initWarm(state, pop.cur, pop.curLen, 0);
    for (int i = 1; i < Genome.POP_SIZE; i++) {
        GenomeOps.initRandom(state, pop.cur, pop.curLen, i, rng);
    }
}
```

## Invariants

L'individu produit par `initWarm` satisfait `GenomeInvariants.check` :
- `len[j] ∈ [0, MAX_TARGETS_PER_TROLL]` (incrément monotone borné par la couche `kLayer + 1`).
- Aucun gène vide entre `0` et `len[j]-1` (on n'écrit jamais `EMPTY_GENE` après reset, et on incrémente `len` immédiatement après écriture).
- Aucun gène actif au-delà de `len[j]` (reset initial complet du segment).
- Aucun doublon : `warmTreeTakenBuf` empêche de réassigner un arbre déjà pris dans CE individu.
- Coordonnées valides : extraites de `state.treeX/Y` qui sont par construction des cellules valides.

## Edge cases

| Cas | Comportement |
|-----|--------------|
| Aucun troll own | Retour immédiat, segment vide (len=0 partout). |
| Aucun arbre vivant | Boucle externe casse à la première couche, segment vide. |
| Tous les arbres inaccessibles depuis tous les trolls | `bestTree` reste -1 pour chaque troll, `anyAssigned` reste false, break à la couche 0. |
| Plus de trolls que d'arbres | Certains trolls finissent avec moins de cibles, c'est normal. |
| Plus d'arbres que `MAX_TROLLS × MAX_TARGETS_PER_TROLL` | Les arbres surnuméraires ne sont pas assignés ; les couches s'arrêtent à `MAX_TARGETS_PER_TROLL`. |
| Troll bloqué (entouré d'eau, aucun arbre accessible) | Ses couches restent à 0, n'empêche pas les autres trolls de progresser. |
| Deux arbres équidistants | Le premier en treeIndex croissant gagne — déterministe. |

## Coût

Par appel `initWarm` :
- Itérations : `MAX_TARGETS_PER_TROLL × ownCount × treeCount`
- Cas typique CG : `10 × ~3 × ~60 ≈ 1800` lookups `PathTable.distance` (chacun O(1) array access).
- Reset segment + buffers : `~SLOTS_PER_GENOME + MAX_TROLLS + MAX_TREES ≈ 480` ops.

Total : O(quelques microsecondes). Négligeable face au budget de 45 ms par tour.

## Tests

Fichier `src/test/java/com/bmrt/cgspring2026/ga/GenomeOpsWarmStartTest.java` (JUnit 5) :

1. **initWarm_assignsClosestTreeFirst** : 1 troll en (1,1), arbres en (1,2) et (5,5). Vérifie que l'arbre (1,2) est en position 0.
2. **initWarm_chainsFromLastAssigned** : 1 troll, arbres formant un chemin clair (proche → proche du proche → …). Vérifie l'ordre attendu.
3. **initWarm_roundRobinBetweenTrolls** : 2 trolls éloignés, chacun a un arbre clairement à lui. Vérifie que chaque troll prend SON arbre (pas que le premier troll les rafle).
4. **initWarm_respectsUniqueness** : configuration où deux trolls voudraient le même arbre voisin ; vérifie qu'un seul l'obtient et que le second en prend un autre.
5. **initWarm_emptyWhenNoTrees** : aucun arbre vivant → `len[j] == 0` pour tout `j`, segment intégralement à `EMPTY_GENE`.
6. **initWarm_emptyWhenNoOwnTrolls** : seul un troll adverse présent → segment intégralement vide.
7. **initWarm_satisfiesInvariants** : sur le scénario du smoke test existant, `GenomeInvariants.check` passe.

Style des tests : minimaliste, le nom du test est la spec, pas de commentaires de doublure.

## Hors-scope

- Pas de variante stochastique du warm-start (ce sera une éventuelle évolution si la diversité s'avère insuffisante).
- Pas de seed multiple (2+ warm-start avec ordres de trolls permutés) — peut être une amélioration future.
- Pas de tuning conjoint avec `P_SKIP_INIT` de `initRandom` — `initRandom` n'est pas touché.

## Critères de succès

1. Compilation OK et tous les tests existants passent.
2. Les 7 nouveaux tests passent.
3. Le smoke regression existant (`ga: smoke regression test`) continue de produire un fitness ≥ greedy.
4. Sur le bench mi-game à budget 40 ms, la fitness du meilleur individu au tour 0 est strictement supérieure à celle d'une init full-random (à mesurer manuellement sur le harness existant).
