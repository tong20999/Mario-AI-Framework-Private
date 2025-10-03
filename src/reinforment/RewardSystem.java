package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;
import java.util.WeakHashMap;

public class RewardSystem {
    private static final float WIN_REWARD = 100f;

    private static final float PARTIAL_WIN = 0f;

    private static final float FAILURE_LOSE = -100f;
    private static final float FAILURE_TIMEOUT = -100f;

    // Potential-based shaping parameters
    // Phi(s) = -(remainingEnemies + remainingCoins + remainingBlocks)
    // r_shape = K * (gamma * Phi(s') - Phi(s))
    // Note: Keep K modest so per-progress signal aids learning without dwarfing terminal rewards.
    private static final float SHAPING_K = 3.0f;      // tune 2.0–5.0
    private static final float SHAPING_GAMMA = 0.99f; // match PPO gamma if possible

    // Track previous remaining objectives per world (auto-removed when world GC'ed)
    private static final WeakHashMap<MarioWorld, Integer> prevRemainingMap = new WeakHashMap<>();


    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = 0f;

        // Potential-based shaping (training-only)
        // Compute before handling terminal events; world state already reflects this step's updates.
        int currRemaining = remainingObjectives(world);
        int prevRemaining = prevRemainingMap.getOrDefault(world, currRemaining);
        if (!world.isEvaluation) {
            float phiPrev = -prevRemaining;
            float phiCurr = -currRemaining;
            float shaping = SHAPING_K * (SHAPING_GAMMA * phiCurr - phiPrev);
            reward += shaping;
        }
        // Update tracker for next step
        prevRemainingMap.put(world, currRemaining);

        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();

            if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
                // Episode ended, cleanup tracker
                prevRemainingMap.remove(world);
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
                prevRemainingMap.remove(world);
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
                prevRemainingMap.remove(world);
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