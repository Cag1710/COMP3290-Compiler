import java.time.format.TextStyle;

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
        
        // CD25
        Token tCD25 = ts.expect(TokenType.TCD25);
        if(tCD25 == null) {
            er.syntax("expected 'cd25' at program start", ts.peek());
            prog.add(StNode.undefAt(ts.peek()));
        }

        // CD25 prodId
        Token progId = ts.expect(TokenType.TIDEN);
        if(progId == null) {
            er.syntax("expected program identifier after 'cd25'", ts.peek());
            prog.add(StNode.undefAt(ts.peek()));
        } else {
            prog.add(StNode.leaf(StNodeKind.NSIMV, progId));
        }

        prog.add(parseGlobals());

        // prog.add(parseFunc());

        prog.add(parseMainBody());

        return prog;
    }

    private StNode parseGlobals() {
        return new StNode(StNodeKind.NGLOB, null, ts.peek().line, ts.peek().col);
    }

    private StNode parseFunc() {
        StNode temp = new StNode(null, null, 0, 0);
        return temp;
    }

    private StNode parseMainBody() {
        StNode n = new StNode(StNodeKind.NMAIN, null, ts.peek().line, ts.peek().col);

        // main
        if(ts.expect(TokenType.TMAIN) == null) {
            er.syntax("expected 'main'", ts.peek());
        }
        
        // declare list
        n.add(new StNode(StNodeKind.NSDLST, null, ts.peek().line, ts.peek().col));

        // begin
        if(ts.expect(TokenType.TBEGN) == null) {
            er.syntax("expected 'begin' before statements", ts.peek());
        }

        n.add(parseStats());

        // end
        if(ts.expect(TokenType.TTEND) == null) {
            er.syntax("expected 'end' after statements", ts.peek());
        }

        // end CD25
        if(ts.expect(TokenType.TCD25) == null) {
            er.syntax("expected trailing 'cd25'", ts.peek());
        }

        // end CD25 progId
        Token tail = ts.expect(TokenType.TIDEN);
        if (tail == null) {
            er.syntax("expected identifier after trailing 'cd25'", ts.peek());
        } else {
            n.add(StNode.leaf(StNodeKind.NSIMV, tail));
        }

        return n;
    }












    // parsing fuckin everything
    // technically i could probs collapse some of these into one, but this keeps it best in terms of clarity to the pdf
    private StNode parseStats() {
        StNode stats = new StNode(StNodeKind.NSTATS, null, ts.peek().line, ts.peek().col);

        return stats;
    }

    private StNode parseIoStat() {
        return null;
    }

    private StNode parseAsgnOrCall() {
        return null;
    }

    private StNode parseReturnStat() {
        return null;
    }

    private StNode parseVList() {
        return null;
    }

    private StNode parseVar() {
        return null;
    }

    private StNode parseEList() {
        return null;
    }

    private StNode parseBool() {
        return null;
    }

    private StNode parseRel() {
        return null;
    }

    private StNode parseLogOp() {
        return null;
    }

    private StNode parseRelOp() {
        return null;
    }

    private StNode parseExpr() {
        return null;
    }

    private StNode parseTerm() {
        return null;
    }

    private StNode parseFact() {
        return null;
    }

    private StNode parseExpo() {
        return null;
    }

    private StNode parseFnCall() {
        return null;
    }

    private StNode parsePrList() {
        return null;
    }

    private StNode parsePrintItem() {
        return null;
    }
}
