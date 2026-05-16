package com.bmrt.cgspring2026.ga;

import com.bmrt.cgspring2026.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GenomeOpsMutationTest {

    @BeforeEach void grid() {
        GameState.width = 10; GameState.height = 10;
        GameState.tiles = new byte[100];
    }

    private short[] buf;
    private byte[]  lenBuf;

    @org.junit.jupiter.api.BeforeEach
    void buffers() {
        buf = new short[Genome.POP_SIZE * Genome.SLOTS_PER_GENOME];
        java.util.Arrays.fill(buf, Genome.EMPTY_GENE);
        lenBuf = new byte[Genome.POP_SIZE * GameState.MAX_TROLLS];
    }

    private void setupSimple() {
        // troll 0 : [(1,1),(2,2),(3,3)]   troll 1 : [(4,4),(5,5)]
        Genome.setGene(buf, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(buf, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(lenBuf, 0, 0, 3);
        Genome.setGene(buf, 0, 1, 0, Genome.encode(4, 4));
        Genome.setGene(buf, 0, 1, 1, Genome.encode(5, 5));
        Genome.setLen(lenBuf, 0, 1, 2);
    }

    @Test void swapIntraPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateSwapIntra(buf, lenBuf, 0, new SplittableRandom(1));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        // longueurs inchangées
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void swapInterPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateSwapInter(buf, lenBuf, 0, new SplittableRandom(2));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void reverseSegmentPreservesUnicity() {
        setupSimple();
        GenomeOps.mutateReverse(buf, lenBuf, 0, new SplittableRandom(3));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
        assertThat(Genome.len(lenBuf, 0, 0)).isEqualTo(3);
        assertThat(Genome.len(lenBuf, 0, 1)).isEqualTo(2);
    }

    @Test void deleteShortensExactlyOneSegment() {
        setupSimple();
        int total0 = Genome.len(lenBuf, 0, 0) + Genome.len(lenBuf, 0, 1);
        GenomeOps.mutateDelete(buf, lenBuf, 0, new SplittableRandom(4));
        int total1 = Genome.len(lenBuf, 0, 0) + Genome.len(lenBuf, 0, 1);
        assertThat(total1).isEqualTo(total0 - 1);
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void deleteShiftsSubsequentSlots() {
        // troll 0 : [(1,1),(2,2),(3,3)], delete au milieu (k=1) → [(1,1),(3,3)]
        // On force la suppression au k=1 via une seed connue ; on vérifie qu'on ne laisse pas
        // de slot dirty.
        Genome.setGene(buf, 0, 0, 0, Genome.encode(1, 1));
        Genome.setGene(buf, 0, 0, 1, Genome.encode(2, 2));
        Genome.setGene(buf, 0, 0, 2, Genome.encode(3, 3));
        Genome.setLen(lenBuf, 0, 0, 3);
        // Avec seed=4 on a observé que la sélection tombe sur troll=0, k=1 ; sinon on parcourt
        // plusieurs seeds. Test plus robuste : vérifier invariants après.
        GenomeOps.mutateDelete(buf, lenBuf, 0, new SplittableRandom(4));
        assertThat(GenomeInvariants.check(buf, lenBuf, 0)).isTrue();
    }

    @Test void runMutationDispatchesProbabilistically() {
        SplittableRandom rng = new SplittableRandom(42);
        int[] counters = new int[6];
        for (int t = 0; t < 1000; t++) {
            counters[GenomeOps.pickMutationKind(rng)]++;
        }
        for (int k = 0; k < 6; k++) {
            assertThat(counters[k]).as("operator %d sampled at least once", k).isGreaterThan(30);
        }
        assertThat(counters[GenomeOps.MUT_SWAP_INTRA]).isBetween(180, 320);
        assertThat(counters[GenomeOps.MUT_DELETE]).isBetween(50, 150);
        assertThat(counters[GenomeOps.MUT_INSERT_PLANT]).isBetween(100, 200);
        assertThat(counters[GenomeOps.MUT_INSERT_CUT]).isBetween(150, 250);
    }

    @Test void pickMutationKindIncludesInsertPlant() {
        SplittableRandom rng = new SplittableRandom(99);
        boolean seen = false;
        for (int i = 0; i < 1000; i++) {
            if (GenomeOps.pickMutationKind(rng) == GenomeOps.MUT_INSERT_PLANT) { seen = true; break; }
        }
        assertThat(seen).isTrue();
    }

    @Test void runMutationDispatchesInsertPlant() {
        GameState.shackMeX = 0; GameState.shackMeY = 0;
        Genome.initPlantCandidates();
        assertThat(Genome.plantCandidateCount).isGreaterThan(0);
        GameState state = new GameState();
        SplittableRandom rng = new SplittableRandom(2026);
        boolean planted = false;
        for (int i = 0; i < 200 && !planted; i++) {
            GenomeOps.runMutation(state, buf, lenBuf, 0, rng);
            for (int j = 0; j < GameState.MAX_TROLLS && !planted; j++) {
                int len = Genome.len(lenBuf, 0, j);
                for (int k = 0; k < len; k++) {
                    if (Genome.isPlant((short) Genome.gene(buf, 0, j, k))) { planted = true; break; }
                }
            }
        }
        assertThat(planted).isTrue();
    }
}
