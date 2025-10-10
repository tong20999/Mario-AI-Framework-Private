package reinforment;

import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.SpriteType;

import java.util.ArrayList;

public class RewardSystem {
    private static final float WIN_REWARD = 100f;

    private static final float PARTIAL_WIN = 0f;

    private static final float FAILURE_LOSE = -100f;
    private static final float FAILURE_TIMEOUT = -100f;

    private static final float POWER_UP_REWARD = 10f;

    private static final float KILL_REWARD = 5f;
    private static final float BUMP_REWARD = 2f;
    private static final float COIN_REWARD = 1f;

    // Progress shaping scale K: Φ_progress(s) = K * (1 - remaining/total)
    // Keep small so per-step shaping stays a nudge.
    private static final float PROGRESS_K          = 15.0f;

    // Weights for each objective to define their relative importance.
    private static final int ENEMY_WEIGHT = 15;
    private static final int BLOCK_WEIGHT = 3;
    private static final int COIN_WEIGHT = 1;

    private static final int   TILE_SIZE          = 16;
    // Scale of potential per tile (keep small so shaping <~5–10% of a win total)
    private static final float PHI_LAMBDA         = 0.05f;
    private static final float PBS_GAMMA          = 0.999f;
    // Overall shaping scale (usually 1.0). We also clip per-step below.
    private static final float PBS_BETA           = 1.0f;
    // Clip shaping each step so it can’t dominate (if win=+100, ±0.5 is a good cap)
    private static final float PBS_PER_STEP_CLIP  = 0.5f;
    // Combine progress and directional potentials: Φ = α*Φ_progress + (1-α)*Φ_dir
    private static final float PBS_ALPHA          = 0.8f;
    // ------- Per-episode state (per env/worker) -------
    private float prevPhi   = 0f;
    private boolean phiInit = false;

    public float getReward(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        float reward = -0.002f;

        if (!world.isEvaluation) {
            if (!phiInit) {
                this.prevPhi = computePotential(world);
                this.phiInit = true;
            } else {
                final float newPhi = computePotential(world);
                float shaping = PBS_BETA * (PBS_GAMMA * newPhi - this.prevPhi);

                // Clip per step so it stays a nudge
                if (shaping > PBS_PER_STEP_CLIP)  shaping = PBS_PER_STEP_CLIP;
                if (shaping < -PBS_PER_STEP_CLIP) shaping = -PBS_PER_STEP_CLIP;

                this.prevPhi = newPhi;
                reward += shaping;
            }
        }

        // --- Terminal Rewards (Win/Loss/Timeout) ---
        for (MarioEvent e : miniStepEvents) {
            int type = e.getEventType();
            int param = e.getEventParam();
            if (type == EventType.STOMP_KILL.getValue() ||
                    type == EventType.FIRE_KILL.getValue() ||
                    type == EventType.SHELL_KILL.getValue() ||
                    type == EventType.BUMP_KILL.getValue() ||
                    type == EventType.FALL_KILL.getValue()) {
                reward += KILL_REWARD;
            } else if (type == EventType.COLLECT.getValue() &&
                    (param == SpriteType.FIRE_FLOWER.getValue() || param == SpriteType.MUSHROOM.getValue())) {
                reward += POWER_UP_REWARD;
            } else if (type == EventType.BUMP.getValue() &&
                    param == MarioForwardModel.OBS_QUESTION_BLOCK) {
                reward += BUMP_REWARD;
            } else if (type == EventType.COLLECT.getValue() && param == 15) {
                reward += COIN_REWARD;
            } else if (type == EventType.WIN.getValue()) {
                reward += WIN_REWARD;
            } else if (type == EventType.LOSE.getValue()) {
                reward += FAILURE_LOSE;
            } else if (type == EventType.TIME_OUT.getValue()) {
                reward += FAILURE_TIMEOUT;
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

    public ArrayList<RewardEvent> logRewardEvent(MarioWorld world, ArrayList<MarioEvent> miniStepEvents) {
        ArrayList<RewardEvent> rewardEvents = new ArrayList<>();
        String timer = (world.currentTimer == -1 ? "Inf"
                : Integer.toString((int) Math.ceil(world.currentTimer / 1000f)));
        for (MarioEvent e : miniStepEvents) {
            float value;
            int type = e.getEventType();
            int param = e.getEventParam();
            if (type == EventType.WIN.getValue()) {
                value = WIN_REWARD;
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

    // Combined potential used for shaping.
    // Φ(s) = α * Φ_progress(s) + (1-α) * Φ_directional(s)
    private static float computePotential(MarioWorld world) {
        float phiProgress = computeProgressPotential(world);
        float phiDirectional = computeDirectionalPotential(world);
        return PBS_ALPHA * phiProgress + (1f - PBS_ALPHA) * phiDirectional;
    }

    // Encourages completing sub-goals (coins/blocks/enemies). Monotonic w.r.t. progress.
    private static float computeProgressPotential(MarioWorld world) {
        int total = totalObjective(world);
        if (total <= 0) return 0f;
        int remaining = remainingObjectives(world);
        float progress = 1f - ((float) remaining / (float) total);
        return PROGRESS_K * progress;
    }

    // Encourages moving toward the nearest unfinished objective ahead of Mario.
    private static float computeDirectionalPotential(MarioWorld world) {
        // Mario's tile X in camera space
        final int marioTileX = (int)(world.mario.x / TILE_SIZE);

        // Scan all *unfinished* objectives and keep the minimum dx ahead.
        int nearestDxTiles = Integer.MAX_VALUE;

        // ---- COINS not yet collected ----
        // TODO: adapt to your actual API. Examples:
        // for (Coin c : world.getCoins()) { if (!c.collected) { ... } }
        for (var coin : world.getUnCollectCoin()) {
            int dx = coin.getX() - marioTileX;
            if (dx > 0 && dx < nearestDxTiles) nearestDxTiles = dx;
        }

        // ---- BLOCKS not yet hit ----
        for (var block : world.getUnbumpBlocks()) {
            int dx = block.getX() - marioTileX;
            if (dx > 0 && dx < nearestDxTiles) nearestDxTiles = dx;
        }

        // ---- ENEMIES still alive ----
        for (var enemy : world.getNearestEnemies()) {
            int dx = (int)((enemy.x / TILE_SIZE) - (world.mario.x/ TILE_SIZE));
            if (dx > 0 && dx < nearestDxTiles) nearestDxTiles = dx;
        }

        if (nearestDxTiles == Integer.MAX_VALUE) {
            // nothing ahead → potential = 0 (no shaping)
            return 0f;
        }

        // Potential increases as we get closer:  Φ(s) = -λ * nearest_dx_tiles
        // (More negative far away; increases toward 0 as we approach)
        return -PHI_LAMBDA * (float) nearestDxTiles;
    }
}