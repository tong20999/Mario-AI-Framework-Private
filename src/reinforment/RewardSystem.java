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
    static float winReward = 1;
    static float losePenalty = -0.0f;
    static float timeoutPenalty = -0.0f;
    static float killReward = 0.2f;
    static float bumpReward = 0.2f;
    static float coinReward = 0.2f;
    static float powerUpReward = 0.2f;
    static float explorerReward = 0.01f;
    ProceduralContentGenerationLevel pcg;

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
            }
            if (e.getEventType() == EventType.COLLECT.getValue()
                    && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += powerUpReward;
            }

            if (e.getEventType() == EventType.BUMP.getValue() && e.getEventParam() == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += bumpReward;
            }

            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == 15) {
                reward += coinReward;
            }

            if (e.getEventType() == EventType.EXPLORER.getValue()) {
                reward += explorerReward;
            }
        }

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            if (world.isSubGoalBlockMet() && world.isSubGoalCoinMet() && world.isSubGoalEnemyMet()) {
                reward += winReward;
            } else {
                reward += 0;
            }

            // reward += distanceToFlag(world.mario);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            reward += timeoutPenalty;
        } else if (world.gameStatus == GameStatus.LOSE) {
            reward += losePenalty;
        }

        return reward;
    }

    public String getRewardInfo() {
        return MessageFormat.format("""
                REWARDS
                WIN {0}
                LOSE {1}
                TIME_OUT {2}
                KILL {3}
                COLLECT_COIN {4}
                HIT_BLOCK {5}
                COLLECT_MUSHROOM {6}
                """,
                winReward,
                losePenalty,
                timeoutPenalty,
                killReward,
                coinReward,
                bumpReward,
                powerUpReward);
    }

}
