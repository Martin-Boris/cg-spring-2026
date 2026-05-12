package com.bmrt.cgspring2026;

import java.util.Scanner;

public class Player {

    private static final long FIRST_TURN_BUDGET_MS = 900;
    private static final long TURN_BUDGET_MS = 45;

    public static void main(String[] args) {
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
            for (int i = 0; i < treesCount; i++) {
                String type = in.next();
                int x = in.nextInt();
                int y = in.nextInt();
                int size = in.nextInt();
                int health = in.nextInt();
                int fruits = in.nextInt();
                int cooldown = in.nextInt();
            }
            int trollsCount = in.nextInt();
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
            }

            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");


            // valid actions:
            // MOVE <id> <x> <y>
            // HARVEST <id> - when you are on the same cell as a tree
            // DROP <id> - when you are next to your shack and carry items
            System.out.println("MOVE 0 7 7");
        }

    }
}
