package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioLevel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.UUID;

public class RewardSystem {
    //static float winReward = 1;
    static float losePenalty = -0.0f;
    static float timeoutPenalty = -0.0f;
    static float killReward = 0.3f;
    static float bumpReward = 0.2f;
    static float coinReward = 0.2f;
    static float powerUpReward = 0.2f;

    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0.0f;
        for (MarioEvent e : miniStepEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue() ||
                    e.getEventType() == EventType.BUMP_KILL.getValue() ||
                    e.getEventType() == EventType.FALL_KILL.getValue()) {
                reward += killReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += powerUpReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += powerUpReward;
                if(reward > 1){
                    System.out.println(reward);
                }
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += powerUpReward;
            }

            if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += bumpReward;
                if(reward > 1){
                    System.out.println(reward);
                }

            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += coinReward;
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            int blockHit = world.getHitBlockCount();
            int totalBlock = world.level.getBumpableBlocks().size();
            int killCount = world.getKillCount();
            int totalEnemies = world.level.getEnemies().size();
            int collectedCoin = world.getCollectedCoinCount();
            int totalCoin = world.level.getCoins().size();
            float winReward = (float) (blockHit + killCount + collectedCoin) /(totalCoin + totalBlock + totalEnemies);
            reward += winReward;
            if(reward > 1){
                System.out.println(reward);
            }
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            reward += timeoutPenalty;
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += losePenalty;
        }
        return reward;
    }
}
