public class CompilerError {
    private int line, col;
    private String type, message;

    public CompilerError(String type, String message, int line, int col) {
        this.type = type;   // Eg: Lexical, Syntax, Semantic etc. 
        this.message = message;
        this.line = line;
        this.col = col;
    }

    @Override
    public String toString() {
        return String.format("\n%s error: @ line: %d | col: %d\n\t%s\n", type, line, col, message);
    }
}