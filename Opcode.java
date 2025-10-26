public enum Opcode {
    
    HALT(0), NOOP(1), TRAP(2),

    ZERO(3), FALSE(4), TRUE(5),

    TYPE(7), ITYPE(8), FTYPE(9),

    ADD(11), SUB(12), MUL(13), DIV(14), REM(15), POW(16), CHS(17), ABS(18),

    GT(21), GE(22), LT(23), LE(24), EQ(25), NE(26),

    AND(31), OR(32), XOR(33), NOT(34),

    BT(35), BF(36), BR(37),

    L(40), LB(41), LH(42), ST(43),

    STEP(51), ALLOC(52), ARRAY(53), INDEX(54), SIZE(55), DUP(56),

    READF(60), READI(61), VALPR(62), STRPR(63), CHRPR(64), NEWLN(65), SPACE(66),

    RVAL(70), RETN(71), JS2(72),
    
    LV0(80), LV1(81), LV2(82),
    LA0(90), LA1(91), LA2(92);

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int getId() {
        return code;
    }
}
