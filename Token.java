public enum Token {
    // === Keywords ===
    TCD25(1), TCONS(2), TTYPS(3), TIS(4), TARRS(5), TMAIN(6), TBEGN(7), TEND(8),
    TARRT(9), TOF(10), TFUNC(11), TVOID(12), TCNST(13), TINTG(14), TREAL(15), TBOOL(16),
    TFOR(17), TRPT(18), TUNTL(19), TIF(20), TELSE(21), TIN(22), TOUT(23), TOUTL(24),
    TRET(25), TNOT(26), TAND(27), TORR(28), TXORR(29), TTRU(30), TFLS(31),
    // === Operators and Delimiters ===
    TCOMA(32), TLBRK(33), TRBRK(34), TLPAR(35), TRPAR(36), TEQUL(37), TPLUS(38), TMINS(39),
    TSTAR(40), TDIVD(41), TPERC(42), TCART(43), TLESS(44), TGRTR(45), TCOLN(46), TLEQL(47),
    TGEQL(48), TNEQL(49), TEQEQ(50), TPLEQ(51), TMNEQ(52), TSTEQ(53), TDVEQ(54), TSEMI(55),
    TDOTT(56), TGRGR(57), TLSLS(58),
    // === Tokens that need tuple values ===
    TIDEN(59), TILIT(60), TFLIT(61), TSTRG(62), TUNDF(63);

    // === Fields and constructor ===
    private final int id;

    Token(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return Integer.toString(id);
    }

    // === Token print strings ===
    // Used for the actual printing
    // Say we want to print a token: System.out.println(Token.TPRINT[token.getId()])
    public static final String[] TPRINT = {
        "TEOF ",  // placed at index 0 since the actual tokens start at 1, lil placeholder
        "TCD25 ", "TCONS ", "TTYPS ", "TIS ", "TARRS ", "TMAIN ", "TBEGN ", "TEND ", "TARRT ", "TOF ",
        "TFUNC ", "TVOID ", "TCNST ", "TINTG ", "TREAL ", "TBOOL ", "TFOR ", "TRPT ", "TUNTL ", "TIF ",
        "TELSE ", "TIN ", "TOUT ", "TOUTL ", "TRET ", "TNOT ", "TAND ", "TORR ", "TXORR ", "TTRU ", "TFLS ",
        "TCOMA ", "TLBRK ", "TRBRK ", "TLPAR ", "TRPAR ", "TEQUL ", "TPLUS ", "TMINS ", "TSTAR ", "TDIVD ",
        "TPERC ", "TCART ", "TLESS ", "TGRTR ", "TCOLN ", "TLEQL ", "TGEQL ", "TNEQL ", "TEQEQ ", "TPLEQ ",
        "TMNEQ ", "TSTEQ ", "TDVEQ ", "TSEMI ", "TDOTT ", "TGRGR ", "TLSLS ",
        "TIDEN ", "TILIT ", "TFLIT ", "TSTRG ", "TUNDF "
    };
}


