import java.io.*;
import java.util.*;

public final class Lexer {

    private final PushbackReader in; // java class that reads characters one at a time from another reader
    private int line = 1; // starting on line 1
    private int col = 0; // starting at column 0

    public Lexer(Reader r) {
        this.in = new PushbackReader(new BufferedReader(r), 2); // wrapping wraps in wraps
                                                                     // specifically, wraps the incoming reader in a bufferedreader for efficient reading, then wraps that in a pushbackreader with a buffer size of 2
                                                                     // allows us to push back tokens onto the buffer to be read next 
                                                                     // e.g. /==, read /, read =, read =. but /== isnt a token, push that last token onto the buffer as it probably starts the next token
    }

    public Token nextToken() throws IOException {
        return null;
    }
} 