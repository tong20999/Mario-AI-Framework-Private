import engine.core.MarioGame;
import engine.core.MarioLevelGenerator;
import engine.core.MarioLevelModel;
import engine.core.MarioResult;
import engine.core.MarioTimer;
import reinforcement.PCGLevelDto;
import reinforcement.ProceduralContentGenerationLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Optional;

public class GenerateLevel {

    public static void main(String[] args) throws SQLException, IOException {
        String dbPath = "C:/thesis_data/pcg_level.db";
        int numberOfLevelsToGenerate = 100; // Define the target number of levels

        // 1. Load the JDBC Driver (optional for modern Java, but good practice)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // 2. Ensure the table exists
            createTable(conn);

            // --- Database Insertion Setup ---
            // Prepare the SQL statement outside the loop for efficiency
            String sqlResults = "INSERT INTO levels (pcg_content) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlResults)) {

                // --- Level Generation and Insertion Loop ---
                for (int i = 1; i <= numberOfLevelsToGenerate; i++) {
                    System.out.println("Generating and saving level " + i + "...");

                    // Create a new level configuration for each iteration
                    // Note: If you want all 100 levels to be different,
                    // the parameters in PCGLevelDto constructor should be randomized.
                    ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel
                            .parseLevel(new PCGLevelDto(50, 51, 50,51, 10,5,11,
                                    true,true,false, 10 ,5 ,11 ,10
                                    , 5 ,14 , 2, 2 ,5 ,4 ,2, 5, 2
                                    ,100, null, false, false, 0));

                    // Generate the level content
                    pcgLevel.generate();

                    // Get the generated content and insert into the database
                    String levelContent = pcgLevel.getContent();

                    // Set the parameter and execute the insert
                    pstmt.setString(1, levelContent);
                    pstmt.executeUpdate();
                }

            }
            System.out.println("\nSuccessfully generated and saved " + numberOfLevelsToGenerate + " levels.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    private static void createTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS levels (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pcg_content TEXT" +
                ");";

        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
