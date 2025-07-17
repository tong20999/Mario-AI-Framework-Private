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
//    static float winRewardMultiplier = 5;
//    static float mileStoneReward = 0.0f;
//    static float jumpOverPitReward = 0f;
//    static float killReward = 0.7f;
//    static float bumpReward = 0.5f;
//    static float coinReward = 0.5f;
//    static float fireworkReward = 0.5f;
//    static float mushroomReward = 0.5f;
//    static float lifeMushroomReward = 0.5f;
    static float losePenalty = -1.0f;
    static float timeoutPenalty = -1.0f;
//    static float hurtReward = -0.0f;
//    static float hitWallReward = -0.0f;
//    static float fallPitReward = -0f;
//    static float timePenaltyRewardCoefficient = 0;
    static float jumpSpamPenalty = -0.00f;
    private final int totalBumpBlock;
    private final int totalCoins;
    private final int totalEnemies;
    private final int totalPowerUp;
    float killReward;
    float bumpReward;
    float coinReward;
    float powerUpReward;

    ProceduralContentGenerationLevel pcg;

    public RewardSystem(MarioLevel level, ProceduralContentGenerationLevel pcg) {
        this.pcg = pcg;
        this.totalBumpBlock = level.totalBumpBlock;
        this.totalCoins = level.totalCoins;
        this.totalEnemies = level.totalEnemies;
        this.totalPowerUp = level.totalPowerUp;

        killReward = 0.2f;
        bumpReward = 0.1f;
        coinReward = 0.1f;
        powerUpReward = 0.2f;
    }

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents, boolean[] actions, boolean isEvaluation,
                           int evaluationEpisode) {
        float reward = 0.0f;

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += killReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("kill reward reward {0}", reward));
                }
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += powerUpReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("firework reward reward {0}", reward));
                }
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += powerUpReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("mushroom reward {0}", reward));
                }
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += powerUpReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("life mushroom reward {0}", reward));
                }
            }

//            if (e.getEventType() == EventType.HURT.getValue()) {
//                if(world.mario.isFire || world.mario.isLarge){
//                    reward += hurtReward;
//                }
//            }
//            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
//                reward += hitWallReward;
//            }
//            if (e.getEventType() == EventType.FALL_PIT.getValue()) {
//                reward += fallPitReward;
//            }

            if (e.getEventType() == EventType.BUMP.getValue()){
                reward += bumpReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("bump reward {0}", reward));
                }
            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15){
                reward += coinReward;
                if(isEvaluation){
                    System.out.println(MessageFormat.format("coin reward {0}", reward));
                }
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the run itself.
            // float total = (world.kill) + (world.bumpBlock) + (world.collectCoin);
            // reward += total * winRewardMultiplier;
            reward += winReward;
            if(isEvaluation && this.pcg != null){
                logPcg(evaluationEpisode, world.gameStatus.name());
                //System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            //reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += timeoutPenalty;
            if(isEvaluation && this.pcg != null){
                logPcg(evaluationEpisode, world.gameStatus.name());
                //System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            //reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.LOSE) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += losePenalty;
            if(isEvaluation && this.pcg != null){
                logPcg(evaluationEpisode, world.gameStatus.name());
                //System.out.println(MessageFormat.format("win reward {0}", reward));
            }
            //reward += distanceToFlag(world.mario);
        }

        return reward;
    }

    private void logPcg(int evaluationEpisode, String name) {
        // First, let's construct the full file path.
        String filePath = MessageFormat.format("C:\\thesis_data\\zpcg\\{0}\\pgc_{1}_{2}.txt", name, name, evaluationEpisode);

        // Now, let's get the parent directory path from the file path.
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        // Check if the parent directory exists. If not, create it.
        if (parentDir != null && !parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs(); // mkdirs() creates all necessary but nonexistent parent directories.
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

    public void printInformation() {
        String rewardInformation = MessageFormat.format("""
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
                powerUpReward
        );

        String levelInformation = MessageFormat.format("""
                        LEVEL
                        ENEMY {0}
                        COIN {1}
                        BLOCK {2}
                        POWER {3}
                        """,
                totalEnemies,
                totalCoins,
                totalBumpBlock,
                totalPowerUp
        );

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\thesis_data\\reward.txt", false))) {
            writer.write(rewardInformation);
            writer.newLine();
            writer.write(levelInformation);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

//    public void printRewardsInformation(MarioWorld world) {
//        String rewardInformation = MessageFormat.format("""
//                        REWARDS
//                        WIN MULTIPLIER {0}
//                        LOSE {1}
//                        TIME_OUT {2}
//                        MILESTONE {3}
//                        JumpOverPit {4}
//                        KILL {5}
//                        COLLECT_COIN {6}
//                        COLLECT_FIREWORK {7}
//                        COLLECT_MUSHROOM {8}
//                        COLLECT_LIFE_MUSHROOM {9}
//                        HURT {10}
//                        HIT_WALL {11}
//                        FALL_PIT {12}
//                        TIME_PENALTY_COEFFICIENT {13}
//                        BONUS COIN {14}
//                        BONUS BLOCK {15}
//                        BONUS KILL {16}
//                        BUMP REWARD {17}
//                        JUMP SPAM {18}
//                        CLIP REWARD {19}
//                        WIN REWARD {20}
//                        """,
//                winRewardMultiplier,
//                loseReward,
//                loseTimeoutReward,
//                mileStoneReward,
//                jumpOverPitReward,
//                killReward,
//                coinReward,
//                fireworkReward,
//                mushroomReward,
//                lifeMushroomReward,
//                hurtReward,
//                hitWallReward,
//                fallPitReward,
//                timePenaltyRewardCoefficient,
//                bumpReward,
//                jumpSpamPenalty,
//                winReward
//        );
//
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\thesis_data\\reward.txt", false))) {
//            writer.write(rewardInformation);
//            writer.newLine();
//        } catch (IOException e) {
//            System.err.println("Error writing to file: " + e.getMessage());
//        }
//    }
}
