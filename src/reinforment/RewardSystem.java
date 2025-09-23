package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    // Per-objective shaping (dense)
    private static final float KILL_REWARD = 5f;
    private static final float BUMP_REWARD = 5f;
    private static final float COIN_REWARD = 5f;
    private static final float POWER_UP_REWARD = 5f;

    // Step & behavior costs
    public static final float STEP_COST = -0.01f;
    private static final float IDLE_PENALTY = -1.5f;      // Slightly harsher
    private static final float HIT_WALL_PENALTY = -0.1f;
    private static final float PROGRESS_REWARD = 0.02f;   // Slightly higher
    private static final float DAMAGE_PENALTY = -1f;

    // Win / completion structure
    private static final float BASE_WIN_REWARD = 5f;      // Lowered so partial wins pay less
    private static final float PERFECT_BONUS = 50f;       // Slight bump
    private static final float MISSING_OBJECT_PENALTY = 5f; // Applied per remaining objective on win

    // Failure penalty
    private static final float FAILURE_BASE = -60f;
    private static final float FAILURE_PROGRESS_DELTA = 50f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = STEP_COST;
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();
            int param = e.getEventParam();

            if (type == EventType.STOMP_KILL.getValue() ||
                    type == EventType.FIRE_KILL.getValue() ||
                    type == EventType.SHELL_KILL.getValue() ||
                    type == EventType.BUMP_KILL.getValue() ||
                    type == EventType.FALL_KILL.getValue()) {
                reward += KILL_REWARD;
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                reward += POWER_UP_REWARD;
            } else if (type == EventType.BUMP.getValue() &&
                    param == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += BUMP_REWARD;
            } else if (type == EventType.COLLECT.getValue() && param == 15) {
                reward += COIN_REWARD;
            } else if (type == EventType.HIT_WALL.getValue()) {
                reward += HIT_WALL_PENALTY;
            } else if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue() || type == EventType.TIME_OUT.getValue()) {
                reward += dynamicFailurePenalty(world);
            } else if (type == EventType.PROGRESS.getValue()) {
                reward += PROGRESS_REWARD;
            } else if (type == EventType.IDLE.getValue()) {
                reward += IDLE_PENALTY;
            } else if (type == EventType.DAMAGE.getValue()) {
                reward += DAMAGE_PENALTY;
            }
        }
        return reward;
    }

    private static float calculateWinReward(MarioWorld world) {
        float ratio = completionRatio(world);
        if (ratio >= 1f) {
            return PERFECT_BONUS;
        }
        int remaining = remainingObjectives(world);
        // Partial win: scaled base + penalty for what is left
        return (BASE_WIN_REWARD * ratio) - (remaining * MISSING_OBJECT_PENALTY);
    }

    private static float completionRatio(MarioWorld world) {
        float total = objectiveTotal(world);
        if (total <= 0f) return 1f;
        float completed = objectiveCompleted(world);
        return completed / total;
    }

    private static int objectiveTotal(MarioWorld world) {
        return world.level.getCoins().size()
                + world.level.getBumpableBlocks().size()
                + world.level.getEnemies().size();
    }

    private static int remainingObjectives(MarioWorld world) {
        int enemiesLeft = world.getAliveEnemies().size();
        int blocksLeft = world.getUnbumpBlocks().size();
        int coinsLeft  = world.getUnCollectCoin().size();
        return enemiesLeft + blocksLeft + coinsLeft;
    }

    private static int objectiveCompleted(MarioWorld world) {
        return objectiveTotal(world) - remainingObjectives(world);
    }

    private static float dynamicFailurePenalty(MarioWorld world) {
        float ratio = completionRatio(world);
        return FAILURE_BASE + (FAILURE_PROGRESS_DELTA * ratio);
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf" : Integer.toString((int)Math.ceil(world.currentTimer / 1000f)));
        for (MarioEvent e : miniStepEvents) {
            float value;
            int type = e.getEventType();
            int param = e.getEventParam();

            if (type == EventType.STOMP_KILL.getValue() ||
                    type == EventType.FIRE_KILL.getValue() ||
                    type == EventType.SHELL_KILL.getValue() ||
                    type == EventType.BUMP_KILL.getValue() ||
                    type == EventType.FALL_KILL.getValue()) {
                value = KILL_REWARD;
            } else if (type == EventType.BUMP.getValue() &&
                    param == MarioForwardModel.OBS_QUESTION_BLOCK) {
                value = BUMP_REWARD;
            } else if (type == EventType.COLLECT.getValue() && param == 15) {
                value = COIN_REWARD;
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                value = POWER_UP_REWARD;
            } else if (type == EventType.WIN.getValue()) {
                value = calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue() || type == EventType.TIME_OUT.getValue()) {
                value = dynamicFailurePenalty(world);
            } else if (type == EventType.IDLE.getValue()) {
                value = IDLE_PENALTY;
            } else if (type == EventType.HIT_WALL.getValue()) {
                value = HIT_WALL_PENALTY;
            } else if (type == EventType.PROGRESS.getValue()) {
                value = PROGRESS_REWARD;
            } else if (type == EventType.DAMAGE.getValue()) {
                value = DAMAGE_PENALTY;
            } else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }
        return rewardEvents;
    }
}
