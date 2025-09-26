package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    // Per-objective shaping (dense)
    private static final float KILL_REWARD = 0.02f;
    private static final float BUMP_REWARD = 0.01f;
    private static final float COIN_REWARD = 0.01f;
    private static final float POWER_UP_REWARD = 0.05f;

    // Step & behavior costs
    private static final float DAMAGE_PENALTY = -0.5f;
    // Win / completion structure
    private static final float BASE_WIN_REWARD = 1f;


    // Failure penalty
    private static final float PARTIAL_WIN = -0.5f;
    private static final float FAILURE_LOSE = -1f;
    private static final float FAILURE_TIMEOUT = -1f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0;
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
            } else if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
            } else if (type == EventType.DAMAGE.getValue()) {
                reward += DAMAGE_PENALTY;
            }
        }
        return reward;
    }

    private static int remainingObjectives(MarioWorld world) {
        int enemiesLeft = world.getAliveEnemies().size();
        int blocksLeft = world.getUnbumpBlocks().size();
        int coinsLeft = world.getUnCollectCoin().size();
        return enemiesLeft + blocksLeft + coinsLeft;
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf"
                : Integer.toString((int) Math.ceil(world.currentTimer / 1000f)));
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
            } else if (type == EventType.LOSE.getValue()) {
                value = FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                value = FAILURE_TIMEOUT;
            } else if (type == EventType.DAMAGE.getValue()) {
                value = DAMAGE_PENALTY;
            } else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }
        return rewardEvents;
    }

    private static float calculateWinReward(MarioWorld world) {
        if(remainingObjectives(world) > 0){
            return PARTIAL_WIN;
        }
        return BASE_WIN_REWARD;
    }
}
