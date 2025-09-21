public class CompilerError {
    private int line, col;
    private String type, message;
    private Token t;

    public CompilerError(String type, String message, int line, int col, Token at) {
        this.type = type;   // Eg: Lexical, Syntax, Semantic etc. 
        this.message = message;
        this.line = line;
        this.col = col;
        this.t = at;
    }

    public CompilerError(String type, String message, int line, int col) {
        this.type = type;   // Eg: Lexical, Syntax, Semantic etc. 
        this.message = message;
        this.line = line;
        this.col = col;
        this.t = null;
    }

    @Override
    public String toString() {
        // if (t == null) {
        //     return String.format("\n%s error: @ line: %d | col: %d\n\t%s\n", type, line, col, message);
        // }
        return String.format("\n%s error: @ line: %d | col: %d\n\t%s\n", type, line, col, message);
    }
}