package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float WIN_REWARD = 5.0f; // Reward for a 100% perfect run
    private final static float PARTIAL_WIN_REWARD = 1.0f; // Max penalty for a 0% objective run
    private final static float LOSE_PENALTY = -1.0f;
    private final static float TIMEOUT_PENALTY = -1.0f;
    private final static float KILL_REWARD = 1.0f;
    private final static float BUMP_REWARD = 1.0f;
    private final static float COIN_REWARD = 1.0f;
    private static final float POWER_UP_REWARD = 0.2f;
    private static final float EXPLORATION_REWARD = 0.002f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0;
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += getKillReward(world);
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
                reward += getBlockReward(world);
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += getCoinReward(world);
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

    private static float getCoinReward(MarioWorld world) {
        return COIN_REWARD/getTotalCoins(world);
    }

    private static float getBlockReward(MarioWorld world) {
        return BUMP_REWARD/getTotalBlocks(world);
    }

    private static float getKillReward(MarioWorld world) {
        return KILL_REWARD/getTotalEnemies(world);
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                rewardEvents.add(new RewardEvent(getKillReward(world), e));
            } else if (e.getEventType() == EventType.BUMP.getValue()
                    && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                rewardEvents.add(new RewardEvent(getBlockReward(world), e));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                rewardEvents.add(new RewardEvent(getCoinReward(world), e));
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
            return WIN_REWARD; // No objectives to complete, so it's a perfect run
        }

        int objectiveCoinsComplete = getUnCollectCoin(world) == 0 ? 1 : 0;
        int objectiveBlocksComplete = getUnbumpBlocks(world) == 0 ? 1 : 0;
        int objectiveEnemyComplete = getAliveEnemies(world) == 0 ? 1 : 0;

        if(getUnCollectCoin(world) == 0 && getUnbumpBlocks(world) == 0 && getAliveEnemies(world) == 0)
        {
            return WIN_REWARD + objectiveCoinsComplete + objectiveBlocksComplete + objectiveEnemyComplete;
        }

        return PARTIAL_WIN_REWARD + objectiveCoinsComplete + objectiveBlocksComplete + objectiveEnemyComplete;
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
