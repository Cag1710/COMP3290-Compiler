import java.util.*;

enum StNodeKind {

    // program & sections
    NPROG, NGLOB, NFUNCS, NMAIN,

    // consts/types/arrays blocks
    NILIST, NINIT,
    NTYPEL, NRTYPE, NATYPE, NFLIST,
    NSDECL, NALIST, NARRD, NSTYPE,

    // functions
    NFUND, NPLIST, NSIMP, NARRP, NARRC,

    // locals / decl lists
    NDLIST, NSDLST,

    // statements / control
    NSTATS, NFORL, NREPT, NASGNS,
    NIFTH, NIFTE,

    // assignment & ops
    NASGN, NPLEQ, NMNEQ, NSTEA, NDVEQ,

    // I/O
    NINPUT, NOUTP, NOUTL,

    // calls / return
    NCALL, NRETN, NFCALL,

    // variables / lists
    NVLIST, NSIMV, NARRV, NAELT,
    NEXPL, NPRLST,

    // booleans / relations
    NBOOL, NNOT, NAND, NOR, NXOR,
    NEQL, NNEQ, NGRT, NLSS, NLEQ, NGEQ,

    // arithmetic
    NADD, NSUB, NMUL, NDIV, NMOD, NPOW,

    // literals / strings
    NILIT, NFLIT, NTRUE, NFALS, NSTRG,

    // error placeholder
    NUNDEF
}

public final class StNode {
    public final StNodeKind kind; // what the node represents
    public final String lexeme; // identifier
    public final int line, col; // position
    private final List<StNode> kids = new ArrayList<>(); // holds the ordered children (left-to-right)

    public StNode(StNodeKind kind, String lexeme, int line, int col) {
        this.kind = Objects.requireNonNull(kind);
        this.lexeme = lexeme;
        this.line = line;
        this.col = col;
    }

    /**
     * used to build the tree
     * e.g. x = 5
     * Token id = ts.expect(TokenType.TIDEN)
     * ts.expect(TokenType.TEQUL)
     * Token lit = ts.expect(TokenType.TILIT)
     * StNode lhs = StNode.leaf(StNodeKind.NSIMV, id)
     * STNode rhs = StNode.leaf(StNodeKind.NILIT, lit)
     * return new STNode(STNodeKind.NASGN, null, id.line, id.col).add(lhs).add(rhs)
     * So leaf wraps the tokens x and 5 into leaves
     * @return
     */
    public static StNode leaf(StNodeKind kind, Token t) {
        return new StNode(kind, (t != null ? t.lexeme : null), 
                                (t != null ? t.line : -1), 
                                (t != null ? t.col : -1));
    }

    // links parent nodes to the chilren e.g. x = 5 has NASGN -> SIMV(x) and NILIT(5)
    public StNode add(StNode child) {
        if (child != null) {
            kids.add(child);
        }
        return this;
    }

    // shows the list of all child nodes this node has
    public List<StNode> children() {
        return Collections.unmodifiableList(kids);
    }

    // special placeholder for undefined node, keeps tree consistent
    public static StNode undefAt(Token t) {
        return new StNode(StNodeKind.NUNDEF, null, 
                            (t != null ? t.line : -1), 
                            (t != null ? t.col : -1));
    }

    @Override
    public String toString() {
        return kind + (lexeme != null ? "(" + lexeme + ")" : "");
    }
}
