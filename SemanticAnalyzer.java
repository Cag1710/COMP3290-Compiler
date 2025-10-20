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
            er.semantic("Semantic: duplicate identifier in this scope: '" + s.name() + "'", null);
        }
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
            er.semantic("Semantic: program end name '" + tail + "' does not match header '" + head + "'", null);
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
                er.semantic("Semantic: function missing name", null);
                continue;
            }

            StNode rtNode = firstChild(f, StNodeKind.NSTYPE);
            Type rType = typeFromNode(rtNode);

            if (rType == null) {
                er.semantic("Semantic: unknown or missing return type for function '" + fname + "'", null);
                rType = new Type.Error();
            }

            List<Type> paramTypes = paramTypesOf(f);

            for (int i = 0; i < paramTypes.size(); i++) {
                if (paramTypes.get(i) == null) {
                    er.semantic("Semantic: parameter '" + (i + 1) + "' of function '" + fname + "' has unknown/invalid type", null);
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

    }
}
