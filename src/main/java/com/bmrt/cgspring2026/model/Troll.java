package com.bmrt.cgspring2026.model;

import java.util.List;
import java.util.Optional;


public record Troll(int id, int player, int x, int y, int movementSpeed, int carryCapacity, int harvestPower,
                    int chopPower, int carryPlum, int carryLemon, int carryApple, int carryBanana, int carryIron,
                    int carryWood) {


    public boolean carrySomething() {
        return carryPlum > 0 || carryLemon > 0 || carryApple > 0 || carryBanana > 0 || carryIron > 0 || carryWood > 0;
    }

    public Tree getTreeOfInterest(List<Tree> trees) {
        trees.sort(TreeComparator.bySize_thenByDistanceTo(this));
        return trees.stream().filter(tree -> !tree.isFocused()).findFirst().orElse(trees.get(0));
    }

    @Override
    public String toString() {
        return "Troll[" +
                "id=" + id + ", " +
                "player=" + player + ", " +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "movementSpeed=" + movementSpeed + ", " +
                "carryCapacity=" + carryCapacity + ", " +
                "harvestPower=" + harvestPower + ", " +
                "chopPower=" + chopPower + ", " +
                "carryPlum=" + carryPlum + ", " +
                "carryLemon=" + carryLemon + ", " +
                "carryApple=" + carryApple + ", " +
                "carryBanana=" + carryBanana + ", " +
                "carryIron=" + carryIron + ", " +
                "carryWood=" + carryWood + ']';
    }

    public Optional<Tree> getTreeOnPosition(List<Tree> trees) {
        for (Tree tree : trees) {
            if (tree.x() == x && tree.y() == y) {
                return Optional.of(tree);
            }
        }
        return Optional.empty();
    }

    public boolean adjacentTo(int shackX, int shackY) {
        return (Math.abs(x - shackX) == 1 && y == shackY) || (x == shackX && Math.abs(y - shackY) == 1);
    }
}
