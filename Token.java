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

    @Override
    public String toString() {
        if (lexeme != null) {
            return String.format("%s%s", TokenType.TPRINT[tokenType.getId()], lexeme);
        } else {
            return TokenType.TPRINT[tokenType.getId()];
        }
    }
}


