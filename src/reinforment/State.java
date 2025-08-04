package reinforment;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;

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

    public static byte[] toByte(MarioForwardModel model) throws Exception {
        var sceneObservation = model.getScreenSceneObservation(0);
        var enemiesObservation = model.getScreenEnemiesObservation(0);

        int[] flatSceneObservation = Arrays.stream(sceneObservation)
                .flatMapToInt(Arrays::stream)
                .toArray();

        int[] flatEnemiesObservation = Arrays.stream(enemiesObservation)
                .flatMapToInt(Arrays::stream)
                .toArray();

        byte[] sceneObservationPayload = intArrayToBytes(flatSceneObservation);
        byte[] enemiesObservationPayload = intArrayToBytes(flatEnemiesObservation);

        byte marioMode = (byte) model.getMarioMode();
        byte isMarioOnGround = (byte) (model.isMarioOnGround() ? 1 : 0);
        byte isMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1 : 0);
        byte marioFacing = (byte) (model.getMarioFacing());

        var velocity = model.getMarioFloatVelocity();
        byte velocityXSign = (byte) (velocity[0] > 0 ? 1 : 0);
        byte velocityYSign = (byte) (velocity[1] > 0 ? 1 : 0);

        float normalizedVelX = Math.max(-1.0f, Math.min(1.0f, velocity[0] / maxVelocity));
        if(Math.abs(normalizedVelX) < 0.001f){
            normalizedVelX = 0.0f;
        }
        byte[] velocityX = float2ByteArray(normalizedVelX);

        float normalizedVelY = Math.max(-1.0f, Math.min(1.0f, velocity[1] / maxVelocity));
        if(Math.abs(normalizedVelY) < 0.001f){
            normalizedVelY = 0.0f;
        }
        byte[] velocityY = float2ByteArray(normalizedVelY);

        byte isSubGoalBlockMet = (byte) (model.isSubGoalBlockMet() ? 1 : 0);
        byte isSubGoalEnemyMet = (byte) (model.isSubGoalEnemyMet() ? 1 : 0);
        byte isSubGoalCoinMet = (byte) (model.isSubGoalCoinMet() ? 1 : 0);

        byte[] percentComplete = float2ByteArray(model.getCompletionPercentage());

        float[] pos = model.getMarioFloatPos();
        float[] levelDims = model.getLevelFloatDimensions(); // [width, height]
        float normalizedWorldX = pos[0] / levelDims[0];
        float normalizedWorldY = pos[1] / levelDims[1];
        byte[] marioWorldXPayload = float2ByteArray(normalizedWorldX);
        byte[] marioWorldYPayload = float2ByteArray(normalizedWorldY);

        var normalizedTimer = (float) model.getRemainingTime() / (float) model.getInitialTimer();
        byte[] payloadNormalizedTimer = float2ByteArray(normalizedTimer);

        ByteBuffer buffer = ByteBuffer.allocate(
                sceneObservationPayload.length +
                        enemiesObservationPayload.length
                        + 4
                        + 10
                        + 3
                        + 4
                        + 8
                        + 4);

        buffer.put(sceneObservationPayload);
        buffer.put(enemiesObservationPayload);

        // single byte
        buffer.put(marioMode);
        buffer.put(isMarioOnGround);
        buffer.put(isMarioCanJumpHigher);
        buffer.put(marioFacing);
        buffer.put(velocityXSign);
        buffer.put(velocityYSign);
        buffer.put(isSubGoalBlockMet);
        buffer.put(isSubGoalEnemyMet);
        buffer.put(isSubGoalCoinMet);

        // float
        buffer.put(velocityX);
        buffer.put(velocityY);
        buffer.put(percentComplete);
        buffer.put(marioWorldXPayload);
        buffer.put(marioWorldYPayload);
        buffer.put(payloadNormalizedTimer);
        return buffer.array();
    }
}
