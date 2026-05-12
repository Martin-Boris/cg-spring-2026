package com.bmrt.cgspring2026.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GameState {

    public int width;
    public int height;
    public byte[] grid = new byte[0];

    public int myShackX;
    public int myShackY;
    public int oppShackX;
    public int oppShackY;

    public int[] myShackInv = new int[ResourceType.COUNT];
    public int[] oppShackInv = new int[ResourceType.COUNT];

    public List<Tree> trees = new ArrayList<>();
    public List<Troll> trolls = new ArrayList<>();

    public int turn;

    public GameState() {
    }

    public static void readInit(Scanner in, GameState state) {
        state.width = in.nextInt();
        state.height = in.nextInt();
        if (in.hasNextLine()) {
            in.nextLine();
        }
        state.grid = new byte[state.width * state.height];
        for (int y = 0; y < state.height; y++) {
            String line = in.nextLine();
            for (int x = 0; x < state.width; x++) {
                Tile t = Tile.from(line.charAt(x));
                state.grid[y * state.width + x] = (byte) t.ordinal();
                if (t == Tile.SHACK_ME) {
                    state.myShackX = x;
                    state.myShackY = y;
                } else if (t == Tile.SHACK_OPP) {
                    state.oppShackX = x;
                    state.oppShackY = y;
                }
            }
        }
    }

    public static void readTurn(Scanner in, GameState state) {
        state.turn++;
        for (int i = 0; i < ResourceType.COUNT; i++) {
            state.myShackInv[i] = in.nextInt();
        }
        for (int i = 0; i < ResourceType.COUNT; i++) {
            state.oppShackInv[i] = in.nextInt();
        }
        int treeCount = in.nextInt();
        state.trees.clear();
        for (int i = 0; i < treeCount; i++) {
            Tree t = new Tree();
            t.type = TreeType.parse(in.next());
            t.x = in.nextInt();
            t.y = in.nextInt();
            t.size = in.nextInt();
            t.health = in.nextInt();
            t.fruits = in.nextInt();
            t.cooldown = in.nextInt();
            state.trees.add(t);
        }
        int trollsCount = in.nextInt();
        state.trolls.clear();
        for (int i = 0; i < trollsCount; i++) {
            Troll tr = new Troll();
            tr.id = in.nextInt();
            tr.player = in.nextInt();
            tr.x = in.nextInt();
            tr.y = in.nextInt();
            tr.movementSpeed = in.nextInt();
            tr.carryCapacity = in.nextInt();
            tr.harvestPower = in.nextInt();
            tr.chopPower = in.nextInt();
            for (int k = 0; k < ResourceType.COUNT; k++) {
                tr.carry[k] = in.nextInt();
            }
            state.trolls.add(tr);
        }
    }

    public Tile tileAt(int x, int y) {
        return Tile.byOrdinal(grid[y * width + x]);
    }

    public boolean walkable(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return tileAt(x, y).isWalkable();
    }

    public Troll trollById(int id) {
        for (Troll t : trolls) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    public GameState copy() {
        GameState c = new GameState();
        c.width = this.width;
        c.height = this.height;
        c.grid = this.grid.clone();
        c.myShackX = this.myShackX;
        c.myShackY = this.myShackY;
        c.oppShackX = this.oppShackX;
        c.oppShackY = this.oppShackY;
        c.myShackInv = this.myShackInv.clone();
        c.oppShackInv = this.oppShackInv.clone();
        c.trees = new ArrayList<>(this.trees.size());
        for (Tree t : this.trees) {
            c.trees.add(t.copy());
        }
        c.trolls = new ArrayList<>(this.trolls.size());
        for (Troll tr : this.trolls) {
            c.trolls.add(tr.copy());
        }
        c.turn = this.turn;
        return c;
    }
}
