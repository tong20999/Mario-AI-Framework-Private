package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private static final float WIN_REWARD = 1f;

    private static final float PARTIAL_WIN = 0f;

    private static final float FAILURE_LOSE = -1f;
    private static final float FAILURE_TIMEOUT = -1f;


    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0;
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();

            if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
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

            if (type == EventType.WIN.getValue()) {
                value = calculateWinReward(world);
            } else if (type == EventType.LOSE.getValue()) {
                value = FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                value = FAILURE_TIMEOUT;
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
        return WIN_REWARD;
    }
}