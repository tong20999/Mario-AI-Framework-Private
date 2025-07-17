package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import info.EndInfo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;

public class Helper {

    public static void printInfo(MarioWorld world, ArrayList<MarioEvent> gameEvents, boolean evaluation, EndInfo evaluationInfo, int evaluationTimer, float evaluationReward, EndInfo episodeInfo, int episode, int episodeTimer, float episodeReward) {
        if(world.gameStatus != GameStatus.RUNNING){
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

            if(evaluation){
                evaluationInfo.win += win;
                evaluationInfo.lose += lose;
                evaluationInfo.hurt += hurt;
                evaluationInfo.fallPit += fallPit;
                evaluationInfo.fallKill += fallKill;
                evaluationInfo.stompKill += stompKill;
                evaluationInfo.shellKill += shellKill;
                evaluationInfo.fireKill += fireKill;
                evaluationInfo.collect += collect;
                evaluationInfo.timeout += timeout;
                var evaluationMsg = MessageFormat.format("Evaluation {0} time {1} reward {2}", evaluationInfo, evaluationTimer/1000, String.format("%.2f", evaluationReward));
                try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\thesis_data\\evaluation_log.txt", true))) {
                    writer.write(evaluationMsg);
                    writer.newLine();
                } catch (IOException e) {
                    System.err.println("Error writing to file: " + e.getMessage());
                }
            }
            else {
                episodeInfo.win += win;
                episodeInfo.lose += lose;
                episodeInfo.hurt += hurt;
                episodeInfo.fallPit += fallPit;
                episodeInfo.fallKill += fallKill;
                episodeInfo.stompKill += stompKill;
                episodeInfo.shellKill += shellKill;
                episodeInfo.fireKill += fireKill;
                episodeInfo.collect += collect;
                episodeInfo.timeout += timeout;
                var episodeMsg = MessageFormat.format("Episode {0} {1} time {2} reward {3}", episode, episodeInfo, episodeTimer/1000, String.format("%.2f", episodeReward));
                try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\thesis_data\\episode_log.txt", true))) {
                    writer.write(episodeMsg);
                    writer.newLine();
                } catch (IOException e) {
                    System.err.println("Error writing to file: " + e.getMessage());
                }
            }
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
}
