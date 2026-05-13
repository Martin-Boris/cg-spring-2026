# Banana Farming — Spec de conception

> Extension du `GreedyAi` (cf. `2026-05-12-greedy-chopper-design.md`). Permet à un troll `LOCAL` adjacent à mon shack de transformer des bananes (1 pt/unité) en wood (4 pts/unité) via un cycle PICK → PLANT → CHOP → DROP de 4 tours.

## 1. Objectif & hypothèse stratégique

Le wood vaut 4× plus qu'un fruit. Une banane fraîchement plantée (size 0) a 1 HP : un seul CHOP suffit à la convertir en 1 wood. Cycle de conversion :

| Tour | Action | Effet sur le score |
|------|--------|--------------------|
| N+0  | `PICK BANANA` (depuis shack adjacent)          | −1 (banane retirée du shack) |
| N+1  | `PLANT BANANA` (sur case du troll)             |  0           |
| N+2  | `CHOP` (tue l'arbre size-0, +1 wood en carry)  |  0           |
| N+3  | `DROP` (dépose le wood au shack)               | +4           |

**Bilan : +3 score / 4 tours / troll farmer.** Avec 2 cases adjacentes farmables sans menace, ≈ +1.5 score/tour gratuit tant que le stock banane > 0.

Activation **conditionnée** à : aucun troll ennemi à portée ≤ 3 tours de la case de plantation (sinon l'ennemi peut nous voler le CHOP).

## 2. Architecture

Une **nouvelle classe** `BananaFarmer` dans `com.bmrt.cgspring2026.ai`. Pas de modification de `TargetSelector`, `RoleAssigner`, `TreeZoning`, `TrainPlanner`, `Tile`, `GameState`.

| Composant       | Fichier                       | Rôle                                                                                  |
|-----------------|-------------------------------|---------------------------------------------------------------------------------------|
| `BananaFarmer`  | `ai/BananaFarmer.java`        | Décide une `Action` de farming pour un troll LOCAL, ou `null` si non applicable.      |
| `GreedyAi`      | `ai/GreedyAi.java` (modifié)  | Initialise un `bananaBudget` ; appelle `BananaFarmer.plan` avant la logique actuelle. |

Signature publique :
```java
public final class BananaFarmer {
    private BananaFarmer() {}

    public static Action plan(Troll troll,
                              GameState state,
                              int[] bananaBudget,
                              Set<Long> assignedTrees);
}
```

- `bananaBudget` est un holder mutable (`int[1]`) initialisé à `state.myShackInv[BANANA]` au début de `GreedyAi.decide`. Décrémenté à chaque PICK décidé.
- `assignedTrees` est le même `Set<Long>` que celui utilisé par `TargetSelector` (clé = `(long) tree.y * state.width + tree.x`).

## 3. Préconditions globales

`BananaFarmer.plan` retourne `null` immédiatement si **au moins une** des conditions échoue :

- `troll.player == 0` (sécurité, le caller filtre déjà)
- `adjacentToMyShack(troll, state)` (Manhattan == 1 vs `state.myShackX/Y`)
- `troll.carryCapacity > 0` (sinon il ne peut rien porter)

Le rôle `LOCAL` est filtré par le **caller** (`GreedyAi.decideForTroll`), pas par `BananaFarmer`.

## 4. Machine à états

États inférés depuis l'état observable du tour. **Premier match gagne**, dans cet ordre :

| Ordre | État        | Conditions supplémentaires                                                                                                | Action émise                          |
|-------|-------------|----------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| 1     | FARM_DROP   | `troll.carry[WOOD] >= 1`                                                                                                   | `Drop(troll.id)`                      |
| 2     | FARM_CHOP   | il existe un `Tree` sur `(troll.x, troll.y)` avec `type == BANANA` et `size == 0`                                          | `Chop(troll.id)` + `assignedTrees.add(key)` |
| 3     | FARM_PLANT  | `troll.carry[BANANA] == 1` ET `troll.carryTotal() == 1` ET aucun `Tree` sur `(troll.x, troll.y)`                            | `Plant(troll.id, BANANA)`             |
| 4     | FARM_PICK   | `troll.carryTotal() == 0` ET `bananaBudget[0] >= 1` ET `isSafeToFarm(troll.x, troll.y, state)`                              | `Pick(troll.id, BANANA)` + `bananaBudget[0]--` |

Aucun match → `null`.

**Justifications :**
- DROP en tête : dès qu'on a du bois, on dépose ; pas besoin de re-checker la menace.
- CHOP avant PLANT : permet de reprendre proprement le cycle en cas de désynchronisation (arbre déjà planté présent).
- PLANT exige `carryTotal == 1` pour exclure les carry mixtes (banane + autre fruit ramassé ailleurs).
- PICK est le **seul** état à vérifier la menace : une fois engagé, abandonner perd la banane investie.

## 5. Détection de menace — `isSafeToFarm`

```
isSafeToFarm(tileX, tileY, state) :
    pour chaque troll E dans state.trolls :
        si E.player == 0 :    continue   // allié
        si E.movementSpeed <= 0 : continue // immobile
        d = |E.x - tileX| + |E.y - tileY|
        reach = ceil(d / E.movementSpeed)
        si reach <= 3 : retourner false
    retourner true
```

**Choix Manhattan plutôt que BFS :** Manhattan ≤ vraie distance avec obstacles. Côté ennemi optimiste = côté nous conservateur. Si Manhattan dit "safe", la réalité est forcément ≥ Manhattan donc safe aussi. Pas de faux négatifs côté sécurité. Coût O(nb ennemis) par appel.

**Cas ignorés (non-menace) :** ennemi avec `movementSpeed <= 0`, aucun troll ennemi.

## 6. Intégration dans `GreedyAi`

### 6.1 `decide` — Initialisation du budget

Au début de la méthode, après l'init du zoning :

```java
int[] bananaBudget = { state.myShackInv[ResourceType.BANANA.ordinal()] };
```

Passé à `decideForTroll` (nouvelle signature : ajout du paramètre `int[] bananaBudget`).

### 6.2 `decideForTroll` — Hook avant la branche carryFull

```java
private Action decideForTroll(Troll troll, Role role, GameState state,
                              Set<Long> assignedTrees, int[] bananaBudget) {
    if (role == Role.LOCAL) {
        Action farm = BananaFarmer.plan(troll, state, bananaBudget, assignedTrees);
        if (farm != null) {
            return farm;
        }
    }
    // ... logique existante inchangée
}
```

### 6.3 Ordre des trolls — inchangé

Tri actuel : LEADER d'abord, puis LOCAL par id croissant. Le LEADER skip le hook. Les LOCAL se partagent le `bananaBudget` dans l'ordre d'id (déterministe).

## 7. Conflit d'arbre — pourquoi marquer le CHOP

Au tour T+2 (CHOP de notre cycle), l'arbre banane size-0 est visible dans `state.trees`. Sans marquage, `TargetSelector.pickTree` le verrait comme candidat attractif (1 chop kill, proche) pour un autre troll LOCAL qui ferait son propre farming sur une **autre** case adjacente. Il abandonnerait sa case pour venir voler le chop.

**Solution :** en émettant FARM_CHOP, `BananaFarmer` ajoute la clé `(long) tree.y * state.width + tree.x` à `assignedTrees`. Les trolls suivants dans la boucle (id supérieur) le voient comme déjà assigné et le skip dans `TargetSelector`.

**Cas `lastTree` (1 seul arbre sur la map) :** `TargetSelector` autorise le sharing — un autre troll peut le viser. Cas suffisamment rare pour être accepté tel quel.

## 8. Cas limites

| Cas                                                                          | Comportement                                                                                |
|------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Troll LOCAL pas adjacent au shack                                            | `plan` retourne `null` → logique existante (chop d'un vrai arbre).                          |
| `myShackInv[BANANA] == 0` au début du tour                                   | `bananaBudget[0] == 0` → FARM_PICK bloqué → `null` → chop normal.                           |
| 2 LOCAL adjacents, 1 seule banane en stock                                   | Premier troll PICK (budget → 0), second tombe sur `null` → chop normal.                     |
| Troll LOCAL adjacent avec carry mixte (1 banane + 1 fruit autre)             | FARM_PLANT bloqué (`carryTotal != 1`) → `null` → logique existante (chop, ou drop si full). |
| Vrai arbre apparaît sur la case adjacente avant qu'on plante                 | FARM_PLANT bloqué (tree sur la case) → `null` → `TargetSelector` cible naturellement.       |
| Troll carrying 1 wood mais pas adjacent au shack                             | FARM_DROP bloqué (précondition adjacent) → `null` → logique existante (move vers shack quand full, sinon continue chop). |
| Ennemi avec `movementSpeed == 0`                                             | Ignoré dans `isSafeToFarm` (pas de menace).                                                  |
| Aucun ennemi sur la map                                                       | `isSafeToFarm` retourne `true` par défaut.                                                  |
| LEADER adjacent au shack avec carry vide                                     | Le hook `BananaFarmer` n'est pas appelé (role != LOCAL) → logique existante.                |

## 9. Tests TDD attendus

Couverture minimale (le plan d'implémentation détaillera ; ici on liste les cas) :

**`BananaFarmerTest` (unitaire) :**
- Préconditions :
    - retourne `null` si troll pas adjacent au shack
    - retourne `null` si `carryCapacity == 0`
- FARM_DROP :
    - troll adjacent + 1 wood en carry → `Drop`
    - prioritaire sur tous les autres états (test avec wood + tree size-0 sur la case)
- FARM_CHOP :
    - troll adjacent + size-0 banana sur sa case → `Chop`
    - ajoute la clé à `assignedTrees`
- FARM_PLANT :
    - troll adjacent + 1 banane carry + case vide → `Plant(BANANA)`
    - bloqué si carry contient autre chose en plus de la banane
    - bloqué si un tree existe déjà sur la case
- FARM_PICK :
    - troll adjacent + empty + banane dispo + safe → `Pick(BANANA)`
    - décrémente le `bananaBudget`
    - bloqué si `bananaBudget == 0`
    - bloqué si menace ennemie (reach ≤ 3)
- Menace :
    - ennemi à distance 6 avec speed 2 → reach=3 → bloqué
    - ennemi à distance 7 avec speed 2 → reach=4 → safe
    - ennemi speed=0 → ignoré

**`GreedyAiTest` (intégration) :**
- 2 trolls LOCAL adjacents, 1 banane en stock → seul le premier (id min) PICK, l'autre fait chop normal.
- 1 troll LOCAL adjacent en milieu de cycle (1 banane en carry, case vide, pas de menace) → PLANT.
- LEADER adjacent au shack avec carry vide et bananes en stock → ne farm pas (chop normal selon zone).
- Cycle complet sur 4 tours simulés (PICK → PLANT → CHOP → DROP) → bilan +3 score, +1 dans `myShackInv[WOOD]`.

## 10. Hors scope

- Réutilisation de cycles sur plusieurs cases ennemies (les LOCAL ne vont jamais farmer adjacent au shack adverse).
- Optimisation BFS pour la distance menace (Manhattan suffit).
- Décompte explicite du cycle restant (la machine est stateless).
- Coordination LOCAL/LEADER au-delà du marquage `assignedTrees`.
