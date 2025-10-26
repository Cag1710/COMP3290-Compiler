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
                default -> {}
            }
        }
        else if (lhs.kind == StNodeKind.NARRV) {
            // handle array lhs (eg: arr[i].field)
            // TODO: 


        }
    }

    private void genIO(StNode n)  {

    }

    private void genControls(StNode n)  {

    }

    private void genReturn(StNode n)  {

    }
}
