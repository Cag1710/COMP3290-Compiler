public class Parser {
    private final TokenStream ts;
    private final ErrorReporter er;
    private final SymbolTable table;

    public Parser(TokenStream ts, SymbolTable table, ErrorReporter er) {
        this.ts = ts;
        this.table = table;
        this.er = er;
    }

    public StNode parseProgram() {
        StNode prog = new StNode(StNodeKind.NPROG, null, ts.peek().line, ts.peek().col);

        // from here we can just parse the rest of the things by calling other methods e.g. parseFuncs, parseGlobals etc. and continually add them to prog -> prog.add(parseFuncs) etc.

        return prog;
    }
}
