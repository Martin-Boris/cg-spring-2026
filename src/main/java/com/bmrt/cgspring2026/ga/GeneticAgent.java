package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.model.GameState;

import java.util.SplittableRandom;

public final class GeneticAgent {

    public static final long TURN_BUDGET_NS = 44_000_000L;
    public static final long INIT_BUDGET_NS = 920_000_000L;
    public static final double P_CROSSOVER = 0.70;
    public static final double HYSTERESIS_BONUS = 0.05;
    // Toggle d'instrumentation diagnostique. Mettre à false pour désactiver
    // tous les calculs de métriques (JIT élimine les branches mortes).
    public static final boolean INSTRUMENT = true;
    public static final int IMMIGRANTS_PER_GEN = 2;
    final Population pop = new Population();
    final short[] prevBestBuf = new short[Genome.SLOTS_PER_GENOME];
    final byte[] prevBestLen = new byte[GameState.MAX_TROLLS];
    private final GameState scratch = new GameState();
    private final int[] evalActionBuf = new int[GameState.MAX_TROLLS + 1];
    private final int[] prevOutActions = new int[GameState.MAX_TROLLS + 1];
    // CODE UNIQUEMENT POUR LE LOGING START
    private final boolean[] coverageSeen = new boolean[GameState.MAX_TREES];
    boolean hasPrevBest = false;
    int lastBestIdx;
    private SplittableRandom rng;
    private int lastGenCount;
    private double lastBestFitness;
    private boolean plantCandidatesInitialized = false;
    private int prevOutCount = 0;
    private boolean hasPrevOut = false;
    private int lastTrollChurn = -1;
    private int lastActiveTrolls = -1;
    private int lastHamming = -1;
    private int lastTieCount = 0;
    private int lastAliveTrees = -1;
    private int lastCoverageInit = -1;
    private int lastCoverageFinal = -1;
    private int lastBestCoverage = -1;
    private int lastIndivCoverageSum = -1;
    private int lastTotalCuts = -1;
    // CODE UNIQUEMENT POUR LE LOGING END
    private int lastUnresolvedGenes = -1;

    public GeneticAgent() {
    }

    private static void copyIndividu(short[] srcBuf, byte[] srcLen, int srcIdx,
                                     short[] dstBuf, byte[] dstLen, int dstIdx) {
        System.arraycopy(srcBuf, Genome.offset(srcIdx, 0),
                dstBuf, Genome.offset(dstIdx, 0), Genome.SLOTS_PER_GENOME);
        System.arraycopy(srcLen, Genome.lenOffset(srcIdx, 0),
                dstLen, Genome.lenOffset(dstIdx, 0), GameState.MAX_TROLLS);
    }

    private static int argmax(double[] arr) {
        int best = 0;
        double bestV = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > bestV) {
                bestV = arr[i];
                best = i;
            }
        }
        return best;
    }

    // CODE UNIQUEMENT POUR LE LOGING START
    private static int computeHamming(short[] cur, byte[] curLen, int idx,
                                      short[] prev, byte[] prevLen) {
        int diff = 0;
        int curBase = Genome.offset(idx, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int curL = curLen[Genome.lenOffset(idx, j)] & 0xFF;
            int prevL = prevLen[j] & 0xFF;
            int common = Math.min(curL, prevL);
            int curTrollBase = curBase + j * Genome.MAX_TARGETS_PER_TROLL;
            int prevTrollBase = j * Genome.MAX_TARGETS_PER_TROLL;
            for (int k = 0; k < common; k++) {
                if (cur[curTrollBase + k] != prev[prevTrollBase + k]) diff++;
            }
            diff += Math.abs(curL - prevL);
        }
        return diff;
    }

    private static int computeTieCount(double[] fit, double best) {
        final double EPS = 1e-6;
        int count = 0;
        for (int i = 0; i < fit.length; i++) {
            if (best - fit[i] < EPS) count++;
        }
        return count;
    }

    private static int computeChurn(int[] cur, int curN, int[] prev, int prevN) {
        int changed = 0;
        for (int i = 0; i < curN; i++) {
            int act = cur[i];
            if (Action.type(act) == ActionType.TRAIN) continue;
            int troll = Action.trollIdx(act);
            boolean foundSame = false;
            for (int j = 0; j < prevN; j++) {
                int p = prev[j];
                if (Action.type(p) == ActionType.TRAIN) continue;
                if (Action.trollIdx(p) == troll) {
                    foundSame = (p == act);
                    break;
                }
            }
            if (!foundSame) changed++;
        }
        return changed;
    }

    private static int countActiveOwnTrolls(GameState state) {
        int count = 0;
        for (int i = 0; i < state.trollCount; i++) {
            if ((state.trollPlayer[i] & 0xFF) == 0) count++;
        }
        return count;
    }

    private static int countAliveTrees(GameState state) {
        int n = 0;
        for (int t = 0; t < state.treeCount; t++) {
            if (state.treeHealth[t] > 0) n++;
        }
        return n;
    }

    /**
     * Nombre d'arbres vivants distincts référencés par un gène CUT dans toute la pop.
     */
    private int computePopTreeCoverage(GameState state, short[] buf, byte[] lenBuf) {
        for (int t = 0; t < state.treeCount; t++) coverageSeen[t] = false;
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lenBuf, i, j);
                int base = Genome.offset(i, j);
                for (int k = 0; k < len; k++) {
                    short g = buf[base + k];
                    if (g == Genome.EMPTY_GENE) continue;
                    if (Genome.isPlant(g)) continue;
                    int t = state.treeIndexAt(Genome.geneX(g), Genome.geneY(g));
                    if (t < 0) continue;
                    if (state.treeHealth[t] <= 0) continue;
                    coverageSeen[t] = true;
                }
            }
        }
        int count = 0;
        for (int t = 0; t < state.treeCount; t++) if (coverageSeen[t]) count++;
        return count;
    }

    /**
     * Calcule trois métriques en un seul passage :
     * - somme (sur tous les individus) du nb d'arbres vivants distincts ciblés par l'individu
     * - nombre total de gènes CUT non-vides (mesure du remplissage de la pop)
     * - nombre de gènes CUT qui ne résolvent pas vers un arbre vivant (test d'encodage)
     */
    private void computeIndivStats(GameState state, short[] buf, byte[] lenBuf) {
        int totalCuts = 0;
        int unresolved = 0;
        int indivCoverageSum = 0;
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            for (int t = 0; t < state.treeCount; t++) coverageSeen[t] = false;
            int distinct = 0;
            for (int j = 0; j < GameState.MAX_TROLLS; j++) {
                int len = Genome.len(lenBuf, i, j);
                int base = Genome.offset(i, j);
                for (int k = 0; k < len; k++) {
                    short g = buf[base + k];
                    if (g == Genome.EMPTY_GENE) continue;
                    if (Genome.isPlant(g)) continue;
                    totalCuts++;
                    int t = state.treeIndexAt(Genome.geneX(g), Genome.geneY(g));
                    if (t < 0 || state.treeHealth[t] <= 0) {
                        unresolved++;
                        continue;
                    }
                    if (!coverageSeen[t]) {
                        coverageSeen[t] = true;
                        distinct++;
                    }
                }
            }
            indivCoverageSum += distinct;
        }
        lastIndivCoverageSum = indivCoverageSum;
        lastTotalCuts = totalCuts;
        lastUnresolvedGenes = unresolved;
    }
    // CODE UNIQUEMENT POUR LE LOGING END

    /**
     * Nombre d'arbres vivants distincts référencés par le best individu uniquement.
     */
    private int computeIndivTreeCoverage(GameState state, short[] buf, byte[] lenBuf, int idx) {
        for (int t = 0; t < state.treeCount; t++) coverageSeen[t] = false;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int len = Genome.len(lenBuf, idx, j);
            int base = Genome.offset(idx, j);
            for (int k = 0; k < len; k++) {
                short g = buf[base + k];
                if (g == Genome.EMPTY_GENE) continue;
                if (Genome.isPlant(g)) continue;
                int t = state.treeIndexAt(Genome.geneX(g), Genome.geneY(g));
                if (t < 0) continue;
                if (state.treeHealth[t] <= 0) continue;
                coverageSeen[t] = true;
            }
        }
        int count = 0;
        for (int t = 0; t < state.treeCount; t++) if (coverageSeen[t]) count++;
        return count;
    }

    public int decide(GameState state, long deadlineNs, int[] outActions) {
        if (!plantCandidatesInitialized) {
            Genome.initPlantCandidates();
            plantCandidatesInitialized = true;
        }
        Genome.initHarvestCandidates(state);
        // Seed déterministe par tour : élimine la divergence d'exploration tour à tour
        rng = new SplittableRandom(state.turn ^ 0x9E3779B97F4A7C15L);
        // 1. Init population
        initPopulation(state);
        evaluatePopulation(state);
        lastGenCount = 0;

        // CODE UNIQUEMENT POUR LE LOGING START
        if (INSTRUMENT) {
            lastAliveTrees = countAliveTrees(state);
            lastCoverageInit = computePopTreeCoverage(state, pop.cur, pop.curLen);
            computeIndivStats(state, pop.cur, pop.curLen);
        } else {
            lastAliveTrees = -1;
            lastCoverageInit = -1;
            lastCoverageFinal = -1;
            lastBestCoverage = -1;
            lastIndivCoverageSum = -1;
            lastTotalCuts = -1;
            lastUnresolvedGenes = -1;
        }
        // CODE UNIQUEMENT POUR LE LOGING END

        // 2. Boucle évolutive deadline-driven
        while (System.nanoTime() < deadlineNs) {
            stepGeneration(state);
            lastGenCount++;
            if (System.nanoTime() >= deadlineNs) break;
        }

        // CODE UNIQUEMENT POUR LE LOGING START
        if (INSTRUMENT) {
            lastCoverageFinal = computePopTreeCoverage(state, pop.cur, pop.curLen);
        }
        // CODE UNIQUEMENT POUR LE LOGING END

        // 3. Best individu courant
        lastBestIdx = argmax(pop.curFit);
        lastBestFitness = pop.curFit[lastBestIdx];

        // CODE UNIQUEMENT POUR LE LOGING START
        if (INSTRUMENT) {
            lastBestCoverage = computeIndivTreeCoverage(state, pop.cur, pop.curLen, lastBestIdx);
        }
        // CODE UNIQUEMENT POUR LE LOGING END

        // 4. Génère les actions du tick 0
        int[] cursor = TrollPolicy.cursorBuf;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) TrollPolicy.policyPhase[j] = 0;
        int n = TrollPolicy.fillOwnActions(state, pop.cur, pop.curLen, lastBestIdx, cursor, outActions);

        // Diagnostic: compute metrics on raw GA output (prevBest holds T-1's best ici)
        // CODE UNIQUEMENT POUR LE LOGING START
        if (hasPrevBest) {
            lastHamming = computeHamming(pop.cur, pop.curLen, lastBestIdx,
                    prevBestBuf, prevBestLen);
        } else {
            lastHamming = -1;
        }
        lastTieCount = computeTieCount(pop.curFit, lastBestFitness);
        if (hasPrevOut) {
            lastTrollChurn = computeChurn(outActions, n, prevOutActions, prevOutCount);
            lastActiveTrolls = countActiveOwnTrolls(state);
        } else {
            lastTrollChurn = -1;
            lastActiveTrolls = -1;
        }
        // CODE UNIQUEMENT POUR LE END

        // Stash du best pour réinjection au tour suivant (APRÈS diagnostic)
        System.arraycopy(pop.cur, Genome.offset(lastBestIdx, 0),
                prevBestBuf, 0, Genome.SLOTS_PER_GENOME);
        System.arraycopy(pop.curLen, Genome.lenOffset(lastBestIdx, 0),
                prevBestLen, 0, GameState.MAX_TROLLS);
        hasPrevBest = true;

        // 5. Ajouter TRAIN au tour 0
        if (state.turn == 0) {
            int trainAction = GreedyAgent.maybeTrain(state);
            if (trainAction != -1) {
                System.arraycopy(outActions, 0, outActions, 1, n);
                outActions[0] = trainAction;
                n++;
            }
        }

        // CODE UNIQUEMENT POUR LE LOGING START
        // Stash outActions pour le tour suivant (diagnostic churn)
        System.arraycopy(outActions, 0, prevOutActions, 0, n);
        prevOutCount = n;
        hasPrevOut = true;
        // CODE UNIQUEMENT POUR LE END

        return n;
    }

    public int lastGenCount() {
        return lastGenCount;
    }

    public double lastBestFitness() {
        return lastBestFitness;
    }

    // CODE UNIQUEMENT POUR LE START
    public int lastTrollChurn() {
        return lastTrollChurn;
    }

    public int lastActiveTrolls() {
        return lastActiveTrolls;
    }

    public int lastHamming() {
        return lastHamming;
    }

    public int lastTieCount() {
        return lastTieCount;
    }

    public int lastAliveTrees() {
        return lastAliveTrees;
    }

    public int lastCoverageInit() {
        return lastCoverageInit;
    }

    public int lastCoverageFinal() {
        return lastCoverageFinal;
    }

    public int lastBestCoverage() {
        return lastBestCoverage;
    }

    public int lastIndivCoverageSum() {
        return lastIndivCoverageSum;
    }

    public int lastTotalCuts() {
        return lastTotalCuts;
    }
    // CODE UNIQUEMENT POUR LE END

    public int lastUnresolvedGenes() {
        return lastUnresolvedGenes;
    }

    private void initPopulation(GameState state) {
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
    }

    private void evaluatePopulation(GameState state) {
        for (int i = 0; i < Genome.POP_SIZE; i++) {
            double base = GenomeEvaluator.evaluate(scratch, state, pop.cur, pop.curLen, i, evalActionBuf);
            pop.curFit[i] = base + hysteresisBonus(pop.cur, pop.curLen, i);
        }
    }

    private void stepGeneration(GameState state) {
        // Élitisme top-1
        int bestIdx = argmax(pop.curFit);
        copyIndividu(pop.cur, pop.curLen, bestIdx, pop.nxt, pop.nxtLen, 0);
        pop.nxtFit[0] = pop.curFit[bestIdx];

        // Génère offspring (les IMMIGRANTS_PER_GEN derniers slots sont réservés à l'immigration)
        int immigrantStart = Genome.POP_SIZE - IMMIGRANTS_PER_GEN;
        for (int i = 1; i < immigrantStart; i++) {
            if (rng.nextDouble() < P_CROSSOVER) {
                int p1 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                int p2 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                GenomeOps.crossover(pop.cur, pop.curLen, p1, pop.cur, pop.curLen, p2,
                        pop.nxt, pop.nxtLen, i, rng);
            } else {
                int p = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                copyIndividu(pop.cur, pop.curLen, p, pop.nxt, pop.nxtLen, i);
                GenomeOps.runMutation(state, pop.nxt, pop.nxtLen, i, rng);
            }
            double base = GenomeEvaluator.evaluate(scratch, state, pop.nxt, pop.nxtLen, i, evalActionBuf);
            pop.nxtFit[i] = base + hysteresisBonus(pop.nxt, pop.nxtLen, i);
        }

        // Immigration : remplace les derniers slots par des génomes fraîchement aléatoires
        for (int i = immigrantStart; i < Genome.POP_SIZE; i++) {
            GenomeOps.initRandom(state, pop.nxt, pop.nxtLen, i, rng);
            double base = GenomeEvaluator.evaluate(scratch, state, pop.nxt, pop.nxtLen, i, evalActionBuf);
            pop.nxtFit[i] = base + hysteresisBonus(pop.nxt, pop.nxtLen, i);
        }

        pop.swap();
    }

    private double hysteresisBonus(short[] buf, byte[] lenBuf, int idx) {
        if (!hasPrevBest) return 0.0;
        int matches = 0;
        int base = Genome.offset(idx, 0);
        for (int j = 0; j < GameState.MAX_TROLLS; j++) {
            int curL = lenBuf[Genome.lenOffset(idx, j)] & 0xFF;
            int prevL = prevBestLen[j] & 0xFF;
            if (curL > 0 && prevL > 0) {
                int curBase = base + j * Genome.MAX_TARGETS_PER_TROLL;
                int prevBase = j * Genome.MAX_TARGETS_PER_TROLL;
                if (buf[curBase] == prevBestBuf[prevBase]) matches++;
            }
        }
        return HYSTERESIS_BONUS * matches;
    }
}
