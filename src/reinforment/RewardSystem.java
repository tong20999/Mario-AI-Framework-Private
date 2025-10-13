package reinforment;

import engine.core.MarioEvent;
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
    private static final float POWER_UP_REWARD = 10f;

    // Discount used for potential difference (match PPO gamma)
    private static final float SHAPING_GAMMA = 0.997f;

    // Per-weight-unit reward you want for removing one weighted objective
    // (e.g. enemy removal gives ENEMY_WEIGHT * BASE_PER_WEIGHTED_UNIT)
    private static final float BASE_PER_WEIGHTED_UNIT = 0.5f;

    // Progress shaping (kept as before)
    private static final float PROGRESS_SCALE = 5f;

    // Weights
    private static final int ENEMY_WEIGHT = 15;
    private static final int BLOCK_WEIGHT = 3;
    private static final int COIN_WEIGHT = 1;

    // Cached totals (fixed for the episode)
    private final int totalWeightedObjectives;   // sum(weight * count) at episode start
    private final float dynamicMaxShapingReward; // K = basePerUnit * totalWeightedObjectives

    // State
    private int prevRemaining;   // weighted remaining objectives
    private float prevProgress;  // 0..1

    public RewardSystem(MarioWorld world){
        // Capture initial full counts (do NOT use "alive"/remaining lists here)
        totalWeightedObjectives = computeTotalWeightedObjectives(world);
        dynamicMaxShapingReward = BASE_PER_WEIGHTED_UNIT * totalWeightedObjectives;

        prevRemaining = computeRemainingWeighted(world);      // should equal totalWeightedObjectives initially
        prevProgress = clamp01(getCompletionPercentage(world));
    }

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = -0.002f; // step cost

        if (!world.isEvaluation) {
            // --- Objective shaping with dynamic K ---
            if (totalWeightedObjectives > 0) {
                int currRemaining = computeRemainingWeighted(world);
                // Normalized potentials in [-1,0]
                float phiPrev = -prevRemaining / (float) totalWeightedObjectives;
                float phiCurr = -currRemaining / (float) totalWeightedObjectives;
                float shapingObj = dynamicMaxShapingReward * (SHAPING_GAMMA * phiCurr - phiPrev);
                reward += shapingObj;
                prevRemaining = currRemaining;
            }

            // --- Progress shaping (unchanged) ---
            float currProgress = clamp01(getCompletionPercentage(world));
            float shapingProg = PROGRESS_SCALE * (SHAPING_GAMMA * currProgress - prevProgress);
            reward += shapingProg;
            prevProgress = currProgress;
        }

        // Events
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();
            int param = e.getEventParam();
            if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                reward += POWER_UP_REWARD;
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
            }
        }
        return reward;
    }

    // Weighted remaining that actually changes during play
    private static int computeRemainingWeighted(MarioWorld world){
        int enemiesLeft = world.getAliveEnemies().size() * ENEMY_WEIGHT;
        int blocksLeft  = world.getUnbumpBlocks().size()   * BLOCK_WEIGHT;
        int coinsLeft   = world.getUnCollectCoin().size()  * COIN_WEIGHT;
        return enemiesLeft + blocksLeft + coinsLeft;
    }

    // Initial weighted total (use static level definitions so denominator fixed)
    private static int computeTotalWeightedObjectives(MarioWorld world){
        int enemies = world.level.getEnemies().size()          * ENEMY_WEIGHT;
        int blocks  = world.level.getBumpableBlocks().size()   * BLOCK_WEIGHT;
        int coins   = world.level.getCoins().size()            * COIN_WEIGHT;
        return enemies + blocks + coins;
    }

    private static float calculateWinReward(MarioWorld world) {
        // Optionally require all objectives cleared
        if (computeRemainingWeighted(world) > 0) return PARTIAL_WIN;
        return WIN_REWARD;
    }

    // Horizontal completion 0..1
    private float getCompletionPercentage(MarioWorld world) {
        float goalPixels = world.level.exitTileX * 16f;
        if (goalPixels <= 1f) return 0f;
        float p = world.mario.x / goalPixels;
        return clamp01(p);
    }

    private static float clamp01(float v){
        return v < 0f ? 0f : (Math.min(v, 1f));
    }

    public static ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf"
                : Integer.toString((int) Math.ceil(world.currentTimer / 1000f)));
        for (MarioEvent e : miniStepEvents) {
            float value;
            int type = e.getEventType();
            int param = e.getEventParam();
            if (type == EventType.WIN.getValue()) {
                value = calculateWinReward(world);
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                value = POWER_UP_REWARD;
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
}