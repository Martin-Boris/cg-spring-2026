---
description: "Applique les principes du Clean Code (Robert C. Martin) au code Java du projet. Adapté au contexte CodinGame : certains principes sont assouplis dans les chemins chauds pour préserver les performances temps-réel."
---

# Clean Code — Adapté Java / CodinGame

> "Code is clean if it can be read, and enhanced by a developer other than its original author." — Grady Booch

## Argument optionnel
$ARGUMENTS
(Si fourni : fichier ou classe spécifique à traiter. Sinon, traiter tout `src/main/java/com/bmrt/cgwinter2026/` sauf `builder/`.)

## Processus
1. Lis les fichiers ciblés.
2. Identifie les violations selon les règles ci-dessous.
3. **Distingue** les chemins chauds (boucle de jeu, simulation, algorithme) des chemins froids (init, parsing, main).
4. Applique les corrections en respectant les exceptions CG dans les chemins chauds.
5. Produis un résumé des changements effectués.

---

## Règle transversale CG — Chemins chauds vs froids

Les règles Clean Code s'appliquent **sans restriction** dans les chemins froids (init, parsing, construction du modèle).
Dans les **chemins chauds** (méthodes appelées à chaque tour ou à chaque nœud de simulation), la performance prime :

| Principe | Chemin froid | Chemin chaud |
|----------|-------------|--------------|
| Pas de création d'objets | Recommandé | **Obligatoire** — réutiliser des structures pré-allouées |
| Law of Demeter | Appliquer | Assouplir si la chaîne évite une indirection coûteuse |
| Optional / null | Préférer Optional | **Interdit** — trop coûteux, utiliser -1 ou une sentinelle |
| Commentaires algorithmiques | Éviter | **Autorisés** — un MCTS ou un minimax mérite une explication |

---

## 1. Nommage

**Règles :**
- Noms qui révèlent l'intention : `elapsedTurns` plutôt que `t`, `bestScore` plutôt que `res`
- Noms de classes : **noms** (`GameState`, `Solver`, `Action`) — éviter `Manager`, `Data`, `Info`
- Noms de méthodes : **verbes** (`computeScore`, `applyAction`, `readInput`)
- Pas d'abréviations sauf si elles sont universelles dans le domaine (`hp`, `pos`, `idx`)

**Exemple Java :**
```java
// Mauvais
int d;
boolean check(int x) { ... }

// Bon
int remainingTurns;
boolean isActionValid(int actionIndex) { ... }
```

---

## 2. Fonctions

**Règles :**
- Une fonction = une responsabilité
- < 20 lignes dans les chemins froids
- Arguments : 0-2 idéal, 3+ justification obligatoire
- Pas d'effets de bord cachés (une méthode `getScore()` ne doit pas modifier l'état)

**Exception CG :** Dans les chemins chauds, une fonction dense de 30-40 lignes est acceptable si le découpage créerait des appels de méthodes en boucle serrée. Commenter l'intention dans ce cas.

```java
// Mauvais : nom trompeur + effet de bord caché
int evaluate() {
    this.cachedMoves = generateMoves(); // effet de bord !
    return score;
}

// Bon
List<Action> generateMoves() { ... }
int evaluate(GameState state) { ... }
```

---

## 3. Commentaires

**Règles :**
- Ne pas commenter du mauvais code — le réécrire
- Les commentaires redondants sont du bruit (`// increment i` → supprimer)

**Commentaires autorisés dans ce projet :**
- Complexité algorithmique (`// O(n log n) — tri nécessaire pour la recherche binaire`)
- Formules métier non évidentes (`// score = dist * weight + bonus si dernière case`)
- Marqueurs TODO avec contexte (`// TODO: remplacer par beam search si BF > 15`)
- Explication des constantes magiques si elles viennent de l'énoncé

```java
// Mauvais
// Boucle sur les actions
for (Action a : actions) { ... }

// Bon (formule issue de l'énoncé)
// score CG = kills * 10 + (100 - turn) * survivorBonus
int score = kills * 10 + (100 - turn) * survivorBonus;
```

---

## 4. Formatage

**Règles :**
- **Newspaper rule** : haut du fichier = concepts de haut niveau, bas = détails
- Variables déclarées près de leur utilisation
- Constantes `static final` regroupées en tête de classe

---

## 5. Objets et structures de données

**Règles :**
- Cacher l'implémentation derrière des méthodes (`state.isTerminal()` plutôt que `state.turns == MAX_TURNS`)
- Law of Demeter : éviter `a.getB().getC().doSomething()`

**Exception CG :** Les champs `public` (ou package-private) sont acceptables sur les inner classes de `GameState` pour éviter les appels de getters dans les boucles chaudes.

---

## 6. Gestion des erreurs

**Règles (chemins froids) :**
- Utiliser des exceptions plutôt que des codes de retour
- Ne pas retourner `null` — lever une exception ou utiliser un objet vide

**En chemin chaud :**
- Pas d'exceptions dans les boucles de simulation (coût du stack unwinding)
- Utiliser une valeur sentinelle (`-1`, `Integer.MIN_VALUE`) avec un contrat documenté

```java
// Chemin froid — acceptable
Action bestAction = solver.solve(state);
if (bestAction == null) throw new IllegalStateException("No action found");

// Chemin chaud — préférer
int bestActionIdx = solver.solve(state); // retourne -1 si aucune action valide
```

---

## 7. Tests

Les tests (`src/test/`) couvrent en priorité :
- Le parsing des inputs (cas limites de l'énoncé)
- La logique de `GameState.copy()` et `applyAction()`
- La fonction d'évaluation (valeurs attendues sur des états connus)

La méthodologie TDD (`/tdd-integration`) s'applique à toutes les fonctionnalités, y compris l'algorithme de recherche.

---

## 8. Classes

**Règles :**
- Single Responsibility Principle : `GameState` gère l'état, `Solver` gère la recherche — pas de mélange
- `GameState` ne doit pas contenir de logique de décision
- `Solver` ne doit pas contenir de logique de parsing

---

## Checklist de sortie

Après corrections, confirme ces points :
- [ ] Toutes les variables ont un nom qui révèle leur intention
- [ ] Pas de fonction > 20 lignes hors chemin chaud justifié
- [ ] Commentaires algorithmiques présents sur les sections complexes (algo de recherche, formule d'évaluation)
- [ ] Pas d'effets de bord cachés dans les getters/évaluateurs
- [ ] SRP respecté : chaque classe a une seule raison de changer
- [ ] Pas de `null` retourné dans les chemins froids
- [ ] Constantes magiques nommées avec leur origine si elles viennent de l'énoncé
