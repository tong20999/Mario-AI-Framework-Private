package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;
import engine.sprites.Mario;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;

public class RewardSystem {
    static float bonusBlock = 50;
    static float bonusCoin = 50;
    static float bonusKill = 50;
    static float winReward = 50;
    static float mileStoneReward = 0.0f;
    static float jumpOverPitReward = 0f;
    static float killReward = 10;
    static float bumpReward = 10;
    static float coinReward = 10;
    static float fireworkReward = 20;
    static float mushroomReward = 20;
    static float lifeMushroomReward = 20;
    static float loseReward = -20f;
    static float loseTimeoutReward = -20f;
    static float hurtReward = -5f;
    static float hitWallReward = -0.0f;
    static float fallPitReward = -10f;
    static float timePenaltyRewardCoefficient = 0;

    public static float getReward(MarioWorld world,
                                  MarioForwardModel nextState,
                                  int lastCoinCount,
                                  Objective objective) {
        float reward = 0.0f;
        //reward += timePenalty();
        //reward += mileStoneReward();

        // Coin collection reward
        int currentCoins = nextState.getNumCollectedCoins();
        if (currentCoins > lastCoinCount) {
            reward += coinReward; // +0.5 reward per coin
            lastCoinCount = currentCoins;
        }

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : world.lastFrameEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue()) {
//                var sprintCode = e.getSprintCode();
//                if(sprintCode != null && !this.clearedSpawnPointsThisEpisode.contains(sprintCode)){
//                    clearedSpawnPointsThisEpisode.add(sprintCode);
//                    reward += killReward; // +2 reward per kill
//                }
                reward += killReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += fireworkReward; // +5 for a power-up
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += mushroomReward; // +2 for a mushroom
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += lifeMushroomReward; // +5 for a life
            }
            if (e.getEventType() == EventType.HURT.getValue()) {
                reward += hurtReward; // -1 for taking damage
            }
            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
                reward += hitWallReward;
            }
            if (e.getEventType() == EventType.FALL_PIT.getValue()) {
                reward += fallPitReward;
            }

            if (e.getEventType() == EventType.BUMP.getValue()){
                reward += bumpReward;
            }
        }

        checkSubGoalMet(world, objective);

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the run itself.

            reward += winReward;
            reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseTimeoutReward;
            reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.LOSE) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseReward;
            reward += distanceToFlag(world.mario);
        }

        return reward;
    }

    private static void checkSubGoalMet(MarioWorld world, Objective objective) {
        if(objective == Objective.COIN){
            if(world.level.totalCoins == world.coins){
                world.subGoalMet = true;
            }
        }

        if(objective == Objective.BLOCK){
            if(world.level.totalBumpBlock == world.bumpBlock){
                //this.world.win();
                world.subGoalMet = true;
            }
        }

        if(objective == Objective.ENEMY){
            if(world.level.totalEnemies == world.kill){
                world.subGoalMet = true;
            }
        }
    }

    private static float distanceToFlag(Mario mario) {
        var complete = mario.x * 0.01f;
        return complete;
    }

    public static float calculateNonlinearBonus(int total, int achieve, float maxBonus) {
        return calculateNonlinearBonus(total, achieve, maxBonus, null);
    }

    public static float calculateNonlinearBonus(int total, int achieve, float maxBonus, String subGoal) {
        if (total == 0) {
            return 0.0f; // Avoid division by zero if a level has no blocks
        }

        // Crucial cast to float: In Java, dividing two integers (e.g., 3 / 5) results in 0.
        // We cast to float to get the correct decimal result (e.g., 0.6f).
        float completionRatio = (float) achieve / total;

        // Apply a non-linear scaling using Math.pow() for squaring the ratio.
        // Math.pow returns a double, so we cast it back to a float.
        float scaledRatio = (float) Math.pow(completionRatio, 2);

        // Calculate the final bonus
        float bonusEarned = maxBonus * scaledRatio;

        return bonusEarned;
    }

    public static void printRewardsInformation() {
        String rewardInformation = MessageFormat.format("""
                        REWARDS
                        WIN {0}
                        LOSE {1}
                        TIME_OUT {2}
                        MILESTONE {3}
                        JumpOverPit {4}
                        KILL {5}
                        COLLECT_COIN {6}
                        COLLECT_FIREWORK {7}
                        COLLECT_MUSHROOM {8}
                        COLLECT_LIFE_MUSHROOM {9}
                        HURT {10}
                        HIT_WALL {11}
                        FALL_PIT {12}
                        TIME_PENALTY_COEFFICIENT {13}
                        BONUS COIN {14}
                        BONUS BLOCK {15}
                        BONUS KILL {16}
                        BUMP REWARD {17}
                        """,
                winReward,
                loseReward,
                loseTimeoutReward,
                mileStoneReward,
                jumpOverPitReward,
                killReward,
                coinReward,
                fireworkReward,
                mushroomReward,
                lifeMushroomReward,
                hurtReward,
                hitWallReward,
                fallPitReward,
                timePenaltyRewardCoefficient,
                bonusCoin,
                bonusBlock,
                bonusKill,
                bumpReward
        );

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\thesis_data\\reward.txt", false))) {
            writer.write(rewardInformation);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

//    private float timePenalty() {
//        float reward = this.world.currentTimer - this.currentTimer;
//        this.currentTimer = this.world.currentTimer;
//        if(reward >= 0){
//            return 0;
//        }
//        var timePenalty = reward * timePenaltyRewardCoefficient;
//        return timePenalty;
//    }
//
//    private float mileStoneReward() {
//        double completePercentage = this.world.mario.x / (this.world.level.exitTileX * 16.0);
//        int milestone = (int)(completePercentage * 100);
//        if (milestone > lastMilestone) {
//            lastMilestone = milestone;
//            return mileStoneReward;
//        }
//        return 0;
//    }
}
