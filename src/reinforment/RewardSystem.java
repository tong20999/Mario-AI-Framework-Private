package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float WIN_REWARD = 100.0f;
    private static final float PARTIAL_WIN_PENALTY = -5.0f;
    private final static float LOSE_PENALTY = -10.0f;
    private final static float TIMEOUT_PENALTY = -10.0f;
    private final static float KILL_REWARD = 0.2f;
    private final static float BUMP_REWARD = 0.1f;
    private final static float COIN_REWARD = 0.1f;
    private static final float POWER_UP_REWARD = 1.0f;
    public static final float STEP_COST = -0.001f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = STEP_COST;
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

            if (e.getEventType() == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            }

            if (e.getEventType() == EventType.LOSE.getValue()) {
                reward += LOSE_PENALTY;
            }

            if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                reward += TIMEOUT_PENALTY;
            }
        }

        return reward;
    }

    private static float calculateWinReward(MarioWorld world) {
        int totalCoins = world.level.getCoins().size();
        int totalBlocks = world.level.getBumpableBlocks().size();
        int totalEnemies = world.level.getEnemies().size();

        // Handle empty levels
        if (totalCoins + totalBlocks + totalEnemies == 0) {
            return WIN_REWARD;
        }

        int aliveEnemies = world.getAliveEnemies().size();
        int unHitBlocks = world.getUnbumpBlocks().size();
        int uncollectedCoins = world.getUnCollectCoin().size();

        boolean isPerfectRun = uncollectedCoins == 0 && unHitBlocks == 0 && aliveEnemies == 0;

        // Return WIN_REWARD if the run is perfect, otherwise return the penalty.
        return isPerfectRun ? WIN_REWARD : PARTIAL_WIN_PENALTY;
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf" : (int) Math.ceil(world.currentTimer / 1000f)).toString();
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                rewardEvents.add(new RewardEvent(KILL_REWARD, e, timer));
            } else if (e.getEventType() == EventType.BUMP.getValue()
                    && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                rewardEvents.add(new RewardEvent(BUMP_REWARD, e, timer));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                rewardEvents.add(new RewardEvent(COIN_REWARD, e, timer));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e, timer));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e, timer));
            } else if (e.getEventType() == EventType.WIN.getValue()) {
                rewardEvents.add(new RewardEvent(calculateWinReward(world), e, timer));
            } else if (e.getEventType() == EventType.LOSE.getValue()) {
                rewardEvents.add(new RewardEvent(LOSE_PENALTY, e, timer));
            } else if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                rewardEvents.add(new RewardEvent(TIMEOUT_PENALTY, e, timer));
            } else {
                rewardEvents.add(new RewardEvent(0, e, timer));
            }
        }

        return rewardEvents;
    }
}
