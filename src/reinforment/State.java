package reinforment;

import engine.core.MarioForwardModel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import static engine.core.MarioForwardModel.*;

public class State {

    private static byte[] intArrayToBytes(int[] input) throws Exception {
        byte[] byteArray = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            byteArray[i] = (byte)input[i];
        }
        return byteArray;
    }

    public static byte[] stepResult(byte[] nextState, float reward, boolean is_terminate) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + nextState.length);
        buffer.put(float2ByteArray(reward));
        buffer.put((byte)(is_terminate ? 1 : 0));
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
        var detail = model.getMarioCompleteObservation(0,0);

        int[] flatState = Arrays.stream(detail)
            .flatMapToInt(Arrays::stream)
            .toArray();


        byte[] state = intArrayToBytes(flatState);

        byte marioMode = (byte) model.getMarioMode();
        byte isMarioOnGround = (byte)(model.isMarioOnGround() ? 1 : 0);
        byte isMarioCanJumpHigher = (byte) (model.getMarioCanJumpHigher() ? 1 : 0);
        byte marioFacing = (byte) (model.getMarioFacing());

        byte velocityXSign = (byte)(model.getMarioFloatVelocity()[0] > 0 ? 1 : 0);
        byte velocityYSign = (byte)(model.getMarioFloatVelocity()[1] > 0 ? 1 : 0);
        byte[] velocityX = float2ByteArray(model.getMarioFloatVelocity()[0]);
        byte[] velocityY = float2ByteArray(model.getMarioFloatVelocity()[1]);



        byte totalSubGoal = (byte) Arrays.stream(model.totalSubGoal()).filter(t -> t > 0).count();
        byte isSubGoalBlockMet = model.isSubGoalBlockMet() == null ? (byte) 255 : (byte) (model.isSubGoalBlockMet() ? 1 : 0);
        byte isSubGoalEnemyMet = model.isSubGoalEnemyMet() == null ? (byte) 255 : (byte) (model.isSubGoalEnemyMet() ? 1 : 0);
        byte isSubGoalCoinMet = model.isSubGoalCoinMet() == null ? (byte) 255 : (byte) (model.isSubGoalCoinMet() ? 1 : 0);
        ByteBuffer buffer = ByteBuffer.allocate(
                state.length
                + 4
                + 10
                + 4
        );

        buffer.put(state);

        buffer.put(marioMode);
        buffer.put(isMarioOnGround);
        buffer.put(isMarioCanJumpHigher);
        buffer.put(marioFacing);

        buffer.put(velocityXSign);
        buffer.put(velocityYSign);
        buffer.put(velocityX);
        buffer.put(velocityY);

//        buffer.put(totalSubGoal);
//        buffer.put(isSubGoalBlockMet);
//        buffer.put(isSubGoalEnemyMet);
//        buffer.put(isSubGoalCoinMet);

        buffer.put(((byte)0));
        buffer.put(((byte)255));
        buffer.put(((byte)255));
        buffer.put(((byte)255));
        return buffer.array();
    }
}
