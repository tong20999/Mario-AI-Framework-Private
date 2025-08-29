package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float WIN_REWARD = 1.0f;
    private static final float WIN_PENALTY_IMPERFECT = -0.0f;
    private static final float OBJECTIVE_CLEAR = 1.0f;
    private final static float LOSE_PENALTY = 0.0f;
    private final static float TIMEOUT_PENALTY = 0.0f;
    private final static float MAX_KILL_REWARD = 1.0f;
    private final static float MAX_BUMP_REWARD = 1.0f;
    private final static float MAX_COIN_REWARD = 1.0f;
    private static final float POWER_UP_REWARD = 0.01f;
    private static final float EXPLORATION_REWARD = 0.00f;
    public static final float IDLE_PENALTY = -0.1f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0;
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                float killReward = getKilLReward(world);
                reward += killReward;
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
                float bumpReward = getBumpReward(world);
                reward += bumpReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                float coinReward = getCoinReward(world);
                reward += coinReward;
            }

//            if (!bumpKill && e.getEventType() == EventType.BONK.getValue()) {
//                reward += BONK_REWARD;
//            }

//            if (e.getEventType() == EventType.EXPLORER.getValue()) {
//                //var tileY = (int)(e.getMarioY()/16f);
//                //var grade = tileY < 6 ? 2f : tileY < 10 ? 1.5f : 1f;
//                //reward += EXPLORATION_REWARD * grade;
//                reward += EXPLORATION_REWARD;
//            }

            if(e.getEventType() == EventType.OBJECTIVE_BLOCK_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }

            if(e.getEventType() == EventType.OBJECTIVE_KILL_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }

            if(e.getEventType() == EventType.OBJECTIVE_COIN_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }

            if(e.getEventType() == EventType.IDLE.getValue()){
                reward += IDLE_PENALTY;
            }
        }

        if (world.gameStatus == GameStatus.WIN) {
            var clear = world.getAliveEnemies().isEmpty()
                    && world.getUnCollectCoin().isEmpty() && world.getUnbumpBlocks().isEmpty();
            reward += clear ? WIN_REWARD : WIN_PENALTY_IMPERFECT;
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            reward += TIMEOUT_PENALTY;
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += LOSE_PENALTY;
        }

        reward = Math.max(-5f, Math.min(5f, reward));
        return reward;
    }

    private static float getKilLReward(MarioWorld world) {
        if(world.level.getEnemies().isEmpty()){
            return 0;
        }

        return MAX_KILL_REWARD / world.level.getEnemies().size();
    }

    private static float getBumpReward(MarioWorld world) {
        if(world.level.getBumpableBlocks().isEmpty()){
            return 0;
        }

        return MAX_BUMP_REWARD / world.level.getBumpableBlocks().size();
    }


    private static float getCoinReward(MarioWorld world) {
        if(world.level.getCoins().isEmpty()){
            return 0;
        }

        return MAX_COIN_REWARD / world.level.getCoins().size();
    }

    public static  ArrayList<RewardEvent> logRewardEvent(MarioWorld world,
                                                         ArrayList<MarioEvent> miniStepEvents,
                                                         GameStatus gameStatus) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                float killReward = getKilLReward(world);
                rewardEvents.add(new RewardEvent(killReward, e));
            } else if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                float bumpReward = getBumpReward(world);
                rewardEvents.add(new RewardEvent(bumpReward, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                float coinReward = getCoinReward(world);
                rewardEvents.add(new RewardEvent(coinReward, e));
        }
        if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                rewardEvents.add(new RewardEvent(POWER_UP_REWARD, e));
            }
        //else if (e.getEventType() == EventType.EXPLORER.getValue()) {
//                rewardEvents.add(new RewardEvent(EXPLORATION_REWARD, e));
//            }
            else if(e.getEventType() == EventType.WIN.getValue()){
                var clear = world.getAliveEnemies().isEmpty()
                        && world.getUnCollectCoin().isEmpty() && world.getUnbumpBlocks().isEmpty();
                float winReward = clear ? WIN_REWARD : WIN_PENALTY_IMPERFECT;
                rewardEvents.add(new RewardEvent(winReward, e));
            } else if(e.getEventType() == EventType.LOSE.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.LOSE_PENALTY, e));
            } else if(e.getEventType() == EventType.OBJECTIVE_BLOCK_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if(e.getEventType() == EventType.OBJECTIVE_KILL_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if(e.getEventType() == EventType.OBJECTIVE_COIN_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if(e.getEventType() == EventType.IDLE.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.IDLE_PENALTY, e));
            }
            else {
                rewardEvents.add(new RewardEvent(0, e));
            }
        }

        if (gameStatus.equals(GameStatus.TIME_OUT)) {
            int marioState = 0;
            if (world.mario.isLarge) {
                marioState = 1;
            }
            if (world.mario.isFire) {
                marioState = 2;
            }
            rewardEvents.add(new RewardEvent(RewardSystem.TIMEOUT_PENALTY,
                    new MarioEvent(EventType.TIME_OUT, 0, world.mario.x,
                            world.mario.y, marioState, world.currentTick)));
        }

        return rewardEvents;
    }
}
