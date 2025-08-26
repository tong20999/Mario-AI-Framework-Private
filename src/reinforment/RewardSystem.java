package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private final static float WIN_REWARD = 20.0f;
    private final static float OBJECTIVE_CLEAR = 5.0f;
    private final static float LOSE_PENALTY = -5.0f;
    private final static float TIMEOUT_PENALTY = -5.0f;
    private final static float BONK_REWARD = -0.01f;
    private final static float KILL_REWARD = 2.0f;
    private final static float BUMP_REWARD = 2.0f;
    private final static float COIN_REWARD = 2.0f;
    private static final float POWER_UP_REWARD = 2.0f;
    //public static float DEBT_PENALTY_FACTOR = -0.12f;
    private static final float EXPLORATION_REWARD = 0.02f;
    //public static float FLAG_PENALTY = -0.3f;
    public static final float IDLE_PENALTY = -2.0f;

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

            if (e.getEventType() == EventType.OBJECTIVE_KILL_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }

            if (e.getEventType() == EventType.OBJECTIVE_BLOCK_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }

            if (e.getEventType() == EventType.OBJECTIVE_COIN_CLEAR.getValue()){
                reward += OBJECTIVE_CLEAR;
            }
        }

        if (world.gameStatus == GameStatus.WIN) {
            int remainTask = world.getUnbumpBlocks().size() + world.getUnCollectCoin().size()
                    + world.getAliveEnemies().size();
            reward += remainTask == 0 ? WIN_REWARD : 0;
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            reward += TIMEOUT_PENALTY;
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += LOSE_PENALTY;
        }
        reward = Math.max(-25.0f, Math.min(25.0f, reward));
        return reward;
    }

    public static  ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents, GameStatus gameStatus, boolean isIdlePenalty) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.KILL_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                rewardEvents.add(new RewardEvent(RewardSystem.POWER_UP_REWARD, e));
            } else if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                rewardEvents.add(new RewardEvent(RewardSystem.BUMP_REWARD, e));
            } else if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) { // COIN
                rewardEvents.add(new RewardEvent(RewardSystem.COIN_REWARD, e));
            } else if(e.getEventType() == EventType.EXPLORER.getValue()){
                var tileY = (int)(e.getMarioY()/16f);
                var grade = tileY < 6 ? 2f : tileY < 10 ? 1.5f : 1f;
                rewardEvents.add(new RewardEvent(RewardSystem.EXPLORATION_REWARD * grade, e));
            } else if(e.getEventType() == EventType.WIN.getValue()){
                int remainTask = world.getUnbumpBlocks().size() + world.getUnCollectCoin().size()
                        + world.getAliveEnemies().size();
                float winReward = remainTask == 0 ? WIN_REWARD : 0;
                rewardEvents.add(new RewardEvent(winReward, e));
            } else if (e.getEventType() == EventType.OBJECTIVE_KILL_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if (e.getEventType() == EventType.OBJECTIVE_BLOCK_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if (e.getEventType() == EventType.OBJECTIVE_COIN_CLEAR.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.OBJECTIVE_CLEAR, e));
            } else if(e.getEventType() == EventType.LOSE.getValue()){
                rewardEvents.add(new RewardEvent(RewardSystem.LOSE_PENALTY, e));
            }
            else {
                rewardEvents.add(new RewardEvent(0, e));
            }
        }

        if(isIdlePenalty){
            int marioState = 0;
            if (world.mario.isLarge) {
                marioState = 1;
            }
            if (world.mario.isFire) {
                marioState = 2;
            }

            rewardEvents.add(new RewardEvent(RewardSystem.IDLE_PENALTY,
                    new MarioEvent(EventType.IDLE, 0, world.mario.x,
                            world.mario.y, marioState, world.currentTick)));
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
