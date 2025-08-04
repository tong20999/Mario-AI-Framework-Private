package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioLevel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;

public class RewardSystem {

    static float winReward = 1;
    // static float winRewardMultiplier = 5;
    // static float mileStoneReward = 0.0f;
    // static float jumpOverPitReward = 0f;
    // static float killReward = 0.7f;
    // static float bumpReward = 0.5f;
    // static float coinReward = 0.5f;
    // static float fireworkReward = 0.5f;
    // static float mushroomReward = 0.5f;
    // static float lifeMushroomReward = 0.5f;
    static float losePenalty = 0.0f;
    static float timeoutPenalty = 0.0f;
    // static float hurtReward = -0.0f;
    // static float hitWallReward = -0.0f;
    // static float fallPitReward = -0f;
    // static float timePenaltyRewardCoefficient = 0;
    static float jumpSpamPenalty = -0.00f;
    private final int totalBumpBlock;
    private final int totalCoins;
    private final int totalEnemies;
    float killReward;
    float bumpReward;
    float coinReward;
    float powerUpReward;
    float explorerReward = 0.005f;

    ProceduralContentGenerationLevel pcg;
    private String workingDir;

    public RewardSystem(MarioLevel level, ProceduralContentGenerationLevel pcg, String workingDir) {
        this.pcg = pcg;
        this.workingDir = workingDir;
        this.totalBumpBlock = level.getBumpableBlocks().size();
        this.totalCoins = level.getCoins().size();
        this.totalEnemies = level.getEnemies().size();

        killReward = 0.2f;
        bumpReward = 0.2f;
        coinReward = 0.2f;
        powerUpReward = 0.2f;
    }

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents, boolean[] actions,
            boolean isEvaluation,
            int evaluationEpisode) {
        float reward = 0.0f;

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += killReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += powerUpReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += powerUpReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += powerUpReward;
            }

            // if (e.getEventType() == EventType.HURT.getValue()) {
            // if(world.mario.isFire || world.mario.isLarge){
            // reward += hurtReward;
            // }
            // }
            // if (e.getEventType() == EventType.HIT_WALL.getValue()) {
            // reward += hitWallReward;
            // }
            // if (e.getEventType() == EventType.FALL_PIT.getValue()) {
            // reward += fallPitReward;
            // }

            if (e.getEventType() == EventType.BUMP.getValue()) {
                reward += bumpReward;
            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += coinReward;
            }

            if (e.getEventType() == EventType.EXPLORER.getValue()) {
                reward += explorerReward;
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the
            // run itself.
            // float total = (world.kill) + (world.bumpBlock) + (world.collectCoin);
            // reward += total * winRewardMultiplier;
            if (world.isSubGoalBlockMet() && world.isSubGoalCoinMet() && world.isSubGoalEnemyMet()) {
                reward += winReward;
            } else {
                reward += 0;
            }
            if (isEvaluation && this.pcg != null) {
                logPcg(evaluationEpisode, world.gameStatus.name());
                // System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            // reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the
            // "laziest" win.
            reward += timeoutPenalty;
            if (isEvaluation && this.pcg != null) {
                logPcg(evaluationEpisode, world.gameStatus.name());
                // System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            // reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.LOSE) {
            // A massive penalty that ensures any failure is always worse than even the
            // "laziest" win.
            reward += losePenalty;
            if (isEvaluation && this.pcg != null) {
                logPcg(evaluationEpisode, world.gameStatus.name());
                // System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            // reward += distanceToFlag(world.mario);
        }

        return reward;
    }

    private void logPcg(int evaluationEpisode, String name) {
        if (this.workingDir == null) {
            return;
        }
        // First, let's construct the full file path.
        String filePath = MessageFormat.format(this.workingDir + "\\zpcg\\{0}\\pgc_{1}_{2}.txt", name, name,
                evaluationEpisode);

        // Now, let's get the parent directory path from the file path.
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        // Check if the parent directory exists. If not, create it.
        if (parentDir != null && !parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs(); // mkdirs() creates all necessary but nonexistent parent
                                                     // directories.
            if (dirCreated) {
                System.out.println("Created directory: " + parentDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
                // You might want to throw an exception here or return to prevent
                // the file writing from failing later.
                return;
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
            writer.write(pcg.getContent());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public String getRewardInfo() {
        return MessageFormat.format("""
                REWARDS
                WIN {0}
                LOSE {1}
                TIME_OUT {2}
                KILL {3}
                COLLECT_COIN {4}
                HIT_BLOCK {5}
                COLLECT_MUSHROOM {6}
                """,
                winReward,
                losePenalty,
                timeoutPenalty,
                killReward,
                coinReward,
                bumpReward,
                powerUpReward);
    }

    public String getLevelInfo() {
        return MessageFormat.format("""
                LEVEL
                ENEMY {0}
                COIN {1}
                BLOCK {2}
                POWER {3}
                """,
                totalEnemies,
                totalCoins,
                totalBumpBlock);

        // logger.writeLog(rewardInformation);
        // logger.appendLog("\r\n");
        // logger.appendLog(levelInformation);
        //
        // try (BufferedWriter writer = new BufferedWriter(new
        // FileWriter("C:\\thesis_data\\reward.txt", false))) {
        // writer.write(rewardInformation);
        // writer.newLine();
        // writer.write(levelInformation);
        // } catch (IOException e) {
        // System.err.println("Error writing to file: " + e.getMessage());
        // }
    }
}
