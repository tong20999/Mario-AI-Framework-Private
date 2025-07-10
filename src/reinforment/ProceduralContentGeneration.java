package reinforment;

import java.util.ArrayList;
import java.util.Random;

public class ProceduralContentGeneration {
    private static final Random rand = new Random();

    private static final int LAN_LEVEL = 13;

    private static final int GROUND_1_LEVEL = 14;

    private static final int GROUND_2_LEVEL = 15;

    private static final int FLAG_LEVEL = 12;

    public static String replaceSingleBlock(String level){
        return replaceSingleBlock(level,1);
    }

    public static String replaceSingleBlock(String level, int min){
        return replaceSingleBlock(level, min, 2);
    }

    public static String replaceSingleBlock(String level, int min, int minFromFlag){
        return replaceSingleBlock(level, min, minFromFlag, 10);
    }

    public static String replaceSingleBlock(String level, int min, int minFromFlag, int height){
        if(min == 0){
            min = 1;
        }
        String[] levelHeightFromTop = level.split("\r\n");
        int indexOfFlag = levelHeightFromTop[FLAG_LEVEL].indexOf('F');
        char[] target = levelHeightFromTop[height].toCharArray();

        int maxIndex = target.length - (levelHeightFromTop[FLAG_LEVEL].length() - indexOfFlag) - minFromFlag;
        int replaceIndex = rand.nextInt(min, maxIndex);

        while (true){
            char[] f = levelHeightFromTop[GROUND_1_LEVEL].toCharArray();
            if(f[replaceIndex] == 'X'){
                break;
            }
            replaceIndex = rand.nextInt(min, maxIndex);
        }

        target[replaceIndex] = '!';
        levelHeightFromTop[height] = new String(target);

        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    public static String randomFlag(String level) {
        String[] levelHeightFromTop = level.split("\r\n");
        char[] flagLine = levelHeightFromTop[FLAG_LEVEL].toCharArray();
        char[] landLevel = levelHeightFromTop[LAN_LEVEL].toCharArray();
        int randomFlagPos = rand.nextInt(5,16);
        flagLine[randomFlagPos] = 'F';
        landLevel[randomFlagPos] = '#';
        levelHeightFromTop[FLAG_LEVEL] = new String(flagLine);
        levelHeightFromTop[LAN_LEVEL] = new String(landLevel);
        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    public static String replaceSingleEnemy(String level, int min, int minFromFlag) {
        if(min < 5){
            min = 5;
        }

        String[] levelHeightFromTop = level.split("\r\n");
        int indexOfFlag = levelHeightFromTop[FLAG_LEVEL].indexOf('F');
        char[] target = levelHeightFromTop[LAN_LEVEL].toCharArray();

        int maxIndex = target.length - (levelHeightFromTop[FLAG_LEVEL].length() - indexOfFlag) - minFromFlag;
        int replaceIndex = rand.nextInt(min, maxIndex);

        while (true){
            char[] f = levelHeightFromTop[GROUND_1_LEVEL].toCharArray();
            if(f[replaceIndex] == 'X'){
                break;
            }
            replaceIndex = rand.nextInt(min, maxIndex);
        }

        target[replaceIndex] = 'g';
        levelHeightFromTop[LAN_LEVEL] = new String(target);
        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    public static String replacePit(String level, int min, int minFromFlag,int minWidth, int maxWidth) {
        if(min < 5){
            min = 5;
        }

        if(minWidth < 1){
            minWidth = 1;
        }

        int randomWidth = rand.nextInt(minWidth, 7);
        if(randomWidth > maxWidth){
            randomWidth = maxWidth;
        }

        String[] levelHeightFromTop = level.split("\r\n");
        int indexOfFlag = levelHeightFromTop[FLAG_LEVEL].indexOf('F');
        char[] ground1 = levelHeightFromTop[GROUND_1_LEVEL].toCharArray();
        char[] ground2 = levelHeightFromTop[GROUND_2_LEVEL].toCharArray();

        int maxIndex = ground1.length - (levelHeightFromTop[FLAG_LEVEL].length() - indexOfFlag) - minFromFlag;
        int replaceIndexStart = rand.nextInt(min, maxIndex);

        for (int i = replaceIndexStart; i < replaceIndexStart + randomWidth; i++) {
            ground1[i] = '-';
            ground2[i] = '-';
        }

        levelHeightFromTop[GROUND_1_LEVEL] = new String(ground1);
        levelHeightFromTop[GROUND_2_LEVEL] = new String(ground2);
        var combineBack = String.join("\r\n", levelHeightFromTop);
        return combineBack;
    }

    public static String replacePipe(String level, int min, int minFromFlag) {
        int randomHeight = rand.nextInt(2, 5);
        String[] levelHeightFromTop = level.split("\r\n");
        int indexOfFlag = levelHeightFromTop[FLAG_LEVEL].indexOf('F');

        ArrayList<char[]> levels = new ArrayList<>();
        levels.add(levelHeightFromTop[LAN_LEVEL].toCharArray());
        levels.add(levelHeightFromTop[LAN_LEVEL - 1].toCharArray());
        levels.add(levelHeightFromTop[LAN_LEVEL - 2].toCharArray());
        levels.add(levelHeightFromTop[LAN_LEVEL - 3].toCharArray());
        levels.add(levelHeightFromTop[LAN_LEVEL - 4].toCharArray());

        int maxIndex = levels.get(0).length - (levelHeightFromTop[FLAG_LEVEL].length() - indexOfFlag) - minFromFlag;
        int replaceIndexStart = rand.nextInt(min, maxIndex);

        for (int i = 0; i < randomHeight; i++) {
            levels.get(i)[replaceIndexStart] = 't';
            levels.get(i)[replaceIndexStart + 1] = 't';
        }

        for (int i = 0; i < 4; i++) {
            levelHeightFromTop[LAN_LEVEL - i] = new String(levels.get(i));
        }

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
