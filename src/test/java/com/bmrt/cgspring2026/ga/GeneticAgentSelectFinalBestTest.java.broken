package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests directs de selectFinalBest : tie-break par hystérésis post-process,
 * filtré par HYSTERESIS_TIEBREAK_EPS.
 */
class GeneticAgentSelectFinalBestTest {

    private static GeneticAgent agentWithPrevBest() {
        GeneticAgent agent = new GeneticAgent();
        agent.hasPrevBest = true;
        // prevBest : 1er gène troll 0 = (3,3), troll 1 = (5,5). longueurs > 0.
        agent.prevBestBuf[0 * Genome.MAX_TARGETS_PER_TROLL] = Genome.makeTarget(3, 3);
        agent.prevBestBuf[1 * Genome.MAX_TARGETS_PER_TROLL] = Genome.makeTarget(5, 5);
        agent.prevBestLen[0] = 1;
        agent.prevBestLen[1] = 1;
        return agent;
    }

    /**
     * Configure le génome d'index idx avec :
     *  - troll 0 : 1er gène = match0 (true → match prevBest sur troll 0)
     *  - troll 1 : 1er gène = match1 (true → match prevBest sur troll 1)
     */
    private static void setupGenome(GeneticAgent agent, int idx, boolean match0, boolean match1) {
        int base = Genome.offset(idx, 0);
        // Troll 0
        agent.pop.cur[base + 0 * Genome.MAX_TARGETS_PER_TROLL] =
                match0 ? Genome.makeTarget(3, 3) : Genome.makeTarget(7, 7);
        agent.pop.curLen[Genome.lenOffset(idx, 0)] = 1;
        // Troll 1
        agent.pop.cur[base + 1 * Genome.MAX_TARGETS_PER_TROLL] =
                match1 ? Genome.makeTarget(5, 5) : Genome.makeTarget(2, 2);
        agent.pop.curLen[Genome.lenOffset(idx, 1)] = 1;
        // Trolls restants : longueur 0 (n'entrent pas dans hysteresisMatches)
        for (int j = 2; j < GameState.MAX_TROLLS; j++) {
            agent.pop.curLen[Genome.lenOffset(idx, j)] = 0;
        }
    }

    @Test
    void picksHysteresisFriendlyWithinEps() {
        GeneticAgent agent = agentWithPrevBest();
        // idx 0 : fit=10.0, 0 match
        // idx 1 : fit=9.9,  2 matches  ← devrait gagner (écart = 0.1 ≤ 0.5)
        // idx 2 : fit=9.0,  2 matches  (écart = 1.0 > 0.5, exclu)
        setupGenome(agent, 0, false, false);
        setupGenome(agent, 1, true, true);
        setupGenome(agent, 2, true, true);
        double[] fit = new double[Genome.POP_SIZE];
        fit[0] = 10.0;
        fit[1] = 9.9;
        fit[2] = 9.0;
        for (int i = 3; i < Genome.POP_SIZE; i++) fit[i] = 0.0;

        int chosen = agent.selectFinalBest(fit);
        assertThat(chosen).isEqualTo(1);
    }

    @Test
    void ignoresHysteresisBeyondEps() {
        GeneticAgent agent = agentWithPrevBest();
        // idx 0 : fit=10.0, 0 match  ← gagne (top-1, idx 1 hors EPS)
        // idx 1 : fit=8.0,  2 matches (écart = 2.0 > 0.5, exclu)
        setupGenome(agent, 0, false, false);
        setupGenome(agent, 1, true, true);
        double[] fit = new double[Genome.POP_SIZE];
        fit[0] = 10.0;
        fit[1] = 8.0;
        for (int i = 2; i < Genome.POP_SIZE; i++) fit[i] = 0.0;

        int chosen = agent.selectFinalBest(fit);
        assertThat(chosen).isEqualTo(0);
    }

    @Test
    void fallsBackToHammingOnMatchTie() {
        GeneticAgent agent = agentWithPrevBest();
        // Étend prevBest : troll 0 a 2 gènes [(3,3), (4,4)], troll 1 a 1 gène [(5,5)].
        agent.prevBestBuf[0 * Genome.MAX_TARGETS_PER_TROLL + 1] = Genome.makeTarget(4, 4);
        agent.prevBestLen[0] = 2;

        // idx 0 et idx 1 ont même fitness ET même nombre de matches (2),
        // mais idx 0 est plus proche en Hamming (gène[1] troll 0 identique).
        int b0 = Genome.offset(0, 0);
        agent.pop.cur[b0 + 0] = Genome.makeTarget(3, 3); // match
        agent.pop.cur[b0 + 1] = Genome.makeTarget(4, 4); // match aussi (réduit Hamming)
        agent.pop.curLen[Genome.lenOffset(0, 0)] = 2;
        agent.pop.cur[b0 + 1 * Genome.MAX_TARGETS_PER_TROLL] = Genome.makeTarget(5, 5); // match
        agent.pop.curLen[Genome.lenOffset(0, 1)] = 1;
        for (int j = 2; j < GameState.MAX_TROLLS; j++) agent.pop.curLen[Genome.lenOffset(0, j)] = 0;

        int b1 = Genome.offset(1, 0);
        agent.pop.cur[b1 + 0] = Genome.makeTarget(3, 3); // match (1er gène)
        agent.pop.cur[b1 + 1] = Genome.makeTarget(9, 9); // diffère sur 2e gène
        agent.pop.curLen[Genome.lenOffset(1, 0)] = 2;
        agent.pop.cur[b1 + 1 * Genome.MAX_TARGETS_PER_TROLL] = Genome.makeTarget(5, 5); // match
        agent.pop.curLen[Genome.lenOffset(1, 1)] = 1;
        for (int j = 2; j < GameState.MAX_TROLLS; j++) agent.pop.curLen[Genome.lenOffset(1, j)] = 0;

        double[] fit = new double[Genome.POP_SIZE];
        fit[0] = 10.0;
        fit[1] = 10.0;
        for (int i = 2; i < Genome.POP_SIZE; i++) fit[i] = 0.0;

        int chosen = agent.selectFinalBest(fit);
        assertThat(chosen).isEqualTo(0);
    }

    @Test
    void noPrevBestReturnsFirstFitMax() {
        GeneticAgent agent = new GeneticAgent();
        // hasPrevBest=false par défaut. Doit retourner le premier idx atteignant fitMax.
        double[] fit = new double[Genome.POP_SIZE];
        fit[0] = 5.0;
        fit[1] = 10.0;
        fit[2] = 10.0;
        for (int i = 3; i < Genome.POP_SIZE; i++) fit[i] = 0.0;

        int chosen = agent.selectFinalBest(fit);
        assertThat(chosen).isEqualTo(1);
    }
}
