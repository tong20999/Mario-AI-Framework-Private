package reinforcement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import engine.core.MarioEvent;
import engine.core.MarioWorld;
import engine.helper.GameStatus;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    public static void logEvaluationResultToFile(int evaluationEpisode, String gameStatus, ProceduralContentGenerationLevel pcg, MarioWorld world, ArrayList<RewardEvent> rewardEvents, float evaluationReward, int minTimer, int maxTimer) throws IOException {
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

    public static String getLatestTrainingNumber() throws IOException {
        Optional<File> optional = getWorkingDir(false);
        var workingDir = optional.get().getAbsolutePath();
        File dir = new File(workingDir);
        return dir.getName();
    }

    public static void logEvaluationResultToDataBase(int evaluationEpisode, String gameStatus, ProceduralContentGenerationLevel pcg, MarioWorld world, ArrayList<RewardEvent> rewardEvents, float evaluationReward, int minTimer, int maxTimer) throws IOException {
        String dbPath = "C:/thesis_data/training/evaluation_results.db";
        String dbEventPath = "C:/thesis_data/training/evaluation_events.db";
        Optional<File> optional = getWorkingDir(false);
        var workingDir = optional.get().getAbsolutePath();
        File dir = new File(workingDir);
        int trainingNumber = Integer.parseInt(dir.getName());
        boolean blockClear = world.getHitBlockCount() == world.level.getBumpableBlocks().size();
        boolean killClear = world.getKillCount() == world.level.getEnemies().size();
        boolean coinClear = world.getCollectedCoinCount() == world.level.getCoins().size();

        String status = gameStatus;

        try {
            // 1. Load the JDBC Driver (optional for modern Java, but good practice)
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
            return;
        }
        long resultId = -1;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // 2. Ensure the table exists (this method is shown below)
            createResultsTable(conn);



            // --- PART 1: Insert into results table and get ID ---
            String sqlResults = "INSERT INTO results (episode, game_status, final_status, " +
                    "mario_mode, total_block, total_coin, total_enemies, " +
                    "block_clear, kill_clear, coin_clear, pcg_content, " +
                    "initial_timer, min_timer, max_timer, total_reward, " +
                    "unbumped_blocks, uncollected_coins, alive_enemies_count, training_number) " + // Removed reward_events
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"; // Now 19 placeholders

            // Use Statement.RETURN_GENERATED_KEYS to get the new row's ID
            try (PreparedStatement pstmt = conn.prepareStatement(sqlResults, Statement.RETURN_GENERATED_KEYS)) {

                // Set parameters (1 to 18)
                pstmt.setInt(1, evaluationEpisode);
                pstmt.setString(2, gameStatus);
                pstmt.setString(3, status);
                pstmt.setInt(4, world.mario.isLarge ? 1 : world.mario.isFire ? 2 : 0);
                pstmt.setInt(5, world.level.getBumpableBlocks().size());
                pstmt.setInt(6, world.level.getCoins().size());
                pstmt.setInt(7, world.level.getEnemies().size());
                pstmt.setBoolean(8, blockClear);
                pstmt.setBoolean(9, killClear);
                pstmt.setBoolean(10, coinClear);
                pstmt.setString(11, pcg.getContent()); // PCG Content
                pstmt.setInt(12, world.initTimer);
                pstmt.setInt(13, minTimer);
                pstmt.setInt(14, maxTimer);
                pstmt.setFloat(15, evaluationReward);
                pstmt.setInt(16, world.getUnbumpBlocks().size());
                pstmt.setInt(17, world.getUnCollectCoin().size());
                pstmt.setInt(18, world.getAliveEnemies().size());
                pstmt.setInt(19, trainingNumber);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        resultId = rs.getLong(1);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error result: " + e.getMessage());
        }

        if (resultId != -1){
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbEventPath)) {
                    createRewardEventTable(conn);

                    String sqlEvents = "INSERT INTO reward_events (reward, event_type, " +
                            "eventParam, marioX, marioY, marioState, time, timer, training_number, result_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    try (PreparedStatement pstmtEvents = conn.prepareStatement(sqlEvents)) {
                        for (RewardEvent event : rewardEvents) {
                            MarioEvent marioEvent = event.getEvent();
                            pstmtEvents.setFloat(1, event.getReward());
                            pstmtEvents.setString(2, marioEvent.getEventTypeEnum().toString());
                            pstmtEvents.setInt(3, marioEvent.getEventParam());
                            pstmtEvents.setFloat(4, marioEvent.getMarioX());
                            pstmtEvents.setFloat(5, marioEvent.getMarioY());
                            pstmtEvents.setInt(6, marioEvent.getMarioState());
                            pstmtEvents.setInt(7, marioEvent.getTime());
                            pstmtEvents.setString(8, event.getTimer());
                            pstmtEvents.setInt(9, trainingNumber);
                            pstmtEvents.setLong(10, resultId);
                            pstmtEvents.addBatch(); // Add the insert to the batch
                        }

                        // Execute all batched inserts at once for performance
                        pstmtEvents.executeBatch();
                    }
                } catch (SQLException e) {
                    System.err.println("Database error events: " + e.getMessage());
                }
        }
    }

    public static List<String> getPcgContentForLatestTraining(int trainingNumber) throws SQLException {
        String dbPath = "C:/thesis_data/training/evaluation_results.db";
        List<String> pcgContentList = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // This query finds the maximum training_number and then selects the
            // pcg_content for all rows matching that number and the specified statuses.
            // CAST is used to ensure numeric comparison for training_number, which is safer.
            String sql = MessageFormat.format("SELECT pcg_content FROM results " +
                    "WHERE training_number = {0} " +
                    "AND final_status IN (''TIME_OUT'', ''LOSE'')", trainingNumber);

            // Using try-with-resources to ensure the Statement and ResultSet are auto-closed
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                // Loop through all the results and add them to the list
                while (rs.next()) {
                    pcgContentList.add(rs.getString("pcg_content"));
                }
            }
            return pcgContentList;
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }

        return pcgContentList;
    }

    private static void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // Helper method to create the table if it doesn't exist
    private static void createResultsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "episode INTEGER NOT NULL," +
                "game_status TEXT NOT NULL," +
                "final_status TEXT NOT NULL," +
                "mario_mode INTEGER," +
                "total_block INTEGER," +
                "total_coin INTEGER," +
                "total_enemies INTEGER," +
                "block_clear BOOLEAN," +
                "kill_clear BOOLEAN," +
                "coin_clear BOOLEAN," +
                "pcg_content TEXT," +
                "initial_timer INTEGER," +
                "min_timer INTEGER," +
                "max_timer INTEGER," +
                "total_reward REAL," +
                "unbumped_blocks INTEGER," +
                "uncollected_coins INTEGER," +
                "alive_enemies_count INTEGER," +
                "training_number INTEGER" +
                ");";

        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createRewardEventTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS reward_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "reward REAL," +
                "event_type TEXT NOT NULL," +
                "eventParam INTEGER," +
                "marioX REAL," +
                "marioY REAL," +
                "marioState INTEGER," +
                "time INTEGER," +
                "timer TEXT NOT NULL," +
                "training_number INTEGER," +
                "result_id INTEGER" +
                ");";

        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
