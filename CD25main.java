import java.io.*;
import java.util.*;

public class CD25main {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("No file inserted");
            return;
        }

        String filename = args[0];

        try(BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNumber = 1;

            while((line = reader.readLine()) != null) {
                System.out.println("Line " + lineNumber + ": " + line);
                lineNumber++;
            }
        } catch(IOException e) {
            System.err.println("Error reading file: " + filename);
        }
    }
}
