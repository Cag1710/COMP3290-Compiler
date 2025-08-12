import java.io.*;
import java.util.*;

public final class Lexer {

    private final PushbackReader in; // java class that reads characters one at a time from another reader
    private int line = 1; // starting on line 1
    private int col = 0; // starting at column 0
    private int state;

    // state constants for the state machine
    private final int READY = 0;
    private final int WORD = 1;
    private final int DIGIT = 2;
    private final int OP = 3;
    private final int STRING = 4;
    private final int COMMENT = 5;

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
        Map.entry("=", TokenType.TEQUL),
        Map.entry("+", TokenType.TPLUS),
        Map.entry("-", TokenType.TMINS),
        Map.entry("*", TokenType.TSTAR),
        Map.entry("/", TokenType.TDIVD),
        Map.entry("<", TokenType.TLESS),
        Map.entry(">", TokenType.TGRTR)
    );

    // always single ops
    private static final Map<String, TokenType> SINGLE_OPS = Map.ofEntries(
        Map.entry(",", TokenType.TCOMA),
        Map.entry("[", TokenType.TLBRK),
        Map.entry("]", TokenType.TRBRK),
        Map.entry("(", TokenType.TLPAR),
        Map.entry(")", TokenType.TRPAR),
        Map.entry("%", TokenType.TPERC),
        Map.entry("^", TokenType.TCART),
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
        this.state = READY;                                          // specifically, wraps the incoming reader in a bufferedreader for efficient reading, then wraps that in a pushbackreader with a buffer size of 2
                                                                     // allows us to push back tokens onto the buffer to be read next 
                                                                     // e.g. /==, read /, read =, read =. but /== isnt a token, push that last token onto the buffer as it probably starts the next token and process just /=
    }

    // for skipping whitespace as defined by the assignment spec
    private static boolean isSpace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    // to identify the start of keywords and identifiers
    private static boolean isLetter(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    // to identify integers or real literals
    private static boolean isDigit(int c) {
        return (c >= '0' && c <= '9');
    }

    // as identifiers can contain letters and digits this is needed 
    private static boolean isLetterOrDigit(int c) {
        return isLetter(c) || isDigit(c);
    }

    public Token nextToken() throws IOException {    
        List<String> buff = new ArrayList<>();     // can use this to build the token (Eg: reading CD25. buff = ["C", "D", "2", "5"])
        // loop until token is found or error

        /*
        * IF space
        *      skip / delimit
        * IF single token
        *      return token
        * IF character [a-z]
        *      set state = WORD
        *      ... determine keyword, iden etc.
        * IF digit [0-9]
        *      set state = DIGIT
        *      ... determine integer, real
        * IF operator
        *      set state = OP
        *      ... determine operator
        * IF quote (")
        *      set state = STRING
        *      ... determine content
        */

        // just an example read and store in buff
        int c = read();
        buff.add(Character.toString(c));

        return null;
    }

    private int read() throws IOException {
        return in.read();
    }

    private void unread() throws IOException {

    }
} 
