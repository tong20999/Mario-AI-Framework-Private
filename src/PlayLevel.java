import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;

import engine.core.MarioAgent;
import engine.core.MarioGame;

public class PlayLevel {

    public static String getLevel(String filepath) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filepath)));
        } catch (IOException e) {
            e.printStackTrace(); // It's good practice to print the stack trace for exceptions
        }
        return content;
    }

    public static void main(String[] args) throws Exception {
        MarioGame game = new MarioGame();
        var agents = new ArrayList<String>();
        agents.add("collector");
        agents.add("killer");
        agents.add("runner");
        String dbPath = "C:/thesis_data/astart_result.db";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // 2. Ensure the table exists
            createTable(conn);
            // Prepare the insert statement once before the loops
            String insertSql = "INSERT INTO astar_results (agent_name, level_number, run_count, total_blocks, total_enemies, " +
                    "total_coins, collected_coins, hit_blocks, kills, remaining_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (int i = 4; i < 11; i++) {
                    var level = getLevel("./levels/evaluation/lvl-" + i + ".txt");
                    for (var agentName : agents) {
                        MarioAgent agent = getAgent("runner");
                        for (int j = 0; j < 5; j++) {
                            System.out.println("agent " + agent.getAgentName() + " level " + i + " run " + (j + 1));
                            // Ensure to pass the correct level index 'i' to the print statement

                            var result = game.runGame(agent, level, 50, 0, true);
                            var world = result.getWorld();

                            // Metrics extraction
                            var totalBlock = world.level.getBumpableBlocks().size();
                            var totalEnemies = world.level.getEnemies().size();
                            var totalCoin = world.level.getCoins().size();
                            var collectedCoin = world.collectCoin;
                            // Assumption: hitBlock is the count of blocks that were bumped
                            var hitBlock = totalBlock - world.getUnbumpBlocks().size();
                            // Assumption: kill is the count of enemies that are NOT alive
                            var kill = totalEnemies - world.getAliveEnemies().size();
                            var remainingTime = result.getRemainingTime();
                            var levelNumber = i;
                            var runCount = j + 1;

                            // Insert result
//                            pstmt.setString(1, agentName);
//                            pstmt.setInt(2, levelNumber);
//                            pstmt.setInt(3, runCount);
//                            pstmt.setInt(4, totalBlock);
//                            pstmt.setInt(5, totalEnemies);
//                            pstmt.setInt(6, totalCoin);
//                            pstmt.setInt(7, collectedCoin);
//                            pstmt.setInt(8, hitBlock);
//                            pstmt.setInt(9, kill);
//                            pstmt.setFloat(10, remainingTime);
//
//                            pstmt.executeUpdate();
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }

    }

    private static MarioAgent getAgent(String agentName) throws IOException {
        return switch (agentName) {
            case "collector" -> new agents.collector.Agent();
            case "killer" -> new agents.killer.Agent();
            case "runner" -> new agents.runner.Agent();
            default -> throw new IOException("agent not found");
        };
    }

    private static void createTable(Connection conn) throws SQLException {
        // Complete the column definitions based on the metrics you are collecting
        String sql = "CREATE TABLE IF NOT EXISTS astar_results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "agent_name TEXT NOT NULL," +
                "level_number INTEGER NOT NULL," +
                "run_count INTEGER NOT NULL," +
                "total_blocks INTEGER," +
                "total_enemies INTEGER," +
                "total_coins INTEGER," +
                "collected_coins INTEGER," +
                "hit_blocks INTEGER," +
                "kills INTEGER," +
                "remaining_time REAL" +
                ");";

        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table 'astar_results' checked/created successfully.");
        }
    }
}