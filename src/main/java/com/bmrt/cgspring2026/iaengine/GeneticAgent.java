package com.bmrt.cgspring2026.iaengine;

import com.bmrt.cgspring2026.action.ActionType;
import com.bmrt.cgspring2026.action.Actions;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Resource;
import com.bmrt.cgspring2026.model.TreeType;

import java.util.Random;

public final class GeneticAgent {

    public static final int HORIZON         = 6;
    public static final int POP_SIZE        = 30;
    public static final int ELITE_K         = 3;
    public static final int TOURNAMENT_SIZE = 3;
    public static final int MUTATION_PCT    = 7;
    public static final int CHROMO_LEN      = HORIZON * GameState.SLOTS_PER_TURN;

    private static final int[] TRAIN_CATALOG = {
            Actions.train(1, 1, 1, 0),
            Actions.train(1, 1, 1, 1),
            Actions.train(1, 2, 1, 1),
            Actions.train(2, 1, 1, 1)
    };

    private final int[][] population = new int[POP_SIZE][CHROMO_LEN];
    private final int[][] nextPop    = new int[POP_SIZE][CHROMO_LEN];
    private final double[] fitness   = new double[POP_SIZE];
    private final int[] sortedIdx    = new int[POP_SIZE];

    private final GameState sim = new GameState();
    private final int[] oppSlice = new int[GameState.SLOTS_PER_TURN];

    private final Random rng = new Random(2026L);

    public int decide(GameState state, int[] out, long deadlineMs) {
        initPopulation(state);
        evaluateAll(state);
        while (System.currentTimeMillis() < deadlineMs) {
            evolve(state);
        }
        int best = argmaxFitness();
        return writeFirstTurnActions(population[best], state, out);
    }

    private void initPopulation(GameState state) {
        for (int i = 0; i < POP_SIZE; i++) {
            randomChromosome(population[i], state);
        }
    }

    private void randomChromosome(int[] chromo, GameState state) {
        int myCount = state.myTrollCount;
        for (int t = 0; t < HORIZON; t++) {
            int base = t * GameState.SLOTS_PER_TURN;
            for (int s = 0; s < GameState.MAX_TROLLS; s++) {
                chromo[base + s] = (s < myCount) ? randomTrollGene(s, state) : Actions.waitAction();
            }
            chromo[base + GameState.TRAIN_SLOT] = randomTrainGene();
        }
    }

    private int randomTrollGene(int trollIdx, GameState state) {
        int roll = rng.nextInt(10);
        return switch (roll) {
            case 0, 1, 2 -> Actions.move(trollIdx, rng.nextInt(state.tileCount));
            case 3       -> Actions.harvest(trollIdx);
            case 4       -> Actions.chop(trollIdx);
            case 5       -> Actions.mine(trollIdx);
            case 6       -> Actions.drop(trollIdx);
            case 7       -> Actions.pick(trollIdx, rng.nextInt(TreeType.COUNT));
            case 8       -> Actions.plant(trollIdx, rng.nextInt(TreeType.COUNT));
            default      -> Actions.waitAction();
        };
    }

    private int randomTrainGene() {
        int roll = rng.nextInt(8);
        if (roll >= TRAIN_CATALOG.length) return Actions.waitAction();
        return TRAIN_CATALOG[roll];
    }

    private void evaluateAll(GameState state) {
        for (int i = 0; i < POP_SIZE; i++) {
            fitness[i] = evaluate(population[i], state);
        }
    }

    private double evaluate(int[] chromo, GameState state) {
        sim.copyFrom(state);
        double scoreInit = sim.score(0);
        for (int t = 0; t < HORIZON; t++) {
            int base = t * GameState.SLOTS_PER_TURN;
            sim.applyTurn(sliceFrom(chromo, base), null);
        }
        double delta = sim.score(0) - scoreInit;
        return delta + carryPotential(sim);
    }

    private int[] sliceFrom(int[] chromo, int base) {
        System.arraycopy(chromo, base, scratchSlice, 0, GameState.SLOTS_PER_TURN);
        return scratchSlice;
    }

    private final int[] scratchSlice = new int[GameState.SLOTS_PER_TURN];

    private double carryPotential(GameState s) {
        double p = 0;
        for (int k = 0; k < s.myTrollCount; k++) {
            int i = s.myTrollSlot[k];
            int base = i * Resource.COUNT;
            p += 0.5 * ((s.trollCarry[base + Resource.PLUM]   & 0xFF)
                      + (s.trollCarry[base + Resource.LEMON]  & 0xFF)
                      + (s.trollCarry[base + Resource.APPLE]  & 0xFF)
                      + (s.trollCarry[base + Resource.BANANA] & 0xFF));
            p += 2.0 * (s.trollCarry[base + Resource.WOOD] & 0xFF);
            p += 0.3 * (s.trollCarry[base + Resource.IRON] & 0xFF);
        }
        return p;
    }

    private void evolve(GameState state) {
        sortByFitnessDesc();
        for (int i = 0; i < ELITE_K; i++) {
            System.arraycopy(population[sortedIdx[i]], 0, nextPop[i], 0, CHROMO_LEN);
        }
        for (int i = ELITE_K; i < POP_SIZE; i++) {
            int p1 = tournament();
            int p2 = tournament();
            crossover(population[p1], population[p2], nextPop[i]);
            mutate(nextPop[i], state);
        }
        for (int i = 0; i < POP_SIZE; i++) {
            int[] tmp = population[i];
            population[i] = nextPop[i];
            nextPop[i] = tmp;
        }
        evaluateAll(state);
    }

    private void sortByFitnessDesc() {
        for (int i = 0; i < POP_SIZE; i++) sortedIdx[i] = i;
        for (int i = 1; i < POP_SIZE; i++) {
            int key = sortedIdx[i];
            double keyFit = fitness[key];
            int j = i - 1;
            while (j >= 0 && fitness[sortedIdx[j]] < keyFit) {
                sortedIdx[j + 1] = sortedIdx[j];
                j--;
            }
            sortedIdx[j + 1] = key;
        }
    }

    private int tournament() {
        int best = rng.nextInt(POP_SIZE);
        for (int i = 1; i < TOURNAMENT_SIZE; i++) {
            int cand = rng.nextInt(POP_SIZE);
            if (fitness[cand] > fitness[best]) best = cand;
        }
        return best;
    }

    private void crossover(int[] a, int[] b, int[] child) {
        int cutTurn = 1 + rng.nextInt(HORIZON - 1);
        int cut = cutTurn * GameState.SLOTS_PER_TURN;
        System.arraycopy(a, 0, child, 0, cut);
        System.arraycopy(b, cut, child, cut, CHROMO_LEN - cut);
    }

    private void mutate(int[] chromo, GameState state) {
        int myCount = state.myTrollCount;
        for (int t = 0; t < HORIZON; t++) {
            int base = t * GameState.SLOTS_PER_TURN;
            for (int s = 0; s < GameState.MAX_TROLLS; s++) {
                if (rng.nextInt(100) < MUTATION_PCT) {
                    chromo[base + s] = (s < myCount) ? randomTrollGene(s, state) : Actions.waitAction();
                }
            }
            if (rng.nextInt(100) < MUTATION_PCT) {
                chromo[base + GameState.TRAIN_SLOT] = randomTrainGene();
            }
        }
    }

    private int argmaxFitness() {
        int best = 0;
        for (int i = 1; i < POP_SIZE; i++) {
            if (fitness[i] > fitness[best]) best = i;
        }
        return best;
    }

    private int writeFirstTurnActions(int[] chromo, GameState state, int[] out) {
        int n = 0;
        for (int s = 0; s < state.myTrollCount; s++) {
            int trollIdx = state.myTrollSlot[s];
            out[n++] = retypeTroll(chromo[s], trollIdx);
        }
        int trainSlot = chromo[GameState.TRAIN_SLOT];
        if (Actions.type(trainSlot) == ActionType.TRAIN) {
            out[n++] = trainSlot;
        }
        return n;
    }

    private int retypeTroll(int packed, int trollIdx) {
        int type = Actions.type(packed);
        int p1   = Actions.p1(packed);
        int p2   = Actions.p2(packed);
        int p3   = Actions.p3(packed);
        return Actions.encode(type, trollIdx, p1, p2, p3);
    }
}
