# Agent GENERATE-TEST-ENV — Générateur d'environnements de test fine-tuning

Tu es un ingénieur de build automatisé pour bots CodinGame.
Ta mission : lire `docs/fine_tuning.md`, et pour chaque run d'expérience, modifier les paramètres dans le code, compiler le projet, puis archiver le jar et sa fiche de paramètres dans `fineTuning/`.

## Argument optionnel
$ARGUMENTS
(Si fourni : filtre les expériences à générer — ex: "EXP-01" génère uniquement les runs de EXP-01, "EXP-01 EXP-06" génère deux expériences. Sans argument : génère TOUTES les expériences.)

## Prérequis à lire avant d'agir

1. Lis `docs/fine_tuning.md` — source de vérité pour les runs à générer.
2. Lis `src/main/java/com/bmrt/cgwinter2026/iaengine/GeneticAlgorithm.java` — contient les constantes AG.
3. Lis `src/main/java/com/bmrt/cgwinter2026/iaengine/Evaluator.java` — contient les constantes fitness.

## Localisation des paramètres dans le code

### GeneticAlgorithm.java (lignes ~22-27)
```java
private static final int POP_SIZE            = 15;
private static final int HORIZON             = 10;
private static final float MUTATION_RATE     = 0.15f;
private static final int ELITES              = 2;
private static final int TOURNAMENT_K        = 3;
private static final long DEADLINE_MARGIN_MS = 6;
```

### Evaluator.java (lignes ~21-24)
```java
private static final int SCORE_WEIGHT   = 10;
private static final int SURVIVAL_BONUS = 200;
private static final int DIST_MAX       = 5;
private static final int UNIT_BONUS     = 2;
```

## Table de routing paramètre → fichier

| Paramètre | Fichier à modifier |
|-----------|-------------------|
| `POP_SIZE` | `GeneticAlgorithm.java` |
| `HORIZON` | `GeneticAlgorithm.java` |
| `MUTATION_RATE` | `GeneticAlgorithm.java` |
| `ELITES` | `GeneticAlgorithm.java` |
| `TOURNAMENT_K` | `GeneticAlgorithm.java` |
| `DEADLINE_MARGIN_MS` | `GeneticAlgorithm.java` |
| `SCORE_WEIGHT` | `Evaluator.java` |
| `SURVIVAL_BONUS` | `Evaluator.java` |
| `DIST_MAX` | `Evaluator.java` |
| `UNIT_BONUS` | `Evaluator.java` |

## Processus par run

Pour chaque run à générer, exécute cette séquence **dans l'ordre exact** :

### Étape 1 — Modifier les paramètres
- Identifier le(s) fichier(s) source concernés par ce run.
- Modifier **uniquement** les constantes du run (ne pas toucher aux autres).
- Si un run modifie plusieurs paramètres simultanément (ex: "POP=20 HORIZON=15"), les modifier tous dans la même étape.

### Étape 2 — Compiler
```bash
mvn package -q -DskipTests
```
- Si la compilation échoue : afficher l'erreur, restaurer les fichiers modifiés (étape 4), et passer au run suivant en signalant l'échec.

### Étape 3 — Archiver le jar et créer la fiche
- Créer le dossier `fineTuning/` s'il n'existe pas.
- Copier le jar compilé vers `fineTuning/` avec le nom formaté :
  ```bash
  cp target/cg-summer-2024-1.0-SNAPSHOT.jar fineTuning/EXP-XX-RunY.jar
  ```
  Où `EXP-XX` est l'identifiant de l'expérience (ex: `EXP-01`) et `RunY` est la lettre du run (ex: `RunA`).
- Créer le fichier `fineTuning/EXP-XX-RunY.md` avec le template défini ci-dessous.

### Étape 4 — Restaurer le code source (OBLIGATOIRE)
```bash
git checkout -- src/main/java/com/bmrt/cgwinter2026/iaengine/GeneticAlgorithm.java
git checkout -- src/main/java/com/bmrt/cgwinter2026/iaengine/Evaluator.java
```
Cette étape est **toujours exécutée**, même si la compilation a échoué.
Le code source doit être identique au baseline avant de passer au run suivant.

## Template de la fiche de paramètres (EXP-XX-RunY.md)

```markdown
# [EXP-XX] — [Nom de l'expérience] — Run [Y]

## Paramètre testé

| Paramètre | Valeur baseline | Valeur testée |
|-----------|----------------|--------------|
| PARAM     | valeur_base    | valeur_test  |

## Paramètres fixes (inchangés)

| Paramètre | Valeur | Fichier |
|-----------|--------|---------|
| POP_SIZE | ... | GeneticAlgorithm.java |
| HORIZON | ... | GeneticAlgorithm.java |
| MUTATION_RATE | ... | GeneticAlgorithm.java |
| ELITES | ... | GeneticAlgorithm.java |
| TOURNAMENT_K | ... | GeneticAlgorithm.java |
| DEADLINE_MARGIN_MS | ... | GeneticAlgorithm.java |
| SCORE_WEIGHT | ... | Evaluator.java |
| SURVIVAL_BONUS | ... | Evaluator.java |
| DIST_MAX | ... | Evaluator.java |
| UNIT_BONUS | ... | Evaluator.java |

## Hypothèse testée

[Copier l'hypothèse depuis fine_tuning.md pour cette expérience]

## Critère de succès

[Copier le critère de succès depuis fine_tuning.md]

## Jar généré

`fineTuning/EXP-XX-RunY.jar`

## Résultat

Score moyen : —
Remarques : —
```

## Conventions de nommage

- Expérience `EXP-01`, run `A` → `EXP-01-RunA.jar` et `EXP-01-RunA.md`
- Expérience `EXP-07`, run `C` → `EXP-07-RunC.jar` et `EXP-07-RunC.md`
- COMBO-01 → `COMBO-01-RunA.jar` et `COMBO-01-RunA.md`

## Rapport de fin d'exécution

À la fin de tous les runs, afficher un tableau récapitulatif :

```
Runs générés avec succès :
  ✓ EXP-01-RunA  → fineTuning/EXP-01-RunA.jar
  ✓ EXP-01-RunB  → fineTuning/EXP-01-RunB.jar
  ...

Runs en échec :
  ✗ EXP-XX-RunY  → [raison de l'échec]

Total : X succès, Y échecs
```

## Règles importantes

- **Ne jamais laisser le code dans un état modifié** : la restauration git est obligatoire après chaque run.
- **Ne pas modifier `pom.xml`** : le build doit rester identique entre les runs.
- **Ne pas modifier les tests** : `-DskipTests` est utilisé pour la vitesse, les tests ne sont pas le sujet ici.
- **Un run = un jar = un .md** : toujours créer les deux fichiers ensemble ou aucun.
- **Si fine_tuning.md contient des runs non encore résolus** (valeur `—`) : les ignorer et signaler qu'ils ne peuvent pas être générés sans valeurs définies.
- **Vérifier l'invariant fitness avant chaque modification** : si le run viole `SCORE_WEIGHT > (DIST_MAX - 1) * UNIT_BONUS`, afficher un avertissement mais générer quand même le jar (le run sert précisément à mesurer l'impact).
