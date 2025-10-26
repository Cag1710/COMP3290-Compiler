import java.util.List;

public class CodeGenerator {
    private final Emitter em;
    private final SymbolTable table;

    public CodeGenerator(SymbolTable table, Emitter em) {
        this.table = table;
        this.em = em;
    }

    public void generate(StNode root) {
        visitProgram(root);
    }

    private void visitProgram(StNode root) {

        StNode nglob = root.getChild(StNodeKind.NGLOB);
        StNode nfuncs = root.getChild(StNodeKind.NFUNCS);
        StNode nmain = root.getChild(StNodeKind.NMAIN);
        // do main first and then funcs
        // TODO: globals confuse me
        // if (nglob != null) { genGlobals(nglob); }
        if (nmain != null) { genMain(nmain); }
        if (nfuncs != null) { genFuncs(nfuncs); }

        em.emit("HALT");

        

    }

    private void genMain(StNode root) {
        em.label("main");

        // allocate space for local vars
        StNode dlist = root.getChild(StNodeKind.NSDLST);
        if (dlist != null) {
            int numLocalVars = dlist.children().size();
            em.emit("ALLOC", numLocalVars);
        }

        // gen stats
        StNode stats = root.getChild(StNodeKind.NSTATS);
        if (stats != null) {
            for (StNode stmt : stats.children()) {
                genStatement(stmt);
            }
        }
    }

    private void genFuncs(StNode root) {

    }

    private void genStatement(StNode stat) {
        switch (stat.kind) {
            case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> genAssign(stat);
            case NINPUT, NOUTP, NOUTL -> genIO(stat);
            case NFORL, NREPT, NIFTH, NIFTE -> genControls(stat);
            case NRETN -> genReturn(stat);
            case NCALL -> {}
            default -> {}
        }
    }

    private void genAssign(StNode n)  {
        List<StNode> children = n.children();
        StNode lhs = children.get(0);
        StNode rhs = children.get(1);

        // gen the rhs first
        genExpression(rhs);

        if (lhs.kind == StNodeKind.NSIMV) {
            // handle simple var
            VarSymbol s = (VarSymbol) table.resolve(lhs.lexeme);
            switch (n.kind) {
                case NASGN -> { 
                    em.emit("ST", s.base(), s.offset()); 
                }
                case NPLEQ -> {  
                    em.emit("LV2", s.base(), s.offset());   // load current value first
                    em.emit("ADD", s.base(), s.offset());   // add RHS
                    em.emit("ST", s.base(), s.offset());
                }
                case NMNEQ -> {
                    em.emit("LV2", s.base(), s.offset());
                    em.emit("SUB", s.base(), s.offset());
                    em.emit("ST", s.base(), s.offset());
                }
                case NSTEA -> { 
                    em.emit("LV2", s.base(), s.offset());
                    em.emit("MUL", s.base(), s.offset());
                    em.emit("ST", s.base(), s.offset());
                }
                case NDVEQ -> { 
                    em.emit("LV2", s.base(), s.offset());
                    em.emit("DIV", s.base(), s.offset());
                    em.emit("ST", s.base(), s.offset());
                }
                default -> {
                    System.out.println("Cannot generate code: unknown assign kind.");
                    em.emit("TRAP");
                }
            }
        }
        else if (lhs.kind == StNodeKind.NARRV) {
            // handle array lhs (eg: arr[i].field)
            // TODO: 


        }
    }

    private void genExpression(StNode expr) {
        switch (expr.kind) {
            case NILIT -> {
                pushIntLiteral(expr);
            }
            case NFLIT -> {
                // build and push float onto stack
                double val = Double.parseDouble(expr.lexeme);
                if (val == 0.0) {
                    em.emit("ZERO");
                    em.emit("FTYPE");   // convert int 0 to float
                }
                else {
                    pushNonZeroFloat(expr);
                }
            }
            case NTRUE -> {
                em.emit("TRUE");
            }
            case NFALS -> {
                em.emit("FALSE");
            }
            case NSIMV -> {
                VarSymbol s = (VarSymbol) table.resolve(expr.lexeme);
                em.emit("LV2", s.base(), s.offset());
            }
            case NADD, NSUB, NMUL, NDIV, NMOD, NPOW -> {
                genBinaryOp(expr);
            }
            case NARRV -> {
                // TODO: generate array expr
            }
            case NFCALL -> {
                // genFnCall(expr);
            }
            default -> {
                System.out.println("Cannot generate code: unknown expression kind.");
                em.emit("TRAP");
            }
        }
    }

    // push int literal onto stack
    private void pushIntLiteral(StNode expr) {
        int val = Integer.parseInt(expr.lexeme);
        if (val >= -128 && val <= 127) {
            // int literal fits in 1 byte
            em.emit("LB", val);
        }
        else if (val >= -32768 && val <= 32767) {
            // fits in 2 bytes
            em.emit("LH", val);
        }
        else {
            System.out.println("Cannot generate code: integer literal cannot be larger than 2 bytes.");
            em.emit("TRAP");
        }
    }

    private void pushNonZeroFloat(StNode expr) {
        double val = Double.parseDouble(expr.lexeme);
        int intPart = (int) val;
        double fracPart = Math.abs(val - intPart);

        if (intPart != 0) {
            // push integer part to stack, convert to float
            pushIntLiteral(expr);
            em.emit("FTYPE");
        }

        // TODO: finish this, I can't figure out the best way to add the fractional part to the stack
        // there must be a way to push floats to the stack?

    }

    private void genBinaryOp(StNode expr) {
        StNode lhs = expr.children().get(0);
        StNode rhs = expr.children().get(1);

        genExpression(lhs);
        genExpression(rhs);

        switch (expr.kind) {
            case NADD -> em.emit("ADD");
            case NSUB -> em.emit("SUB");
            case NMUL -> em.emit("MUL");
            case NDIV -> em.emit("DIV");
            case NMOD -> em.emit("REM");
            case NPOW -> em.emit("POW");
            default -> {}
        }
    }

    private void genIO(StNode n)  {
        switch (n.kind) {
            case NINPUT -> genInput(n);
            case NOUTP, NOUTL -> genOutput(n);
            default -> {}
        }
    }

    private void genInput(StNode n) {
        // for each varible in the input decl
        for (StNode var : n.children()) {
            VarSymbol s = (VarSymbol) table.resolve(var.lexeme);
            if (Type.isInteger(s.type())) { em.emit("READI"); }
            else if (Type.isReal(s.type())) { em.emit("READF"); }
            // store the read val into the var location
            em.emit("LV2", s.base(), s.offset());
            em.emit("ST");    
        }
    }

    private void genOutput(StNode n) {
        if (n.kind == StNodeKind.NOUTL && n.children().isEmpty()) {
            // Out << Line
            em.emit("NEWLN");
            return;
        }
        for (StNode child : n.children()) {
            switch (child.kind) {
                case NSTRG -> {
                    // TODO: handle string literals
                    // need a way to store strings into addresses to use the STRPR opcode
                }
                case NSIMV, NILIT, NFLIT, NADD, NSUB, NMUL, NDIV, NFCALL -> {
                    // handle expressions
                    genExpression(child);
                    em.emit("VALPR");
                }
                default -> {}
            }
        }
    }

    private void genControls(StNode n)  {
         
    }

    private void genReturn(StNode n)  {

    }
}
