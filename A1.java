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
            Lexer scanner = new Lexer(new StringReader(source));
            List<Token> tokens = new ArrayList<>();

            Token token;
            do {
                token = scanner.nextToken();
                tokens.add(token);
            } while (token.tokenType != TokenType.T_EOF);
        } catch(IOException e) {
            System.err.println("Error reading file: " + filename);
        }
    }
}
