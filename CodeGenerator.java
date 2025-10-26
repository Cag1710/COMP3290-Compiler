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
        // globals, main, funcs
        if (nglob != null) { genGlobals(nglob); }
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
        for (StNode f : root.children()) {
            genFunc(f);
        }
    }

    private void genFunc(StNode f) {
        // function name
        String name = f.lexeme;
        em.label(name);
        // function locals
        int localCount = 0;
        StNode locals = f.getChild(StNodeKind.NDLIST);
        if (locals != null) {
            for (StNode d : locals.children()) {
                Symbol sym = d.getSymbol();
                if (sym instanceof VarSymbol v) {
                    localCount += Math.max(1, v.sizeWords());
                } else {
                    localCount += 1;
                }
            }
        }

        if (localCount > 0) {
            em.emit("ALLOC", localCount);
        }
        
        // function body
        StNode stats = f.getChild(StNodeKind.NSTATS);
        if (stats != null) {
            for (StNode s : stats.children()) {
                genStatement(s);
            }
        }
        em.emit("RETN");
    }

    private void genGlobals(StNode nglob) {

    }

    private void genCallCommon(String fn, List<StNode> args) {
        for (StNode a : args) {
            genExpression(a);
        }

        em.emit("LB", args.size()); // param count
        em.emit("LA_LABEL", fn); // address of label
        em.emit("JS2"); // perform call
    }

    private void gennFCall(StNode call) {

        String fn = call.lexeme; // name
        List<StNode> args = call.children(); // params
        genCallCommon(fn, args);
    }

    private void genCallStmt(StNode call) {
        String fn = call.lexeme; // name
        List<StNode> args = call.children(); // params

        Symbol s = table.resolve(fn);

        if (s instanceof FuncSymbol fs && !(fs.returnType() instanceof Type.VoidT)) {
            genCallCommon(fn, args);
            em.emit("STEP");
            return;
        }

        genCallCommon(fn, args);
    }

    private void genStatement(StNode stat) {
        switch (stat.kind) {
            case NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ -> genAssign(stat);
            case NINPUT, NOUTP, NOUTL -> genIO(stat);
            case NFORL, NREPT, NIFTH, NIFTE -> genControls(stat);
            case NRETN -> genReturn(stat);
            case NCALL -> genCallStmt(stat);
            default -> {}
        }
    }

    private void genAssign(StNode n)  {
        List<StNode> children = n.children();
        StNode lhs = children.get(0);
        StNode rhs = children.get(1);
    
        if (lhs.kind == StNodeKind.NSIMV) {
            // simple var
            Symbol s = symOf(lhs);
            int base, off;
            if (s instanceof VarSymbol v) { base = v.base(); off = v.offset(); }
            else if (s instanceof ParamSymbol p) { base = p.base(); off = p.offset(); }
            else { em.emit("TRAP"); return; }
    
            switch (n.kind) {
                case NASGN -> {
                    // x = RHS
                    genExpression(rhs);
                    em.emit("ST", base, off);
                }
                case NPLEQ -> {
                    // x += RHS  ==>  x  RHS  ADD  -> x
                    em.emit("LV2", base, off);
                    genExpression(rhs);
                    em.emit("ADD");
                    em.emit("ST", base, off);
                }
                case NMNEQ -> {
                    em.emit("LV2", base, off);
                    genExpression(rhs);
                    em.emit("SUB");
                    em.emit("ST", base, off);
                }
                case NSTEA -> {
                    em.emit("LV2", base, off);
                    genExpression(rhs);
                    em.emit("MUL");
                    em.emit("ST", base, off);
                }
                case NDVEQ -> {
                    em.emit("LV2", base, off);
                    genExpression(rhs);
                    em.emit("DIV");
                    em.emit("ST", base, off);
                }
                default -> {
                    System.out.println("Cannot generate code: unknown assign kind.");
                    em.emit("TRAP");
                }
            }
        }
        else if (lhs.kind == StNodeKind.NARRV) {
            // --- keep your original array/field handling exactly as-is ---
            StNode arrId = lhs.children().get(0);
            StNode index = lhs.children().get(1);
            StNode field = lhs.children().get(2);
    
            VarSymbol arrSym = (VarSymbol) table.resolve(arrId.lexeme);
            // get the array type and therefore the struct type
            Type.Array arrType = (Type.Array) arrSym.type();
            Type.Struct elemStruct = (Type.Struct) arrType.elem();
    
            em.emit("LV2", arrSym.base(), arrSym.offset());
            genExpression(index);
            em.emit("INDEX", typeSize(elemStruct));
            int fieldOffset = computeFieldOffset(elemStruct, field.lexeme);
            if (fieldOffset != 0) em.emit("STEP", fieldOffset);
    
            em.emit("ST");
        }
    }
    

    // compute the size in words of a type
    private int typeSize(Type t) {
        if (t instanceof Type.Int || t instanceof Type.Real || t instanceof Type.Bool) {
            return 1; // primitive types take 1 word
        } else if (t instanceof Type.Array arr) {
            // array size = number of elements * size of one element
            return arr.size() * typeSize(arr.elem());
        } else if (t instanceof Type.Struct s) {
            // sum of sizes of all fields
            int size = 0;
            for (Type fieldType : s.fields().values()) {
                size += typeSize(fieldType);
            }
            return size;
        } else {
            return 1; // default
        }
    }

    // compute the offset in words of a field inside a struct
    private int computeFieldOffset(Type.Struct s, String fieldName) {
        int offset = 0;
        for (Map.Entry<String, Type> entry : s.fields().entrySet()) {
            if (entry.getKey().equals(fieldName)) {
                return offset; // found the field, return offset
            }
            offset += typeSize(entry.getValue()); // add size of this field
        }
        return 0;  // default
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
                Symbol s = symOf(expr);
                if (s instanceof VarSymbol v)      em.emit("LV2", v.base(), v.offset());
                else if (s instanceof ParamSymbol p) em.emit("LV2", p.base(), p.offset());
                else em.emit("TRAP");
            }
            case NADD, NSUB, NMUL, NDIV, NMOD, NPOW -> {
                genBinaryOp(expr);
            }
            case NARRV -> {
                genArrayExpr(expr);
            }
            case NFCALL -> {
                gennFCall(expr);
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
                    default -> {}
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

    private void genArrayExpr(StNode arrNode) {
        // lhs is NARRV: <id>[<index>].<field>
        StNode arrId = arrNode.children().get(0);
        StNode index = arrNode.children().get(1);
        StNode field = arrNode.children().get(2);

        VarSymbol arrSym = (VarSymbol) table.resolve(arrId.lexeme);
        Type.Array arrType = (Type.Array) arrSym.type();
        Type.Struct elemStruct = (Type.Struct) arrType.elem();

        // load array base
        em.emit("LV2", arrSym.base(), arrSym.offset());

        // generate index
        genExpression(index);

        // compute element address
        em.emit("INDEX", typeSize(elemStruct));

        // step to the correct field within the struct
        int fieldOffset = computeFieldOffset(elemStruct, field.lexeme);
        if (fieldOffset != 0) em.emit("STEP", fieldOffset);

        // load the value from the computed address
        em.emit("L");
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
        // Handle "Out << Line"
        if (n.kind == StNodeKind.NOUTL && n.children().isEmpty()) {
            em.emit("NEWLN");
            return;
        }
    
        List<StNode> items;
        if (!n.children().isEmpty() && n.children().get(0) != null) {
            items = n.children().get(0).children(); 
        } else {
            items = n.children();
        }
    
        for (StNode child : items) {
            switch (child.kind) {
                case NSTRG -> {
                    pushStringLiteral(child.lexeme);
                }
                case NSIMV, NILIT, NFLIT, NADD, NSUB, NMUL, NDIV, NFCALL, NEQL, NNEQ, NGRT, NLSS, NGEQ, NLEQ -> {
                    genExpression(child);
                    em.emit("VALPR");
                }
                default -> {
                    
                }
            }
        }
    
        if (n.kind == StNodeKind.NOUTL) {
            em.emit("NEWLN");
        }
    }

    private void genControls(StNode n)  {
        switch (n.kind) {
            case NFORL -> genFor(n);
            case NREPT -> genRept(n);
            case NIFTH, NIFTE -> genIf(n);
            default -> {}
        }
    }

    private void genFor(StNode n) {
        StNode asgnList = n.children().get(0);
        StNode cond = n.children().get(1);
        StNode body = n.children().get(2);
        // label generation for control flow
        String startLabel = em.newLabel("for_start");
        String endLabel = em.newLabel("for_end");
        
        // generate each assignment
        if (asgnList != null) {
            for (StNode init : asgnList.children()) {
                genAssign(init);
            }
        }

        em.label(startLabel);

        // generate condition
        if (cond != null) {
            genExpression(cond);
            em.emit("BF", endLabel);    // jump to end if pushed cond is false
        }

        // generate loop body
        if (body != null) {
            for (StNode stat : body.children()) {
                genStatement(stat);
            }
        }

        em.emit("BR", startLabel);      // jump back to start
        em.label(endLabel);             // mark exit
    }

    private void genRept(StNode n) {
        StNode asgnList = n.children().get(0);
        StNode body = n.children().get(1);
        StNode cond = n.children().get(2);
        // label generation for control flow
        String startLabel = em.newLabel("rept_start");

        em.label(startLabel);

        // generate assignments
        if (asgnList != null) {
            for (StNode init : asgnList.children()) {
                genAssign(init);
            }
        }

        // generate body statements
        if (body != null) {
            for (StNode stat : body.children()) {
                genStatement(stat);
            }
        }

        // generate and check repeat condition
        if (cond != null) {
            genExpression(cond);
            em.emit("BF", startLabel);  // jump back to start if condition is false
        }
    }

    private void genIf(StNode n) {
        StNode cond = n.children().get(0);  // condition
        StNode ifStats = n.children().get(1); // statements for IF block
        StNode elseStats = (n.kind == StNodeKind.NIFTE) ? n.children().get(2) : null;   // statements for ELSE

        String endLabel = em.newLabel("end_if");
        String elseLabel = em.newLabel("else");

        // gen condition
        genExpression(cond);
        em.emit("BF", elseLabel);   // skip if block if false

        // generate if block
        for (StNode stat : ifStats.children()) {
            genStatement(stat);
        }

        if (elseStats != null) {
            // jump to end label after if block to avoid else
            em.emit("BR", endLabel);
        }

        // generate else block (if exists)
        em.label(elseLabel);
        if (elseStats != null) {
            for (StNode stat : elseStats.children()) {
                genStatement(stat);
            }
            em.label(endLabel);
        }
    }

    private void genReturn(StNode n)  {
        if (!n.children().isEmpty()) {
            genExpression(n.children().get(0));
            em.emit("RVAL");
        }
        em.emit("RETN");
    }

    private Symbol symOf(StNode n) {
        Symbol s = n.getSymbol();
        if (s == null) {
            // As a fallback you *can* try resolve, but expect null for locals/params:
            s = (n.lexeme != null) ? table.resolve(n.lexeme) : null;
        }
        if (s == null) {
            throw new IllegalStateException(
                "Unbound identifier '" + n.lexeme + "' at " + n.line + ":" + n.col);
        }
        return s;
    }
}
