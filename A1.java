/* Main entry point for group 3 A1 Part B */

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class A1 {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("No file inserted");
            return;
        }

        String filename = args[0];

        try {
            String source = Files.readString(Path.of(filename));
            OutputController oc = new OutputController(filename);
            Lexer scanner = new Lexer(new StringReader(source), oc);
            List<Token> tokens = new ArrayList<>();

            Token token;
            do {
                token = scanner.nextToken();
                tokens.add(token);
            } while (token.tokenType != TokenType.T_EOF);

            TokenPrinter.printTokens(tokens, System.out);

            TokenStream ts = new TokenStream(tokens);
            ErrorReporter er = new ErrorReporter(oc);
            Parser parser = new Parser(ts, er);
            StNode root = parser.parseProgram();

            // Assumedly we would probs have something to print here as well, maybe a seperate class called TreePrinter or smthn to print the tree

        } catch(IOException e) {
            System.err.println("Error reading file: " + filename);
        }
    }
}
