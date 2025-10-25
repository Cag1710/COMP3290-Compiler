import java.util.*;

public final class SemanticAnalyzer {
    private final SymbolTable table;
    private final ErrorReporter er;
    private Type currentFuncReturnType = null;
    private boolean sawReturnInCurrentFunc = false;

    // code gen stuff
    private static final int WORD_BYTES = 8;
    private int globalsNextOff = 0;     // base=1
    private int funcLocalsNextOff = 16; // base=2 locals start at +16
    private int funcParamsNextOff = -8; // base=2 params start at -8
    private boolean inFunction = false; // true while inside a NFUND

    public SemanticAnalyzer(SymbolTable table, ErrorReporter er) {
        this.table = table;
        this.er = er;
    }

    public void analyze(StNode root) {
        if (root == null || root.kind != StNodeKind.NPROG) return;

        table.enter(); // enter the global scope

        // 1. check the programe header and footer match
        checkProgramName(root);

        StNode nglob = firstChild(root, StNodeKind.NGLOB);
        StNode nfuncs = firstChild(root, StNodeKind.NFUNCS);
        StNode nmain = firstChild(root, StNodeKind.NMAIN);

        // 2. analyze globals
        if (nglob != null) visitGlobals(nglob);

        // 3. declare function signatures (name, return type, param types)
        if (nfuncs != null) declareFuncSignatures(nfuncs);

        // 4. for each function, enter their scope, define params/locals and validate its body
        if (nfuncs != null) checkFuncBodies(nfuncs);

        // 5. analyze main body
        if (nmain != null) visitMain(nmain);

        // 6. pop out of the global scope
        table.exit();
    }

    /******** Helpers *********/

    // convert source type name into an actual type value so NSTYPE holding "integer" returns new Type.Int();
    private Type baseTypeFromLexeme(String lx) {
        if (lx == null) return null;
        switch (lx.toLowerCase()) {
            case "integer": return new Type.Int();
            case "real":    return new Type.Real();
            case "boolean": return new Type.Bool();
            case "void":    return new Type.VoidT();
            default:
                Symbol s = table.resolve(lx);
                if (s != null && s.kind() == SymbolKind.TYPE) {
                    return s.type();
                }
                return null;
        }
    }
    // for error reporting, mostly just in stats
    private TokenType tokenTypeForOp(StNodeKind k) {
        return switch (k) {
            // --- Arithmetic ---
            case NADD -> TokenType.TPLUS;   // +
            case NSUB -> TokenType.TMINS;   // -
            case NMUL -> TokenType.TSTAR;   // *
            case NDIV -> TokenType.TDIVD;   // /
            case NMOD -> TokenType.TPERC;   // %
            case NPOW -> TokenType.TCART;   // ^
    
            // --- Relational ---
            case NLSS -> TokenType.TLESS;   // <
            case NGRT -> TokenType.TGRTR;   // >
            case NLEQ -> TokenType.TLEQL;   // <=
            case NGEQ -> TokenType.TGEQL;   // >=
            case NEQL -> TokenType.TEQEQ;   // ==
            case NNEQ -> TokenType.TNEQL;   // !=
    
            // --- Boolean ---
            case NNOT -> TokenType.TNOTT;   // not
            case NAND -> TokenType.TTAND;   // and
            case NOR  -> TokenType.TTTOR;   // or
            case NXOR -> TokenType.TTXOR;   // xor
    
            // --- Assignment ---
            case NASGN -> TokenType.TEQUL;  // =
            case NPLEQ -> TokenType.TPLEQ;  // +=
            case NMNEQ -> TokenType.TMNEQ;  // -=
            case NSTEA -> TokenType.TSTEQ;  // *=
            case NDVEQ -> TokenType.TDVEQ;  // /=
    
            default -> TokenType.TUNDF;     // fallback
        };
    }

    // for error reporting
    private Token tokenAtOp(StNode n) {
        return tokenAt(n, tokenTypeForOp(n.kind));
    }

    // check the type and return a printable string from it
    private String printable(Type t) {
        if (t == null) return "<?>"; 
        if (t instanceof Type.Int) return "integer";
        if (t instanceof Type.Real) return "real";
        if (t instanceof Type.Bool) return "boolean";
        if (t instanceof Type.VoidT) return "void";
        if (t instanceof Type.Error) return "<error>";
        if (t instanceof Type.Array a) return "array[" + a.size() + "] of " + printable(a.elem());
        if (t instanceof Type.Struct) return "struct";
        return t.toString();
    }

    // returns the first direct child of node n of a certain node kind
    // e.g. firstChidl(NFUND, NPLIST) -> the parameter list node
    private static StNode firstChild(StNode n, StNodeKind k) {
        if (n == null) return null;
        for (StNode c : n.children()) {
            if (c.kind == k) {
                return c;
            }
        }
        return null;
    }

    // extracts the name associated with a node
    // e.g. firstName(NFUND) -> returns the function identifier
    private static String firstName(StNode n) {
        if (n == null) return null;
        for (StNode c : n.children()) {
            if (c.kind == StNodeKind.NSIMV) {
                return c.lexeme;
            }
        }
        return n.lexeme;
    }

    // create a token at this point, for tracking line and col nums accurately
    private Token tokenAt(StNode n, TokenType tt) {
        int line = (n != null && n.line >= 0) ? n.line : 0;
        int col  = (n != null && n.col  >= 0) ? n.col  : 0;
        String lx = (n != null) ? n.lexeme : null;
        return new Token(tt, lx, line, col);
    }

    // converts NSTYPE nodes into an actual Type
    private Type typeFromNode(StNode tNode) {
        if (tNode == null) return null;
        if (tNode.kind != StNodeKind.NSTYPE) return null;
        return baseTypeFromLexeme(tNode.lexeme);
    }

    // reads a functions parameter list
    private List<Type> paramTypesOf(StNode f) {
        List<Type> out = new ArrayList<>();
        StNode plist = firstChild(f, StNodeKind.NPLIST);
        if (plist == null) return out;

        for (StNode p : plist.children()) {
            switch (p.kind) {
                case NSIMP -> {
                    for (StNode sd : p.children()) {
                        if (sd.kind == StNodeKind.NSDECL) {
                            Type t = typeFromNode(firstChild(sd, StNodeKind.NSTYPE));
                            out.add(t);
                        }
                    }
                }

                case NARRP, NARRC -> {
                    StNode arrd = firstChild(p, StNodeKind.NARRD);
                    String typeName = null;
                    if (arrd != null && arrd.children().size() >= 2) {
                        typeName = arrd.children().get(1).lexeme;
                    } else if (p.children().size() >= 2 && p.children().get(1).kind == StNodeKind.NSIMV) {
                        typeName = p.children().get(1).lexeme;
                    }
                    Type named = (typeName != null) ? baseTypeFromLexeme(typeName) : null;
                    out.add(named);
                }

                default -> { }
            }
        }
        return out;
    }


    // check if a name already exists in a scope
    private void defineOrDup(Symbol s, StNode at) {
        if (!table.define(s)) {
            er.semantic("Semantic: duplicate identifier in this scope: '" + s.name() + "'", tokenAt(at, TokenType.TIDEN));
        }
    }

    // ensure the left hand value is actually a variable that can handle assignment - id, arr[index], arr[index].field
    private void ensureLValue(StNode n) {
        boolean ok = n.kind == StNodeKind.NSIMV || n.kind == StNodeKind.NAELT || n.kind == StNodeKind.NARRV;
        if (!ok) er.semantic("Semantic: left side of assignment must be a variable", tokenAt(n, TokenType.TIDEN));
    }

    // if either type is real, result is real, otherwise its in an int
    private Type numericResult (Type a, Type b) {
        if (a instanceof Type.Error || b instanceof Type.Error) return new Type.Error();
        if (Type.isNumeric(a) && Type.isNumeric(b)) {
            return (a instanceof Type.Real || b instanceof Type.Real) ? new Type.Real() : new Type.Int();
        }
        return null;
    }

    private static boolean isWhole(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d) && Math.floor(d) == d;
    }

    // to check whether the expr node is valid for constants
    private boolean isConstExpr(StNode n) {
        switch (n.kind) {
            case NILIT, NFLIT, NTRUE, NFALS -> {
                return true;
            }
    
            case NNOT -> {
                return n.children().size() == 1 && isConstExpr(n.children().get(0));
            }
    
            case NADD, NSUB, NMUL, NDIV, NMOD, NPOW,
                 NEQL, NNEQ, NGRT, NLSS, NGEQ, NLEQ,
                 NAND, NOR, NXOR -> {
                return n.children().size() == 2 &&
                       isConstExpr(n.children().get(0)) &&
                       isConstExpr(n.children().get(1));
            }
    
            case NSIMV, NARRV, NAELT, NFCALL -> {
                return false;
            }
    
            default -> {
                if (!n.children().isEmpty() && n.children().size() == 1) {
                    return isConstExpr(n.children().get(0));
                }
                return false;
            }
        }
    }

    // takes in a node and returns its type
    // used to check when only specific types are allowed e.g. in assignment, check the lhs and rhs types, if they cant be assigned to eachother, its an error, this will be expanded as we go along
    private Type typeOf(StNode n) {
        if (n == null) return null;
        if (n.getType() != null) return n.getType();

        Type t;
        switch (n.kind) {
            // literals
            case NILIT -> t = new Type.Int();
            case NFLIT -> t = new Type.Real();
            case NTRUE, NFALS -> t = new Type.Bool();

            // variables
            case NSIMV -> t = typeOfId(n.lexeme, n);
            case NAELT -> t = typeOfArrayIndex(n);
            case NARRV -> t = typeOfArrayElem(n);

            // arithemetic
            case NADD, NSUB, NMUL, NDIV, NMOD, NPOW -> t = typeOfArith(n);

            // relation/bool
            case NEQL, NNEQ, NGRT, NLSS, NGEQ, NLEQ -> t = typeOfRel(n);
            case NNOT -> t = typeOfNot(n);
            case NAND, NOR, NXOR -> t = typeOfBoolBin(n);

            case NFCALL -> t = typeOfNfCall(n);

            default -> t = new Type.Error();
        }

        n.setType(t);
        return t;
    }

    // check name and footer match
    private void checkProgramName(StNode prog) {
        // act on proper program node
        if (prog == null || prog.kind != StNodeKind.NPROG) return; 

        // find the header program id
        String head = null;
        for (StNode c: prog.children()) {
            if (c.kind == StNodeKind.NSIMV) {
                head = c.lexeme;
                break;
            }
        }

        // find NMAIN under NPROG
        StNode nmain = null;
        for (StNode c: prog.children()) {
            if (c.kind == StNodeKind.NMAIN) {
                nmain = c;
                break;
            }
        }

        // last trailing simv directly under nmain
        String tail = null;
        if (nmain != null) {
            List <StNode> kids = nmain.children();
            for (int i = kids.size() - 1; i >= 0; i--) {
                StNode c = kids.get(i);
                if (c.kind == StNodeKind.NSIMV) {
                    tail = c.lexeme;
                    break;
                }
            }
        }

        // compare the names to check if its consistent
        if (head != null && tail != null && !head.equals(tail)) {
            er.semantic("Semantic: program end name '" + tail + "' does not match header '" + head + "'", tokenAt(prog, TokenType.TCD25));
        }
    }

    // array declarations, const initializers etc.
    private void visitGlobals(StNode nglob) {
        StNode consts = firstChild(nglob, StNodeKind.NILIST);
        StNode structs = firstChild(nglob, StNodeKind.NTYPEL);
        StNode arrs = firstChild(nglob, StNodeKind.NALIST);

        if (consts != null) { declareConsts(consts); }
        if (structs != null) { declareStructs(structs); }
        if (arrs != null) { declareArrays(arrs); }
    }

    private void declareConsts(StNode consts) {
        for (StNode c: consts.children()) {
            if (c.kind != StNodeKind.NINIT) continue;

            String cname = firstName(c);
            if (cname == null) {
                er.semantic("Semantic: constant missing identifier", tokenAt(c, TokenType.TCNST));
                continue;
            }

            // size < 2 means that we have an id probably but no expr
            if (c.children().size() < 2) {
                er.semantic("Semantic: constant missing expression", tokenAt(c, TokenType.TCNST));
                continue;
            }
            
            // get just the <expr> of the init
            StNode expr = c.children().get(1);
            // make sure the expression is even valid for const
            if (!isConstExpr(expr)) {
                er.semantic("Semantic: constant '" + cname + "' must use a constant expression",
                            tokenAt(c, TokenType.TCNST));
                continue;
            }

            Type cType = typeOf(expr);
            // any of these types aren't valid for const
            if (cType == null || cType instanceof Type.Error ||
                cType instanceof Type.VoidT || cType instanceof Type.Array || cType instanceof Type.Struct) {
                er.semantic("Semantic: invalid constant type for '" + cname + "': " + printable(cType),
                            tokenAt(c, TokenType.TCNST));
                continue;
            }

            Object cVal = evalExpr(expr);

            // if the type is int but the value is a double and the double is a whole number i.e. 3.0, then its safe to convert it to int
            if (cType instanceof Type.Int && cVal instanceof Double d && isWhole(d)) {
                cVal = d.intValue();
            } else if (cType instanceof Type.Real && cVal instanceof Integer i) { // the opposite
                cVal = i.doubleValue(); 
            }

            // if evalExpr returned null then for some reason whatevers there, we cant evaluate
            if (cVal == null) {
                er.semantic("Semantic: expression for constant '" + cname + "' is not evaluable",
                            tokenAt(c, TokenType.TCNST));
                continue;
            }

            // to make sure the type and value actually match
            if (cType instanceof Type.Int   && !(cVal instanceof Integer) ||
                cType instanceof Type.Real  && !(cVal instanceof Double)  ||
                cType instanceof Type.Bool  && !(cVal instanceof Boolean)) {
                er.semantic("Semantic: constant '" + cname + "' type/value mismatch; expected " + printable(cType),
                            tokenAt(c, TokenType.TCNST));
                continue;
            }

            ConstSymbol cSym = new ConstSymbol(cname, cType, cVal);
            defineOrDup(cSym, c);
        }
    }

    private Object evalExpr(StNode expr) {
        
        switch (expr.kind) {
            case NILIT: return Integer.parseInt(expr.lexeme);
            case NFLIT: return Double.parseDouble(expr.lexeme);
            case NTRUE: return Boolean.TRUE;
            case NFALS: return Boolean.FALSE;

            case NNOT: {
                Object a = evalExpr(expr.children().get(0));
                return (a instanceof Boolean b) ? !b : null;
            }

            /**
             * Explaining for one as it just applies to the rest
             * if either side of the expression is a real, do the operation as if they are both doubles
             * if neither side of the expression is a real, do the operation as if they were ints (technically toI not rly needed here, but used for consistency) 
             */
            case NADD: return num2(expr, (x,y) -> isReal(x,y) ? (toD(x) + toD(y)) : (toI(x) + toI(y))); 
            case NSUB: return num2(expr, (x,y) -> isReal(x,y) ? (toD(x) - toD(y)) : (toI(x) - toI(y)));
            case NMUL: return num2(expr, (x,y) -> isReal(x,y) ? (toD(x) * toD(y)) : (toI(x) * toI(y)));
            case NDIV: return num2(expr, (x,y) -> isReal(x,y) ? (toD(x) / toD(y)) : safeIntDiv(toI(x), toI(y))); // safeIntDiv makes sure y isnt 0

            // same thing as safeIntDiv, safeIntMod checks if y is 0, but applies %
            case NMOD: return num2(expr, (x,y) -> safeIntMod(toI(x), toI(y)));
            /**
             * e.g. 3 ^ 2
             * a = int
             * b = int
             * if a isnt a int or double return null, if b is anything but an int return null
             * type cast exponent since its an object type rn
             * if a is a double, pattern match, create new double da, apply the operation
             * otherwise apply the operation but as if they were ints (Math.pow returns double so custom method)
             */
            case NPOW: {
                Object a = evalExpr(expr.children().get(0));
                Object b = evalExpr(expr.children().get(1));
                if (!(a instanceof Integer || a instanceof Double) || !(b instanceof Integer)) return null;
                int e = (Integer) b;
                if (a instanceof Double da) return Math.pow(da, e);
                return intPow((Integer) a, e);
            }

            case NEQL: return rel(expr, (x,y) -> cmp(x,y) == 0);
            case NNEQ: return rel(expr, (x,y) -> cmp(x,y) != 0);
            case NGRT: return rel(expr, (x,y) -> cmp(x,y) > 0);
            case NLSS: return rel(expr, (x,y) -> cmp(x,y) < 0);
            case NGEQ: return rel(expr, (x,y) -> cmp(x,y) >= 0);
            case NLEQ: return rel(expr, (x,y) -> cmp(x,y) <= 0);

            case NAND: {
                Object a = evalExpr(expr.children().get(0));
                Object b = evalExpr(expr.children().get(1));
                return (a instanceof Boolean x && b instanceof Boolean y) ? (x && y) : null;
            }
            case NOR: {
                Object a = evalExpr(expr.children().get(0));
                Object b = evalExpr(expr.children().get(1));
                return (a instanceof Boolean x && b instanceof Boolean y) ? (x || y) : null;
            }
            case NXOR: {
                Object a = evalExpr(expr.children().get(0));
                Object b = evalExpr(expr.children().get(1));
                return (a instanceof Boolean x && b instanceof Boolean y) ? (x ^ y) : null;
            }

            default:
                if (expr.children().size() == 1) return evalExpr(expr.children().get(0));
                return null;
        }
    }
    
    // this refers to the <types> section changed to structs to save confusion with our Type class
    private void declareStructs(StNode ntypel) {
        if (ntypel == null || ntypel.kind != StNodeKind.NTYPEL) return;

        for (StNode t : ntypel.children()) {
            switch (t.kind) {
                case NRTYPE -> { 
                    String typeName = firstName(t);
                    if (typeName == null) {
                        er.semantic("Semantic: struct type missing name", tokenAt(t, TokenType.TIDEN));
                        continue;
                    }
                    Map<String, Type> fields = new LinkedHashMap<>();
                    StNode flist = firstChild(t, StNodeKind.NFLIST);
                    if (flist != null) {
                        for (StNode sd : flist.children()) {
                            if (sd.kind != StNodeKind.NSDECL) continue;
                            String fname = firstName(sd);
                            Type ftype = typeFromNode(firstChild(sd, StNodeKind.NSTYPE));
                            if (fname == null || ftype == null) {
                                er.semantic("Semantic: bad field in type '" + typeName + "'", tokenAt(sd, TokenType.TIDEN));
                                ftype = new Type.Error();
                            }
                            fields.put(fname, ftype);
                        }
                    }
                    defineOrDup(new TypeSymbol(typeName, new Type.Struct(fields)), t);
                }

                case NATYPE -> {
                    List<StNode> kids = t.children();
                    if (kids.size() < 3) {
                        er.semantic("Semantic: malformed array type", tokenAt(t, TokenType.TLBRK));
                        continue;
                    }
                    String typeName = kids.get(0).lexeme;     
                    StNode sizeNode = kids.get(1);            
                    String elemName = kids.get(2).lexeme;     

                    Type elemType = baseTypeFromLexeme(elemName);
                    if (elemType == null) {
                        er.semantic("Semantic: unknown element type '" + elemName + "' for type '" + typeName + "'", tokenAt(t, TokenType.TIDEN));
                        elemType = new Type.Error();
                    }
                    if (elemType instanceof Type.VoidT) {
                        er.semantic("Semantic: array element type cannot be void for type '" + typeName + "'", tokenAt(t, TokenType.TLBRK));
                        elemType = new Type.Error();
                    }

                    Object szVal = evalExpr(sizeNode);
                    Integer size = (szVal instanceof Integer i) ? i
                                : (szVal instanceof Double d && isWhole(d)) ? d.intValue()
                                : null;
                    if (size == null || size < 0) {
                        er.semantic("Semantic: invalid array size for type '" + typeName + "'", tokenAt(t, TokenType.TLBRK));
                        size = 0;
                    }

                    defineOrDup(new TypeSymbol(typeName, new Type.Array(elemType, size)), t);
                }

                default -> {
                    
                }
            }
        }
    }

    private void declareArrays(StNode arrs) {
        for (StNode d : arrs.children()) {
            if (d.kind == StNodeKind.NARRD) {
                defineArrayDecl(d, firstName(d));
            } else { // in the event that the node is like an extra step down, wrapped by a different node
                StNode arrd = firstChild(d, StNodeKind.NARRD);
                if (arrd != null) defineArrayDecl(arrd, firstName(arrd));
            }
        }
    }

    /** 
     * for each NFUND
     * extract the function name
     * extract the return type
     * extract param types
     * define a funcSymbol
     */
    private void declareFuncSignatures(StNode nfuncs) {
        for (StNode f : nfuncs.children()) {
            if (f.kind != StNodeKind.NFUND) continue;

            String fname = firstName(f);
            if (fname == null) {
                er.semantic("Semantic: function missing name", tokenAt(nfuncs, TokenType.TFUNC));
                continue;
            }

            StNode rtNode = firstChild(f, StNodeKind.NSTYPE);
            Type rType = typeFromNode(rtNode);

            if (rType == null) {
                er.semantic("Semantic: unknown or missing return type for function '" + fname + "'", tokenAt(nfuncs, TokenType.TFUNC));
                rType = new Type.Error();
            }

            List<Type> paramTypes = paramTypesOf(f);

            for (int i = 0; i < paramTypes.size(); i++) {
                if (paramTypes.get(i) == null) {
                    er.semantic("Semantic: parameter '" + (i + 1) + "' of function '" + fname + "' has unknown/invalid type", tokenAt(nfuncs, TokenType.TFUNC));
                }
            }

            FuncSymbol sig = new FuncSymbol(fname, rType, paramTypes);
            defineOrDup(sig, f);
        }
    }

    /**
     * resolve the function symbol to get its signature (check its already been reported in the symboltable)
     * enter the function scope
     * define parameters
     * validate the locals statements
     * if instanceof Type.VOIDT it doesnt need a return, but otherwise it needs a return statement
     * exit the scope
     */
    private void checkFuncBodies(StNode nfuncs) {
        if (nfuncs == null || nfuncs.kind != StNodeKind.NFUNCS) return;

        for (StNode f : nfuncs.children()) {
            if (f.kind == StNodeKind.NFUND) {
                checkFuncBody(f);
            }
        }
    }

    private void checkFuncBody(StNode fund) {
        Type prevRet = currentFuncReturnType;
        currentFuncReturnType = typeFromNode(firstChild(fund, StNodeKind.NSTYPE));
        boolean prevSaw = sawReturnInCurrentFunc;
        sawReturnInCurrentFunc = false;

        boolean prevInFn = inFunction;
        inFunction = true;
        int prevLocals = funcLocalsNextOff;
        int prevParams = funcParamsNextOff;
        funcLocalsNextOff = 16;
        funcParamsNextOff = -8;

        table.enter();

        StNode plist = firstChild(fund, StNodeKind.NPLIST);
        if (plist != null) defineParams(plist);
        StNode dlist = firstChild(fund, StNodeKind.NDLIST);
        if (dlist != null) {
            for (StNode d : dlist.children()) {
                declareLocal(d);
            }
        }

        StNode stats = firstChild(fund, StNodeKind.NSTATS);
        if (stats != null) {
            for (StNode s : stats.children()) {
                visitStat(s);
            }
        }

        table.exit();

        inFunction = prevInFn;
        funcLocalsNextOff = prevLocals;
        funcParamsNextOff = prevParams;

        if (!(currentFuncReturnType instanceof Type.VoidT) && !sawReturnInCurrentFunc) {
            er.semantic("Semantic: function " + firstName(fund) + " is missting a return statement", tokenAt(fund, TokenType.TFUNC));
        }

        currentFuncReturnType = prevRet;
        sawReturnInCurrentFunc = prevSaw;
    }

    /**
     * enter mains scope
     * validate statements
     * exit main scope
     * 
     */
    private void visitMain(StNode nmain) {

        Type prevRet = currentFuncReturnType;
        boolean prevSaw = sawReturnInCurrentFunc;
        currentFuncReturnType = new Type.VoidT();
        sawReturnInCurrentFunc = false;

        table.enter();

        StNode dlist = firstChild(nmain, StNodeKind.NSDLST);

        if (dlist != null) {
            for (StNode d : dlist.children()) {
                declareLocal(d);
            }
        }

        StNode stats = firstChild(nmain, StNodeKind.NSTATS);
        if (stats != null) {
            for (StNode s : stats.children()) {
                visitStat(s);
            }
        }

        table.exit();

        currentFuncReturnType = prevRet;
        sawReturnInCurrentFunc = prevSaw;
    }

    // declare locals
    private void declareLocal(StNode d) {
        switch (d.kind) {
            case NSDECL -> defineSimpleDecl(d);
            case NARRD -> defineArrayDecl(d, firstName(d));

            default -> {
                StNode arrd = firstChild(d, StNodeKind.NARRD);
                if (arrd != null) defineArrayDecl(arrd, firstName(d));
            }
        }
    }

    private void defineParams(StNode plist) {
        if (plist == null || plist.kind != StNodeKind.NPLIST) return;

        for (StNode p : plist.children()) {
            switch (p.kind) {
                case NSIMP -> {
                    for (StNode sd : p.children()) {
                        if (sd.kind == StNodeKind.NSDECL) { // <-- FIX: was "!="
                            String pname = firstName(sd);
                            Type pt = typeFromNode(firstChild(sd, StNodeKind.NSTYPE));
                            defineParamSymbol(pname, pt, false, sd);
                        }
                    }
                }

                case NARRP -> { // non-const array param
                    String pname = firstName(p); // first child NSIMV(arr)
                    String tname = (p.children().size() >= 2 && p.children().get(1).kind == StNodeKind.NSIMV)
                                    ? p.children().get(1).lexeme : null;
                    if (pname == null || tname == null) {
                        er.semantic("Semantic: malformed array parameter", tokenAt(p, TokenType.TIDEN));
                        continue;
                    }
                    Type pt = baseTypeFromLexeme(tname); // should resolve to Type.Array
                    defineParamSymbol(pname, pt, false, p);
                }

                case NARRC -> { // const array param (can come wrapped in NARRD)
                    StNode arrd = firstChild(p, StNodeKind.NARRD);
                    String pname, tname;
                    if (arrd != null && arrd.children().size() >= 2) {
                        pname = firstName(arrd);
                        tname = arrd.children().get(1).lexeme;
                    } else {
                        // also accept the flat two-child shape
                        pname = firstName(p);
                        tname = (p.children().size() >= 2 && p.children().get(1).kind == StNodeKind.NSIMV)
                                    ? p.children().get(1).lexeme : null;
                    }
                    if (pname == null || tname == null) {
                        er.semantic("Semantic: malformed const array parameter", tokenAt(p, TokenType.TCNST));
                        continue;
                    }
                    Type pt = baseTypeFromLexeme(tname); // should resolve to Type.Array
                    defineParamSymbol(pname, pt, true, p);
                }

                default -> er.semantic("Semantic: unknown parameter form", tokenAt(p, TokenType.TIDEN));
            }
        }
    }


    private void defineParamSymbol(String pname, Type ptype, boolean isConst, StNode at) {
        if (ptype == null) {
            er.semantic("Semantic: unknown parameter type for '" + pname + "'", tokenAt(at, TokenType.TIDEN));
            ptype = new Type.Error();
        }

        ParamSymbol ps = new ParamSymbol(pname, ptype, isConst);

        if (inFunction) {
            ps.setAddr(2, funcParamsNextOff);
            funcParamsNextOff -= WORD_BYTES;
        }
        defineOrDup(ps, at);
    }

    private ParamSymbol constParamOfLValue(StNode lv) {
        if (lv == null) return null;

        switch (lv.kind) {
            case NSIMV: {
                Symbol s = lv.getSymbol();
                if (s == null) s = table.resolve(lv.lexeme);
                if (s instanceof ParamSymbol ps && ps.isConst()) return ps;
                return null;
            }
            case NAELT:
            case NARRV: {
                StNode base = lv.children().isEmpty() ? null : lv.children().get(0);
                return constParamOfLValue(base);
            }
            default:
                return null;
        }
    }

    private void defineSimpleDecl(StNode nsdecl) {
        if (nsdecl == null || nsdecl.kind != StNodeKind.NSDECL) return;

        String name = firstName(nsdecl);

        Type t = typeFromNode(firstChild(nsdecl, StNodeKind.NSTYPE));
        if (t == null) {
            er.semantic("Semantic: unknown type for '" + name + "'", tokenAt(nsdecl, TokenType.TUNDF)); 
            t = new Type.Error();
        }

        VarSymbol vs = new VarSymbol(name, t);

        if (inFunction) {
            vs.setAddr(2, funcLocalsNextOff);
            funcLocalsNextOff += WORD_BYTES;
        } else {
            vs.setAddr(1, globalsNextOff);
            globalsNextOff += WORD_BYTES;
        }
        defineOrDup(vs, nsdecl);
    }

    // nameOverride if the caller already knows the name of the array e.g. in declareLocal we call
    // firstName(d)
    private void defineArrayDecl(StNode d, String nameOverride) {
        if (d == null || d.kind != StNodeKind.NARRD) return;

        // name
        String aname = (nameOverride != null) ? nameOverride : firstName(d);
        if (aname == null) {
            er.semantic("Semantic: array declaration missing identifier", tokenAt(d, TokenType.TIDEN));
            return;
        }

        List<StNode> kids = d.children();
        if (kids.size() < 2) {
            er.semantic("Semantic: malformed array declaration for '" + aname + "'", tokenAt(d, TokenType.TLBRK));
            return;
        }

        // second child is the type id
        StNode typeNameNode = kids.get(1);
        String tname = (typeNameNode != null) ? typeNameNode.lexeme : null;
        Type t = baseTypeFromLexeme(tname);

        if (t == null) {
            er.semantic("Semantic: unknown element type for array '" + aname + "'", tokenAt(d, TokenType.TIDEN));
            t = new Type.Error();
        }

        if (!(t instanceof Type.Array) && !(t instanceof Type.Error)) {
            er.semantic("Semantic: arrays section requires an array type; got " + printable(t) + " for '" + aname + "'", tokenAt(d, TokenType.TLBRK));
        }

        VarSymbol vs = new VarSymbol(aname, t);

        if (inFunction) { // if inside a function
            vs.setAddr(2, funcLocalsNextOff); // 2 -> current function scope
            funcLocalsNextOff += WORD_BYTES; // increment the offset
        } else {
            vs.setAddr(1, globalsNextOff); // 1 -> global scope
            globalsNextOff += WORD_BYTES; // increment the offset
        }

        defineOrDup(new VarSymbol(aname, t), d);
    }


    // statement dispatcher
    private void visitStat(StNode s) {

        switch (s.kind) {
            case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> visitAssign(s);
            case NINPUT, NOUTP, NOUTL -> visitIO(s);
            case NFORL, NREPT, NIFTH, NIFTE -> visitControls(s);

            case NRETN -> visitReturn(s);

            case NCALL -> {
                if (!s.children().isEmpty() && s.children().get(0).kind == StNodeKind.NFCALL) {
                    StNode call = s.children().get(0);
                    Type rt = typeOfNfCall(call);

                    if (!(rt instanceof Type.VoidT)) {
                        String fname = firstName(call);
                        er.semantic("Semantic: cannot use value-returning function '" + fname + "' as a statement",
                                    tokenAt(s, TokenType.TFUNC));
                    }
                }
            }

            default -> {}
        }
    }

    private Type typeOfNfCall(StNode n) {
        if (n == null || n.kind != StNodeKind.NFCALL) return new Type.Error();

        String fname = firstName(n);
        if (fname == null) {
            er.semantic("Semantic: malformed function call (missing name)", tokenAt(n, TokenType.TFUNC));
            return new Type.Error();
        }

        Symbol s = table.resolve(fname);
        if (!(s instanceof FuncSymbol fs)) {
            er.semantic("Semantic: '" + fname + "' is not a function", tokenAt(n, TokenType.TFUNC));
            return new Type.Error();
        }

        List<StNode> kids = n.children();
        StNode argList = (kids.size() >= 2 && kids.get(1).kind == StNodeKind.NALIST) ? kids.get(1) : null;
        List<StNode> actuals = (argList != null) ? argList.children() : Collections.emptyList();

        List<Type> formals = fs.paramTypes();

        if (actuals.size() != formals.size()) {
            er.semantic("Semantic: function '" + fname + "' expects " + formals.size() +
                        " argument(s), got " + actuals.size(), tokenAt(n, TokenType.TFUNC));
        }

        int c = Math.min(actuals.size(), formals.size());
        for (int i = 0; i < c; i++) {
            Type at = typeOf(actuals.get(i));
            Type ft = formals.get(i);

            if (ft == null) continue;

            if (!assignable(ft, at)) {
                er.semantic("Semantic: argument " + (i + 1) + " of '" + fname +
                            "' cannot accept " + printable(at) + " (expected " + printable(ft) + ")",
                            tokenAt(n, TokenType.TFUNC));
            }
        }

        return fs.returnType();
    }

    // Variables
    // resolve an NSIMV node to a declaration and return its type
    private Type typeOfId(String name, StNode at) {
        Symbol s = table.resolve(name); // check the symbol is actually declared in a scope somewhere

        if (s == null) {
            er.semantic("Semantic: identifier used before declaration: " + name, tokenAt(at, TokenType.TIDEN));
            return new Type.Error();
        }

        at.setSymbol(s); // bind this declaration to the node
        return s.type(); // return the symbols type
    }

    // Arrays
    // handles base[index]
    private Type typeOfArrayIndex(StNode n) {
        StNode base = n.children().get(0); // get the base
        StNode index = n.children().get(1); // get the indexd

        // get their types
        Type bt = typeOf(base);
        Type it = typeOf(index);

        // if base type is not an array, error
        if (!(bt instanceof Type.Array a)) { // pattern matching - instanceof checks if object is specific type, if true creates a new variable and assigns the object to it
            er.semantic("Semantic: indexed expression is not an array: " + base.lexeme, tokenAt(n, TokenType.TLBRK));
            return new Type.Error();
        }

        // if index type is not an int, error
        if (!(it instanceof Type.Int)) {
            er.semantic("Semantic: array index must be an integer", tokenAt(n, TokenType.TLBRK));
            return new Type.Error();
        }

        // the type of base[index] is the arrays element type
        return a.elem();
    }

    // Fields
    // handles base[index].field
    private Type typeOfArrayElem(StNode n) {
        StNode base = n.children().get(0); // base
        StNode index = n.children().get(1); // index
        StNode field = n.children().get(2); // field

        // ensure base[index] is valid and yields a struct element type
        Type elem = typeOfArrayIndex(new StNode(StNodeKind.NAELT, null, n.line, n.col).add(base).add(index));

        // ensure the element is a struct
        if (!(elem instanceof Type.Struct s)) {
            er.semantic("Semantic: array element is not a struct, cannot select field '" + field.lexeme + "'" , tokenAt(n, TokenType.TIDEN));
            return new Type.Error();
        }
        
        // look up the field type in the structs field map - Map<String, Type> fields
        Type ft = s.fields().get(field.lexeme);

        // unknown field, error
        if (ft == null) {
            er.semantic("Semantic: unknown field '" + field.lexeme + "' in struct", tokenAt(n, TokenType.TIDEN));
            return new Type.Error();
        }

        // return the field type
        return ft;
    }

    // Arithmetic
    private Type typeOfArith(StNode n) {
        Type a = typeOf(n.children().get(0));
        Type b = typeOf(n.children().get(1));

        if (a instanceof Type.Error || b instanceof Type.Error) return new Type.Error();
        
        if (n.kind == StNodeKind.NPOW) {
        
            if (!Type.isNumeric(a)) {
                er.semantic("Semantic: left operand of '^' must be numeric, got " + printable(a), tokenAt(n, TokenType.TCART));
                return new Type.Error();
            }
        
            if (!(b instanceof Type.Int)) {
                er.semantic("Semantic: right operand of '^' must be integer, got " + printable(b), tokenAt(n, TokenType.TCART));
                return new Type.Error();
            }
        
            return (a instanceof Type.Real) ? new Type.Real() : new Type.Int();
        }

        Type r = numericResult(a, b);

        if (r == null) {
            er.semantic("Semantic: arithmetic operands must be numeric, got " + printable(a) + " and " + printable(b), tokenAtOp(n));
            return new Type.Error();
        }

        if (n.kind == StNodeKind.NMOD && !(a instanceof Type.Int && b instanceof Type.Int)) {
            er.semantic("Semantic: operator '%' requires integer operands", tokenAt(n, TokenType.TPERC));
            return new Type.Error();
        }

        return r;
    }

    // Relations
    private Type typeOfRel(StNode n) {
        Type a = typeOf(n.children().get(0));
        Type b = typeOf(n.children().get(1));

        if (a instanceof Type.Error || b instanceof Type.Error) return new Type.Error();

        switch (n.kind) {
            case NGRT, NLSS, NGEQ, NLEQ -> {
                if (Type.isNumeric(a) && Type.isNumeric(b)) {
                    return new Type.Bool();
                }
                er.semantic("Semantic: relational operator requires numeric operands, got " + printable(a) + " and " + printable(b) , tokenAtOp(n));
                return new Type.Error();
            }

            case NEQL, NNEQ -> {
                boolean bothNumeric = Type.isNumeric(a) && Type.isNumeric(b);
                boolean bothBool = Type.isBool(a) && Type.isBool(b);
                if (bothNumeric || bothBool) {
                    return new Type.Bool();
                }
                er.semantic("Semantic: '=='/'!=' require both numeric or both boolean got " + printable(a) + " and " + printable(b), tokenAtOp(n));
                return new Type.Error();
            }

            default -> {
                er.semantic("Semantic: internal error: typeOfRel called on " + n.kind, tokenAt(n, TokenType.TUNDF));
                return new Type.Error();
            }
        }
    }

    private Type typeOfNot(StNode n) {
        Type t = typeOf(n.children().get(0));

        if (!(t instanceof Type.Bool)) {
            er.semantic("Semantic: 'not' requires boolean operand, got " + printable(t), tokenAt(n, TokenType.TNOTT));
            return new Type.Error();
        }

        return new Type.Bool();
    }

    private Type typeOfBoolBin(StNode n) {
        Type a = typeOf(n.children().get(0));
        Type b = typeOf(n.children().get(1));

        if (!(a instanceof Type.Bool && b instanceof Type.Bool)) {
            er.semantic("Semantic: boolean operator requries boolean operands, got " + printable(a) + " and " + printable(b), tokenAtOp(n));
            return new Type.Error();
        }

        return new Type.Bool();
    }

    // Assignments
    private void visitAssign(StNode asgn) {
        List<StNode> k = asgn.children();

        if (k.size() < 2) return;

        StNode left = k.get(0);
        StNode right = k.get(1);

        ensureLValue(left);
        Type lt = typeOf(left);

        if (lt instanceof Type.Error) return; 

        ParamSymbol cps = constParamOfLValue(left);
        if (cps != null) {
            er.semantic("Semantic: cannot assign to constant parameter '" + cps.name() + "'",
                        tokenAtOp(asgn));
            return;
        }

        Type rt;

        switch (asgn.kind) {

            case NASGN -> {
                rt = typeOf(right);
            }

            case NPLEQ -> {
                rt = typeOf(new StNode(StNodeKind.NADD, null, asgn.line, asgn.col).add(left).add(right));
            }

            case NMNEQ -> {
                rt = typeOf(new StNode(StNodeKind.NSUB, null, asgn.line, asgn.col).add(left).add(right));
            }

            case NSTEA -> {
                rt = typeOf(new StNode(StNodeKind.NMUL, null, asgn.line, asgn.col).add(left).add(right));
            }

            case NDVEQ -> {
                rt = typeOf(new StNode(StNodeKind.NDIV, null, asgn.line, asgn.col).add(left).add(right));
            }

            default -> {
                return;
            }
        }

        if (rt instanceof Type.Error) return; 

        if (!assignable(lt, rt)) {
            er.semantic("Semantic: cannot assign " + printable(rt) + " to " + printable(lt), tokenAtOp(asgn));
        }

    }

    private boolean assignable(Type target, Type src) {
        if (target instanceof Type.Error || src instanceof Type.Error) return true;
        if (target == null || src == null) return false;
        if (target.getClass() == src.getClass()) return true;
        if (target instanceof Type.Real && src instanceof Type.Int) return true;
        if (target instanceof Type.Array ta && src instanceof Type.Array sa) {
            return assignable(ta.elem(), sa.elem()) && ta.size() == sa.size();
        }
        return false;
    }

    // I/O
    private void visitIO(StNode io) {
        switch (io.kind) {
            case NINPUT -> {
                if (io.children().isEmpty()) return;
                StNode vlist = io.children().get(0);
                for (StNode v : vlist.children()) {
                    ensureLValue(v);

                    Type t = typeOf(v);
                    if (t instanceof Type.Error) continue;

                    if (t instanceof Type.Array || t instanceof Type.Struct || t instanceof Type.VoidT) {
                        er.semantic("Semantic: cannot read into " + printable(t), tokenAt(io, TokenType.TINPT));
                    }
                }
            }

            case NOUTP -> {
                if (io.children().isEmpty()) return;
                StNode prlist = io.children().get(0);
                for (StNode pr : prlist.children()) {
                    if (pr.kind == StNodeKind.NSTRG) continue;
                    Type t = typeOf(pr);

                    if (t instanceof Type.Error) continue;
                    if (t instanceof Type.VoidT) {
                        er.semantic("Semantic: cannot print value of type void", tokenAt(io, TokenType.TOUTP));
                    }
                }
            }

            case NOUTL -> {
                if (io.children().isEmpty()) return;
                StNode prlist = io.children().get(0);
                for (StNode pr : prlist.children()) {
                    if (pr.kind == StNodeKind.NSTRG) continue;
                    Type t = typeOf(pr);
                    
                    if (t instanceof Type.Error) continue;
                    if (t instanceof Type.VoidT) {
                        er.semantic("Semantic: cannot print value of type void", tokenAt(io, TokenType.TOUTL));
                    }
                }
            }

            default -> {}
        }
    }

    private void visitControls(StNode n) {

        switch (n.kind) {
            case NFORL -> visitFor(n);

            case NREPT -> visitRept(n);
            case NIFTH -> visitIf(n);
            case NIFTE -> visitIf(n);

            default -> {}
        }
    }

    private void visitFor(StNode n) {
        if (n == null || n.children().isEmpty()) return;

        StNode trio = n.children().get(0);
        StNode body = (n.children().size() > 1) ? n.children().get(1) : null;

        StNode init = null, cond = null;
        if (trio != null && trio.kind == StNodeKind.NALIST) {
            List<StNode> t = trio.children();
            if (t.size() > 0 ) init = t.get(0);
            if (t.size() > 1 ) cond = t.get(1);
        }

        if (init != null) {
            switch (init.kind) {
                case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> visitAssign(init);
                default -> typeOf(init);
            }
        }

        if (cond != null) {
            Type ct = typeOf(cond);
            if(!(ct instanceof Type.Error) && !(ct instanceof Type.Bool)) {
                er.semantic("Semantic: for-loop condition must be boolean, got " + printable(ct),  tokenAt(cond, TokenType.TTFOR));
            }
        }

        if (body != null && body.kind == StNodeKind.NSTATS) {
            for (StNode s : body.children()) {
                visitStat(s);
            }
        }
    }

    private void visitRept(StNode n) {
        if (n == null || n.children().size() < 3) return;

        StNode a = n.children().get(0);
        StNode body = n.children().get(1);
        StNode cond = n.children().get(2);

        if (a != null && a.kind == StNodeKind.NALIST) {
            for (StNode assign : a.children()) {
                switch (assign.kind) {
                    case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> visitAssign(assign);
                    default -> typeOf(assign);
                }
            }
        }

        if (body != null && body.kind == StNodeKind.NSTATS) {
            for (StNode s : body.children()) {
                visitStat(s);
            }
        }

        if (cond != null) {
            Type ct = typeOf(cond);
            if (!(ct instanceof Type.Error) && !(ct instanceof Type.Bool)) {
                er.semantic("Semantic: Repeat-until condition must be boolean, got " + printable(ct),  tokenAt(cond, TokenType.TUNTL));
            }
        }
    }

    private void visitIf(StNode n) {
        if (n == null || n.children().size() < 2) return;

        StNode cond = n.children().get(0);
        if (cond != null) {
            Type ct = typeOf(cond);
            if (!(ct instanceof Type.Error) && !(ct instanceof Type.Bool)) {
                er.semantic("Semantic: If condition must be boolean, got " + printable(ct),  tokenAt(cond, TokenType.TIFTH));
            }
        }

        StNode body = n.children().get(1);
        if (body != null && body.kind == StNodeKind.NSTATS) {
            for (StNode s : body.children()) {
                visitStat(s);
            }
        }

        if (n.kind == StNodeKind.NIFTE && n.children().size() > 2) {
            StNode elseStats = n.children().get(2);
            if (elseStats != null && elseStats.kind == StNodeKind.NSTATS) {
                for (StNode s : elseStats.children()) {
                    visitStat(s);
                }
            }
        }
    }

    private void visitReturn(StNode n) {

        boolean hasExpr = !n.children().isEmpty();
        Type exprType = hasExpr ? typeOf(n.children().get(0)) : null;

        if (currentFuncReturnType == null) {
            er.semantic("Semantic: 'return' not allowed at global scope", tokenAt(n, TokenType.TRETN));
            return;
        }

        if (hasExpr) {
            exprType = typeOf(n.children().get(0));
        }

        if (currentFuncReturnType instanceof Type.VoidT) {
            if (hasExpr) {
                er.semantic("Semantic: 'return' in a void function must not return a value", tokenAt(n, TokenType.TRETN));
            }
        } else {
            if (!hasExpr) {
                er.semantic("Semantic: missing return value", tokenAt(n, TokenType.TRETN));
            } else if (!(exprType instanceof Type.Error) && !assignable(currentFuncReturnType, exprType)) {
                er.semantic("Semantic: cannot return " + printable(exprType) + " from function returning " + printable(currentFuncReturnType), tokenAt(n, TokenType.TRETN));
            }
        }

        sawReturnInCurrentFunc = true;
    }


    private interface N2 { Object f(Object a, Object b); } // so we can take in the lambda function defined in evalExpr (x, y)

    // applies a numeric operation on two nums
    private Object num2(StNode n, N2 f) {
        Object a = evalExpr(n.children().get(0));
        Object b = evalExpr(n.children().get(1));
        if (!isNum(a) || !isNum(b)) return null; // if neither side becomes a int or double, we cont do a numeric operation
        return f.f(a, b); // both sides are numeric, produce the result 
    }

    private boolean isNum(Object o){ return o instanceof Integer || o instanceof Double; }
    private boolean isReal(Object a, Object b){ return (a instanceof Double) || (b instanceof Double); }
    private int toI(Object o){ return (o instanceof Integer i) ? i : ((Number) o).intValue(); }
    private double toD(Object o){ return (o instanceof Double d) ? d : ((Number) o).doubleValue(); }

    private Object safeIntDiv(int x, int y) { return (y == 0) ? null : (x / y); }
    private Object safeIntMod(int x, int y) { return (y == 0) ? null : (x % y); }

    // made so we return an int since Math.pow returns doubles, needed that gpt help with this one, my goat
    private Object intPow(int base, int exp) {
        if (exp < 0) return null;
        long res = 1, b = base;
        int e = exp;
        while (e > 0) {
            if ((e & 1) == 1) res *= b;
            b *= b; e >>= 1;
        }
        return (int) res;
    }

    private interface R2 { boolean f(Object a, Object b); }

    // same as num2 but for relational operators
    private Object rel(StNode n, R2 r) {
        Object a = evalExpr(n.children().get(0));
        Object b = evalExpr(n.children().get(1));
        if (a == null || b == null) return null;
        return r.f(a, b);
    }

    /**
     * Boolean.compare -> false is treated as less than true. so if x is true and y is false = x > y
     * if they arent booleans, assume they are numeric, convert to doubles
     * then just compare them on that, negatie if x < y, zero if equal, positive if x > y
     * 
     */
    private int cmp(Object a, Object b) {
        if (a instanceof Boolean ba && b instanceof Boolean bb) return Boolean.compare(ba, bb);
        double x = toD(a), y = toD(b);
        return Double.compare(x, y);
    }
}
