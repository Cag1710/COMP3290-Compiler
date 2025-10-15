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

    private static StNode firstChild(StNode n, StNodeKind k) {
        if (n == null) return null;
        for (StNode c : n.children()) {
            if (c.kind == k) {
                return c;
            }
        }
        return null;
    }

    // check name and footer match
    private void checkProgramName(StNode prog) {

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
