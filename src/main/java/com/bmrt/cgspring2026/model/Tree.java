package com.bmrt.cgspring2026.model;

public class Tree {

    public TreeType type;
    public int x;
    public int y;
    public int size;
    public int health;
    public int fruits;
    public int cooldown;

    public Tree() {
    }

    public Tree copy() {
        Tree c = new Tree();
        c.type = this.type;
        c.x = this.x;
        c.y = this.y;
        c.size = this.size;
        c.health = this.health;
        c.fruits = this.fruits;
        c.cooldown = this.cooldown;
        return c;
    }
}
