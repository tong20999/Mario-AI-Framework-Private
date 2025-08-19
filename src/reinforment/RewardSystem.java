package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    public static float WIN_REWARD = 1.0f;
    public static float TARGET_PERFECT_REWARD  = 5.0f;
    static float LOSE_PENALTY = -0.9f;
    public static float TIMEOUT_PENALTY = -0.5f;
    public static float BONK_REWARD = -0.01f;
    public static float KILL_REWARD = 0.1f;
    public static float BUMP_REWARD = 0.1f;
    public static float COIN_REWARD = 0.1f;
    public static float POWER_UP_REWARD = 0.1f;
    public static float DEBT_PENALTY_FACTOR = -0.12f;
    public static float EXPLORATION_REWARD = 0.0005f;
    public static float FLAG_PENALTY = -0.3f;

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

            if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += BUMP_REWARD;
            }

//            if (!bumpKill && e.getEventType() == EventType.BONK.getValue()) {
//                reward += BONK_REWARD;
//            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += COIN_REWARD;
            }

            if (e.getEventType() == EventType.EXPLORER.getValue()) {
                var tileY = (int)(e.getMarioY()/16f);
                var grade = tileY < 6 ? 2f : tileY < 10 ? 1.5f : 1f;
                reward += EXPLORATION_REWARD * grade;
            }
        }
        var remainTask = world.getUnbumpBlocks().size() +
                world.getUnCollectCoin().size() +
                world.getAliveEnemies().size();
        if (world.gameStatus == GameStatus.WIN) {
            int totalTasksInThisLevel = world.level.getBumpableBlocks().size()
                    + world.level.getCoins().size() + world.level.getEnemies().size();
            float taskBonusForThisLevel = 0;
            if (totalTasksInThisLevel > 0) {
                taskBonusForThisLevel = (TARGET_PERFECT_REWARD - WIN_REWARD) / totalTasksInThisLevel;
            }
            int tasksCompleted = totalTasksInThisLevel - remainTask;
            reward += WIN_REWARD + (tasksCompleted * taskBonusForThisLevel);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            float penalty = Math.min(TIMEOUT_PENALTY, DEBT_PENALTY_FACTOR * remainTask);
            reward += penalty;
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += LOSE_PENALTY;
        }
        reward = Math.max(-15.0f, Math.min(15.0f, reward));
        return reward;
    }
}
