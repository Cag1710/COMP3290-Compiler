public class TreePrinter {
    static void print(StNode n, int indent) {
        if(n == null) return;

        for(int i = 0; i < indent; i++) System.out.println(" ");
        System.out.println(n);
        for(StNode c : n.children()) {
            print(c, indent + 1);
        }
    }
}
