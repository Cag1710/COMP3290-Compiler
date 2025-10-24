import java.util.*;

public final class SemanticAnalyzer {
    private final SymbolTable table;
    private final ErrorReporter er;

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
                case NSIMP -> { // simple parameters
                    for (StNode sd : p.children()) {
                        if (sd.kind == StNodeKind.NSDECL) {
                            Type t = typeFromNode(firstChild(sd, StNodeKind.NSTYPE));
                            out.add(t);
                        }
                    }
                }
                case NARRP, NARRC -> { // array parameters
                    StNode arrd = firstChild(p, StNodeKind.NARRD);
                    if (arrd != null && arrd.children().size() >= 2) {
                        String typeName = arrd.children().get(1).lexeme;
                        Type named = baseTypeFromLexeme(typeName);      
                        out.add(named); 
                    } else {
                        out.add(null);
                    }
                }
                default -> {}
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

    }

    /**
     * enter mains scope
     * validate statements
     * exit main scope
     * 
     */
    private void visitMain(StNode nmain) {
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

    private void defineSimpleDecl(StNode nsdecl) {
        String name = firstName(nsdecl);

        Type t = typeFromNode(firstChild(nsdecl, StNodeKind.NSTYPE));
        if (t == null) {
            er.semantic("Semantic: unknown type for '" + name + "'", tokenAt(nsdecl, TokenType.TUNDF)); 
            t = new Type.Error();
        }
        defineOrDup(new VarSymbol(name, t), nsdecl);
    }

    // nameOverride if the caller already knows the name of the array e.g. in declareLocal we call
    // firstName(d)
    private void defineArrayDecl(StNode d, String nameOverride) {

    } 

    // statement dispatcher
    private void visitStat(StNode s) {

        switch (s.kind) {
            case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> visitAssign(s);
            case NINPUT, NOUTP, NOUTL -> visitIO(s);
            case NCALL -> {}
            case NFORL, NREPT, NIFTH, NIFTE -> visitControls(s);

            case NRETN -> {

            }

            default -> {}
        }
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
            Token pos = new Token(TokenType.TIDEN, left.lexeme, left.line, left.col);
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
}
