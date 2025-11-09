package reinforcement;

import engine.core.MarioEvent;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private static final float WIN_REWARD = 100f;
    private static final float FAILURE_LOSE = -100f;
    private static final float FAILURE_TIMEOUT = -100f;

    public RewardSystem(MarioWorld world) {
    }

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0f;

        // Events
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();
            if (type == EventType.WIN.getValue()) {
                reward += WIN_REWARD;
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
            }
        }
        return reward;
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf"
                : Integer.toString((int) Math.ceil(world.currentTimer / 1000f)));
        for (MarioEvent e : miniStepEvents) {
            float value;
            int type = e.getEventType();
            if (type == EventType.WIN.getValue()) {
                value = WIN_REWARD;
            } else if (type == EventType.LOSE.getValue()) {
                value = FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                value = FAILURE_TIMEOUT;
            }else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }
        return rewardEvents;
    }
}