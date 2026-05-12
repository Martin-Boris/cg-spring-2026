package com.bmrt.cgspring2026;

import com.bmrt.cgspring2026.model.Tree;
import com.bmrt.cgspring2026.model.Troll;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Player {

    private static final long FIRST_TURN_BUDGET_MS = 900;
    private static final long TURN_BUDGET_MS = 45;

    public static void main(String[] args) {
        int turns = 0;
        int shackX = 0;
        int shackY = 0;
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        if (in.hasNextLine()) {
            in.nextLine();
        }
        for (int i = 0; i < height; i++) {
            String line = in.nextLine();
        }

        // game loop
        while (true) {
            for (int i = 0; i < 2; i++) {
                int plum = in.nextInt();
                int lemon = in.nextInt();
                int apple = in.nextInt();
                int banana = in.nextInt();
                int iron = in.nextInt();
                int wood = in.nextInt();
            }
            int treesCount = in.nextInt();
            List<Tree> trees = new ArrayList<>();
            for (int i = 0; i < treesCount; i++) {
                String type = in.next();
                int x = in.nextInt();
                int y = in.nextInt();
                int size = in.nextInt();
                int health = in.nextInt();
                int fruits = in.nextInt();
                int cooldown = in.nextInt();
                trees.add(new Tree(type, x, y, size, health, fruits, cooldown));
            }
            int trollsCount = in.nextInt();
            List<Troll> myTrolls = new ArrayList<>();
            for (int i = 0; i < trollsCount; i++) {
                int id = in.nextInt();
                int player = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int movementSpeed = in.nextInt();
                int carryCapacity = in.nextInt();
                int harvestPower = in.nextInt();
                int chopPower = in.nextInt();
                int carryPlum = in.nextInt();
                int carryLemon = in.nextInt();
                int carryApple = in.nextInt();
                int carryBanana = in.nextInt();
                int carryIron = in.nextInt();
                int carryWood = in.nextInt();
                if (player == 0) {
                    myTrolls.add(new Troll(id, player, x, y, movementSpeed, carryCapacity, harvestPower, chopPower,
                            carryPlum, carryLemon, carryApple, carryBanana, carryIron, carryWood));
                    if (turns == 0) {
                        shackX = x;
                        shackY = y;
                    }
                }
            }
            String actionToDisplay = "";
            if (turns == 0) {
                actionToDisplay += "TRAIN " + " " + 1 + " " + 1 + " " + 1 + " " + 0 + ";";
            }

            for (Troll troll : myTrolls) {
                if (troll.carrySomething()) {
                    if (troll.adjacentTo(shackX, shackY)) {
                        actionToDisplay += "DROP " + troll.id() + ";";
                        continue;
                    }
                    actionToDisplay += "MOVE " + troll.id() + " " + (shackX) + " " + (shackY) + ";";
                    continue;
                }
                Tree treeOfInterest = troll.getTreeOfInterest(trees);
                treeOfInterest.focus();
                Optional<Tree> treeOnPosition = troll.getTreeOnPosition(trees);
                if (treeOnPosition.isPresent() && treeOnPosition.get().size() == 4) {
                    actionToDisplay += "HARVEST " + troll.id() + ";";
                    continue;
                }
                actionToDisplay += "MOVE " + troll.id() + " " + treeOfInterest.x() + " " + treeOfInterest.y() + ";";
            }
            System.out.println(actionToDisplay);

            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");


            // valid actions:
            // MOVE <id> <x> <y>
            // HARVEST <id> - when you are on the same cell as a tree
            // DROP <id> - when you are next to your shack and carry items
            turns++;
        }

    }
}
