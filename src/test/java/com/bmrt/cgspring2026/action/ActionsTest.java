package com.bmrt.cgspring2026.action;

import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TreeType;
import com.bmrt.cgspring2026.support.GameStateBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionsTest {

    @Test
    void encode_decode_move_roundtrips() {
        int packed = Actions.move(5, 200);
        assertThat(Actions.type(packed)).isEqualTo((int) ActionType.MOVE);
        assertThat(Actions.trollIdx(packed)).isEqualTo(5);
        assertThat(Actions.p1(packed)).isEqualTo(200);
    }

    @Test
    void encode_decode_train_roundtrips() {
        int packed = Actions.train(2, 3, 1, 4);
        assertThat(Actions.type(packed)).isEqualTo((int) ActionType.TRAIN);
        assertThat(Actions.p1(packed)).isEqualTo(2);
        assertThat(Actions.p2(packed)).isEqualTo(3);
        int p3 = Actions.p3(packed);
        assertThat(p3 & 0x7).isEqualTo(1);
        assertThat((p3 >>> 3) & 0x7).isEqualTo(4);
    }

    @Test
    void wait_action_format_emits_WAIT_keyword() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .build();

        assertThat(Actions.format(Actions.waitAction(), s)).isEqualTo("WAIT");
    }

    @Test
    void move_format_uses_original_troll_id_and_coordinates() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(42, 0, 0, 1, 1, 1, 1)
                .build();

        int packed = Actions.move(0, 3 * s.width + 2);
        assertThat(Actions.format(packed, s)).isEqualTo("MOVE 42 2 3");
    }

    @Test
    void plant_format_uses_tree_type_name() {
        GameState s = new GameStateBuilder()
                .withGrid("0...",
                          "....",
                          "....",
                          "....")
                .withMyTroll(11, 0, 1, 1, 1, 1, 1)
                .build();

        int packed = Actions.plant(0, TreeType.BANANA);
        assertThat(Actions.format(packed, s)).isEqualTo("PLANT 11 BANANA");
    }
}
