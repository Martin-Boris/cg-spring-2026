# Algorithme IA — Troll Farm (CG Spring 2026)

## Analyse du problème

| Caractéristique | Valeur | Implication |
|---|---|---|
| Type de jeu | 2 joueurs, somme non-nulle (chacun farme) | Pas besoin de minimax pur ; adversaire peu interactif |
| Déterministe | Oui | Simulation parfaite possible |
| Information | Parfaite | On voit tout l'état |
| Branching factor / tour | **~50 actions par troll** (MOVE vers cases atteignables + HARVEST/CHOP/MINE/DROP/PICK/PLANT×4/WAIT/TRAIN) ; produit cartésien sur N trolls → 10³–10⁶ avec 2-4 trolls | minimax/alpha-beta exclus ; AG/beam adaptés |
| Horizon partie | 300 tours | Long, mais cycle utile = 5-15 tours (farm → drop) |
| Budget temps | 50 ms/tour (1000 ms tour 1) | AG faisable si simu rapide (cible < 2 µs/tour de jeu) |
| Score | Continu (fruits=1pt, wood=4pt), discret par déposit | Bonne fitness possible |
| Séquence multi-tours à optimiser | **Oui** (trajets MOVE→…→DROP) | Beam/AG indispensables |
| Multi-unités | **Oui** (1 → ~5-10 trolls) | AG avec chromosome multi-troll = naturel |
| Interaction adversaire | **Faible** (zones contestées : eau, iron, gros arbres ; pas de combat direct) | Adversaire passif/scripté suffit |
| Vitesse de simu estimée | Grille 22×11, ~10 arbres, ~5 trolls → cible < 2 µs/tour | Permet 5 000–25 000 simus/tour |

## Options évaluées

### Option 1 — Greedy heuristique

- **Principe** : à chaque tour, pour chaque troll, énumérer les actions plausibles et choisir celle de plus grande EV (gain de points attendu / coût en tours). Coordination par assignation gloutonne (un troll = une cible, première-meilleure-cible-disponible).
- **Avantages** : trivial à coder, < 1 ms, très lisible, debug facile, baseline indispensable.
- **Inconvénients** : myope (ne planifie pas le retour shack), coordination naïve, ne sait pas pré-positionner pour la fructification future.
- **Pertinence** : ✅ baseline V1 idéale pour passer Bronze.

### Option 2 — Beam Search (1 joueur)

- **Principe** : développer en largeur les K meilleurs plans de profondeur d, branching sur actions composites de tous les trolls.
- **Avantages** : déterministe, contrôle du budget, planification multi-tours.
- **Inconvénients** : produit cartésien sur N trolls = explosion ; la décomposition séquentielle (troll par troll) est sous-optimale ; sensible aux optima locaux.
- **Pertinence** : ⚠️ moins bon que l'AG pour multi-unités.

### Option 3 — Algorithme Génétique (AG)

- **Principe** : population de **plans** (séquence d'actions sur H tours × N trolls). Fitness = simulation des H tours + heuristique de potentiel à l'horizon. Sélection par tournoi, croisement à 1-point + uniforme, mutation, élitisme.
- **Avantages** :
  - Multi-unités natif (chromosome couvre tous les trolls)
  - **Warm start** : on shift le meilleur plan du tour précédent, énorme accélérateur
  - S'adapte au budget temps (s'arrête à 45 ms)
  - Échappe aux optima locaux (mutation)
- **Inconvénients** : demande simulation très rapide ; paramétrage (taille pop, taux mutation) ; ne réagit pas tactiquement à un adversaire intelligent (acceptable ici).
- **Pertinence** : ✅ choix cœur V2+.

### Option 4 — MCTS

- ❌ Branching trop élevé sans progressive widening lourd à coder.
- ❌ Rollouts random catastrophiques pour un jeu de farming/planification.
- ❌ Inutile car peu d'interaction adversaire.

### Option 5 — Hill Climbing / Simulated Annealing

- Variante mono-individu de l'AG. Plus simple mais moins exploratoire. Plan B si l'AG ne tient pas la budget temps.

## Choix retenu : **Greedy V1 → AG V2**

**Stratégie progressive validée :**

1. **V1 — Greedy heuristique** : passer Bronze, obtenir une baseline mesurable contre le bot par défaut. Sert aussi de générateur de **plans d'initialisation** pour l'AG (cf. plus bas).
2. **V2 — Algorithme Génétique** : cœur de l'effort. Multi-trolls + séquences + warm start.

**Adversaire dans la simulation** : **passif (statique)** — les trolls adverses ne bougent pas pendant nos H tours de simu. Hypothèse acceptable en Bronze (peu d'interaction). À raffiner en greedy scripté plus tard si nécessaire.

**Horizon de planification AG** : **H = 6-8 tours** (à fine-tuner). Couvre un cycle farm → drop standard.

---

## Conception détaillée

### V1 — Greedy

#### Boucle principale

```
pour chaque troll t (ordre = priorité décroissante : trolls inactifs / pleins en premier) :
    candidats = énumérer_actions_plausibles(t, state)
    pour chaque action a dans candidats :
        score(a) = heuristique(t, a, state)
    meilleure = argmax(candidats, score)
    réserver_cible(meilleure)  # éviter conflits avec autres trolls
    output(meilleure)
```

#### Heuristique greedy (par troll, par action)

| Cas | Score |
|---|---|
| Troll plein (carry == capacity) | MOVE vers shack — score = 1000 - distance |
| Troll adjacent shack avec ressources | DROP — score = 999 |
| Arbre avec fruits accessible | HARVEST/MOVE — score = (fruits × points_par_fruit) / (tours_pour_atteindre + 1) |
| Arbre adulte (size=4) non fruité | CHOP/MOVE — score = (size × 4) / (tours_pour_chop + 1) |
| Case IRON disponible | MINE/MOVE — score = chopPower × poids_iron (faible en Bronze) / distance |
| TRAIN possible | si ressources shack suffisent ET ROI > ROI cible — score selon `n + v²` |

#### Pseudo-code

```
fonction tour() :
    state = parseTurn()
    plan = []
    cibles_reservees = {}
    pour t in trolls_owned trié par priorité :
        a = greedy_action(t, state, cibles_reservees)
        plan.append(a)
    output(plan.join(';'))
```

### V2 — Algorithme Génétique

#### Boucle principale

```
fonction tour() :
    state = parseTurn()
    population = warm_start(best_plan_previous_turn, state)
    pour chaque génération jusqu'à budget_épuisé :
        évaluer(population)         # via simulation
        élites = top_K(population)
        enfants = []
        tant que |enfants| < pop_size - |élites| :
            p1, p2 = tournoi_select(population)
            enfant = crossover(p1, p2)
            mutate(enfant, taux_mut)
            enfants.append(enfant)
        population = élites + enfants
    best_plan = argmax(population, fitness)
    output(actions du tour 0 de best_plan)
    sauvegarder best_plan pour warm_start prochain tour
```

### Chromosome / Représentation

- **Structure** : `Gene[H][N_trolls]` aplati en `Gene[H * N_max_trolls]`.
- **Gène** = un `int` packé :
  - bits 0-3 : type d'action (4 bits, 16 valeurs possibles)
  - bits 4-31 : paramètres encodés (cf. ci-dessous)
- **Types d'actions et encodage des paramètres** :
  - `MOVE` → 2× int (coordonnée cible x, y) compressés : `x * height + y` sur ~8 bits (grille ≤ 242 cases)
  - `HARVEST`, `CHOP`, `MINE`, `DROP` → pas de param
  - `PLANT` → 2 bits (type de fruit)
  - `PICK` → 2 bits (type de fruit)
  - `TRAIN` → 4 × ~4 bits (valeurs des 4 attributs jusqu'à ~15)
  - `WAIT` → pas de param
- **Longueur** : `H × N_trolls_actuel`. H = 6 par défaut.
- **Adaptation dynamique** : quand un troll est entraîné via TRAIN, la longueur augmente. Le warm start gère le shift en ajoutant des gènes aléatoires si besoin.

#### Repair / contraintes de légalité

Pendant la simulation, si un gène est illégal (ex. HARVEST sans arbre), il est **silencieusement converti en WAIT** dans la simu (pas de pénalité dure mais aucun gain → fitness réduite naturellement). On évite ainsi un mécanisme de repair coûteux.

### Warm start

```
fonction warm_start(prev_best, state) :
    base = prev_best.shifted_left_by_one()   # on a déjà joué le tour 0
    base.last_turn = random_actions(state)   # remplir le dernier tour
    population = [base]
    population += k mutations de base
    population += k_random aléatoires
    population += 1 plan greedy (graine déterministe)
    return population
```

### Fonction d'évaluation (fitness)

```
fitness(plan, state) :
    sim_state = state.copy()
    moi_score_init = sim_state.score(me)
    pour t = 0 à H-1 :
        actions = plan.actions_at_turn(t)
        sim_state.apply(actions, adversaire = NOOP)
    delta_score_réel = sim_state.score(me) - moi_score_init
    bonus_potentiel = potentiel(sim_state)
    return delta_score_réel + bonus_potentiel
```

**Composantes du `potentiel(state)` à l'horizon** (à fine-tuner) :

| Critère | Poids initial | Justification |
|---|---|---|
| Ressources portées pondérées (fruit=0.5, wood=2.0, iron=0.2) | + | Représente valeur "presque marquée" |
| Distance la plus proche d'un troll plein au shack | − 0.1 par case | Inciter à se rapprocher |
| Arbres adultes fruités accessibles (vivants à horizon) | + 0.3 par fruit accessible | Potentiel de récolte future |
| Cases IRON contrôlées (proximité d'un troll dispo) | + 0.1 par tile | Préparation au TRAIN |
| Pénalité fortes actions illégales (WAIT forcé) | − 0.05 par WAIT issu de repair | Décourager les gènes pourris sans tuer la diversité |
| Bonus TRAIN exécuté pendant l'horizon | + selon ROI estimé | Encourager la croissance d'unités |

### Génération / Opérateurs

- **Population** : 30-50 individus (à ajuster).
- **Sélection** : tournoi de taille 3.
- **Crossover** : 1-point sur l'axe temporel (mélange "ma première moitié de plan + sa seconde moitié") + uniforme par troll (chaque troll prend sa séquence du parent 1 ou 2 indépendamment).
- **Mutation** :
  - Taux par gène : ~5-10%
  - Types : remplacement complet du gène par une action random valide, ou modification du paramètre uniquement (perturbation de la cible MOVE)
- **Élitisme** : top 2-4 conservés intacts.

### Optimisations prévues

- [ ] **Priorité haute** : simulation incrémentale (pas de full copy ; structures int[] flat avec arraycopy)
- [ ] **Priorité haute** : pré-calcul des distances BFS depuis chaque case spéciale (shack, iron, arbres) au tour 1
- [ ] **Priorité haute** : pool d'objets / réutilisation d'arrays (zéro alloc dans la boucle)
- [ ] **Priorité moyenne** : early termination si fitness > seuil
- [ ] **Priorité moyenne** : timer interne avec marge de 5 ms
- [ ] **Priorité basse** : co-évolution adversaire pour ligues hautes

---

## Plan d'itération

1. **V1 — Greedy baseline**
   - Parsing complet
   - 1 troll : aller au plus proche arbre fruité, harvest, retour shack, drop
   - Validation : passer Bronze, score stable
2. **V2 — AG minimal**
   - Modèle de jeu copiable (state.copy() rapide)
   - Simulateur de tour (apply 1 action)
   - AG avec pop=30, H=6, sans warm start
   - Fitness = Δscore seulement (pas de potentiel)
3. **V3 — AG complet**
   - Warm start avec plan précédent
   - Potentiel à l'horizon (poids initiaux ci-dessus)
   - Plan greedy injecté comme graine de la population
4. **V4 — Optimisations perf & fine-tuning**
   - Simu zero-alloc, BFS pré-calculé
   - Fine-tune des poids de fitness (skill `fine-tune`)
   - Ajustement H, taille pop, taux mutation
5. **V5 (optionnel)** — Adversaire greedy dans la simu
6. **V6 (optionnel)** — Co-évolution / minimax racine sur quelques coups

---

## Implications pour le modèle (à transmettre à /model)

L'AG impose des contraintes structurantes sur les données :

- **Simulation rapide** ⇒ structures plates en `int[]`/`byte[]`, zéro allocation par tour de simu, copy via `System.arraycopy`.
- **Multi-trolls indexés** ⇒ tableaux parallèles plutôt que `List<Troll>`.
- **Lookups O(1) par case** ⇒ grilles flat indexées par `y * width + x` (terrain, troll-par-case, arbre-par-case).
- **Actions packées** ⇒ `int` plutôt que sealed interface (évite l'allocation dans la boucle AG).
- **GameState.copy() en < 1 µs** ⇒ tableaux primitifs uniquement dans le state hot path.

Ces contraintes vont guider le choix entre POJO, SoA, ou hybride dans `docs/model.md`.
