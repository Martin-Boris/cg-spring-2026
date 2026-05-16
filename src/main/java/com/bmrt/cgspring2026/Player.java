package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.ga.GeneticAgent;
import com.bmrt.cgspring2026.ga.Genome;
import com.bmrt.cgspring2026.greedy.ShackAdjacency;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.pathfinding.PathTable;

import java.util.Scanner;

public class Player {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        GameState.readInit(in);
        PathTable.init();
        ShackAdjacency.init();

        GameState state = new GameState();
        GeneticAgent agent = new GeneticAgent();
        int[] actionBuf = new int[GameState.MAX_TROLLS + 1];
        StringBuilder sb = new StringBuilder();

        boolean firstTurn = true;
        while (true) {

            state.readTurn(in);
            long start = System.nanoTime();


            long deadline = start + (firstTurn ? GeneticAgent.INIT_BUDGET_NS
                    : GeneticAgent.TURN_BUDGET_NS);
            int n = agent.decide(state, deadline, actionBuf);
            firstTurn = false;

            sb.setLength(0);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(';');
                sb.append(Action.toCommand(actionBuf[i], state));
            }
            sb.append(";MSG GA gen=").append(agent.lastGenCount())
                    .append(" fit=").append((int) agent.lastBestFitness())
                    .append(" t=").append((System.nanoTime() - start) / 1_000_000).append("ms");
            if (agent.lastTrollChurn() >= 0) {
                sb.append(" chg=").append(agent.lastTrollChurn())
                        .append("/").append(agent.lastActiveTrolls());
            }
            if (agent.lastHamming() >= 0) {
                sb.append(" dh=").append(agent.lastHamming());
            }
            sb.append(" tie=").append(agent.lastTieCount());
            if (agent.lastCoverageInit() >= 0) {
                sb.append(" cov=").append(agent.lastBestCoverage())
                        .append('|').append(agent.lastCoverageFinal())
                        .append('|').append(agent.lastCoverageInit())
                        .append('/').append(agent.lastAliveTrees());
                sb.append(" avgCov=").append(agent.lastIndivCoverageSum() / Genome.POP_SIZE)
                        .append(" tot=").append(agent.lastTotalCuts())
                        .append(" unres=").append(agent.lastUnresolvedGenes());
                if (state.turn == 0) sb.append(" w=").append(GameState.width);
            }
            System.out.println(sb);

            state.turn++;
        }
    }
}
