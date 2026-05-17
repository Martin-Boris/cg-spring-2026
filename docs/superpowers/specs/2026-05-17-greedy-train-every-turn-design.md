# Design — Train greedy à chaque tour + exemption cp=0

**Date :** 2026-05-17
**Branche :** ga-v2

## Contexte

Actuellement, un seul troll est entraîné au tour 0 via `GreedyAgent.maybeTrain()`. L'objectif est d'entraîner un nouveau troll dès que les ressources le permettent, à chaque tour, avec des contraintes de stats minimales, un plafond de 5 trolls, et une désactivation après le tour 200.

Impact secondaire : les trolls entraînés peuvent avoir cp=0 (pas de chop power). Ces trolls doivent être exemptés de cibler un arbre pour chopper, mais peuvent planter.

---

## Section 1 — `GreedyAgent.maybeTrain()` : nouvelles conditions

Remplace entièrement la logique actuelle.

**Conditions de déclenchement :**
- `n = countOwnTrolls(s)` — nombre de trolls propres actuels
- `n >= 5` → return -1 (max 5 trolls)
- `s.turn >= 200` → return -1 (désactivé dès le tour 200)

**Calcul des stats (inchangé via `maxV`) :**
```
ms = maxV(plum,  n, 1)   // floor=1, speed
cc = maxV(lemon, n, 1)   // floor=1, carry capacity
hp = maxV(apple, n, 0)   // floor=0, harvest power
cp = maxV(iron,  n, 0)   // floor=0, chop power
```

**Guards de stats minimales :**
- `plum < n + 1` → return -1 (ms≥1 impossible)
- `lemon < n + 1` → return -1 (cc≥1 impossible)
- `hp == 0 && cp == 0` → return -1 (ni harvest ni chop : pas de train)

**Suppression des anciennes guards :**
- `apple < 1` → supprimée (trop restrictive si cp≥1)
- `iron < n + 1` → supprimée (trop restrictive si hp≥1)

L'abordabilité complète est garantie par construction via `maxV` (qui ne dépasse jamais ce que la ressource couvre) combinée aux guards explicites.

**Cas edge — base cost quand stat=0 :** Quand cp=0, le Simulator déduit quand même `n + 0² = n` unités d'iron. Si `iron < n`, le Simulator ignorera l'action TRAIN. On accepte ce comportement : `maybeTrain` ne vérifie pas ce cas (fréquence très faible, coût négligeable).

**Retour :** `Action.train(ms, cc, hp, cp)`

---

## Section 2 — `GeneticAgent.decide()` : injection TRAIN à chaque tour

**Avant :**
```java
if (state.turn == 0) {
    int trainAction = GreedyAgent.maybeTrain(state);
    if (trainAction != -1) { ... }
}
```

**Après :**
```java
int trainAction = GreedyAgent.maybeTrain(state);
if (trainAction != -1) {
    System.arraycopy(outActions, 0, outActions, 1, n);
    outActions[0] = trainAction;
    n++;
}
```

`maybeTrain()` porte toutes les guards (turn, n, stats). Le TRAIN est toujours inséré en tête du tableau d'actions. `GreedyAgent.decide()` (path secondaire) n'est pas modifié pour l'instant.

---

## Section 3 — `GenomeOps` : filtre cp=0 pour les gènes CUT

Miroir exact du pattern existant pour hp=0/HARVEST (ligne 104 de `GenomeOps.java`) :

```java
// Existant (HARVEST) :
if ((state.trollHP[trollIdx] & 0xFF) == 0) continue;

// Nouveau (CUT) :
if ((state.trollCP[trollIdx] & 0xFF) == 0) continue;
```

Ce guard est ajouté **partout** où des gènes CUT sont insérés ou sélectionnés pour un troll :
- `initRandom` — génération de la population initiale
- Toute mutation qui insère un gène CUT (ciblage d'arbre)

Les gènes PLANT ne sont pas affectés : un troll cp=0 peut toujours recevoir des gènes PLANT.

---

## Section 4 — `TrollPolicy` : guard défensif cp=0 sur les gènes CUT

Guard ajouté en tête du branchement CUT dans `TrollPolicy.fillActions()` :

```java
if (!Genome.isPlant(g)) {
    if ((s.trollCP[trollIdx] & 0xFF) == 0) {
        cursor[trollIdx]++;
        policyPhase[trollIdx] = 0;
        continue;
    }
    // ... suite inchangée (treeIndexAt, chop, move)
}
```

Le gène CUT est sauté comme un gène invalide. Le troll passe au gène suivant de son genome. Aucun impact sur PLANT ni HARVEST.

---

## Récapitulatif des fichiers touchés

| Fichier | Changement |
|---|---|
| `greedy/GreedyAgent.java` | Refactor `maybeTrain()` — nouvelles guards, suppression anciennes |
| `ga/GeneticAgent.java` | Suppression du `if (state.turn == 0)` autour du TRAIN |
| `ga/GenomeOps.java` | Ajout guard `cp == 0` sur insertions de gènes CUT |
| `ga/TrollPolicy.java` | Ajout guard défensif `cp == 0` sur exécution de gènes CUT |

## Non concerné

- Genome encoding : inchangé
- GenomeInvariants : inchangé (les gènes CUT restent valides structurellement)
- Simulator.applyTrains : inchangé
- Fitness / scoring : inchangé
