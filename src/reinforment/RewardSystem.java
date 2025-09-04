package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float MAX_WIN_REWARD = 0; // Reward for a 100% perfect run
    private final static float BONUS_WIN_BASE = 20.0f; // Max penalty for a 0% objective run
    private final static float LOSE_PENALTY = -10.0f;
    private final static float TIMEOUT_PENALTY = -10.0f;
    private final static float KILL_REWARD = 2.0f;
    private final static float BUMP_REWARD = 2.0f;
    private final static float COIN_REWARD = 2.0f;
    private static final float POWER_UP_REWARD = 4.0f;
    private static final float EXPLORATION_REWARD = 0.02f;
    private static final float BONK_PENALTY = -0.0f;
    private static final float BREAK_PENALTY = -0.0f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0;
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += KILL_REWARD;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += POWER_UP_REWARD;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += POWER_UP_REWARD;
            }

            if (e.getEventType() == EventType.BUMP.getValue()
                    && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += BUMP_REWARD;
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += COIN_REWARD;
            }

            if (e.getEventType() == EventType.EXPLORER.getValue()) {
                // var tileY = (int)(e.getMarioY()/16f);
                // var grade = tileY < 6 ? 2f : tileY < 10 ? 1.5f : 1f;
                // reward += EXPLORATION_REWARD * grade;
                reward += EXPLORATION_REWARD;
            }

            // if(e.getEventType() == EventType.IDLE.getValue()){
            // reward += IDLE_PENALTY;
            // }

            if (e.getEventType() == EventType.WIN.getValue()) {
                reward += calculateScaledWinReward(world);
            }

            if (e.getEventType() == EventType.LOSE.getValue()) {
                int totalObjectives = getTotalCoins(world) + getTotalEnemies(world) + getTotalBlocks(world);
                reward += (LOSE_PENALTY - totalObjectives);
            }

            if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                int totalObjectives = getTotalCoins(world) + getTotalEnemies(world) + getTotalBlocks(world);
                reward += (TIMEOUT_PENALTY - totalObjectives);
            }
        }

        return reward;
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                rewardEvents.add(new RewardEvent(KILL_REWARD, e));
            } else if (e.getEventType() == EventType.BUMP.getValue()
                    && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                rewardEvents.add(new RewardEvent(BUMP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                rewardEvents.add(new RewardEvent(COIN_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.EXPLORER.getValue()) {
                rewardEvents.add(new RewardEvent(EXPLORATION_REWARD, e));
             } else if (e.getEventType() == EventType.WIN.getValue()) {
                float winReward = calculateScaledWinReward(world);
                rewardEvents.add(new RewardEvent(winReward, e));
            } else if (e.getEventType() == EventType.LOSE.getValue()) {
                int totalObjectives = getTotalCoins(world) + getTotalEnemies(world) + getTotalBlocks(world);
                float loseReward = LOSE_PENALTY - totalObjectives;
                rewardEvents.add(new RewardEvent(loseReward, e));
            } else if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                int totalObjectives = getTotalCoins(world) + getTotalEnemies(world) + getTotalBlocks(world);
                float timeoutReward = TIMEOUT_PENALTY - totalObjectives;
                rewardEvents.add(new RewardEvent(timeoutReward, e));
            } else {
                rewardEvents.add(new RewardEvent(0, e));
            }
        }

        return rewardEvents;
    }

    private static float calculateScaledWinReward(MarioWorld world) {
        // Tally up the total objectives and the number completed
        int objectivesCompleted = (getTotalCoins(world) - getUnCollectCoin(world)) +
                (getTotalEnemies(world) - getAliveEnemies(world)) +
                (getTotalBlocks(world) - getUnbumpBlocks(world));
        int totalObjectives = getTotalCoins(world) + getTotalEnemies(world) + getTotalBlocks(world);

        // Avoid division by zero on levels with no objectives
        if (totalObjectives == 0) {
            return MAX_WIN_REWARD; // No objectives to complete, so it's a perfect run
        }

        // Calculate the completion fraction (from 0.0 to 1.0)
        float completionFraction = (float) objectivesCompleted / totalObjectives;

        if (completionFraction >= 1.0){
            return (BONUS_WIN_BASE) + objectivesCompleted;
        }

        return (float) objectivesCompleted / 2;
    }

    private static int getAliveEnemies(MarioWorld world){
        return world.getAliveEnemies().size();
    }

    private static int getUnbumpBlocks(MarioWorld world){
        return world.getUnbumpBlocks().size();
    }

    private static int getUnCollectCoin(MarioWorld world){
        return world.getUnCollectCoin().size();
    }

    private static int getTotalCoins(MarioWorld world){
        return world.level.getCoins().size();
    }

    private static int getTotalEnemies(MarioWorld world){
        return world.level.getEnemies().size();
    }

    private static int getTotalBlocks(MarioWorld world){
        return world.level.getBumpableBlocks().size();
    }
}
