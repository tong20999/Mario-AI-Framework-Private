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

    private static void writeRunLog(Logger logger,
                                     MarioWorld world,
                                     ArrayList<MarioEvent> gameEvents,
                                     EndInfo info,
                                     int count,
                                     int timer,
                                     float reward){
        long fallKill = gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.FALL_KILL.getValue())
                .count();

        long shellKill = gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.SHELL_KILL.getValue())
                .count();

        long stompKill = gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.STOMP_KILL.getValue())
                .count();

        long fireKill = gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.FIRE_KILL.getValue())
                .count();

        // hurt may not dead
        int hurt = (int) gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.HURT.getValue())
                .count();

        int fallPit = (int) gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.FALL_PIT.getValue())
                .count();

        int collect = (int) gameEvents.stream()
                .filter(e -> e.getEventType() == EventType.COLLECT.getValue())
                .count();

        int lose = world.gameStatus == GameStatus.LOSE ? 1 : 0;

        int timeout = world.gameStatus == GameStatus.TIME_OUT ? 1 : 0;

        int win = gameEvents.stream()
                .anyMatch(e -> e.getEventType() == EventType.WIN.getValue()) ? 1 : 0;

        info.win += win;
        info.lose += lose;
        info.hurt += hurt;
        info.fallPit += fallPit;
        info.fallKill += fallKill;
        info.stompKill += stompKill;
        info.shellKill += shellKill;
        info.fireKill += fireKill;
        info.collect += collect;
        info.timeout += timeout;
        var episodeMsg = MessageFormat.format("Episode {0} {1} time {2} reward {3}\r\n", count, info, timer/1000, String.format("%.2f", reward));
        logger.appendLog(episodeMsg);
    }

    public static void writeEpisodeLog(
            Logger logger,
            MarioWorld world,
            ArrayList<MarioEvent> gameEvents,
            EndInfo episodeInfo,
            int episode,
            int episodeTimer,
            float episodeReward){
        writeRunLog(logger, world, gameEvents, episodeInfo, episode, episodeTimer, episodeReward);
    }

    public static void writeEvaluationLog(
            Logger logger,
            MarioWorld world,
            ArrayList<MarioEvent> gameEvents,
            EndInfo evaluationInfo,
            int evaluationCount,
            int evaluationTimer,
            float evaluationReward) {
        writeRunLog(logger, world, gameEvents, evaluationInfo, evaluationCount, evaluationTimer, evaluationReward);
    }

    public static Map<String, String> parseParameter(String value){
        Map<String, String> params = new HashMap<>();

        // 1. Split the string into key-value pairs.
        String[] pairs = value.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        return params;
    }

    public static String getWorkingDir(String pcgName) throws IOException {
        File folder = new File(MessageFormat.format("C:\\thesis_data\\{0}",pcgName));

        // List subfolders and parse numeric names
        int latestNumber = Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                .filter(File::isDirectory)
                .map(File::getName)
                .filter(name -> name.matches("\\d+")) // Keep only numeric folder names
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(-1); // Use -1 if no numeric folders

        if (latestNumber != -1) {
            return MessageFormat.format("C:\\thesis_data\\{0}\\{1}",pcgName, latestNumber);
        } else {
            throw new IOException("Folder not found");
        }
    }

    public static void writeToFile(String path, String value){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, false))) {
            writer.write(value);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
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

    public static String getLevel(String level){
        var levelLocation = MessageFormat.format("./levels/{0}", level);
        return getFileFromLevel(levelLocation);
    }



    public static void logEvaluationResult(int evaluationEpisode, String gameStatus, ProceduralContentGenerationLevel pcg, MarioWorld world, ArrayList<RewardEvent> rewardEvents, float evaluationReward, int minTimer, int maxTimer) throws IOException {
        String workingDir;
        try {
            workingDir = getWorkingDir(pcg.getPcgName());
        } catch (IOException e) {
            System.out.println("WARN: can't get working dir");
            return;
        }

        boolean blockClear = world.getHitBlockCount() == world.level.getBumpableBlocks().size();
        boolean killClear = world.getKillCount() == world.level.getEnemies().size();
        boolean coinClear = world.getCollectedCoinCount() == world.level.getCoins().size();
        boolean completeObjective = blockClear && killClear && coinClear;

        String status = gameStatus;
        if(!completeObjective && gameStatus.equals(GameStatus.WIN.toString())){
            status = "PARTIAL_WIN";
        }

        String pcgFilePath = MessageFormat.format(workingDir + "\\zpcg\\{0}\\pgc_{1}_{2}.txt", status, status,
                evaluationEpisode);

        String resultPath = MessageFormat.format(workingDir + "\\results\\{0}\\result_{1}_{2}.json", status, status,
                evaluationEpisode);

        createDirectory(pcgFilePath);
        createDirectory(resultPath);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pcgFilePath, false))) {
            writer.write(pcg.getContent());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

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
        data.put("pcg",pcg.getPcgDto());
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
