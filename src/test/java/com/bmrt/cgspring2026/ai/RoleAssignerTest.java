package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.Troll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignerTest {

    private Troll troll(int id, int ms, int cc, int cp) {
        Troll t = new Troll();
        t.id = id;
        t.player = 0;
        t.movementSpeed = ms;
        t.carryCapacity = cc;
        t.chopPower = cp;
        return t;
    }

    @Test
    void single_troll_is_local() {
        Map<Integer, Role> roles = RoleAssigner.assign(List.of(troll(0, 1, 1, 1)));

        assertThat(roles).containsExactly(Map.entry(0, Role.LOCAL));
    }

    @Test
    void highest_score_becomes_leader() {
        Troll weak = troll(0, 1, 1, 1);   // score 3
        Troll strong = troll(1, 3, 2, 4); // score 9

        Map<Integer, Role> roles = RoleAssigner.assign(List.of(weak, strong));

        assertThat(roles.get(0)).isEqualTo(Role.LOCAL);
        assertThat(roles.get(1)).isEqualTo(Role.LEADER);
    }

    @Test
    void tie_broken_by_smallest_id() {
        Troll a = troll(2, 2, 2, 2); // score 6, id=2
        Troll b = troll(0, 2, 2, 2); // score 6, id=0

        Map<Integer, Role> roles = RoleAssigner.assign(List.of(a, b));

        assertThat(roles.get(0)).isEqualTo(Role.LEADER);
        assertThat(roles.get(2)).isEqualTo(Role.LOCAL);
    }
}
