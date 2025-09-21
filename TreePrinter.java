import java.io.PrintStream;

final class TreePrinter {

    private static final int COLS_PER_LINE = 10;
    private static final int CELLS = 7;


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

    public static void printReport(StNode root, ErrorReporter er, String listingOrNull, PrintStream out) {

        printPreOrder(root, out);

        if (er != null && !er.all().isEmpty()) {
            out.println("\n==== Errors ====");
            for (CompilerError e : er.all()) {
                out.print(e.toString());
            }
        }
    }

    private static void printPreOrder(StNode root, PrintStream out) {
        CellWriter w = new CellWriter(out, COLS_PER_LINE, CELLS);
        emitNode(root, w);
        w.flushLineIfOpen();
    }

    private static void emitNode(StNode n, CellWriter w) {
        if (n == null) return;

        w.emit(n.kind.name());

        String payload = payloadFor(n);

        if (payload != null && !payload.isEmpty()) {
            w.emit(payload);
        }

        for (StNode c : n.children()) {
            emitNode(c, w);
        }
    }

    private static String payloadFor(StNode n) {
        switch (n.kind) {
            case NSIMV:
            case NSTRG:
            case NILIT:
            case NFLIT:
            case NSTYPE:
                return n.lexeme;
            default:
                return null;
        }
    }

    private static final class CellWriter {
        private final PrintStream out;
        private final int colsPerLine;
        private final int cells;
        private int colCount = 0;

        CellWriter(PrintStream out, int colsPerLine, int cells) {
            this.out = out;
            this.colsPerLine = colsPerLine;
            this.cells = cells;
        }

        void emit(String s) {
            out.print(padCell(s, cells));
            colCount++;

            if (colCount == colsPerLine) {
                out.println();
                colCount = 0;
            }
        }

        void flushLineIfOpen() {
            if (colCount != 0) {
                out.println();
            }
            colCount = 0;
        }

        private static String padCell(String raw, int cells) {
            String s = (raw == null) ? "" : raw;
            int needed = s.length() + 1;
            int width = ((needed + cells - 1) / cells) * cells;
            StringBuilder sb = new StringBuilder(width);

            sb.append(s);
            while (sb.length() < width) {
                sb.append(" ");
            }
            return sb.toString();
        }
    }
    
}
