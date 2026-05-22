package com.bmrt.cgspring2026.ga;

import java.util.SplittableRandom;

public final class Selection {

    private Selection() {}

    public static int tournament(double[] fit, SplittableRandom rng, int popSize, int k) {
        int best = rng.nextInt(popSize);
        double bestFit = fit[best];
        for (int i = 1; i < k; i++) {
            int c = rng.nextInt(popSize);
            if (fit[c] > bestFit) {
                best = c;
                bestFit = fit[c];
            }
        }
        return best;
    }
}
