package reinforment;

import java.util.Random;

public class ProceduralContentGeneration {
    private static final Random rand = new Random();

    public static String randomAddSingleBlock(String level){
        String[] levelHeightFromTop = level.split("\r\n");
        int indexOfFlag = levelHeightFromTop[12].indexOf('F');
        //var randomHeight = rand.nextInt(9, 12);
        var randomHeight = 10;
        char[] target = levelHeightFromTop[randomHeight].toCharArray();

        int maxIndex = target.length - (levelHeightFromTop[12].length() - indexOfFlag) - 2;

        int replaceIndex = rand.nextInt(0, maxIndex);
        target[replaceIndex] = '!';
        levelHeightFromTop[randomHeight] = new String(target);

        //levelHeightFromTop[13] = randomModifySpawnPoint(levelHeightFromTop[13]);
        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    public static String randomFlag(String level) {
        String[] levelHeightFromTop = level.split("\r\n");
        char[] flagLine = levelHeightFromTop[12].toCharArray();
        char[] playLine = levelHeightFromTop[13].toCharArray();
        int randomFlagPos = rand.nextInt(5,16);
        flagLine[randomFlagPos] = 'F';
        playLine[randomFlagPos] = '#';
        levelHeightFromTop[12] = new String(flagLine);
        levelHeightFromTop[13] = new String(playLine);
        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    private String randomModifySpawnPoint(String levelHeightFromTop){
        int randomSpawn = rand.nextInt(0,8);
        char[] levelMarioSpawn = levelHeightFromTop.toCharArray();
        levelMarioSpawn[randomSpawn] = 'M';
        return new String(levelMarioSpawn);
    }
}
