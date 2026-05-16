package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.greedy.GreedyAgent;
import com.bmrt.cgspring2026.model.GameState;

import java.util.SplittableRandom;

public final class GeneticAgent {

    public static final long TURN_BUDGET_NS = 45_000_000L;
    public static final long INIT_BUDGET_NS = 920_000_000L;
    public static final double P_CROSSOVER = 0.70;
    final Population pop = new Population();
    final short[] prevBestBuf = new short[Genome.SLOTS_PER_GENOME];
    final byte[] prevBestLen = new byte[GameState.MAX_TROLLS];
    private final GameState scratch = new GameState();
    private final SplittableRandom rng = new SplittableRandom();
    private final int[] evalActionBuf = new int[GameState.MAX_TROLLS + 1];
    boolean hasPrevBest = false;
    int lastBestIdx;
    private int lastGenCount;
    private double lastBestFitness;

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

    public int decide(GameState state, long deadlineNs, int[] outActions) {
        // 1. Init population
        initPopulation(state);
        evaluatePopulation(state);
        lastGenCount = 0;

        // 2. Boucle évolutive deadline-driven
        while (System.nanoTime() < deadlineNs) {
            stepGeneration(state);
            lastGenCount++;
            if (System.nanoTime() >= deadlineNs) break;
        }

        // 3. Best individu courant
        lastBestIdx = argmax(pop.curFit);
        lastBestFitness = pop.curFit[lastBestIdx];

        // Stash du best pour réinjection au tour suivant
        System.arraycopy(pop.cur, Genome.offset(lastBestIdx, 0),
                prevBestBuf, 0, Genome.SLOTS_PER_GENOME);
        System.arraycopy(pop.curLen, Genome.lenOffset(lastBestIdx, 0),
                prevBestLen, 0, GameState.MAX_TROLLS);
        hasPrevBest = true;

        // 4. Génère les actions du tick 0
        int[] cursor = TrollPolicy.cursorBuf;
        for (int j = 0; j < GameState.MAX_TROLLS; j++) cursor[j] = 0;
        int n = TrollPolicy.fillOwnActions(state, pop.cur, pop.curLen, lastBestIdx, cursor, outActions);

        // 5. Ajouter TRAIN au tour 0
        if (state.turn == 0) {
            int trainAction = GreedyAgent.maybeTrain(state);
            if (trainAction != -1) {
                System.arraycopy(outActions, 0, outActions, 1, n);
                outActions[0] = trainAction;
                n++;
            }
        }
        return n;
    }

    public int lastGenCount() {
        return lastGenCount;
    }

    public double lastBestFitness() {
        return lastBestFitness;
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
            pop.curFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.cur, pop.curLen, i, evalActionBuf);
        }
    }

    private void stepGeneration(GameState state) {
        // Élitisme top-1
        int bestIdx = argmax(pop.curFit);
        copyIndividu(pop.cur, pop.curLen, bestIdx, pop.nxt, pop.nxtLen, 0);
        pop.nxtFit[0] = pop.curFit[bestIdx];

        // Génère offspring
        for (int i = 1; i < Genome.POP_SIZE; i++) {
            if (rng.nextDouble() < P_CROSSOVER) {
                int p1 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                int p2 = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                GenomeOps.crossover(pop.cur, pop.curLen, p1, pop.cur, pop.curLen, p2,
                        pop.nxt, pop.nxtLen, i, rng);
            } else {
                int p = Selection.tournament(pop.curFit, rng, Genome.POP_SIZE);
                copyIndividu(pop.cur, pop.curLen, p, pop.nxt, pop.nxtLen, i);
                GenomeOps.runMutation(pop.nxt, pop.nxtLen, i, rng);
            }
            pop.nxtFit[i] = GenomeEvaluator.evaluate(scratch, state, pop.nxt, pop.nxtLen, i, evalActionBuf);
        }
        pop.swap();
    }
}
