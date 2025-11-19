package reinforcement;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;
import java.util.List;

public class RewardSystem {
    private static final float WIN_REWARD = 100f;
    private static final float PARTIAL_WIN = 50f;
    private static final float FAILURE_LOSE = -100f;
    private static final float FAILURE_TIMEOUT = -50f;
    private static final float POWER_UP_REWARD = 10f;
    private static final float STUCK_PENALTY = -1f;
    private static final float BONK_PENALTY = -1f;
    private static final float DAMAGE_PENALTY = -50;
    private static final float STALL_PENALTY = -0.2f;
    private static final float JUMP_SPAM_PENALTY = -1f;

    // Weights
    private static final int KILL_REWARD = 10;
    private static final int BUMP_REWARD = 8;
    private static final int COIN_REWARD = 5;
    private static final List<EventType> ignoreBonk = List.of(EventType.BUMP_KILL, EventType.COLLECT);

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0f; // step cost

        // Events
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
            }  else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
            } else if (type == EventType.JUMP_SPAM.getValue()) {
                reward += JUMP_SPAM_PENALTY;
            } else if (type == EventType.STUCK_RIGHT.getValue()) {
                reward += STUCK_PENALTY;
            } else if (type == EventType.DAMAGE.getValue()) {
                reward += DAMAGE_PENALTY;
            } else if (type == EventType.STALL.getValue()) {
                reward += STALL_PENALTY;
            } else if (type == EventType.BONK.getValue()) {
                boolean hasKillOrCollect = miniStepEvents.stream()
                        .anyMatch(me -> ignoreBonk.contains(me.getEventTypeEnum()));
                reward += hasKillOrCollect ? 0 : BONK_PENALTY;
            }
        }
        return reward;
    }

    private static int computeRemainingWeighted(MarioWorld world) {
        int enemiesLeft = world.getAliveEnemies().size();
        int blocksLeft = world.getUnbumpBlocks().size();
        int coinsLeft = world.getUnCollectCoin().size();
        return enemiesLeft + blocksLeft + coinsLeft;
    }

    private static float calculateWinReward(MarioWorld world) {
        // Optionally require all objectives cleared
        if (computeRemainingWeighted(world) > 0)
            return PARTIAL_WIN;
        return WIN_REWARD;
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
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                value = POWER_UP_REWARD;
            } else if (type == EventType.BUMP.getValue() &&
                    param == MarioForwardModel.OBS_QUESTION_BLOCK) {
                value = BUMP_REWARD;
            } else if (type == EventType.COLLECT.getValue() && param == 15) {
                value = COIN_REWARD;
            } else if (type == EventType.WIN.getValue()) {
                value = calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue()) {
                value = FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                value = FAILURE_TIMEOUT;
            } else if (type == EventType.JUMP_SPAM.getValue()) {
                value = JUMP_SPAM_PENALTY;
            } else if (type == EventType.STUCK_RIGHT.getValue()) {
                value = STUCK_PENALTY;
            } else if (type == EventType.DAMAGE.getValue()) {
                value = DAMAGE_PENALTY;
            } else if (type == EventType.STALL.getValue()) {
                value = STALL_PENALTY;
            } else if (type == EventType.BONK.getValue()) {
                boolean hasKillOrCollect = miniStepEvents.stream()
                        .anyMatch(me -> ignoreBonk.contains(me.getEventTypeEnum()));
                value = hasKillOrCollect ? 0 : BONK_PENALTY;
            } else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }
        return rewardEvents;
    }
}