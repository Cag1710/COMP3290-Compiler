import java.io.*;
import java.util.*;

public final class Lexer {

    private final PushbackReader in; // java class that reads characters one at a time from another reader
    private int line = 1; // starting on line 1
    private int col = 0; // starting at column 0

    // the big 3 of the compiler world
    // the big keywords
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("cd25", TokenType.TCD25),
        Map.entry("constants", TokenType.TCONS),
        Map.entry("types", TokenType.TTYPS),
        Map.entry("is", TokenType.TTTIS),
        Map.entry("arrays", TokenType.TARRS),
        Map.entry("main", TokenType.TMAIN),
        Map.entry("begin", TokenType.TBEGN),
        Map.entry("end", TokenType.TTEND),
        Map.entry("array", TokenType.TARAY),
        Map.entry("of", TokenType.TTTOF),
        Map.entry("func", TokenType.TFUNC),
        Map.entry("void", TokenType.TVOID),
        Map.entry("const", TokenType.TCNST),
        Map.entry("integer", TokenType.TINTG),
        Map.entry("real", TokenType.TREAL),
        Map.entry("boolean", TokenType.TBOOL),
        Map.entry("for", TokenType.TTFOR),
        Map.entry("repeat", TokenType.TREPT),
        Map.entry("until", TokenType.TUNTL),
        Map.entry("if", TokenType.TIFTH),
        Map.entry("else", TokenType.TELSE),
        Map.entry("in", TokenType.TINPT),
        Map.entry("out", TokenType.TOUTP),
        Map.entry("line", TokenType.TOUTL),
        Map.entry("return", TokenType.TRETN),
        Map.entry("not", TokenType.TNOTT),
        Map.entry("and", TokenType.TTAND),
        Map.entry("or", TokenType.TTTOR),
        Map.entry("xor", TokenType.TTXOR),
        Map.entry("true", TokenType.TTRUE),
        Map.entry("false", TokenType.TFALS)
    );
    // the big singles
    private static final Map<String, TokenType> ONE_CHAR_OPS = Map.ofEntries(
        Map.entry(",", TokenType.TCOMA),
        Map.entry("[", TokenType.TLBRK),
        Map.entry("]", TokenType.TRBRK),
        Map.entry("(", TokenType.TLPAR),
        Map.entry(")", TokenType.TRPAR),
        Map.entry("=", TokenType.TEQUL),
        Map.entry("+", TokenType.TPLUS),
        Map.entry("-", TokenType.TMINS),
        Map.entry("*", TokenType.TSTAR),
        Map.entry("/", TokenType.TDIVD),
        Map.entry("%", TokenType.TPERC),
        Map.entry("^", TokenType.TCART),
        Map.entry("<", TokenType.TLESS),
        Map.entry(">", TokenType.TGRTR),
        Map.entry(":", TokenType.TCOLN),
        Map.entry(";", TokenType.TSEMI),
        Map.entry(".", TokenType.TDOTT)
    );
    // the big doubles
    private static final Map<String, TokenType> TWO_CHAR_OPS = Map.ofEntries(
        Map.entry(">>", TokenType.TGRGR),
        Map.entry("<<", TokenType.TLSLS),
        Map.entry("<=", TokenType.TLEQL),
        Map.entry(">=", TokenType.TGEQL),
        Map.entry("!=", TokenType.TNEQL),
        Map.entry("==", TokenType.TEQEQ),
        Map.entry("+=", TokenType.TPLEQ),
        Map.entry("-=", TokenType.TMNEQ),
        Map.entry("*=", TokenType.TSTEQ),
        Map.entry("/=", TokenType.TDVEQ)
    );

    public Lexer(Reader r) {
        this.in = new PushbackReader(new BufferedReader(r), 2); // wrapping wraps in wraps
                                                                     // specifically, wraps the incoming reader in a bufferedreader for efficient reading, then wraps that in a pushbackreader with a buffer size of 2
                                                                     // allows us to push back tokens onto the buffer to be read next 
                                                                     // e.g. /==, read /, read =, read =. but /== isnt a token, push that last token onto the buffer as it probably starts the next token and process just /=
    }

    public Token nextToken() throws IOException {
        return null;
    }

    private int read() throws IOException {
        int i = 1;
        return i;
    }

    private void unread() throws IOException {

    }
} 