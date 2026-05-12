import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Objects;
import java.util.Comparator;
import java.util.ArrayList;
class Player {
	private static record Troll(int id, int player, int x, int y, int movementSpeed, int carryCapacity, int harvestPower,                     int chopPower, int carryPlum, int carryLemon, int carryApple, int carryBanana, int carryIron,                     int carryWood) {
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
	private static class TreeComparator {
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
	private static class Tree {
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
            turns++;
        }
    }
}
