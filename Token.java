public class Token {
    public final TokenType tokenType;
    public final String lexeme;
    public final int line;
    public final int col;

    public Token(TokenType tokenType, String lexeme, int line, int col) {
        this.tokenType = tokenType;
        this.lexeme = lexeme;
        this.line = line;
        this.col = col;
    }

    // pad lexeme so its printed field is a multiple of 6
    private static String padToNext6(String s) {
        int len = s.length();
        int next = ((len + 5) / 6) * 6; // multiples of 6
        if (next == len) next += 6;     // if already a multiple of 6, add one trailing column so 6 spaces
        int pad = next - len;           // e.g. len = 9, next = 12, therfor pad = 3
        StringBuilder sb = new StringBuilder(next); // presizes the sb to the length we wish to produce
        sb.append(s); // append the lexeme
        for (int i = 0; i < pad; i++) sb.append(' '); // adds the padding
        return sb.toString(); // return the lexeme
    }

    @Override
    public String toString() {
        String name = TokenType.TPRINT[tokenType.getId()]; // get the token name
        // only these print a lexeme alongside them
        boolean withLex = tokenType == TokenType.TIDEN
                       || tokenType == TokenType.TILIT
                       || tokenType == TokenType.TFLIT
                       || tokenType == TokenType.TSTRG;
        if (!withLex) return name; // just print the name if it isnt a part of the lexeme needing tokens         
        String lx = (lexeme == null) ? "" : lexeme; // if lexeme is null we just print nothing  
        if (tokenType == TokenType.TSTRG) lx = "\"" + lx + "\""; // if the lexeme is a string we wrap it with quotes
        return name + padToNext6(lx); // return the token name and the padded lexeme
    }
}


