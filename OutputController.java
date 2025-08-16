import java.io.*;

public class OutputController {
    private String file;
    private FileWriter fw;

    public OutputController(String filename) {
        file = createFileName(filename);
        fw = initFileWriter();
    }

    public void outputToFile() {
        System.out.println("Outputting to file.");
    }

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

    private String createFileName(String filename) {
        File f = new File(filename);
        String name = f.getName();
        String[] arr = name.split("\\.");
        return arr[0] + ".lst";
    }
}