package com.bmrt.cgspring2026.ga;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTest {

    @Test void tournamentFavorsHigherFitness() {
        double[] fit = new double[]{ 0.0, 10.0, 5.0, 20.0 };
        int wins = 0;
        SplittableRandom rng = new SplittableRandom(0);
        for (int t = 0; t < 1000; t++) {
            int pick = Selection.tournament(fit, rng, fit.length, 2);
            if (fit[pick] >= 10.0) wins++;
        }
        // dans la moitié des cas un des deux tirages est ≤ 5.0 → l'autre gagne. Donc on devrait
        // toujours préférer ≥ 10.0 quand l'un est ≥ 10. Au pire 25% des paires sont {0,5}.
        assertThat(wins).isGreaterThan(700);
    }

    @Test void tournamentWithSingleIndividualReturnsIt() {
        double[] fit = new double[]{ 42.0 };
        int pick = Selection.tournament(fit, new SplittableRandom(1), 1, 2);
        assertThat(pick).isEqualTo(0);
    }
}
