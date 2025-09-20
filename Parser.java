import java.time.format.TextStyle;
import java.util.Set;

public class Parser {
    private final TokenStream ts;
    private final ErrorReporter er;
    private final SymbolTable table;

    private static final Set<TokenType> STAT_FOLLOW = Set.of(
        TokenType.TSEMI,   // end of a simple statement
        TokenType.TTEND,   // end of block
        TokenType.TELSE,   // else
        TokenType.TUNTL,   // until 
        TokenType.T_EOF    // safety
    );

    private static final Set<TokenType> INIT_FOLLOW = Set.of(
        TokenType.TCOMA,    // next init statement
        TokenType.TTYPS,    // types block
        TokenType.TARRS,    // arrays block
        TokenType.TFUNC,    // funcs block
        TokenType.TMAIN     // main
    );

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

        // CD25 progId
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
        StNode glob = new StNode(StNodeKind.NGLOB, null, ts.peek().line, ts.peek().col);
        if (ts.expect(TokenType.TCONS) != null) {
            glob.add(parseConsts());
        }
        if (ts.expect(TokenType.TTYPS) != null) {
            glob.add(parseTypes());
        }
        if (ts.expect(TokenType.TARRS) != null) {
            glob.add(parseArrays());
        }
        return glob;
    }

    private StNode parseConsts() {
        StNode initList = new StNode(StNodeKind.NILIST, null, ts.peek().line, ts.peek().col);

        initList.add(parseInit());

        while(ts.match(TokenType.TCOMA)) {
            initList.add(parseInit());
        }

        return initList;
    }

    // TODO: parseExpo() needs implementation to work
    private StNode parseInit() {
        StNode init = new StNode(StNodeKind.NINIT, null, ts.peek().line, ts.peek().col);
        Token iden = ts.expect(TokenType.TIDEN);
        if (iden == null) {
            er.syntax("expected identifier in constant declaration", ts.peek());
            ts.syncTo(INIT_FOLLOW);
            return init;  // return dummy node to maintain AST
        }
        if (ts.expect(TokenType.TTTIS) == null) {
            er.syntax("expected 'is' in constant declaration", ts.peek());
        }

        StNode expr = parseExpr();
        init.add(expr);

        // TODO add iden and its value from expr into the symbol table
        return init;
    }

    private StNode parseTypes() {
        return null;
    }
    
    private StNode parseArrays() {
        return null;
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

        while(startsStat(ts.peek().tokenType)) {
            stats.add(parseStat());
            if(ts.expect(TokenType.TSEMI) == null) {
                er.syntax("expected ';' after statement", ts.peek());
                ts.syncTo(STAT_FOLLOW);
                ts.match(TokenType.TSEMI);
            }
        }

        return stats;
    }

    private boolean startsStat(TokenType t) {
        return t == TokenType.TINPT // In
        || t == TokenType.TOUTP // Out
        || t == TokenType.TRETN // Return
        || t == TokenType.TIDEN; // AsgnStat or CallStat
    }

    private StNode parseStat() {
        TokenType t = ts.peek().tokenType;
        if (t == TokenType.TINPT || t == TokenType.TOUTP) return parseIoStat();
        if (t == TokenType.TRETN) return parseReturnStat();
        if (t == TokenType.TIDEN) return parseAsgnOrCall();
        er.syntax("invalid statement start", ts.peek());
        return StNode.undefAt(ts.peek());
    }

    /**
     * <iostat> ::= In >> <vlist> | Out << <outTail> 
     * <outTail> ::= Line | <prlist> <lineTail>
     * <lineTail> ::=  << Line | ε
     */
    private StNode parseIoStat() {
        // In >>
        if(ts.match(TokenType.TINPT)) {
            if(ts.expect(TokenType.TGRGR) == null) { 
                er.syntax("expected '>>' after 'In'", ts.peek()); 
                return StNode.undefAt(ts.peek());
            }
            // In >> <vlist>
            StNode vlist = parseVList();
            return new StNode(StNodeKind.NINPUT, null, vlist.line, vlist.col).add(vlist);
        }
        
        // Out <<
        Token outTok = ts.expect(TokenType.TOUTP);
        if(outTok == null) {
            er.syntax("expected 'Out'", ts.peek());
            return StNode.undefAt(ts.peek());
        }
        if(ts.expect(TokenType.TLSLS) == null) {
            er.syntax("expected '<<' after 'Out'", ts.peek());
            return StNode.undefAt(ts.peek());
        }
        if(ts.match(TokenType.TOUTL)) {
            // Out << Line
            return new StNode(StNodeKind.NOUTL, null, outTok.line, outTok.col);
        }

        // Out << <prlist> << Line
        StNode list = parsePrList();
        if(ts.match(TokenType.TLSLS)) {
            if(ts.expect(TokenType.TOUTL) == null) {
                er.syntax("expected 'Line' after '<<'", ts.peek());
                return StNode.undefAt(ts.peek());
            }
            return new StNode(StNodeKind.NOUTL, null, outTok.line, outTok.col).add(list);
        }
        // Out << <prlist>
        return new StNode(StNodeKind.NOUTP, null, outTok.line, outTok.col).add(list);
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

    /**
     * <expr> ::= <term> <expr’> 
     * <expr’> ::= + <term> <expr’> | - <term> <expr’> | ε
     */
    private StNode parseExpr() {
        StNode left = parseTerm();
        while(true) {
            if(ts.match(TokenType.TPLUS)) {
                left = new StNode(StNodeKind.NADD, null, ts.peek().line, ts.peek().col).add(left).add(parseTerm());
            } else if (ts.match(TokenType.TMINS)) {
                left = new StNode(StNodeKind.NSUB, null, ts.peek().line, ts.peek().col).add(left).add(parseTerm());
            } else break;
        }
        return left;
    }

    /**
     * <term> ::= <fact> <term’>
     * <term’> ::= * <fact> <term’> | / <fact> <term’> | % <fact> <term’> | ε
     */
    private StNode parseTerm() {
        StNode left = parseFact();
        while(true) {
            if(ts.match(TokenType.TSTAR)) {
                left = new StNode(StNodeKind.NMUL, null, ts.peek().line, ts.peek().col).add(left).add(parseFact());
            } else if (ts.match(TokenType.TDIVD)) {
                left = new StNode(StNodeKind.NDIV, null, ts.peek().line, ts.peek().col).add(left).add(parseFact());
            } else if (ts.match(TokenType.TPERC)) {
                left = new StNode(StNodeKind.NMOD, null, ts.peek().line, ts.peek().col).add(left).add(parseFact());
            } else break;
        }
        return left;
    }

    /**
     * <fact> ::= <exponent> <fact’> 
     * <fact’> ::= ^ <exponent> <fact’> | ε 
     */
    private StNode parseFact() {
        StNode left = parseExpo();
    
        if (ts.match(TokenType.TCART)) {
            Token caret = ts.peek(); // store location if you want
            StNode right = parseFact(); // handles both <exponent> and <fact’>
            return new StNode(StNodeKind.NPOW, null, caret.line, caret.col)
                       .add(left)
                       .add(right);
        }
    
        return left;
    }

    private StNode parseExpo() {
        return null;
    }

    private StNode parseFnCall() {
        return null;
    }

    private StNode parsePrList() {
        StNode n = new StNode(StNodeKind.NPRLST, null, ts.peek().line, ts.peek().col);

        n.add(parsePrintItem());
        while(ts.match(TokenType.TCOMA)) {
            n.add(parsePrintItem());
        }

        return n;
    }

    // printitem ::= <expr> | <string>
    private StNode parsePrintItem() {
        if(ts.peek().tokenType == TokenType.TSTRG) {
            Token s = ts.expect(TokenType.TSTRG);
            return StNode.leaf(StNodeKind.NSTRG, s);
        }
        return parseExpr();
    }
}
