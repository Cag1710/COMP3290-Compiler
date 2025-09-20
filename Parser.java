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

    private static final Set<TokenType> TYPE_FOLLOW = Set.of(
        TokenType.TCOMA,
        TokenType.TTEND,
        TokenType.TARRS,
        TokenType.TFUNC,
        TokenType.TMAIN
    );

    private static final Set<TokenType> DECL_FOLLOW = Set.of(
        TokenType.TTEND,
        TokenType.TARRS,
        TokenType.TFUNC,
        TokenType.TMAIN
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

    private StNode parseInit() {
        Token iden = ts.expect(TokenType.TIDEN);
        if (iden == null) {
            er.syntax("expected identifier in constant declaration", ts.peek());
            ts.syncTo(INIT_FOLLOW);
            return new StNode(StNodeKind.NINIT, null, ts.peek().line, ts.peek().col);  // return dummy node to maintain AST
        }
        if (ts.expect(TokenType.TTTIS) == null) {
            er.syntax("expected 'is' in constant declaration", ts.peek());
            ts.syncTo(INIT_FOLLOW);
            return new StNode(StNodeKind.NINIT, null, ts.peek().line, ts.peek().col);
        }

        StNode init = new StNode(StNodeKind.NINIT, iden.lexeme, ts.peek().line, ts.peek().col);
        StNode expr = parseExpr();
        init.add(expr);

        return init;
    }

    private StNode parseTypes() {
        StNode typeList = new StNode(StNodeKind.NTYPEL, null, ts.peek().line, ts.peek().col);

        typeList.add(parseType());

        while(ts.match(TokenType.TTEND)) {
            // breakout of type block if another block is encountered
            if (ts.peek().tokenType == TokenType.TARRS || ts.peek().tokenType == TokenType.TFUNC || ts.peek().tokenType == TokenType.TMAIN) {
                break;
            }
            typeList.add(parseType());
        }
        return typeList;
    }

    private StNode parseType() {
        Token iden = ts.expect(TokenType.TIDEN);
        if (iden == null) {
            er.syntax("expected an identifier for the type", iden);
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(iden);
        }
        if (ts.expect(TokenType.TTTIS) == null) {
            er.syntax("expected 'is' in type declaration", ts.peek());
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(iden);
        }
        // type is an array
        if (ts.peek().tokenType == TokenType.TARAY) {
            ts.consume();
            return parseArrayType(iden);
        }
        // type is a struct
        StNode structType =  new StNode(StNodeKind.NRTYPE, null, ts.peek().line, ts.peek().col);
        structType.add(new StNode(StNodeKind.NSIMV, iden.lexeme, ts.peek().line, ts.peek().col));
        structType.add(parseFields());
        return structType;
    }

    private StNode parseFields() {
        StNode fieldList = new StNode(StNodeKind.NFLIST, null, ts.peek().line, ts.peek().col);
        
        fieldList.add(parseDecl());

        while(ts.match(TokenType.TCOMA)) {
            fieldList.add(parseDecl());
        }
        return fieldList;
    }

    private StNode parseDecl() {
        StNode decl = new StNode(StNodeKind.NSDECL, null, ts.peek().line, ts.peek().col);
        Token iden = ts.expect(TokenType.TIDEN);
        if (iden == null) {
            er.syntax("expected identifier in simple declaration", ts.peek());
            ts.syncTo(DECL_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        decl.add(new StNode(StNodeKind.NSIMV, iden.lexeme, ts.peek().line, ts.peek().col));
        if (ts.expect(TokenType.TCOLN) == null) {
            er.syntax("expected ':' in simple declaration", ts.peek());
            ts.syncTo(DECL_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        Token stype = ts.peek();
        if (stype.tokenType == TokenType.TINTG || stype.tokenType == TokenType.TREAL || stype.tokenType == TokenType.TBOOL) {
            ts.consume();
            decl.add(StNode.leaf(StNodeKind.NSTYPE, stype));    // added custom StNode here
        }
        else {
            er.syntax("expected type in simple declaration", ts.peek());
            ts.syncTo(DECL_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        return decl;
    }

    private StNode parseArrayType(Token iden) {
        StNode node = new StNode(StNodeKind.NATYPE, null, ts.peek().line, ts.peek().col);
        // add typeId child
        node.add(new StNode(StNodeKind.NSIMV, iden.lexeme, ts.peek().line, ts.peek().col));
        if (ts.expect(TokenType.TLBRK) == null) {
            er.syntax("expected '[' for array type declaration", ts.peek());
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        // add expr child
        node.add(parseExpr());
        if (ts.expect(TokenType.TRBRK) == null) {
            er.syntax("expected ']' for array type declaration", ts.peek());
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        if (ts.expect(TokenType.TTTOF) == null) {
            er.syntax("expected 'of' for array type declaration", ts.peek());
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        Token structId = ts.expect(TokenType.TIDEN);
        if ( structId == null) {
            er.syntax("expected a struct identifier for the type", ts.peek());
            ts.syncTo(TYPE_FOLLOW);
            return StNode.undefAt(ts.peek());
        }
        node.add(new StNode(StNodeKind.NSIMV, structId.lexeme, ts.peek().line, ts.peek().col));
        return node;
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
        if (ts.peek().tokenType != TokenType.TIDEN) {
            er.syntax("expected identifier to start assignment or call", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        if (ts.lookahead(1).tokenType == TokenType.TLPAR) {
            return parseCallStat();
        }

        return parseAssignStat();
    }

    // <asgnstat> ::= <var> <asgnop> <bool> 
    private StNode parseAssignStat() {
        StNode left = parseVar();
        if (left == null) {
            er.syntax("invalid variable in assignment", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        StNode op = parseAsgnOp();
        if (op == null) {
            er.syntax("expected assignment operator (=, +=, -=, *=, /=)", ts.peek());
            return StNode.undefAt(ts.peek());
        }
        
        StNode right = parseBool();
        if (right == null) {
            er.syntax("expected expression after assignment operator", ts.peek());
            right = StNode.undefAt(ts.peek());
        }

        
        return op.add(left).add(right);
    }

    /**
     * <callstat> ::= <id> ( <calltail> ) 
     * <calltail> ::= <elist> | ε 
     */
    private StNode parseCallStat() {
        int line = ts.peek().line;
        int col = ts.peek().col;
        StNode call = parseFnCall();
        return new StNode(StNodeKind.NCALL, null, line, col).add(call);
    }

    private StNode parseAsgnOp() {
        Token t = ts.peek();
        if (t.tokenType == TokenType.TEQUL) {
            ts.consume();
            return new StNode(StNodeKind.NASGN, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TPLEQ) {
            ts.consume();
            return new StNode(StNodeKind.NPLEQ, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TMNEQ) {
            ts.consume();
            return new StNode(StNodeKind.NMNEQ, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TSTEQ) {
            ts.consume();
            return new StNode(StNodeKind.NSTEA, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TDVEQ) {
            ts.consume();
            return new StNode(StNodeKind.NDVEQ, null, t.line, t.col);
        }
        return null;
    }

    // <returnstat> ::= return void | return <expr>
    private StNode parseReturnStat() {
        Token r = ts.expect(TokenType.TRETN);
        if (r == null) {
            er.syntax("expected 'return'", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        if (ts.match(TokenType.TVOID)) {
            return new StNode(StNodeKind.NRETN, null, r.line, r.col);
        }

        StNode expr = parseExpr();
        if(expr == null) {
            er.syntax("expected 'expression' after return statement", ts.peek());
            return new StNode(StNodeKind.NRETN, null, r.line, r.col).add(StNode.undefAt(ts.peek()));
        }

        return new StNode(StNodeKind.NRETN, null, r.line, r.col).add(expr);
    }

    // <vlist> ::= <var> { , <var> } 
    private StNode parseVList() {
        StNode n = new StNode(StNodeKind.NVLIST, null, ts.peek().line, ts.peek().col);

        n.add(parseVar());
        while(ts.match(TokenType.TCOMA)) {
            n.add(parseVar());
        }

        return n;
    }

    /**
     * <var> ::= <id> <vartail> 
     * <vartail> ::= [<expr>] <vartail2> | ε 
     * <vartail2> ::= .<id> | ε 
     */
    private StNode parseVar() {
        Token id = ts.expect(TokenType.TIDEN);

        if (id == null) {
            er.syntax("expected variable name (identifier)", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        StNode base = StNode.leaf(StNodeKind.NSIMV, id);

        if (ts.match(TokenType.TLBRK)) {
            StNode index = parseExpr();

            if (ts.expect(TokenType.TRBRK) == null) {
                er.syntax("expected closing ']' after expression", ts.peek());
                return StNode.undefAt(ts.peek());
            }

            if (ts.match(TokenType.TDOTT)) {
                Token field = ts.expect(TokenType.TIDEN);
                if (field == null) {
                    er.syntax("expected identifier after '.'", ts.peek());
                    return StNode.undefAt(ts.peek());
                }

                return new StNode(StNodeKind.NARRV, null, id.line, id.col).add(base).add(index).add(StNode.leaf(StNodeKind.NSIMV, field));
            }

            return new StNode(StNodeKind.NAELT, null, id.line, id.col).add(base).add(index);
        }

        return base;
    }

    // <elist> ::= <bool> { , <bool> }
    private StNode parseEList() {
        StNode n = new StNode(StNodeKind.NEXPL, null, ts.peek().line, ts.peek().col);

        n.add(parseBool());
        while(ts.match(TokenType.TCOMA)) {
            n.add(parseBool());
        }

        return n;
    }

    /**
     * <bool> ::= <rel> <bool’>
     * <bool’> ::= <logop> <rel> <bool’> | ε 
     */
    private StNode parseBool() {
        StNode left = parseRel();
        if (left == null) {
            er.syntax("expected relational expression", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        while(true) {
            StNode logOp = parseLogOp();
            if (logOp == null) {
                break;
            }

            StNode right = parseRel();
            if (right == null) {
                er.syntax("expected relational expression after boolean operator", ts.peek());
                return StNode.undefAt(ts.peek());
            }

            logOp.add(left).add(right);
            left = logOp;
        }
        return left;
    }

    /**
     * <rel> ::= not <expr> <relop> <expr> | <expr> <relTail> 
     * <relTail> ::= <relop> <expr> | ε
     */
    private StNode parseRel() {
        Token t = ts.peek();

        // not <expr> <relop> <expr>
        if (t.tokenType == TokenType.TNOTT) {
            ts.consume();

            StNode left = parseExpr();
            if (left == null) {
                er.syntax("expected expression after 'not'", ts.peek());
                return StNode.undefAt(ts.peek());
            }

            StNode op = parseRelOp();
            if (op == null) {
                er.syntax("expected relational operator after expression", ts.peek());
                return StNode.undefAt(ts.peek());
            }

            StNode right = parseExpr();
            if (right == null) {
                er.syntax("expected expression after relational operator", ts.peek());
                return StNode.undefAt(ts.peek());
            }

            op.add(left).add(right);
            return new StNode(StNodeKind.NNOT, null, t.line, t.col).add(op);
        }

        // <expr> <relTail>
        StNode left = parseExpr();
        if (left == null) {
            er.syntax("expected expression in relational", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        // <relop> <expr> | ε
        StNode op = parseRelOp();
        if (op == null) {
            return left;
        }

        StNode right = parseExpr();
        if (right == null) {
            er.syntax("expected expressionafter relational operator", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        return op.add(left).add(right);

    }

    // <logop> ::= and | or | xor
    private StNode parseLogOp() {
        Token t = ts.peek();
        if (t.tokenType == TokenType.TTAND) {
            ts.consume();
            return new StNode(StNodeKind.NAND, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TTTOR) {
            ts.consume();
            return new StNode(StNodeKind.NOR, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TTXOR) {
            ts.consume();
            return new StNode(StNodeKind.NXOR, null, t.line, t.col);
        }
        return null;
    }

    // <relop> ::= == | != || > | <= | < | >= 
    private StNode parseRelOp() {
        Token t = ts.peek();
        if (t.tokenType == TokenType.TEQEQ) {
            ts.consume();
            return new StNode(StNodeKind.NEQL, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TNEQL) {
            ts.consume();
            return new StNode(StNodeKind.NNEQ, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TGRTR) {
            ts.consume();
            return new StNode(StNodeKind.NGRT, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TGEQL) {
            ts.consume();
            return new StNode(StNodeKind.NGEQ, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TLESS) {
            ts.consume();
            return new StNode(StNodeKind.NLSS, null, t.line, t.col);
        } else if (t.tokenType == TokenType.TLEQL) {
            ts.consume();
            return new StNode(StNodeKind.NLEQ, null, t.line, t.col);
        }
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

    // <exponent> ::= <var> | <inlit> | <reallit> | <fncall> | true | false | (<bool>) 
    private StNode parseExpo() {
        Token expo = ts.peek();
        if (expo.tokenType == TokenType.TILIT) {
            ts.consume();
            return new StNode(StNodeKind.NILIT, expo.lexeme, expo.line, expo.col);
        }
        if (expo.tokenType == TokenType.TFLIT) {
            ts.consume();
            return new StNode(StNodeKind.NFLIT, expo.lexeme, expo.line, expo.col);
        }
        if (expo.tokenType == TokenType.TTRUE) {
            ts.consume();
            return new StNode(StNodeKind.NTRUE, null, expo.line, expo.col);
        }
        if (expo.tokenType == TokenType.TFALS) {
            ts.consume();
            return new StNode(StNodeKind.NFALS, null, expo.line, expo.col);
        }
        if (expo.tokenType == TokenType.TIDEN) {
            if (ts.lookahead(1).tokenType == TokenType.TLPAR) {
                return parseFnCall();
            }
            return parseVar();
        }
        if (expo.tokenType == TokenType.TLPAR) {
            ts.consume();
            StNode inner = parseBool();
            if (ts.expect(TokenType.TRPAR) == null) {
                er.syntax("expected ')' to close '('", expo);
                return StNode.undefAt(ts.peek());
            }
            return inner;
        }
        er.syntax("expected operand (<var> | <inlit> | <reallit> | <fncall> | true | false | (<bool>))", expo);
        return StNode.undefAt(expo);
    }


    /**
     * <fncall> ::= <id> ( <fntail> ) 
     * <fntail> ::= <elist> | ε
     */
    private StNode parseFnCall() {
        Token name = ts.expect(TokenType.TIDEN);
        if (name == null) {
            er.syntax("expected function name (identifier)", ts.peek());
            return StNode.undefAt(ts.peek());
        }
        if (ts.expect(TokenType.TLPAR) == null) {
            er.syntax("expected '(' after function name", ts.peek());
            return StNode.undefAt(ts.peek());
        }

        StNode args;
        if (ts.peek().tokenType == TokenType.TRPAR) {
            ts.consume();
            args = new StNode(StNodeKind.NEXPL, null, name.line, name.col);
        } else {
            args = parseEList();

            if (ts.expect(TokenType.TRPAR) == null) {
                er.syntax("expected ')' after argument list", name);
            }
        }
        return new StNode(StNodeKind.NFCALL, null, name.line, name.col).add(StNode.leaf(StNodeKind.NSIMV, name)).add(args);
    }

    // <prlist> ::= <printitem> { , <printitem>  }
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
