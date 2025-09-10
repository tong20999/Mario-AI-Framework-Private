package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float WIN_REWARD = 0.0f;
    private final static float LOSE_PENALTY = -1.0f;
    private final static float TIMEOUT_PENALTY = -1.0f;
    private final static float KILL_REWARD = 1.0f;
    private final static float BUMP_REWARD = 1.0f;
    private final static float COIN_REWARD = 1.0f;
    private static final float POWER_UP_REWARD = 1.0f;
    public static final float STEP_COST = -0.01f;

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
        boolean hasObjectiveCoins = !world.level.getCoins().isEmpty();
        boolean hasObjectiveBlocks = !world.level.getBumpableBlocks().isEmpty();
        boolean hasObjectiveEnemies = !world.level.getEnemies().isEmpty();

        if(!hasObjectiveCoins && !hasObjectiveBlocks && !hasObjectiveEnemies){
            return 1f;
        }

        float bonusCoin = 0;
        if(hasObjectiveCoins){
            bonusCoin = (world.getCollectedCoinCount() * 1f/world.level.getCoins().size());
        }

        float bonusBlock = 0;
        if(hasObjectiveBlocks){
            bonusBlock = (world.getHitBlockCount() * 1f/world.level.getBumpableBlocks().size());
        }

        float bonusEnemies = 0;
        if(hasObjectiveEnemies){
            bonusEnemies = (world.getKillCount() * 1f/world.level.getEnemies().size());
        }

        return bonusCoin + bonusBlock + bonusEnemies;
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
            } else if (e.getEventType() == EventType.WIN.getValue()) {
                rewardEvents.add(new RewardEvent(calculateWinReward(world), e));
            } else if (e.getEventType() == EventType.LOSE.getValue()) {
                rewardEvents.add(new RewardEvent(LOSE_PENALTY, e));
            } else if (e.getEventType() == EventType.TIME_OUT.getValue()) {
                rewardEvents.add(new RewardEvent(TIMEOUT_PENALTY, e));
            } else {
                rewardEvents.add(new RewardEvent(0, e));
            }
        }

        return rewardEvents;
    }
}
