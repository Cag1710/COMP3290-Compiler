import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class CodeGenerator {
    private final Emitter em;
    private final SymbolTable table;

    private final Map<String, Integer> constPool = new LinkedHashMap<>();
    private int constNextOff = 0; // bytes from b0 where we place next const

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
                pushFloatLiteral(expr);
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
            case NEQL, NNEQ, NGRT, NLSS, NGEQ, NLEQ -> {
                genExpression(expr.children().get(0)); // lhs
                genExpression(expr.children().get(1)); // rhs
                em.emit("SUB");   
                switch (expr.kind) {
                    case NEQL -> em.emit("EQ");  
                    case NNEQ -> em.emit("NE");  
                    case NGRT -> em.emit("GT");  
                    case NLSS -> em.emit("LT");  
                    case NGEQ -> em.emit("GE");  
                    case NLEQ -> em.emit("LE"); 
                }
            }
            case NNOT -> { genExpression(expr.children().get(0)); em.emit("NOT"); }
            case NAND -> { genExpression(expr.children().get(0)); genExpression(expr.children().get(1)); em.emit("AND"); }
            case NOR  -> { genExpression(expr.children().get(0)); genExpression(expr.children().get(1)); em.emit("OR");  }
            case NXOR -> { genExpression(expr.children().get(0)); genExpression(expr.children().get(1)); em.emit("XOR"); }

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

    private void pushFloatLiteral(StNode expr) {
        double val = Double.parseDouble(expr.lexeme);

        if (val == 0.0) {
            em.emit("ZERO");
            em.emit("FTYPE");
            return;
        }

        int off = internFloatConst(val);
        em.emit("LA0", off);
        em.emit("L");

    }

    private void pushStringLiteral(String s) {
        int off = internStringConst(s);
        em.emit("LA", off);
        em.emit("STRPR");
    }

    // running memory map of float constants
    private int internFloatConst(double d) {
        String k = "F:" + Double.toString(d);
        return constPool.computeIfAbsent(k, kk -> { int off = constNextOff; constNextOff += 8; return off; });
    }

    private int internStringConst(String s) {
        String k = "S:" + s;
        return constPool.computeIfAbsent(k, kk -> {
            int off = constNextOff;
            constNextOff += 8;
            return off;
        });
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
            else { em.emit("TRAP"); continue; }
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
                    String s = child.lexeme;
                    pushStringLiteral(s);
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
