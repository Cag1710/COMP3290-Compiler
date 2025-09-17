public class Parser {
    private final TokenStream ts;
    private final ErrorReporter er;

    public Parser(TokenStream ts, ErrorReporter er) {
        this.ts = ts;
        this.er = er;
    }

    public StNode parseProgram() {
        StNode prog = new StNode(StNodeKind.NPROG, null, ts.peek().line, ts.peek().col);

        // from here we can just parse the rest of the things by calling other methods e.g. parseFuncs, parseGlobals etc. and continually add them to prog -> prog.add(parseFuncs) etc.

        return prog;
    }
}
