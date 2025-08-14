package reinforment;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.nio.ByteOrder.BIG_ENDIAN;

public class State {

    private static float maxVelocity = 10.0f;

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte) input[i];
        }
        return byteArray;
    }

    public static byte[] stepResult(byte[] nextState, float reward, boolean is_terminate, boolean is_truncated) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + nextState.length);
        buffer.put(float2ByteArray(reward));
        buffer.put((byte) (is_terminate ? 1 : 0));
        buffer.put((byte) (is_truncated ? 1 : 0));
        buffer.put(nextState);
        return buffer.array();
    }

    public static byte[] float2ByteArray(float value) {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static byte[] int2ByteArray(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static ObservationGrid createObservationGrid(MarioForwardModel model) throws Exception {
        int size = 16;
        var sceneObservation = model.getScreenSceneObservation(1);
        var flagObservation = model.getScreenSceneObservation(0);
        var enemyObservation = model.getScreenEnemiesObservation(1);
        //var visitObservation = model.getVisitHeat();
        int[][] solid = new int[size][size];
        int[][] semiSolid = new int[size][size];
        int[][] flags = new int[size][size];
        int[][] collectible = new int[size][size];
        int[][] blocks = new int[size][size];
        int[][] coins = new int[size][size];
        int[][] stompableEnemy = new int[size][size];
        int[][] unstompableEnemy = new int[size][size];

        for (int col = 0; col < size; col++) {
            for (int row = 0; row < size; row++) {
                int scene = sceneObservation[col][row];
                int enemy = enemyObservation[col][row];
                int flag = flagObservation[col][row];

                if(flag == 40 + 16 || flag == 39 + 16){
                    flags[col][row] = 1;
                }

                if(scene == MarioForwardModel.OBS_SOLID
                        || scene == MarioForwardModel.OBS_CANNON
                        || scene == MarioForwardModel.OBS_PIPE){
                    solid[col][row] = 1;
                }

                if( scene == MarioForwardModel.OBS_BRICK
                        || scene == MarioForwardModel.OBS_PLATFORM)  {
                    semiSolid[col][row] = 1;
                }

                if(scene == MarioForwardModel.OBS_QUESTION_BLOCK){
                    blocks[col][row] = 1;
                }

                if(scene == MarioForwardModel.OBS_COIN){
                    coins[col][row] = 1;
                }

                if(enemy == MarioForwardModel.OBS_SPECIAL_ITEM){
                    collectible[col][row] = 1;
                }

                if(enemy == MarioForwardModel.OBS_STOMPABLE_ENEMY){
                    stompableEnemy[col][row] = 1;
                }

                if(enemy == MarioForwardModel.OBS_NONSTOMPABLE_ENEMY){
                    unstompableEnemy[col][row] = 1;
                }
            }
        }

        int[] flatSolid = Arrays.stream(solid)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatSemiSolid = Arrays.stream(semiSolid)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatCollectible = Arrays.stream(collectible)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatFlags = Arrays.stream(flags)
                .flatMapToInt(Arrays::stream)
                .toArray();

//        int[] flatVisited = Arrays.stream(visitObservation)
//                .flatMapToInt(Arrays::stream)
//                .toArray();

        int[] flatBlocks = Arrays.stream(blocks)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatCoins = Arrays.stream(coins)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatStompableEnemy = Arrays.stream(stompableEnemy)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatUnStompableEnemy = Arrays.stream(unstompableEnemy)
                .flatMapToInt(Arrays::stream)
                .toArray();

        return new ObservationGrid(
                intArrayToBytes(flatSolid),
                intArrayToBytes(flatSemiSolid),
                intArrayToBytes(flatCollectible),
                intArrayToBytes(flatFlags),
                intArrayToBytes(flatBlocks),
                intArrayToBytes(flatCoins),
                intArrayToBytes(flatStompableEnemy),
                intArrayToBytes(flatUnStompableEnemy)
        );
    }

    private static byte[] getMode(MarioForwardModel model){
        byte[] output = new byte[3];
        var mode = model.getMarioMode();
        output[mode] = 1;
        return output;
    }

    public static byte[] toByte(MarioForwardModel model) throws Exception {
        //Grid observation
        var observationGrid = createObservationGrid(model);
        byte[] solid = observationGrid.getSolid();
        byte[] semiSolid =observationGrid.getSemiSolid();
        byte[] collectible =observationGrid.getCollectible();
        byte[] flags = observationGrid.getFlag();
        byte[] blocks = observationGrid.getBlocks();
        byte[] coins = observationGrid.getCoins();
        byte[] stompableEnemy = observationGrid.getStompableEnemy();
        byte[] unstopableEnemy = observationGrid.getUnstompableEnemy();

        byte[] marioMode = getMode(model);
        byte isMarioOnGround = (byte) (model.isMarioOnGround() ? 1 : 0);
        byte isMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1 : 0);
        byte marioFacing = (byte) (model.getMarioFacing() == 1 ? 1 : 0);

//        byte[] isHeadRoomClearance =  float2ByteArray(model.isHeadRoomClearance());
//        byte[] isHeadRoomForwardClearance = float2ByteArray(model.isHeadRoomForwardClearance());
//        byte[] gapAheadDistance = float2ByteArray(model.gapAheadDistance());
//        byte[] riskDensity = model.getRiskDensity();

        //Velocity
        var velocity = model.getMarioFloatVelocity();
        float normalizedVelX = Math.max(-1.0f, Math.min(1.0f, velocity[0] / maxVelocity));
        if (Math.abs(normalizedVelX) < 0.001f) {
            normalizedVelX = 0.0f;
        }
        byte[] velocityX = float2ByteArray(normalizedVelX);

        float normalizedVelY = Math.max(-1.0f, Math.min(1.0f, velocity[1] / maxVelocity));
        if (Math.abs(normalizedVelY) < 0.001f) {
            normalizedVelY = 0.0f;
        }
        byte[] velocityY = float2ByteArray(normalizedVelY);

        // Block
        var nearestBlock = model.getNearestBlockScreenPos();
        var dxNormalize = nearestBlock == null ? 0 : nearestBlock[0];
        var dyNormalize = nearestBlock == null ? 0 : nearestBlock[1];
        var distNormalize =  nearestBlock == null ? 0 : nearestBlock[2];
        byte nearestBlockFound = (byte) (nearestBlock == null ? 0 : 1);
        byte[] nearestBlockDx = float2ByteArray(dxNormalize);
        byte[] nearestBlockDy = float2ByteArray(dyNormalize);
        byte[] nearestBlockDistance = float2ByteArray(distNormalize);

        // Coin
        var nearestCoin = model.getNearestCoinScreenPos();
        dxNormalize = nearestCoin == null ? 0 : nearestCoin[0];
        dyNormalize = nearestCoin == null ? 0 : nearestCoin[1];
        distNormalize =  nearestCoin == null ? 0 : nearestCoin[2];
        byte nearestCoinFound = (byte) (nearestCoin == null ? 0 : 1);
        byte[] nearestCoinDx = float2ByteArray(dxNormalize);
        byte[] nearestCoinDy = float2ByteArray(dyNormalize);
        byte[] nearestCoinDistance = float2ByteArray(distNormalize);

        //Enemy
        var nearestEnemy = model.getNearestAliveEnemyScreenPos();
        dxNormalize = nearestEnemy == null ? 0 : nearestEnemy[0];
        dyNormalize = nearestEnemy == null ? 0 : nearestEnemy[1];
        distNormalize =  nearestEnemy == null ? 0 : nearestEnemy[2];
        byte nearestEnemyFound = (byte) (nearestEnemy == null ? 0 : 1);
        byte[] nearestEnemyDx = float2ByteArray(dxNormalize);
        byte[] nearestEnemyDy = float2ByteArray(dyNormalize);
        byte[] nearestEnemyDistance = float2ByteArray(distNormalize);

        //Timer
        var normalizedTimer = (float) model.getRemainingTime() / (float) model.getInitialTimer();
        byte[] payloadNormalizedTimer = float2ByteArray(normalizedTimer);

        ByteBuffer buffer = ByteBuffer.allocate(
                solid.length +
                        semiSolid.length +
                        collectible.length +
                        flags.length +
                        blocks.length +
                        coins.length +
                        stompableEnemy.length +
                        unstopableEnemy.length +
                        6 +
                        8 + //Velocity
                        13 + // Block
                        13 + // Coin
                        13 + // Enemy
                        4 // Time
        );
        buffer.order(BIG_ENDIAN);

        buffer.put(solid);
        buffer.put(semiSolid);
        buffer.put(collectible);
        buffer.put(flags);
        buffer.put(blocks);
        buffer.put(coins);

        buffer.put(stompableEnemy);
        buffer.put(unstopableEnemy);

        buffer.put(marioMode);
        buffer.put(isMarioOnGround);
        buffer.put(isMarioCanJumpHigher);
        buffer.put(marioFacing);

//        buffer.put(isHeadRoomClearance);
//        buffer.put(isHeadRoomForwardClearance);
//        buffer.put(gapAheadDistance);
//        buffer.put(riskDensity);

        // float
        buffer.put(velocityX);
        buffer.put(velocityY);

        buffer.put(nearestBlockFound);
        buffer.put(nearestBlockDx);
        buffer.put(nearestBlockDy);
        buffer.put(nearestBlockDistance);

        buffer.put(nearestCoinFound);
        buffer.put(nearestCoinDx);
        buffer.put(nearestCoinDy);
        buffer.put(nearestCoinDistance);

        buffer.put(nearestEnemyFound);
        buffer.put(nearestEnemyDx);
        buffer.put(nearestEnemyDy);
        buffer.put(nearestEnemyDistance);

        buffer.put(payloadNormalizedTimer);

        return buffer.array();
    }
}
