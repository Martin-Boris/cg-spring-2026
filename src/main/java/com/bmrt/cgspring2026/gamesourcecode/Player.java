package com.bmrt.cgspring2026.gamesourcecode;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class Player {
    private ArrayList<Unit> units;
    private Inventory inventory;
    private Cell shack;
    private String message = "";
    private ArrayList<String> summaries = new ArrayList<>();
    private boolean active = true;
    private final int index;
    private int score = 0;

    public Player(ArrayList<Unit> units, Inventory inventory, Cell shack, String message, ArrayList<String> summaries, int index, int score) {
        this.units = units;
        this.inventory = inventory;
        this.shack = shack;
        this.message = message;
        this.summaries = summaries;
        this.index = index;
        this.score = score;
    }

    public void init(Cell shack, int league) {
        this.units = new ArrayList<>();
        this.inventory = new Inventory();
        this.shack = shack;
        Unit unit = new Unit(this, new int[]{1, 1, 1, league >= 3 ? 1 : 0}, league);
    }

    public void setInventory(int[] inventory) {
        for (int i = 0; i < inventory.length; i++) this.inventory.setItem(i, inventory[i]);
    }

    public ArrayList<Unit> getUnits() {
        return units;
    }

    public void AddUnit(Unit unit) {
        units.add(unit);
    }

    public Cell getShack() {
        return shack;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void recomputeScore() {
        if (isActive())
            setScore(inventory.getItemCount(Item.PLUM) +
                    inventory.getItemCount(Item.LEMON) +
                    inventory.getItemCount(Item.APPLE) +
                    inventory.getItemCount(Item.BANANA) +
                    Constants.WOOD_POINTS * inventory.getItemCount(Item.WOOD));
    }

    public int getColor() {
        return getIndex() == 0 ? 0xff8080 : 0x8080ff;
    }

    public int getLightColor() {
        return getIndex() == 0 ? 0xffa0a0 : 0xa0a0ff;
    }

    public int getDarkColor() {
        return getIndex() == 0 ? 0x603030 : 0x303060;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        if (this.message.length() > 50) this.message = this.message.substring(0, 50);
    }

    public ArrayList<String> popSummaries() {
        ArrayList<String> result = summaries;
        summaries = new ArrayList<>();
        return result;
    }

    public void addSummary(String summary) {
        summaries.add(summary);
    }

    public final void setScore(int score) {
        this.score = score;
    }

    public final void deactivate() {
        this.active= false;
    }

    public final boolean isActive() {
        return this.active;
    }

    public final int getIndex() {
        return this.index;
    }

    public final int getScore() {
        return this.score;
    }

}
