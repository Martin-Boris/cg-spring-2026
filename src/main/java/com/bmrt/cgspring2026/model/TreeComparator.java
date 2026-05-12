package com.bmrt.cgspring2026.model;

import java.util.Comparator;

public class TreeComparator {

    public static Comparator<Tree> bySize_thenByDistanceTo(Troll troll) {
        return Comparator
                .comparingInt(Tree::size).reversed()
                .thenComparingDouble(tree -> distanceTo(tree, troll))
                .thenComparing(Comparator.comparingInt(Tree::fruits).reversed());
    }

    private static double distanceTo(Tree tree, Troll troll) {
        int dx = tree.x() - troll.x();
        int dy = tree.y() - troll.y();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
