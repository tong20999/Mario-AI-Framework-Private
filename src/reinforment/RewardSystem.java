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
    private final static float LOSE_PENALTY = -50.0f;
    private final static float TIMEOUT_PENALTY = -50.0f;
    private final static float KILL_REWARD = 5f;
    private final static float BUMP_REWARD = 1f;
    private final static float COIN_REWARD = 1f;
    private static final float POWER_UP_REWARD = 5.0f;
    public static final float STEP_COST = -0.01f;
    public static final float PROGRESS_REWARD = 0.02f;
    private static final float IDLE_PENALTY = -1f;

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

            if (e.getEventType() == EventType.PROGRESS.getValue()) {
                reward += PROGRESS_REWARD;
            }

            if(e.getEventType() == EventType.IDLE.getValue()){
                reward += IDLE_PENALTY;
            }
        }

        return reward;
    }

    private static float calculateWinReward(MarioWorld world) {
        // A smaller base reward for just finishing the level.
        float baseWinReward = 20.0f;

        // A large pool of bonus points for being perfect.
        float perfectionBonus = 80.0f;

        float totalObjectives = world.level.getCoins().size() +
                world.level.getBumpableBlocks().size() +
                world.level.getEnemies().size();

        if (totalObjectives == 0) {
            return WIN_REWARD; // Keep original reward for empty levels
        }

        float completedObjectives = (world.level.getEnemies().size() - world.getAliveEnemies().size()) +
                (world.level.getBumpableBlocks().size() - world.getUnbumpBlocks().size()) +
                (world.level.getCoins().size() - world.getUnCollectCoin().size());

        // Calculate the completion percentage.
        float completionRatio = completedObjectives / totalObjectives;

        if (completionRatio >= 1){
            return  WIN_REWARD;
        }

        // The final reward is the base for winning plus a proportional share of the bonus.
        return baseWinReward + (20 * completionRatio);
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
            } else if (e.getEventType() == EventType.PROGRESS.getValue()) {
                rewardEvents.add(new RewardEvent(PROGRESS_REWARD, e, timer));
            } else if(e.getEventType() == EventType.IDLE.getValue()){
                rewardEvents.add(new RewardEvent(IDLE_PENALTY, e, timer));
            }
            else {
                rewardEvents.add(new RewardEvent(0, e, timer));
            }
        }

        return rewardEvents;
    }
}
