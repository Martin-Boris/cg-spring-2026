package com.bmrt.cgspring2026.model;

public class Troll {

    public int id;
    public int player;
    public int x;
    public int y;
    public int movementSpeed;
    public int carryCapacity;
    public int harvestPower;
    public int chopPower;
    public int[] carry = new int[ResourceType.COUNT];

    public Troll() {
    }

    public Troll copy() {
        Troll c = new Troll();
        c.id = this.id;
        c.player = this.player;
        c.x = this.x;
        c.y = this.y;
        c.movementSpeed = this.movementSpeed;
        c.carryCapacity = this.carryCapacity;
        c.harvestPower = this.harvestPower;
        c.chopPower = this.chopPower;
        c.carry = this.carry.clone();
        return c;
    }

    public int carryTotal() {
        int sum = 0;
        for (int v : carry) {
            sum += v;
        }
        return sum;
    }
}
