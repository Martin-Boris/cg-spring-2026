package com.bmrt.cgspring2026.model;

import java.util.Objects;

public final class Tree {
    private final String type;
    private final int x;
    private final int y;
    private final int size;
    private final int health;
    private final int fruits;
    private final int cooldown;
    private boolean focused = false;

    public Tree(String type, int x, int y, int size, int health, int fruits, int cooldown) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.size = size;
        this.health = health;
        this.fruits = fruits;
        this.cooldown = cooldown;
    }

    @Override
    public String toString() {
        return "Tree[" +
                "type=" + type + ", " +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "size=" + size + ", " +
                "health=" + health + ", " +
                "fruits=" + fruits + ", " +
                "cooldown=" + cooldown + ']';
    }

    public String type() {
        return type;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int size() {
        return size;
    }

    public int health() {
        return health;
    }

    public int fruits() {
        return fruits;
    }

    public int cooldown() {
        return cooldown;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Tree) obj;
        return Objects.equals(this.type, that.type) &&
                this.x == that.x &&
                this.y == that.y &&
                this.size == that.size &&
                this.health == that.health &&
                this.fruits == that.fruits &&
                this.cooldown == that.cooldown;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, x, y, size, health, fruits, cooldown);
    }

    public void focus() {
        this.focused = true;
    }


    public boolean isFocused() {
        return focused;
    }
}
