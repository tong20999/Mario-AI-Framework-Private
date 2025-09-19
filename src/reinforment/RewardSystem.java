package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    // Per-objective shaping (dense)
    private static final float KILL_REWARD = 4f;
    private static final float BUMP_REWARD = 2f;
    private static final float COIN_REWARD = 2f;
    private static final float POWER_UP_REWARD = 4f;

    // Step & behavior costs
    public static final float STEP_COST = -0.01f;
    private static final float IDLE_PENALTY = 0.0f;
    private static final float HIT_WALL_PENALTY = -0.5f;
    private static final float PROGRESS_REWARD = 0.0f;

    // Win structure (small base + modest perfect bonus)
    private static final float BASE_WIN_REWARD = 5f;
    private static final float PERFECT_BONUS = 150f;

    // Dynamic failure penalty parameters
    private static final float FAILURE_BASE = -60f;          // Worst-case (0% completion)
    private static final float FAILURE_PROGRESS_DELTA = 20f;
    private static final float FAILURE_80_PERCENT = -20f;

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

            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
                reward += HIT_WALL_PENALTY;
            }

            if (e.getEventType() == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            }

            if (e.getEventType() == EventType.LOSE.getValue()) {
                reward += dynamicFailurePenalty(world);
            }

            if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                reward += dynamicFailurePenalty(world);
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
        float ratio = completionRatio(world);
        if (ratio >= 1f) {
            return BASE_WIN_REWARD + PERFECT_BONUS;
        }

        return BASE_WIN_REWARD * ratio;
    }

    private static float completionRatio(MarioWorld world) {
        float total = world.level.getCoins().size()
                + world.level.getBumpableBlocks().size()
                + world.level.getEnemies().size();
        if (total <= 0f) return 1f;

        float completed = (world.level.getEnemies().size() - world.getAliveEnemies().size())
                + (world.level.getBumpableBlocks().size() - world.getUnbumpBlocks().size())
                + (world.level.getCoins().size() - world.getUnCollectCoin().size());
        return completed / total;
    }

    private static float dynamicFailurePenalty(MarioWorld world) {
        float ratio = completionRatio(world);
        if(ratio > 0.8f){
            return FAILURE_80_PERCENT;
        }
        return FAILURE_BASE + (FAILURE_PROGRESS_DELTA * ratio); // [-60, -40]
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf" : (int) Math.ceil(world.currentTimer / 1000f)).toString();
        float value;
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                value = KILL_REWARD;
            } else if (e.getEventType() == EventType.BUMP.getValue()
                    && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                value = BUMP_REWARD;
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                value = COIN_REWARD;
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                value = POWER_UP_REWARD;
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                value = POWER_UP_REWARD;
            } else if (e.getEventType() == EventType.WIN.getValue()) {
                value = calculateWinReward(world);
            } else if (e.getEventType() == EventType.LOSE.getValue()) {
                value = dynamicFailurePenalty(world);
            } else if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                value = dynamicFailurePenalty(world);
            } else if(e.getEventType() == EventType.IDLE.getValue()){
                value = IDLE_PENALTY;
            } else if(e.getEventType() == EventType.HIT_WALL.getValue()){
                value = HIT_WALL_PENALTY;
            } else if(e.getEventType() == EventType.PROGRESS.getValue()){
                value = PROGRESS_REWARD;
            } else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }

        return rewardEvents;
    }
}
