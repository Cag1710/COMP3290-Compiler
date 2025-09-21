import java.io.*;
import java.util.*;

public class OutputController {
    private String file;    
    private FileWriter fw;
    private int line;
    private List<Integer> buff;
    private List<CompilerError> errors;

    public OutputController(String filename) {
        file = createFileName(filename);
        fw = initFileWriter();
        buff = new ArrayList<>();
        errors = new ArrayList<>();
        line = 0;
    }

    /* Adds a char to the lsiting file buffer, helps keep this buffer in sync with the chars
     * read/ unread while lexing to save needing multiple file reads.
     */
    public void addToBuffer(int c) {
        buff.add(c);
    } 

    /* Removes latest char in the lsiting file buffer, helps keep this buffer in sync with the chars    
     * read/ unread while lexing to save needing multiple file reads.
     */
    public void removeLastFromBuffer() {
        buff.remove(buff.size() - 1);
    }

    /* Reports an error to be outputted to the listing file. */
    public void addError(CompilerError e) {
        errors.add(e);
    }

    /* Commits and writes the listing file buffer to the output file. This is called when the EOF is reached in the lexer. */
    public void commitBuffer() throws IOException {
        addLineNum();
        for (int c : buff) { outputChar(c); }
        if (errors.size() > 0) { outputErrors(); }
    }

    /* Outputs one char at a time to the output file. */
    private void outputChar(int c) throws IOException {
        if (c == '\n') {    // if char is newline, append and then increment line num
            fw.append((char) c);
            fw.flush();
            addLineNum(); 
            return;
        }
        if (c == -1) { 
            fw.append("\n");
            fw.flush();
            return;
        }
        fw.append((char) c);
        fw.flush();
    }

    /* Outputs the errors to the listing file (if any) */
    private void outputErrors() throws IOException {
        fw.append("\n\tERRORS:\n-----------------------------------");
        for (CompilerError e : errors) {
            fw.append(e.toString());
            fw.flush();
        }
    }

    /* Handles the printing and incrementing of line numbers. */
    private void addLineNum() throws IOException {
        line++;
        fw.append(String.valueOf(line) + "  ");
        fw.flush();
    }

    /* Creates a new file writer object with correct filename: 'inputfilename.lst' */
    private FileWriter initFileWriter() {
        try {
            FileWriter writer = new FileWriter(file, false);
            return writer;
        }
        catch (IOException e) {
            System.out.println("There was an error writing to the output file. " + e);
            return null;
        }
    }

    /* Creates the filename based on the input source file name */
    private String createFileName(String filename) {
        File f = new File(filename);
        String name = f.getName();
        String[] arr = name.split("\\.");
        return arr[0] + ".lst";
    }
}