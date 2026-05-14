package com.bmrt.cgspring2026.pathfinding;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTableInitBudgetTest {

    @Test void initFitsInOneSecondOnMaxMap() {
        // Carte max : 22 × 11 entièrement GRASS = 242 cases (worst case all-pairs).
        GameState.width = 22;
        GameState.height = 11;
        GameState.tiles = new byte[22 * 11];
        java.util.Arrays.fill(GameState.tiles, TileType.GRASS);

        // warm-up JIT
        PathTable.init();

        long start = System.nanoTime();
        PathTable.init();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // budget tour 1 = 1000 ms ; on s'autorise 200 ms ici (large marge pour le reste : I/O, autres init)
        assertThat(elapsedMs).isLessThan(200L);
        assertThat(PathTable.N).isEqualTo(242);
    }
}
