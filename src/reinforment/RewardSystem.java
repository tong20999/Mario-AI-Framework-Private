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

    // The total shaping reward an agent receives for completing 100% of a level's
    // sub-goals (enemies, coins, etc.). This value now acts as the scaling factor 'K'.
    private static final float MAX_SHAPING_REWARD = 25.0f;
    private static final float SHAPING_GAMMA = 0.999f; // Match PPO gamma if possible

    // Weights for each objective to define their relative importance.
    private static final int ENEMY_WEIGHT = 15;
    private static final int BLOCK_WEIGHT = 3;
    private static final int COIN_WEIGHT = 1;



    // Track previous remaining objectives per world. This is still needed.
    private static final WeakHashMap<MarioWorld, Integer> prevRemainingMap = new WeakHashMap<>();


    public static float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = -0.002f;

        // --- Potential-based shaping (training-only) ---
        if (!world.isEvaluation) {
            // Step 1: Calculate the level's total potential dynamically on each step.
            // This replaces the need for a cache/map.
            int maxPotential = totalObjective(world);

            if (maxPotential > 0) {
                // Step 2: Calculate current and previous potential.
                int currRemaining = remainingObjectives(world);
                int prevRemaining = prevRemainingMap.getOrDefault(world, currRemaining);

                // Step 3: Normalize potential to a range of [-1, 0].
                float phiPrev = -prevRemaining / (float) maxPotential;
                float phiCurr = -currRemaining / (float) maxPotential;

                // Step 4: Calculate the shaping reward.
                float shaping = MAX_SHAPING_REWARD * (SHAPING_GAMMA * phiCurr - phiPrev);
                reward += shaping;

                // Update tracker for the next step.
                prevRemainingMap.put(world, currRemaining);
            }
        }

        // --- Terminal Rewards (Win/Loss/Timeout) ---
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();
            int param = e.getEventParam();
            if (type == EventType.WIN.getValue()) {
                reward += calculateWinReward(world);
                prevRemainingMap.remove(world); // Episode ended, cleanup tracker.
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                reward += POWER_UP_REWARD;
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
        int enemiesLeft = world.getAliveEnemies().size() * ENEMY_WEIGHT;
        int blocksLeft = world.getUnbumpBlocks().size() * BLOCK_WEIGHT;
        int coinsLeft = world.getUnCollectCoin().size() * COIN_WEIGHT;
        return enemiesLeft + blocksLeft + coinsLeft;
    }

    private static int totalObjective(MarioWorld world) {
        int enemies = world.level.getEnemies().size() * ENEMY_WEIGHT;
        int blocks = world.level.getBumpableBlocks().size() * BLOCK_WEIGHT;
        int coins = world.level.getCoins().size() * COIN_WEIGHT;
        return enemies + blocks + coins;
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

    private static float calculateWinReward(MarioWorld world) {
        if(remainingObjectives(world) > 0){
            return PARTIAL_WIN;
        }
        return WIN_REWARD;
    }
}