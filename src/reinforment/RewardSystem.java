package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    public static float WIN_REWARD = 0.3f;
    static float LOSE_PENALTY = -0.9f;
    static float TIMEOUT_PENALTY = -0.0f;
    public static float BONK_REWARD = -0.01f;
    public static float KILL_REWARD = 0.1f;
    public static float BUMP_REWARD = 0.1f;
    public static float COIN_REWARD = 0.1f;
    public static float POWER_UP_REWARD = 0.1f;
    public static float DEBT_PENALTY_FACTOR = -0.12f;
    public static float EXPLORATION_REWARD = 0.01f;
    public static float FLAG_PENALTY = -0.9f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0.0f;
        for (MarioEvent e : miniStepEvents) {
            boolean bumpKill = e.getEventType() == EventType.BUMP_KILL.getValue();
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

            if (!bumpKill && e.getEventType() == EventType.BONK.getValue()) {
                reward += BONK_REWARD;
            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += COIN_REWARD;
            }

            //if (e.getEventType() == EventType.EXPLORER.getValue()) {
                //reward += EXPLORATION_REWARD;
            //}
        }

        if (world.gameStatus == GameStatus.WIN) {
            var objectiveClear = world.isSubGoalBlockMet() && world.isSubGoalCoinMet()
                    && world.isSubGoalCoinMet();
            if(objectiveClear){
                reward += WIN_REWARD;
            } else {
                var remainTask = world.getUnbumpBlocks().size() +
                        world.getUnCollectCoin().size() +
                        world.getAliveEnemies().size();
                reward += Math.min(FLAG_PENALTY, DEBT_PENALTY_FACTOR * remainTask);
            }
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            var debtBlock = world.level.getBumpableBlocks().size() -
                    world.getUnbumpBlocks().size();
            var debtCoin = world.level.getCoins().size() -
                    world.getUnCollectCoin().size();
            var debtEnemy = world.level.getEnemies().size() -
                    world.getAliveEnemies().size();
            var debt = debtBlock + debtEnemy + debtCoin;
            if(debt == 0){
                reward = 0.1f;
            }
            else {
                float penalty = Math.min(-0.90f, -0.12f * debt);
                reward += penalty;
            }
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += LOSE_PENALTY;
        }
        reward = Math.max(-1.0f, Math.min(1.0f, reward));
        return reward;
    }
}
