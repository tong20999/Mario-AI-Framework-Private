package reinforcement;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class Pattern {
    public static final Map<Integer, String> patternMap = new HashMap<>();
    private static Random rand = new Random();
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

    public static String getRandomPattern() {
        return patternMap.get(rand.nextInt(1, patternMap.size() + 1));
    }

    public static String getRandomPatternWithWeight() {
        // 1. Define Weights: Map of {Pattern Key: Weight}
        // Adjust these integer values to change the pattern chance.
        // Pattern 1 has 5x the chance of Pattern 2.
        Map<Integer, Integer> patternWeights = new TreeMap<>();
        for (int i = 1; i < 5; i++) {
            patternWeights.put(i, 1);
        }

        for (int i = 5; i < 15; i++) {
            patternWeights.put(i, 5);
        }

        // The sum of all weights determines the maximum random number.
        int totalWeight = patternWeights.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // 3. Select a Random Number (Range: [0, totalWeight - 1])
        int randomValue = rand.nextInt(totalWeight);

        // 4. Find the Chosen Pattern using the Cumulative Weight
        int cumulativeWeight = 0;

        // Iterate through the weights in order (TreeMap ensures this)
        for (Map.Entry<Integer, Integer> entry : patternWeights.entrySet()) {
            cumulativeWeight += entry.getValue();

            // If the random number falls within this pattern's weighted range, select it.
            if (randomValue < cumulativeWeight) {
                int patternKey = entry.getKey();

                // Return the pattern from the main map using the selected key
                return patternMap.get(patternKey);
            }
        }

        // Fallback: If for some reason the loop fails (e.g., misconfigured weights),
        // revert to equal chance selection using the original logic.
        return patternMap.get(rand.nextInt(1, patternMap.size() + 1));
    }

    public static String getRandomPattern(int origin) {
        return patternMap.get(rand.nextInt(origin, patternMap.size() + 1));
    }
}
