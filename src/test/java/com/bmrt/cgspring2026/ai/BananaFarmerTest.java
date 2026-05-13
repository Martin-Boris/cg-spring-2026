package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.ResourceType;
import com.bmrt.cgspring2026.model.Tile;
import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.TreeType;
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
    void drops_when_adjacent_to_shack_and_carrying_wood() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.WOOD.ordinal()] = 1;
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Drop(0));
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

    @Test
    void chops_size_zero_banana_under_troll_and_marks_assigned() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(7, 6, 1, 5);
        s.trolls.add(t);
        Tree banana = new Tree();
        banana.type = TreeType.BANANA;
        banana.x = 6;
        banana.y = 1;
        banana.size = 0;
        banana.health = 1;
        s.trees.add(banana);
        int[] budget = {0};
        java.util.Set<Long> assigned = new HashSet<>();

        Action a = BananaFarmer.plan(t, s, budget, assigned);

        assertThat(a).isEqualTo(new Action.Chop(7));
        assertThat(assigned).contains((long) 1 * 10 + 6);
    }

    @Test
    void plants_banana_when_carrying_exactly_one_banana_and_tile_empty() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.BANANA.ordinal()] = 1;
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Plant(0, TreeType.BANANA));
    }

    @Test
    void does_not_plant_when_carry_mixed_with_other_fruit() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.BANANA.ordinal()] = 1;
        t.carry[ResourceType.PLUM.ordinal()] = 1;
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
    }

    @Test
    void is_safe_to_farm_when_no_enemy() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);

        assertThat(BananaFarmer.isSafeToFarm(6, 1, s)).isTrue();
    }

    @Test
    void does_not_plant_when_tile_already_has_tree() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.BANANA.ordinal()] = 1;
        s.trolls.add(t);
        Tree existing = new Tree();
        existing.type = TreeType.PLUM;
        existing.x = 6;
        existing.y = 1;
        existing.size = 2;
        s.trees.add(existing);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
    }

    @Test
    void picks_banana_when_empty_adjacent_and_budget_available() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        s.trolls.add(t);
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Pick(0, ResourceType.BANANA));
        assertThat(budget[0]).isZero();
    }

    @Test
    void does_not_pick_when_budget_zero() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        s.trolls.add(t);
        int[] budget = {0};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
        assertThat(budget[0]).isZero();
    }

    @Test
    void does_not_pick_when_carry_not_empty() {
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.PLUM.ordinal()] = 1;
        s.trolls.add(t);
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
        assertThat(budget[0]).isOne();
    }

    private Troll enemy(int id, int x, int y, int speed) {
        Troll t = new Troll();
        t.id = id;
        t.player = 1;
        t.x = x;
        t.y = y;
        t.movementSpeed = speed;
        return t;
    }

    @Test
    void does_not_pick_when_enemy_reach_is_three_turns() {
        // distance 6, speed 2 => reach = 3 => unsafe
        GameState s = openMap(20, 4, 5, 1, 15, 2);
        Troll t = troll(0, 6, 1, 5);
        s.trolls.add(t);
        s.trolls.add(enemy(99, 12, 1, 2));
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isNull();
        assertThat(budget[0]).isOne();
    }

    @Test
    void picks_when_enemy_reach_is_four_turns() {
        // distance 7, speed 2 => reach = 4 => safe
        GameState s = openMap(20, 4, 5, 1, 15, 2);
        Troll t = troll(0, 6, 1, 5);
        s.trolls.add(t);
        s.trolls.add(enemy(99, 13, 1, 2));
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Pick(0, ResourceType.BANANA));
    }

    @Test
    void ignores_enemy_with_zero_movement_speed() {
        // Adjacent enemy but movementSpeed=0 => not a threat
        GameState s = openMap(20, 4, 5, 1, 15, 2);
        Troll t = troll(0, 6, 1, 5);
        s.trolls.add(t);
        s.trolls.add(enemy(99, 7, 1, 0));
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Pick(0, ResourceType.BANANA));
    }

    @Test
    void drop_takes_priority_over_chop_when_both_apply() {
        // Troll carries wood AND stands on a size-0 banana — DROP must win.
        GameState s = openMap(10, 4, 5, 1, 8, 2);
        Troll t = troll(0, 6, 1, 5);
        t.carry[ResourceType.WOOD.ordinal()] = 1;
        s.trolls.add(t);
        Tree banana = new Tree();
        banana.type = TreeType.BANANA;
        banana.x = 6;
        banana.y = 1;
        banana.size = 0;
        banana.health = 1;
        s.trees.add(banana);
        int[] budget = {1};

        Action a = BananaFarmer.plan(t, s, budget, new HashSet<>());

        assertThat(a).isEqualTo(new Action.Drop(0));
    }
}
