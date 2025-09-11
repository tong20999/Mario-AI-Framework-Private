package reinforment;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;

import static java.nio.ByteOrder.BIG_ENDIAN;

public class State {

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

    private static float maxVelocity = 10f;

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte) input[i];
        }
        return byteArray;
    }


    private static byte[] createObservationGrid(MarioForwardModel model) throws Exception {
        int size = 16;
        var sceneObservation = model.getMarioSceneObservation(1);
        var flagObservation = model.getMarioSceneObservation(0);
        var enemyObservation = model.getMarioEnemiesObservation(0);
        var grid = new int[size][size];

        for (int col = 0; col < size; col++) {
            for (int row = 0; row < size; row++) {
                int scene = sceneObservation[col][row];
                int enemy = enemyObservation[col][row];
                int flag = flagObservation[col][row];

                if (flag == 40 + 16 || flag == 39 + 16) {
                    grid[col][row] = 8;
                }

                if (scene == MarioForwardModel.OBS_SOLID
                        || scene == MarioForwardModel.OBS_CANNON
                        || scene == MarioForwardModel.OBS_PIPE
                        || scene == MarioForwardModel.OBS_PLATFORM
                ) {
                    grid[col][row] = 1;
                } else if (scene == MarioForwardModel.OBS_QUESTION_BLOCK) {
                    grid[col][row] = 3;
                } else if(scene == MarioForwardModel.OBS_BRICK){
                    grid[col][row] = 2;
                } else if (scene == MarioForwardModel.OBS_COIN) {
                    grid[col][row] = 4;
                }

                if (enemy == MarioForwardModel.OBS_MUSHROOM) {
                    grid[col][row] = 5;
                } else if(enemy == MarioForwardModel.OBS_LIFE_MUSHROOM){
                    grid[col][row] = 6;
                } else if(enemy == MarioForwardModel.OBS_FIRE_FLOWER){
                    grid[col][row] = 7;
                } else if (enemy == MarioForwardModel.OBS_GOOMBA) {
                    grid[col][row] = 8;
                } else if(enemy == MarioForwardModel.OBS_GOOMBA_WINGED){
                    grid[col][row] = 9;
                } else if(enemy == MarioForwardModel.OBS_GREEN_KOOPA){
                    grid[col][row] = 10;
                } else if(enemy == MarioForwardModel.OBS_GREEN_KOOPA_WINGED){
                    grid[col][row] = 11;
                } else if(enemy == MarioForwardModel.OBS_RED_KOOPA){
                    grid[col][row] = 12;
                } else if(enemy == MarioForwardModel.OBS_RED_KOOPA_WINGED){
                    grid[col][row] = 13;
                } else if(enemy == MarioForwardModel.OBS_SPIKY){
                    grid[col][row] = 14;
                } else if(enemy == MarioForwardModel.OBS_SPIKY_WINGED){
                    grid[col][row] = 15;
                } else if(enemy == MarioForwardModel.OBS_ENEMY_FLOWER){
                    grid[col][row] = 16;
                } else if(enemy == MarioForwardModel.OBS_SHELL){
                    grid[col][row] = 17;
                } else if(enemy == MarioForwardModel.OBS_BULLET_BILL){
                    grid[col][row] = 18;
                } else if(enemy == MarioForwardModel.OBS_FIREBALL) {
                    grid[col][row] = 19;
                }
            }
        }

        var flatGrid = Arrays.stream(grid).flatMapToInt(Arrays::stream).toArray();

        return intArrayToBytes(flatGrid);
    }

    private static byte[] getMode(MarioForwardModel model) {
        byte[] output = new byte[3];
        var mode = model.getMarioMode();
        if (mode >= 0 && mode < 3) {
            output[mode] = 1;
        }
        return output;
    }

    public static byte[] toByte(MarioForwardModel model) throws Exception {


        // Observation grid
        var observationGridPayload = createObservationGrid(model);

        // Mario state
        byte[] marioMode = getMode(model);

        // Velocity
        var velocity = model.getMarioFloatVelocity();
        float normalizedVelX = Math.max(-1.0f, Math.min(1.0f, velocity[0] / maxVelocity));
        if (Math.abs(normalizedVelX) < 0.001f) {
            normalizedVelX = 0.0f;
        }
        float normalizedVelY = Math.max(-1.0f, Math.min(1.0f, velocity[1] / maxVelocity));
        if (Math.abs(normalizedVelY) < 0.001f) {
            normalizedVelY = 0.0f;
        }

        // Sub-tile position
        float[] marioPos = model.getMarioFloatPos();
        float subTileX = (marioPos[0] / 16.0f) - (int) (marioPos[0] / 16);
        float subTileY = (marioPos[1] / 16.0f) - (int) (marioPos[1] / 16);

        // Nearest Block
        var nearestBlock = model.getNearestBlockScreenPos();
        byte nearestBlockFound = (byte) (nearestBlock != null ? 1 : 0);
        float nearestBlockDx = nearestBlock != null ? nearestBlock[0] : 0.0f;
        float nearestBlockDy = nearestBlock != null ? nearestBlock[1] : 0.0f;

        // Nearest Coin
        var nearestCoin = model.getNearestCoinScreenPos();
        byte nearestCoinFound = (byte) (nearestCoin != null ? 1 : 0);
        float nearestCoinDx = nearestCoin != null ? nearestCoin[0] : 0.0f;
        float nearestCoinDy = nearestCoin != null ? nearestCoin[1] : 0.0f;

        // Nearest Enemy
        var nearestEnemy = model.getNearestAliveEnemyScreenPos();
        byte nearestEnemyFound = (byte) (nearestEnemy != null ? 1 : 0);
        float nearestEnemyDx = nearestEnemy != null ? nearestEnemy[0] : 0.0f;
        float nearestEnemyDy = nearestEnemy != null ? nearestEnemy[1] : 0.0f;

        // Timer
        float normalizedTimer = (float) model.getRemainingTime() / (float) model.getInitialTimer();

        // Objective
        float normalizedCoinsLeft = model.getTotalCoins() > 0
                ? (float) model.getUnCollectCoin() / (float) model.getTotalCoins()
                : 0.0f;
        byte goalCoinReach = (byte) (model.getUnCollectCoin() == 0 ? 1 : 0);

        // Enemy Task
        float normalizedEnemiesLeft = model.getTotalEnemies() > 0
                ? (float) model.getAliveEnemies() / (float) model.getTotalEnemies()
                : 0.0f;
        byte goalEnemyReach = (byte) (model.getAliveEnemies() == 0 ? 1 : 0);

        // Block Task
        float normalizedBlocksLeft = model.getTotalBlocks() > 0
                ? (float) model.getUnbumpBlocks() / (float) model.getTotalBlocks()
                : 0.0f;
        byte goalBlockReach = (byte) (model.getUnbumpBlocks() == 0 ? 1 : 0);

        // Completion percentage
        float completionPercentage = model.getCompletionPercentage();

        // Write all features to the output stream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(observationGridPayload); // 256
        outputStream.write(marioMode);
        outputStream.write((byte) (model.isMarioOnGround() ? 1 : 0));
        outputStream.write((byte) (model.getMarioCanJumpHigher() ? 1 : 0));
        outputStream.write((byte) (model.getMarioFacing() == 1 ? 1 : 0)); // 6
        outputStream.write(float2ByteArray(normalizedVelX));
        outputStream.write(float2ByteArray(normalizedVelY));
        outputStream.write(float2ByteArray(subTileX));
        outputStream.write(float2ByteArray(subTileY)); // 22

        outputStream.write(nearestBlockFound);
        outputStream.write(float2ByteArray(nearestBlockDx));
        outputStream.write(float2ByteArray(nearestBlockDy));

        outputStream.write(nearestCoinFound);
        outputStream.write(float2ByteArray(nearestCoinDx));
        outputStream.write(float2ByteArray(nearestCoinDy));

        outputStream.write(nearestEnemyFound);
        outputStream.write(float2ByteArray(nearestEnemyDx));
        outputStream.write(float2ByteArray(nearestEnemyDy)); // 49

        outputStream.write(float2ByteArray(normalizedTimer)); // 53

        outputStream.write(float2ByteArray(normalizedCoinsLeft));
        outputStream.write(goalCoinReach);
        outputStream.write(float2ByteArray(normalizedEnemiesLeft));
        outputStream.write(goalEnemyReach);
        outputStream.write(float2ByteArray(normalizedBlocksLeft));
        outputStream.write(goalBlockReach); // 68

        outputStream.write(float2ByteArray(completionPercentage)); //72
        return outputStream.toByteArray();
    }
}