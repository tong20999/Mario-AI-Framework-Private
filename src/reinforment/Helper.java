package reinforment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import engine.core.MarioEvent;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import info.EndInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

public class Helper {

    public static Optional<File> getWorkingDir(boolean returnSecondLatest) throws IOException {
        File directory = new File("C:/thesis_data/training");

        // Crucial check: Ensure the path is a valid directory.
        if (!directory.isDirectory()) {
            return Optional.empty();
        }

        File[] folders = directory.listFiles(File::isDirectory);

        // Crucial check: Ensure the array is not null or empty.
        if (folders == null || folders.length == 0) {
            return Optional.empty();
        }

        // Sort the folders numerically, handling non-numeric names.
        Arrays.sort(folders, Comparator.comparingInt((File f) -> {
            try {
                return Integer.parseInt(f.getName());
            } catch (NumberFormatException e) {
                // Use a negative value to place non-numeric folders at the end.
                return -1;
            }
        }).reversed());

        // If we need to return the second-to-last folder
        if (returnSecondLatest) {
            if (folders.length >= 2) {
                return Optional.of(folders[1]);  // Return the second-to-last folder.
            }
        }

        // Otherwise, return the latest folder
        return Optional.of(folders[0]);  // Return the most recent folder (first in sorted array)
    }

    public static String getFileFromLevel(String file){
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }

    public static void logEvaluationResult(int evaluationEpisode, String gameStatus, ProceduralContentGenerationLevel pcg, MarioWorld world, ArrayList<RewardEvent> rewardEvents, float evaluationReward, int minTimer, int maxTimer) throws IOException {
        Optional<File> optional = getWorkingDir(false);
        var workingDir = optional.get().getAbsolutePath();

        boolean blockClear = world.getHitBlockCount() == world.level.getBumpableBlocks().size();
        boolean killClear = world.getKillCount() == world.level.getEnemies().size();
        boolean coinClear = world.getCollectedCoinCount() == world.level.getCoins().size();
        boolean completeObjective = blockClear && killClear && coinClear;

        String status = gameStatus;
        if(!completeObjective && gameStatus.equals(GameStatus.WIN.toString())){
            status = "PARTIAL_WIN";
        }

        String resultPath = MessageFormat.format(workingDir + "\\results\\{0}\\result_{1}_{2}.json", status, status,
                evaluationEpisode);

        createDirectory(resultPath);

        Map<String, Object> data = new HashMap<>();
        data.put("mode", world.mario.isLarge ? 1 : world.mario.isFire ? 2 : 0);
        data.put("blocks", world.getUnbumpBlocks());
        data.put("coins", world.getUnCollectCoin());
        data.put("enemies", world.getAliveEnemies().stream().map(e -> new Point((int)e.x, (int)e.y)).toList());
        data.put("events", rewardEvents);
        data.put("totalBlock", world.level.getBumpableBlocks().size());
        data.put("totalCoin", world.level.getCoins().size());
        data.put("totalEnemies", world.level.getEnemies().size());
        data.put("blockClear", blockClear);
        data.put("killClear", killClear);
        data.put("coinClear", coinClear);
        data.put("gameStatus", status);
        data.put("pcg",pcg.getContent());
        data.put("timer", world.initTimer);
        data.put("min_timer", minTimer);
        data.put("max_timer", maxTimer);
        data.put("totalReward", evaluationReward);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultPath, false))) {
            gson.toJson(data,writer);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        String path = completeObjective ? MessageFormat.format(workingDir + "\\results\\OBJECTIVES_CLEAR\\result_OBJECTIVES_CLEAR_{0}.json", evaluationEpisode)
                : MessageFormat.format(workingDir + "\\results\\OBJECTIVES_FAILED\\result_OBJECTIVES_FAILED_{0}.json", evaluationEpisode);

        createDirectory(path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, false))) {
            gson.toJson(data,writer);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    private static void createDirectory(String path) throws IOException {
        File file = new File(path);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs(); // mkdirs() creates all necessary but nonexistent parent
            // directories.
            if (dirCreated) {
                System.out.println("Created directory: " + parentDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
                throw new IOException("Failed to create directory");
            }
        }
    }
}
