import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        // Looks for config.properties in the same directory as the JAR file
        try (InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
        } catch (Exception ex) {
            System.out.println("Could not find config.properties next to JAR, or it's invalid. Using default values.");
            // ex.printStackTrace(); // Optional: uncomment to see the exact error
        }
    }

    // --- The getter methods below are exactly the same ---

    public static int getPort() {
        return Integer.parseInt(properties.getProperty("server.port", "4455"));
    }

    public static int getThreadPoolSize() {
        return Integer.parseInt(properties.getProperty("server.threadpool.size", "14"));
    }
}