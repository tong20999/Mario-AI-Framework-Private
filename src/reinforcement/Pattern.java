package reinforcement;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class Pattern {
    public static final Map<Integer, String> patternMap = new HashMap<>();

    static {
        loadPatterns();
    }

    private static void loadPatterns() {
        try {
            Path folderPath = Paths.get("src/reinforcement/pattern");

            // Walk through the folder and load all .txt files
            Files.list(folderPath)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString().replace(".txt", "");
                        try {
                            int index = Integer.parseInt(fileName);
                            String content = Files.readString(path);
                            patternMap.put(index, content);
                        } catch (NumberFormatException e) {
                            System.err.println("Skipping non-numeric file: " + fileName);
                        } catch (IOException e) {
                            System.err.println("Failed to read " + path + ": " + e.getMessage());
                        }
                    });

            System.out.println("Loaded patterns: " + patternMap.keySet());
        } catch (IOException e) {
            System.err.println("Error loading patterns: " + e.getMessage());
        }
    }

    public static String getPattern(int index) {
        return patternMap.get(index);
    }
}
