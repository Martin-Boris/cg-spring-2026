package com.bmrt.cgspring2026.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreeTypeTest {

    @Test void plumFromString()   { assertThat(TreeType.fromString("PLUM")).isEqualTo(TreeType.PLUM); }
    @Test void lemonFromString()  { assertThat(TreeType.fromString("LEMON")).isEqualTo(TreeType.LEMON); }
    @Test void appleFromString()  { assertThat(TreeType.fromString("APPLE")).isEqualTo(TreeType.APPLE); }
    @Test void bananaFromString() { assertThat(TreeType.fromString("BANANA")).isEqualTo(TreeType.BANANA); }
}
