package reinforment;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class State {

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte)input[i];
        }
        return byteArray;
    }

    public static byte[] stepResult(byte[] nextState, float reward, boolean is_terminate, boolean is_truncated) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + nextState.length);
        buffer.put(float2ByteArray(reward));
        buffer.put((byte)(is_terminate ? 1 : 0));
        buffer.put((byte)(is_truncated ? 1 : 0));
        buffer.put(nextState);
        return buffer.array();
    }

    public static byte [] float2ByteArray (float value)
    {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static byte [] int2ByteArray (int value)
    {
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
        byte isMarioOnGround = (byte)(model.isMarioOnGround() ? 1 : 0);
        byte isMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1 : 0);
        byte marioFacing = (byte) (model.getMarioFacing());

        byte velocityXSign = (byte)(model.getMarioFloatVelocity()[0] > 0 ? 1 : 0);
        byte velocityYSign = (byte)(model.getMarioFloatVelocity()[1] > 0 ? 1 : 0);
        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);

        byte marioTileX = (byte)model.getMarioScreenTilePos()[0];
        byte marioTileY = (byte)model.getMarioScreenTilePos()[1];

        byte isSubGoalBlockMet = (byte)(model.isSubGoalBlockMet() ? 1 : 0);
        byte isSubGoalEnemyMet = (byte)(model.isSubGoalEnemyMet() ? 1 : 0);
        byte isSubGoalCoinMet = (byte)(model.isSubGoalCoinMet() ? 1 : 0);

        ByteBuffer buffer = ByteBuffer.allocate(
                sceneObservationPayload.length +
                        enemiesObservationPayload.length
                + 4
                + 10
                + 2
                + 3
        );

        buffer.put(sceneObservationPayload);
        buffer.put(enemiesObservationPayload);
        buffer.put(marioMode);
        buffer.put(isMarioOnGround);
        buffer.put(isMarioCanJumpHigher);
        buffer.put(marioFacing);

        buffer.put(velocityXSign);
        buffer.put(velocityYSign);
        buffer.put(velocityX);
        buffer.put(velocityY);

        buffer.put(marioTileX);
        buffer.put(marioTileY);

        buffer.put(isSubGoalBlockMet);
        buffer.put(isSubGoalEnemyMet);
        buffer.put(isSubGoalCoinMet);
        return buffer.array();
    }
}
