package reinforcement;

import engine.core.MarioEvent;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private static final float WIN_REWARD = 100f;
    private static final float PARTIAL_WIN = 0f;
    private static final float FAILURE_LOSE = -100f;
    private static final float FAILURE_TIMEOUT = -100f;
    private static final float POWER_UP_REWARD = 5f;

    // Discount used for potential difference (match PPO gamma)
    private static final float SHAPING_GAMMA = 0.997f;

    private static final float OBJECTIVE_SHAPING_CAP = 25f;
    // Weights
    private static final int ENEMY_WEIGHT = 10;
    private static final int BLOCK_WEIGHT = 5;
    private static final int COIN_WEIGHT = 2;

    // Cached totals (fixed for the episode)
    private final int totalWeightedObjectives;   // sum(weight * count) at episode start
    private final float dynamicMaxShapingReward; // a constant cap (≈ total shaping budget)

    // State
    private int prevRemaining;   // weighted remaining objectives

    // Progress shaping (kept as before)
    private static final float STUCK_PENALTY = -10f;

    public RewardSystem(MarioWorld world){
        // Capture initial full counts (do NOT use "alive"/remaining lists here)
        totalWeightedObjectives = computeTotalWeightedObjectives(world);
        dynamicMaxShapingReward = OBJECTIVE_SHAPING_CAP;

        prevRemaining = computeRemainingWeighted(world);      // should equal totalWeightedObjectives initially
    }

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = -0.05f; // step cost

        if (totalWeightedObjectives > 0) {
            int currRemaining = computeRemainingWeighted(world);
            // Normalized potentials in [-1,0]
            float phiPrev = -prevRemaining / (float) totalWeightedObjectives;
            float phiCurr = -currRemaining / (float) totalWeightedObjectives;
            float shapingObj = dynamicMaxShapingReward * (SHAPING_GAMMA * phiCurr - phiPrev);
            if (shapingObj > 3f) shapingObj = 3f;
            if (shapingObj < -3f) shapingObj = -3f;
            reward += shapingObj;
            prevRemaining = currRemaining;
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
            } else if (type == EventType.STUCK.getValue()) {
                reward += STUCK_PENALTY;
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
            } else if (type == EventType.STUCK.getValue()) {
                value = STUCK_PENALTY;
            } else {
                value = 0f;
            }
            rewardEvents.add(new RewardEvent(value, e, timer));
        }
        return rewardEvents;
    }
}