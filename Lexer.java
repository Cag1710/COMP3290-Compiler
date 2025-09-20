import java.io.*;
import java.util.*;

public final class Lexer {

    private static final class Pos { final int line, col; Pos(int l, int c){ this.line=l; this.col=c; } } // to keep a more accurate position of token lines and cols
    private final Deque<Pos> posStack = new ArrayDeque<>(8); // used to pop the token on the stack so we can go back even if we cross new lines
    private final PushbackReader in; // java class that reads characters one at a time from another reader
    private int line = 1; // starting on line 1
    private int col = 0; // starting at column 0
    private int tokLine = 1; // keeps track of a specific tokens line number
    private int tokCol = 0; // keeps track of a specific tokens col number
    private int state; // the state we are in
    private List<String> buff; 
    private OutputController oc;

    // state constants for the state machine
    private final int READY = 0;
    private final int WORD = 1;
    private final int DIGIT = 2;
    private final int REAL = 3;
    private final int OP = 4;
    private final int STRING = 5;

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
        Map.entry(">", TokenType.TGRTR),
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

    public Lexer(Reader r, OutputController oc) throws IOException {
        this.in = new PushbackReader(new BufferedReader(r), 2); // wraps the incoming reader in a bufferedreader for efficient reading, then wraps that in a pushbackreader with a buffer size of 2
        this.oc = oc;                          // allows us to push back tokens onto the buffer to be read next 
                                                                // e.g. /==, read /, read =, read =. but /== isnt a token, push that last token onto the buffer as it probably starts the next token and process just /=
        this.state = READY; // begin in the ready state                                 
    }

    // for marking a tokens start line and col number
    private void markTokenStart() {
        tokLine = line;
        tokCol = col;
    }

    // for skipping whitespace
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

    // to indentify if its an operator char
    private static boolean isOperatorChar(int c) {
        return "=+-*/<>[],()%^:;.!".indexOf(c) >= 0;
    }

    // to figure out when we need to stop reading an undefined char, i.e read until we find a valid char
    private static boolean isStopForUndef(int c) {
        return c == -1 || isSpace(c) || isLetterOrDigit(c) || isOperatorChar(c) || c == '"';
    }

    // to read the next token
    public Token nextToken() throws IOException {    
        state = READY;
        buff = new ArrayList<>();     // buffer to build the lexeme (Eg: reading CD25. buff = ["C", "D", "2", "5"])
        Token token = null;
        int c = 0;
        
        // loop until token is found, error or EOF (read returns -1)
        while (token == null)  {
            if (state == READY) {
                c = runCommentState();         // skips WS/comments and returns first real char
                if (c == -1) break;            // EOF
                markTokenStart();              // mark a tokens start col and line number
                token = runReadyState(c);      // then run the ready state
            } else {
                c = read();
                switch (state) {
                    case DIGIT:
                        token = runDigitState(c);
                        break;
                    case REAL:
                        token = runRealState(c);
                        break;
                    case WORD:
                        token = runWordState(c);
                        break;
                    case OP:
                        token = runOpState(c);
                        break;
                    case STRING:
                        token = runStringState(c);
                        break;
                }
            }
            if (c == -1) { break; }     // EOF reached
        }
        //if (c == -1) { oc.commitBuffer(); } // need this check here again after the loop it was buggin when I had it above
        return token != null ? token : new Token(TokenType.T_EOF, "", tokLine, tokCol);
    }

    /* Runs the logic for the READY state */
    private Token runReadyState(int c) throws IOException {
        if (isDigit(c)) {
            state = DIGIT;
            buff.add(Character.toString(c));
            return null;
        }
        if (isLetter(c)) {
            state = WORD;
            buff.add(Character.toString(c));
            return null;
        }
        if (ONE_CHAR_OPS.containsKey(Character.toString(c)) || c == '!') {
            state = OP;
            buff.add(Character.toString(c));
            return null;
        }
        if (c == '\"') {
            state = STRING;     // we do not include the "" in the string itself
            return null;
        }

        buff.add(Character.toString(c)); // the beginning undefined char
        while (true) {
            int x = read();          // we continue to read       
            if (isStopForUndef(x)) { // until we find a char that is valid
                unread(x);                  
                break;
            }
            buff.add(Character.toString(x)); // otherwise continue adding undefined chars
        }
        oc.addError(new CompilerError("Lexical", "Unknown character, lexeme undefined.", tokLine, tokCol));
        return new Token(TokenType.TUNDF, String.join("", buff), tokLine, tokCol);
    }

    /* Runs the logic for the DIGIT state. */
    private Token runDigitState(int c) throws IOException {
        Token token = null;

        if (isDigit(c)) {
            buff.add(Character.toString(c));
        }
        else if (c == 46) {     // check if c is a period
            int nc = read();
            if (isDigit(nc)) {  
                state = REAL;
                buff.add(Character.toString(c));
                buff.add(Character.toString(nc));
            }
            else {              // next char is not of real format, we found the end of the int token
                unread(nc);
                unread(c);
                token = new Token(TokenType.TILIT, String.join("", buff), tokLine, tokCol);
            }
        }
        else {                  // delimiter found, unread and return integer token
            unread(c);
            token = new Token(TokenType.TILIT, String.join("", buff), tokLine, tokCol);
        }
        return token;
    }

    /* Runs the logic for the REAL state */
    private Token runRealState(int c) throws IOException {
        Token token = null;
        if (isDigit(c)) {
            buff.add(Character.toString(c));
        }
        else {
            unread(c);
            token = new Token(TokenType.TFLIT, String.join("", buff), tokLine, tokCol);
        }
        return token;  
    }

    /* Runs the logic for the WORD state */
    private Token runWordState(int c) throws IOException {
        Token token = null;
        if (isLetterOrDigit(c)) {
            buff.add(Character.toString(c));
        }
        // read char is classed as a delimiter therefore check keywords for lexeme
        else if (KEYWORDS.containsKey(String.join("", buff).toLowerCase())) {
            unread(c);
            TokenType tt = KEYWORDS.get(String.join("", buff).toLowerCase());
            token = new Token(tt, String.join("", buff), tokLine, tokCol);
        }
        // lexeme not found in keyword map therefore token must be an identifier
        else {
            unread(c);
            token = new Token(TokenType.TIDEN, String.join("", buff), tokLine, tokCol);
        }
        return token;
    }

    /* Runs the logic for the OP state */
    private Token runOpState(int c) throws IOException {
        Token token = null;
        char oc = buff.get(0).charAt(0); // get the char in the buffer
        
        if (oc == '!') { // ! by itself is undefined but != is defined
            if (c == '=') { // we check if it has a = that follows it
                return new Token(TokenType.TNEQL, "!=" , tokLine, tokCol); // if it does then its just TNEQL
            } else {
                unread(c);
                this.oc.addError(new CompilerError("Lexical", "Unknown character, lexeme undefined.", tokLine, tokCol));
                return new Token(TokenType.TUNDF, "!" , tokLine, tokCol); // but if it doesnt its undefined
            }
        }
        
        String two = "" + oc + (char)c; // add the new char and the old char together e.g. buff : <, c : =, together <=
        TokenType tt2 = TWO_CHAR_OPS.get(two); // try to get it in the map
        if (tt2 != null) { // if it is in the map
            return token = new Token(tt2, two, tokLine, tokCol); // add the token
        }
        unread(c); // if it aint in the map then unread c to read again afterwards

        String one = String.valueOf(oc); // turn oc back into a string 
        TokenType tt1 = ONE_CHAR_OPS.get(one); // get the lexeme
        token = new Token(tt1, one, tokLine, tokCol); // return the token
        return token;
    }

    /* Runs the logic for the STRING state */
    private Token runStringState(int c) throws IOException {
        Token token = null;
        
        if (c == -1) { // just in case for some reason an editor doesnt end with a new line, niche but it rounds it out
            token = new Token(TokenType.TUNDF, String.join("", buff), tokLine, tokCol);
            oc.addError(new CompilerError("Lexical", "Strings cannot terminate with EOF.", tokLine, tokCol));   // adds a lexical error to be reported in the listing file
        }
        else if (c == '\n') {                        // string cannot terminate with a newline, error
            token = new Token(TokenType.TUNDF, String.join("", buff), tokLine, tokCol);
            oc.addError(new CompilerError("Lexical", "Strings cannot terminate with newline.", tokLine, tokCol));
        }
        else if (c == '\"') {
            token = new Token(TokenType.TSTRG, String.join("", buff), tokLine, tokCol);
        }
        else {
            buff.add(Character.toString(c));    // add any character to the string
        }
        return token;
    }

    /**
     * Gets the next character from the pushback reader,
     * Tracks the line and column count
     * @return the next char
     * @throws IOException
     */
    private int read() throws IOException {
        int c = in.read();

        posStack.push(new Pos(line, col)); // when we read, we push the last position on to the stack 
                                           // so if we need to backtrack across lines its actually accurate
        if (c == '\n') {
            line++;
            col = 0;
        } else if (c != -1) {
            col++;
        }
        oc.addToBuffer(c);                  // add char to the listing file buffer
        return c;
    }

    /**
     * Puts the c char back into the stream so that the next call of read will read the same char again
     * Steps everything back to the previously documented position
     * @param c
     * @throws IOException
     */
    private void unread(int c) throws IOException {
        if (c == -1) {
            return;
        }
        in.unread(c); 

        if (!posStack.isEmpty()) { // as long as the stack aint empty
            Pos p = posStack.pop(); // we pop
            this.line = p.line; // and return the line and col numbers to the last position
            this.col  = p.col;
        }
        oc.removeLastFromBuffer();  // removes last added element from the listing file buffer
    }

    /**
     * Handles the white space/comments between tokens
     * @return the next valid char
     * @throws IOException
     */
    private int runCommentState() throws IOException {
        while(true) {
            int c = read();
            if(c == -1) return -1; // EOF
            if(isSpace(c)) continue; // skip whitespace 
            
            // reminder that comments in CD25 are /--
            if (c == '/') { // if we hit a '/', it could be a comment
                int n1 = read(); // read the next char after /, so we can begin checking
                if (n1 == '-') { // if it is a -, we read the next char
                    int n2 = read();
                    if (n2 == '-') { // if we got another -, then we have our guy, sick em fellas
                        while(true) { // consume it all, until a newline or EOF
                            int x = read();
                            if (x == -1 || x == '\n' || x == '\r') {
                                break;
                            }
                        }
                        continue; 
                    } else { // if the next char aint -, then just unread the two chars 
                        unread(n2); 
                        unread(n1);
                        return c;
                    }
                // reminder that multi line strings start with /** and end with **/
                } else if (n1 == '*') { // handling multi line strings
                    int n2 = read();
                    if (n2 == '*') {
                        int stars = 2; // we have just read 2 *'s 
                        while(true) {
                            int x = read(); // read the next char
                            if (x == -1) return -1; // reached EOF, comment was never closed

                            if (x == '*') { // if we encounter another star in the comment, increment the count
                                stars++; 
                                continue; 
                            }
                            if (x == '/') { // if we read a /
                                if(stars >= 2) { // we check if we have read >= 2 stars previously (>= and not == since technically ****/ is completely legal, so as long as its more than 2 followed by a / its fine)
                                    break; // found the closing comment
                                } else { // otherwise this is just a random / in the comment, reset stars back to 0
                                    stars = 0;
                                    continue;
                                }
                            } 
                            stars = 0; // any other char breaks star count
                        }
                        continue;
                    } else {
                        unread(n2);
                        unread(n1);
                        return c;
                    }
                } else { // if the next char isn't - or *, then just unread the char we just read
                    unread(n1);
                    return c;
                }
            }
            return c;
        }
    }
} 
