package reinforcement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Logger {
    private final String path;
    public Logger(String path){
        this.path = path;
    }

    public void writeLog(String value){
        doWriteLog(value , false);
    }

    public void appendLog(String value){
        doWriteLog(value , true);
    }

    private void doWriteLog(String value, boolean append){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.path, append))) {
            writer.write(value);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
