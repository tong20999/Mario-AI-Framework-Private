import engine.core.MarioEvent;
import engine.core.MarioForwardModel;
import engine.core.MarioWorld;
import engine.helper.EventType;
import engine.helper.GameStatus;
import engine.helper.SpriteType;

public class RewardSystem {
    static float bonusBlock = 50;
    static float bonusCoin = 50;
    static float bonusKill = 50;
    static float winReward = 50;
    static float mileStoneReward = 0.0f;
    static float jumpOverPitReward = 0f;
    static float killReward = 10;
    static float bumpReward = 10;
    static float coinReward = 10;
    static float fireworkReward = 20;
    static float mushroomReward = 20;
    static float lifeMushroomReward = 20;
    static float loseReward = -20f;
    static float loseTimeoutReward = -20f;
    static float hurtReward = -5f;
    static float hitWallReward = -0.0f;
    static float fallPitReward = -10f;
    static float timePenaltyRewardCoefficient = 0;

    private static float getReward(MarioWorld world, MarioForwardModel nextState, int lastCoinCount) {
        float reward = 0.0f;
        //reward += timePenalty();
        //reward += mileStoneReward();

        // Coin collection reward
        int currentCoins = nextState.getNumCollectedCoins();
        if (currentCoins > lastCoinCount) {
            reward += coinReward; // +0.5 reward per coin
            lastCoinCount = currentCoins;
        }

        // Event-based rewards (kills, power-ups) and penalties (hurt, walls)
        for (MarioEvent e : world.lastFrameEvents) {
            if (e.getEventType() == EventType.STOMP_KILL.getValue() ||
                    e.getEventType() == EventType.FIRE_KILL.getValue() ||
                    e.getEventType() == EventType.SHELL_KILL.getValue()) {
//                var sprintCode = e.getSprintCode();
//                if(sprintCode != null && !this.clearedSpawnPointsThisEpisode.contains(sprintCode)){
//                    clearedSpawnPointsThisEpisode.add(sprintCode);
//                    reward += killReward; // +2 reward per kill
//                }
                reward += killReward;
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.FIRE_FLOWER.getValue()) {
                reward += fireworkReward; // +5 for a power-up
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.MUSHROOM.getValue()) {
                reward += mushroomReward; // +2 for a mushroom
            }
            if (e.getEventType() == EventType.COLLECT.getValue() && e.getEventParam() == SpriteType.LIFE_MUSHROOM.getValue()) {
                reward += lifeMushroomReward; // +5 for a life
            }
            if (e.getEventType() == EventType.HURT.getValue()) {
                reward += hurtReward; // -1 for taking damage
            }
            if (e.getEventType() == EventType.HIT_WALL.getValue()) {
                reward += hitWallReward;
            }
            if (e.getEventType() == EventType.FALL_PIT.getValue()) {
                reward += fallPitReward;
            }

            if (e.getEventType() == EventType.BUMP.getValue()){
                reward += bumpReward;
            }
        }

        checkSubGoalMet();

        // --- 3. Define the outcome: Keep your score or lose it all ---
        if (world.gameStatus == GameStatus.WIN) {
            // The reward for winning is that you get to keep the score you earned.
            // We can add a small bonus to break ties, but the bulk of the score is from the run itself.

            reward += winReward;
            reward += distanceToFlag(nextState);
        } else if (world.gameStatus == GameStatus.TIME_OUT) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseTimeoutReward;
            reward += distanceToFlag(nextState);
        } else if (world.gameStatus == GameStatus.LOSE) {
            // A massive penalty that ensures any failure is always worse than even the "laziest" win.
            reward += loseReward;
            reward += distanceToFlag(nextState);
        }



        return reward;
    }

    private float distanceToFlag(Mario mario, MarioForwardModel model) {
        var complete = this.world.mario.x * 0.01f;
        return complete;
    }
}
