package com.bmrt.cgspring2026.ga;

import java.util.SplittableRandom;

public final class Selection {

    private Selection() {}

    public static int tournament(double[] fit, SplittableRandom rng, int popSize) {
        int a = rng.nextInt(popSize);
        int b = rng.nextInt(popSize);
        return (fit[a] >= fit[b]) ? a : b;
    }
}
