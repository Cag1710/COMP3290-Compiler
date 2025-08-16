import java.io.*;
import java.util.*;

final class TokenPrinter {
    // removes any non-needed chars that affect printing
    // e.g. if its null just print nothing
    // we print the new line with a space to avoid the error text being on multiple lines
    private static String sanitize(String s) {
        return (s == null) ? "" : s.replace("\r", "").replace("\n", " ").replace("\t", " ");
    }

    // the actual final printer
    static void printTokens(List<Token> tokens, PrintStream out) {
        StringBuilder line = new StringBuilder(80);

        for (Token t : tokens) { 
            if (t.tokenType == TokenType.TUNDF) { // if the token is undefined
                if (line.length() > 0) { out.println(line); line.setLength(0); } // finish current line

                out.println(TokenType.TPRINT[t.tokenType.getId()]); // print TUNDF token name only
                
                out.println("      lexical error " + sanitize(t.lexeme) // print error message
                            + " (line " + t.line + ", column " + t.col + ")");
                continue;
            }

            String chunk = t.toString(); // for all other tokens, format the chunk

            // if chunk > 60, put it on its own line, dont split the chunk
            if (chunk.length() > 60) {
                if (line.length() > 0) { out.println(line); line.setLength(0); } // print the current line if there is one
                out.println(chunk); // then just print out the full incoming chunk
                continue;
            }

            // if we begin to push the current line past 60
            if (line.length() > 0 && line.length() + chunk.length() > 60) {
                out.println(line); // print the current line 
                line.setLength(0); // clear the buffer
            }

            line.append(chunk); // append the chunk to the line
        }

        if (line.length() > 0) out.println(line); // print any remaining text
    }
}