package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomWalkTest {

    private GameState openMap(int w, int h) {
        GameState s = new GameState();
        s.width = w;
        s.height = h;
        s.grid = new byte[w * h];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        return s;
    }

    private Troll troll(int id, int x, int y) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        return t;
    }

    @Test
    void returns_move_to_walkable_cell_different_from_current() {
        GameState s = openMap(10, 10);
        s.turn = 5;
        Troll t = troll(3, 5, 5);

        Action.Move move = RandomWalk.pick(t, s);

        assertThat(move.trollId()).isEqualTo(3);
        assertThat(s.walkable(move.x(), move.y())).isTrue();
        assertThat(move.x() == 5 && move.y() == 5).isFalse();
    }

    @Test
    void same_seed_yields_same_destination() {
        GameState s1 = openMap(10, 10);
        s1.turn = 7;
        GameState s2 = openMap(10, 10);
        s2.turn = 7;
        Troll t1 = troll(2, 4, 4);
        Troll t2 = troll(2, 4, 4);

        Action.Move m1 = RandomWalk.pick(t1, s1);
        Action.Move m2 = RandomWalk.pick(t2, s2);

        assertThat(m1).isEqualTo(m2);
    }
}
