package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class BananaFarmerTest {

    private GameState openMap(int width, int height, int myX, int myY, int oppX, int oppY) {
        GameState s = new GameState();
        s.width = width;
        s.height = height;
        s.grid = new byte[width * height];
        for (int i = 0; i < s.grid.length; i++) {
            s.grid[i] = (byte) Tile.GRASS.ordinal();
        }
        s.grid[myY * width + myX] = (byte) Tile.SHACK_ME.ordinal();
        s.grid[oppY * width + oppX] = (byte) Tile.SHACK_OPP.ordinal();
        s.myShackX = myX;
        s.myShackY = myY;
        s.oppShackX = oppX;
        s.oppShackY = oppY;
        return s;
    }

    private Troll troll(int id, int x, int y, int cc) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.x = x;
        t.y = y;
        t.carryCapacity = cc;
        return t;
    }

    @Test
    void returns_null_when_troll_not_adjacent_to_shack() {
        GameState s = openMap(10, 4, 1, 1, 8, 2);
        Troll t = troll(0, 5, 1, 5);
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
    }
}
