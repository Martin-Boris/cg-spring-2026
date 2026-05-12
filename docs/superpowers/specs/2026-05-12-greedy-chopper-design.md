# Greedy Chopper — Spec de conception

> Bot V1 pour CG Spring 2026 (Wood League / Bronze). Algorithme glouton orienté **rush wood / chopping**, avec différenciation de rôle entre trolls et entraînement (TRAIN) opportuniste.

## 1. Objectif & hypothèse stratégique

Maximiser le score sur 300 tours. Le scoring rend le **wood 4× plus rentable** qu'un fruit (1 wood = 4 pts ; couper un arbre mature size 4 = 16 pts en un CHOP). On parie tout sur le chopping et on dimensionne les trolls pour aller couper le plus vite possible.

Conséquences directes :
- On ne plante pas, on ne récolte pas (V1).
- On entraîne (TRAIN) des trolls avec **carryCapacity, moveSpeed, chopPower** boostés (harvestPower = 0).
- IRON n'est exploité (MINE) que si l'absence de IRON devient bloquante — hors scope V1, voir §10.

## 2. Architecture

Nouveau package `com.bmrt.cgspring2026.ai`. Une classe d'entrée + sous-composants utilitaires, un fichier par responsabilité (le `FileBuilder` existant les fusionnera dans `Player.java` pour la soumission).

| Composant         | Fichier                                           | Rôle                                                                                                       |
|-------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `GreedyAi`        | `ai/GreedyAi.java`                                | Point d'entrée. `List<Action> decide(GameState)`.                                                          |
| `TrainPlanner`    | `ai/TrainPlanner.java`                            | Choisit la commande `Train` du tour (ou null) selon `shackInv` et `nb trolls alliés`.                      |
| `RoleAssigner`    | `ai/RoleAssigner.java`                            | Étiquette mes trolls : `TRAIN_LEADER` (stats max) ou `LOCAL`.                                              |
| `TreeZoning`      | `ai/TreeZoning.java`                              | Voronoi statique BFS depuis les 2 shacks. Pré-calculé une fois (turn 1).                                   |
| `TargetSelector`  | `ai/TargetSelector.java`                          | Choisit l'arbre cible d'un troll selon rôle + assignations courantes.                                      |
| `RandomWalk`      | `ai/RandomWalk.java`                              | Fallback de mouvement si plus aucun arbre.                                                                 |

`Player.main` devient minimaliste :
```
GameState state = new GameState();
GameState.readInit(in, state);
GreedyAi ai = new GreedyAi();
while (true) {
    GameState.readTurn(in, state);
    System.out.println(toCommandString(ai.decide(state)));
}
```

## 3. Boucle de décision (`GreedyAi.decide`)

```
si turn == 1 :
    treeZoning = TreeZoning.precompute(state)

trainAction   = TrainPlanner.plan(state)                  // Action.Train ou null
myTrolls      = trolls où player == 0
roles         = RoleAssigner.assign(myTrolls)             // Map<trollId, Role>
assignedTrees = new HashSet<Long>()                       // clé = y * width + x

actions = []
pour chaque t in myTrolls (LEADER en premier, puis ordre des ids) :
    actions.add(decideForTroll(t, roles[t.id], state, assignedTrees))

si trainAction != null : actions.add(trainAction)
return actions
```

`decideForTroll(troll, role, state, assignedTrees)` :
```
si troll.carryTotal() >= troll.carryCapacity :
    si adjacentToMyShack(troll, state) : return new Drop(troll.id)
    sinon : return moveTowardShack(troll, state)

target = TargetSelector.pickTree(troll, role, state, assignedTrees, treeZoning)
si target == null : return RandomWalk.pick(troll, state)
si troll.x == target.x && troll.y == target.y : return new Chop(troll.id)
return new Move(troll.id, target.x, target.y)
```

## 4. TargetSelector — règles greedy

```
key(tree) = tree.y * width + tree.x
candidates = state.trees filtrés tels que key(tree) NOT IN assignedTrees

si role == LEADER :
    zoned = candidates ∩ { case dans zone OPP }
    si vide : zoned = candidates ∩ { case dans zone OPP ou NEUTRAL }
    si vide : zoned = candidates                        // fallback total
sinon (LOCAL) :
    zoned = candidates ∩ { case dans zone MINE ou NEUTRAL }
    si vide : zoned = candidates

si state.trees.size() == 1 :
    zoned = state.trees                                 // partage autorisé

si zoned vide : return null

// Priorité matures avec fallback strict (et non tri par size décroissant)
mature = zoned filtrés tels que tree.size == 4
pool   = mature.nonVide() ? mature : zoned

choisi = argmin(pool, distanceManhattan(troll, tree))
         tie-break déterministe : (tree.y, tree.x) lexicographique
assignedTrees.add(key(choisi))
return choisi
```

Notes :
- L'identifiant unique d'arbre n'existe pas dans l'input ; on utilise `(x, y)` (un seul arbre par case) comme clé. Type concret : `Set<Long>` avec clé `y * width + x`.
- Distance Manhattan = approximation rapide. Pas de pathfinding réel ici (l'arbitre gère le contournement quand le `MOVE` est émis).
- Règle mature-first **stricte** : tant qu'il existe au moins une mature dans `zoned`, on ignore complètement les immatures. C'est le comportement décrit par le brief utilisateur ("il focus en priorité les arbres mature... Si jamais il n'y en a plus il passe sur les arbres non mature").

## 5. TrainPlanner — un seul TRAIN, au tour 1

Politique V1 simplifiée : **un seul `TRAIN` sur toute la partie, émis uniquement au tour 1**, avec les stats les plus ambitieuses abordables sur **moveSpeed, carryCapacity, chopPower** (`harvestPower = 0`).

Algorithme :
```
si state.turn != 1 : return null

n = nb trolls alliés (= 1 au tour 1 sauf changement de règle)

pour v de V_MAX (= 4) descendant jusqu'à 1 :
    coutPlum  = n + v * v       // moveSpeed → PLUM
    coutLemon = n + v * v       // carryCapacity → LEMON
    coutIron  = n + v * v       // chopPower → IRON
    si shackInv[PLUM]  >= coutPlum
       && shackInv[LEMON] >= coutLemon
       && shackInv[IRON]  >= coutIron :
        return new Train(v, v, 0, v)

// Si IRON manque, on dégrade en abandonnant chopPower plutôt que de skipper le TRAIN
pour v de V_MAX (= 4) descendant jusqu'à 1 :
    si shackInv[PLUM] >= n + v * v && shackInv[LEMON] >= n + v * v :
        return new Train(v, v, 0, 0)

return null
```

`V_MAX = 4` : v=5 coûterait 26 ressources d'un type pour 1 stat de plus (ROI très faible).

Conséquences assumées :
- **Aucun TRAIN après le tour 1**, même si le shack accumule des ressources plus tard. C'est un choix V1 : on parie sur la valeur du 2ᵉ troll précoce + le rush wood des deux unités existantes, sans nouveau spawn.
- Si l'inventaire shack au tour 1 ne permet aucun `Train(v,v,0,v)` ni `Train(v,v,0,0)` même à v=1, **on ne TRAIN jamais** et on joue à 1 troll toute la partie. À mesurer après tests pour décider d'une V2.

## 6. TreeZoning (Voronoi statique)

Pré-calcul une seule fois (turn 1, budget 1000 ms ; coût largement sous la limite pour ≤ 242 cases) :

- Deux BFS multi-source (en réalité mono-source : un BFS depuis `(myShackX, myShackY)`, un depuis `(oppShackX, oppShackY)`), uniquement sur cases GRASS. Les obstacles ne propagent pas mais peuvent être des cases adjacentes au shack — on démarre les BFS depuis **les voisins GRASS** du shack (le shack lui-même n'est pas marchable).
- Pour chaque case `(x, y)` GRASS :
  - `distMine[idx]` = distance BFS depuis mon shack (INF si non atteignable)
  - `distOpp[idx]` = distance BFS depuis shack adverse
  - `zone[idx] = MINE` si `distMine < distOpp`, `OPP` si `>`, `NEUTRAL` si `==`, `UNREACHABLE` si les deux sont INF.

API exposée :
- `Zone zoneOf(int x, int y)` — utilisée par `TargetSelector`.
- Pas de recalcul ultérieur (shacks immobiles).

## 7. RoleAssigner

```
score(t) = t.movementSpeed + t.carryCapacity + t.chopPower
si myTrolls.size() == 1 :
    troll → LOCAL
sinon :
    leader = argmax(myTrolls, score)   // tie-break : id min
    leader → TRAIN_LEADER
    autres → LOCAL
```

## 8. Drop & adjacence shack

- `adjacentToMyShack(t, state)` = `|t.x - state.myShackX| + |t.y - state.myShackY| == 1`. (La case du troll est forcément GRASS car il y stationne déjà.)
- `moveTowardShack(t, state)` :
  - candidates = les 4 voisins de `(myShackX, myShackY)` qui sont GRASS.
  - cible = la plus proche du troll (Manhattan) ; tie-break déterministe par `(y, x)`.
  - return `new Move(t.id, cible.x, cible.y)`.

Cas troll **plein** = `carryTotal() >= carryCapacity` (le `>=` couvre le cas surplus déjà clampé par l'arbitre).

## 9. RandomWalk

Quand `TargetSelector` retourne `null` (plus d'arbre dispo après filtrage) :

```
seed = state.turn * 1000L + troll.id
rng  = new java.util.Random(seed)
pour 16 essais :
    dx = rng.nextInt(11) - 5     // [-5..5]
    dy = rng.nextInt(11) - 5
    tx = clamp(troll.x + dx, 0, width - 1)
    ty = clamp(troll.y + dy, 0, height - 1)
    si state.walkable(tx, ty) && (tx, ty) != (troll.x, troll.y) :
        return new Move(troll.id, tx, ty)
return new Move(troll.id, troll.x, troll.y)   // ultime fallback : stationner
```

Seed déterministe = replay reproductible.

## 10. Hors scope V1 (décisions explicites)

| Sujet                              | Décision V1                                                                                  |
|------------------------------------|----------------------------------------------------------------------------------------------|
| PLANT / HARVEST / PICK             | Pas implémenté. On chop, on drop, c'est tout.                                                |
| MINE                               | Pas implémenté. Conséquence : si IRON ne tombe jamais, on TRAIN sans chopPower (cf §5).      |
| Anti-collision entre trolls alliés | Géré indirectement via `assignedTrees`. Risque de collision finale en bord de shack accepté. |
| Simulation profonde / scoring multi-tour | Hors scope — greedy 1-coup.                                                            |
| Évitement actif de l'adversaire    | Hors scope.                                                                                  |
| Replay anti-dépouillement (fin de game) | Hors scope.                                                                             |

Ces points sont volontairement laissés pour V2+.

## 11. Tests (TDD intégration)

Le projet impose TDD. On écrit des tests d'intégration sur `GreedyAi.decide(GameState)` en construisant un `GameState` directement (constructeurs sans args, champs publics, pas de Scanner).

Tests prévus (un par comportement) :

| #  | Nom                                                              | Comportement vérifié                                               |
|----|------------------------------------------------------------------|--------------------------------------------------------------------|
| 1  | `picks_closest_mature_tree_for_local_troll`                      | Priorité size=4 > size<4, puis distance Manhattan croissante.     |
| 2  | `falls_back_to_immature_when_no_mature`                          | Bascule sur size<4 si aucune mature.                              |
| 3  | `one_tree_per_troll_except_last`                                 | `assignedTrees` interdit le partage sauf si `trees.size() == 1`.  |
| 4  | `drops_when_carry_full_and_adjacent`                             | Émet `Drop` si plein et adjacent.                                 |
| 5  | `moves_toward_shack_when_full_and_not_adjacent`                  | Émet `Move` vers case adjacente walkable du shack.                |
| 6  | `chops_when_on_tree_tile`                                        | Émet `Chop` si troll.x/y == tree.x/y.                             |
| 7  | `leader_targets_opponent_zone_first`                             | Voronoi : LEADER ignore les arbres côté MINE tant qu'il y en a OPP. |
| 8  | `leader_falls_back_when_no_opp_trees`                            | LEADER prend des arbres MINE si plus d'OPP.                       |
| 9  | `random_walk_when_no_trees`                                      | Émet un `Move` vers une case GRASS différente du troll.           |
| 10 | `train_planner_emits_max_affordable_train_at_turn_1`             | Tour 1 : `Train(v,v,0,v)` au plus haut v affordable.              |
| 11 | `train_planner_falls_back_zero_chop_when_no_iron_at_turn_1`      | Tour 1 : `Train(v,v,0,0)` si IRON insuffisant mais PLUM/LEMON OK. |
| 12 | `train_planner_returns_null_when_resources_insufficient_at_turn_1` | Tour 1 : aucun TRAIN si shackInv trop pauvre.                   |
| 12b| `train_planner_returns_null_after_turn_1`                        | Tour > 1 : toujours null, même si ressources OK.                  |
| 13 | `tree_zoning_voronoi_assigns_correct_side`                       | Cases plus proches de mon shack = MINE, etc.                      |
| 14 | `decide_emits_one_action_per_my_troll_plus_optional_train`       | Cardinalité de la sortie correcte.                                |

Pas de mock — on instancie `GameState` directement. Les tests d'intégration suivent le pattern du projet (`tdd-integration` skill).

## 12. Limites connues du design & risques

1. **Pas de pathfinding interne** — on délègue à l'arbitre. Risque : un `MOVE` vers un arbre derrière une mer d'obstacles peut faire stagner le troll plusieurs tours. Atténuation : la distance Manhattan utilisée pour le tri est un mauvais proxy ; on accepte le coût en V1 (un troll qui ne progresse pas un tour est juste une perte de tempo, pas un crash). **Mitigation prévue V2 : BFS dynamique pour mesurer la vraie distance avant tri.**
2. **TRAIN trop ambitieux** — on essaye `v=4` à chaque tour ; on consommera des bursts de fruits pour un seul stat-up coûteux au lieu d'enchaîner deux `v=2`. Acceptable pour la V1 (à mesurer après tests).
3. **Adversaire ignoré** — le LEADER va côté adverse mais n'évite pas les trolls ennemis. Acceptable Bronze.
4. **Fin de partie sans arbres** — règle de défaite "10 tours sans arbre" : on ne replante pas, donc on peut perdre par épuisement. Acceptable en pari sur la durée moyenne (les arbres respawnent ? *non, ils ne respawnent pas seuls — il faut PLANT*). À surveiller via replays.

## 13. Cibles de performance

- Tour 1 : ≤ 1000 ms (Voronoi BFS sur ≤ 242 cases = négligeable).
- Tours suivants : ≤ 50 ms (la décision est en O(`trolls × trees`) avec ≤ qq dizaines de chaque ; largement sous la limite).

## 14. Plan d'implémentation (résumé pour writing-plans)

1. Créer le squelette `ai/GreedyAi` + sous-classes vides + tests vides (RED).
2. Tests + implémentation dans l'ordre : `TreeZoning` → `RoleAssigner` → `TargetSelector` → `TrainPlanner` → `RandomWalk` → `GreedyAi` (orchestration).
3. Recâbler `Player.main` pour appeler `GreedyAi`.
4. Vérifier que `FileBuilder` produit un `Player.java` autonome avec toutes les classes inlinées.
5. Smoke test local : faire tourner le bot avec un input artificiel.

Le détail step-by-step sera produit par la skill `writing-plans`.
