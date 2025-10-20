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

    // ensure the left hand value is actually a variable that can handle assignment - id, arr[index], arr[index].field
    private void ensureLValue(StNode n) {
        boolean ok = n.kind == StNodeKind.NSIMV || n.kind == StNodeKind.NAELT || n.kind == StNodeKind.NARRV;
        if (!ok) er.semantic("Semantic: left side of assignment must be a variable", null);
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

    // resolve an NSIMV node to a declaration and return its type
    private Type typeOfId(String name, StNode at) {
        Symbol s = table.resolve(name); // check the symbol is actually declared in a scope somewhere

        if (s == null) {
            er.semantic("Semantic: identifier used before declaration: " + name, null);
            return new Type.Error();
        }

        at.setSymbol(s); // bind this declaration to the node
        return s.type(); // return the symbols type
    }

    // handles base[index]
    private Type typeOfArrayIndex(StNode n) {
        StNode base = n.children().get(0); // get the base
        StNode index = n.children().get(1); // get the indexd

        // get their types
        Type bt = typeOf(base);
        Type it = typeOf(index);

        // if base type is not an array, error
        if (!(bt instanceof Type.Array a)) { // pattern matching - instanceof checks if object is specific type, if true creates a new variable and assigns the object to it
            er.semantic("Semantic: indexed expression is not an array: " + base.lexeme, null);
            return new Type.Error();
        }

        // if index type is not an int, error
        if (!(it instanceof Type.Int)) {
            er.semantic("Semantic: array index must be an integer", null);
            return new Type.Error();
        }

        // the type of base[index] is the arrays element type
        return a.elem();
    }

    // handles base[index].field
    private Type typeOfArrayElem(StNode n) {
        StNode base = n.children().get(0); // base
        StNode index = n.children().get(1); // index
        StNode field = n.children().get(2); // field

        // ensure base[index] is valid and yields a struct element type
        Type elem = typeOfArrayIndex(new StNode(StNodeKind.NAELT, null, n.line, n.col).add(base).add(index));

        // ensure the element is a struct
        if (!(elem instanceof Type.Struct s)) {
            er.semantic("Semantic: array element is not a struct, cannot select field '" + field.lexeme + "'" , null);
            return new Type.Error();
        }
        
        // look up the field type in the structs field map - Map<String, Type> fields
        Type ft = s.fields().get(field.lexeme);

        // unknown field, error
        if (ft == null) {
            er.semantic("Semantic: unknown field '" + field.lexeme + "' in struct", null);
            return new Type.Error();
        }

        // return the field type
        return ft;
    }
}
