package reinforment;

import engine.core.MarioForwardModel;
import engine.core.MarioWorld;

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
        var sceneObservation = model.getScreenSceneObservation(1);
        var flagObservation = model.getScreenSceneObservation(0);
        var enemyObservation = model.getScreenEnemiesObservation(0);
        var grid = new int[size][size];

        for (int col = 0; col < size; col++) {
            for (int row = 0; row < size; row++) {
                int scene = sceneObservation[col][row];
                int enemy = enemyObservation[col][row];
                int flag = flagObservation[col][row];

                if (flag == 40 + 16 || flag == 39 + 16) {
                    grid[col][row] = 1;
                }

                if (scene == MarioForwardModel.OBS_SOLID
                        || scene == MarioForwardModel.OBS_PLATFORM
                ) {
                    grid[col][row] = 2;
                } else if(scene == MarioForwardModel.OBS_PIPE){
                    grid[col][row] = 18;
                } else if(scene == MarioForwardModel.OBS_CANNON){
                    grid[col][row] = 19;
                } else if(scene == MarioForwardModel.OBS_BRICK){
                    grid[col][row] = 3;
                } else if (scene == MarioForwardModel.OBS_QUESTION_BLOCK) {
                    grid[col][row] = 4;
                } else if (scene == MarioForwardModel.OBS_COIN) {
                    grid[col][row] = 5;
                }

                if (enemy == MarioForwardModel.OBS_MUSHROOM) {
                    grid[col][row] = 6;
                } else if(enemy == MarioForwardModel.OBS_LIFE_MUSHROOM){
                    grid[col][row] = 6;
                } else if(enemy == MarioForwardModel.OBS_FIRE_FLOWER){
                    grid[col][row] = 7;
                } else if (enemy == MarioForwardModel.OBS_GOOMBA) {
                    grid[col][row] = 9;
                } else if(enemy == MarioForwardModel.OBS_GOOMBA_WINGED){
                    grid[col][row] = 10;
                } else if(enemy == MarioForwardModel.OBS_GREEN_KOOPA){
                    grid[col][row] = 11;
                } else if(enemy == MarioForwardModel.OBS_GREEN_KOOPA_WINGED){
                    grid[col][row] = 12;
                } else if(enemy == MarioForwardModel.OBS_RED_KOOPA){
                    grid[col][row] = 11;
                } else if(enemy == MarioForwardModel.OBS_RED_KOOPA_WINGED){
                    grid[col][row] = 12;
                } else if(enemy == MarioForwardModel.OBS_SPIKY){
                    grid[col][row] = 13;
                } else if(enemy == MarioForwardModel.OBS_SPIKY_WINGED){
                    grid[col][row] = 14;
                } else if(enemy == MarioForwardModel.OBS_ENEMY_FLOWER){
                    grid[col][row] = 15;
                } else if(enemy == MarioForwardModel.OBS_SHELL){
                    grid[col][row] = 16;
                } else if(enemy == MarioForwardModel.OBS_BULLET_BILL){
                    grid[col][row] = 17;
                }
            }
        }

        var mario = model.getMarioScreenTilePos();
        if(mario[0] >= 16) mario[0] = 15;
        if(mario[1] >= 16) mario[1] = 15;
        if(mario[0] <= 0) mario[0] = 0;
        if(mario[1] <= 0) mario[1] = 0;

        grid[mario[0]][mario[1]] = 20;
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

        // Sub-tile position
        float[] marioPos = model.getMarioFloatPos();
        float subTileX = (marioPos[0] / 16.0f) - (int) (marioPos[0] / 16);
        float subTileY = (marioPos[1] / 16.0f) - (int) (marioPos[1] / 16);

        // Timer
        float normalizedTimer = (float) model.getRemainingTime() / (float) model.getInitialTimer();

        float completionCoinObjective = model.getCoinCompletionObjective();
        float completionBlockObjective = model.getBlocksCompletionObjective();
        float completionEnemiesObjective = model.getEnemiesCompletionObjective();

        // Completion percentage
        float completionPercentage = model.getCompletionPercentage();

        int[] coinsObjective = model.getCoinsObjective();
        int[] blocksObjective = model.getBlocksObjective();
        int[] enemiesObjective = model.getEnemiesObjective();

        // Write all features to the output stream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(observationGridPayload); // 256
        outputStream.write(marioMode); // one hot small, large, fire
        outputStream.write((byte) (model.isMarioOnGround() ? 1 : 0));
        outputStream.write((byte) (model.getMarioCanJumpHigher() ? 1 : 0));
        outputStream.write((byte) (model.getMarioFacing() == 1 ? 1 : 0)); // 6
        outputStream.write(float2ByteArray(subTileX)); // 10
        outputStream.write(float2ByteArray(subTileY)); // 14
        outputStream.write(float2ByteArray(normalizedTimer)); // 18
        outputStream.write(float2ByteArray(completionCoinObjective)); // 22
        outputStream.write(float2ByteArray(completionBlockObjective)); // 26
        outputStream.write(float2ByteArray(completionEnemiesObjective)); // 30
        outputStream.write(float2ByteArray(completionPercentage)); // 34

        outputStream.write(intArrayToBytes(coinsObjective)); // 64
        outputStream.write(intArrayToBytes(blocksObjective)); // 94
        outputStream.write(intArrayToBytes(enemiesObjective)); // 124
        return outputStream.toByteArray();
    }
}