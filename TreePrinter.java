final class TreePrinter {
    static void print(StNode root) {
        printRec(root, 0);
    }

    private static void printRec(StNode node, int depth) {
        // indent by depth
        for (int i = 0; i < depth; i++) {
            System.out.print("  "); // 2 spaces per level
        }
        System.out.println(node);

        // print children indented
        for (StNode child : node.children()) {
            printRec(child, depth + 1);
        }
    }
}
