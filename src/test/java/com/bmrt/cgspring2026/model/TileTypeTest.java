package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TileTypeTest {

    @Test void grassFromDot()       { assertThat(TileType.fromChar('.')).isEqualTo(TileType.GRASS); }
    @Test void waterFromTilde()     { assertThat(TileType.fromChar('~')).isEqualTo(TileType.WATER); }
    @Test void rockFromHash()       { assertThat(TileType.fromChar('#')).isEqualTo(TileType.ROCK); }
    @Test void ironFromPlus()       { assertThat(TileType.fromChar('+')).isEqualTo(TileType.IRON); }
    @Test void shackMeFromZero()    { assertThat(TileType.fromChar('0')).isEqualTo(TileType.SHACK_ME); }
    @Test void shackOppFromOne()    { assertThat(TileType.fromChar('1')).isEqualTo(TileType.SHACK_OPP); }
}
