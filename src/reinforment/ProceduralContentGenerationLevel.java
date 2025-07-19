package reinforment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

public class ProceduralContentGenerationLevel {
    private static final Random rand = new Random();
    private static final int LAN_LEVEL = 13;
    private static final int GROUND_1_LEVEL = 14;
    private static final int GROUND_2_LEVEL = 15;
    private static final int FLAG_LEVEL = 12;
    private final Map<Integer, ArrayList<Character>> levels = new HashMap<>();

    //private EnumBlockType[] blocks = {EnumBlockType.COIN_QUESTION_BLOCK, EnumBlockType.NORMAL_BLOCK, EnumBlockType.MUSHROOM_QUESTION_BLOCK};

    private int width;

    public int getWidth() {
        return width;
    }

    public String content = null;

    public static ProceduralContentGenerationLevel parseLevel(String pcgName){
        Map<String, String> params = Helper.parseParameter(pcgName);
        // 2. Get values from the map, providing a default of "0" if missing.
        int blockCount = Integer.parseInt(params.getOrDefault("blocks", "0"));
        int enemyCount = Integer.parseInt(params.getOrDefault("enemies", "0"));
        int pitCount = Integer.parseInt(params.getOrDefault("pits", "0"));
        int pipeCount = Integer.parseInt(params.getOrDefault("pipes", "0"));
        int widthMin = Integer.parseInt(params.getOrDefault("width_min", "15"));
        int widthMax = Integer.parseInt(params.getOrDefault("width_max", "15"));

        ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel.randomWidth(widthMin,widthMax);
        if(pitCount > 0){
            pcgLevel.addPit(4,2, 2, 5, pitCount);
        }

        if(pipeCount > 0){
            pcgLevel.addPipe(4, 2, pipeCount);
        }

        if(enemyCount > 0){
            //pcgLevel.addEnemyRandomBetween(1,10, 4,2, EnumEnemy.GOOMBA);
            pcgLevel.addEnemy(1,2, enemyCount, EnumEnemy.GOOMBA);
        }

        if(blockCount > 0){
            //pcgLevel.addBlockRandomBetween(1,10, 2,2);
            pcgLevel.addBlock(1, 2, blockCount);
        }

        return pcgLevel;
    }

    public static ProceduralContentGenerationLevel randomWidth(int min, int max) {
        int width = rand.nextInt(min, max + 1);
        ProceduralContentGenerationLevel pcg = new ProceduralContentGenerationLevel(width);
        return pcg;
    }

    public void generate(boolean randomSpawn){
        if(randomSpawn){
            randomSpawnMario();
        } else {
            ArrayList<Character> line = levels.get(LAN_LEVEL);
            line.set(3, 'M');
            levels.replace(LAN_LEVEL, line);
        }
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < levels.size(); i++) {
            ArrayList<Character> line = levels.get(i);
            StringBuilder lineBuilder = new StringBuilder();
            for (Character c : line) {
                lineBuilder.append(c);
            }

            contentBuilder.append(lineBuilder.toString());

            if (i < levels.size() - 1) {
                contentBuilder.append("\r\n");
            }
        }
        content = contentBuilder.toString();
    }

    public String getContent(){
        return content;
    }

    private ProceduralContentGenerationLevel(int width){
        createEmptyLevel(width);
    }

    private void createEmptyLevel(int width){
        this.width = width;
        for (int i = 0; i < 16; i++) {
            ArrayList<Character> line = new ArrayList<>();
            if(i == LAN_LEVEL){
                for (int j = 0; j < width; j++) {
                    if (j == width - 4) {
                        line.add('#');
                    }
                    else {
                        line.add('-');
                    }
                }
            } else if(i == FLAG_LEVEL){
                for (int j = 0; j < width; j++) {
                    if (j == width - 4) {
                        line.add('F');
                    }
                    else {
                        line.add('-');
                    }
                }
            }
            else if(i == GROUND_1_LEVEL || i == GROUND_2_LEVEL){
                for (int j = 0; j < width; j++) {
                    line.add('X');
                }
            } else {
                for (int j = 0; j < width; j++) {
                    line.add('-');
                }
            }
            levels.put(i, line);
        }
    }

    public ProceduralContentGenerationLevel(String content){
        String[] contents = content.split("\r\n");
        for (int i = 0; i < contents.length; i++) {
            ArrayList<Character> line = new ArrayList<>();
            for (int j = 0; j < contents[i].length(); j++) {
                line.add(contents[i].charAt(j));
            }
            levels.put(i, line);
        }
    }

    private int getFlagIndex(){
        return levels.get(FLAG_LEVEL).indexOf('F');
    }

    private void randomSpawnMario(){
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - 2;
        int replaceIndex = rand.nextInt(2, maxIndex);
        ArrayList<Character> currentLevel = levels.get(LAN_LEVEL);
        char[] invalidSpawns = {'g', 't', '#'};
        while (true){
            if(isValidSpawn(currentLevel, replaceIndex, invalidSpawns)){
                currentLevel.set(replaceIndex, 'M');
                break;
            }
            replaceIndex = rand.nextInt(1, maxIndex);
        }
    }

    private boolean isValidSpawn(ArrayList<Character> currentLevel, int column, char[] invalidSpawns){
        if(column < 2){
            column = 2;
        }
        if(column > currentLevel.size()){
            column = currentLevel.size() - 2;
        }
        var cellPrevious2 = currentLevel.get(column - 2);
        var cellPrevious = currentLevel.get(column - 1);
        var cell = currentLevel.get(column);
        var cellAfter = currentLevel.get(column + 1);
        var cellAfter2 = currentLevel.get(column + 2);
        for (int j = 0; j < invalidSpawns.length; j++) {
            var invalidSpawn = invalidSpawns[j];
            if(cellPrevious2 == invalidSpawn || cellPrevious == invalidSpawn || cell == invalidSpawn
                    || cellAfter == invalidSpawn || cellAfter2 == invalidSpawn){
                return false;
            }
        }

        var ground = levels.get(GROUND_1_LEVEL);
        if(ground.get(column) == '-'){
            return false;
        }

        return true;
    }

    private int getRandomOffsetFromStartIndex(int offsetFromStart, int maxIndex){
        int addIndex = rand.nextInt(offsetFromStart, maxIndex);
        int attempt = 0;
        while (attempt < 20){
            attempt++;
            if(levels.get(GROUND_1_LEVEL).get(addIndex) == '-'){
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(GROUND_1_LEVEL).get(addIndex - 1) == '-'){
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(GROUND_1_LEVEL).get(addIndex + 1) == '-'){
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex - 1) == 't') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex - 1) == 'M') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex) == 't') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex + 1) == 't') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }
            break;
        }
        if(attempt >= 20){
            System.out.println("attempt more than 20");
        }
        return addIndex;
    }

    public void addPit(int offsetFromStart, int offsetFromFlag, int minWidth, int maxWidth, int total){
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            int width = rand.nextInt(minWidth, maxWidth + 1);
            for (int i = 0; i < levels.size(); i++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(i);
                for (int j = 0; j < width; j++) {
                    currentLevel.add(addIndex + j, '-');
                }
            }
        }
    }

    public void addEnemyRandomBetween(int min, int max, int offsetFromStart, int offsetFromFlag, EnumEnemy enemy){
        int total = rand.nextInt(min, max + 1);
        addEnemy(offsetFromStart, offsetFromFlag, total, enemy);
    }

    public void addEnemy(int offsetFromStart, int offsetFromFlag, int total, EnumEnemy enemy){
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);

            for (int i = 0; i < levels.size(); i++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(i);
                // Add the characters based on the conditions
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    // Add 'X' at index and index + 1
                    currentLevel.add(addIndex, 'X');
                } else if (i == LAN_LEVEL) {
                    // Add 't' at index and index + 1
                    currentLevel.add(addIndex, enemy.getValue());
                } else {
                    // Add '-' at index and index + 1
                    currentLevel.add(addIndex, '-');
                }
            }
        }
    }

    public void addBlockRandomBetween(int min, int max, int offsetFromStart, int offsetFromFlag){
        int total = rand.nextInt(min, max + 1);
        addBlock(offsetFromStart, offsetFromFlag, total);
    }

    public void addBlock(int offsetFromStart, int offsetFromFlag, int total){
        int k = 0;
        while (k < total) {
            EnumBlockType blockType = (k + 1) % 3 == 0 ? EnumBlockType.MUSHROOM_QUESTION_BLOCK : EnumBlockType.COIN_QUESTION_BLOCK;
            doAddBlock(blockType, offsetFromStart, offsetFromFlag);
            if((k + 1) % 4 == 0){
                doAddBlock(EnumBlockType.NORMAL_BLOCK, offsetFromStart, offsetFromFlag);
            }
            k++;
        }
    }

    private void doAddBlock(EnumBlockType blockType, int offsetFromStart, int offsetFromFlag){
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
        int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);

        for (int i = 0; i < levels.size(); i++) {
            // Get the current level's list once and reuse it
            ArrayList<Character> currentLevel = levels.get(i);
            // Add the characters based on the conditions
            if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                // Add 'X' at index and index + 1
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, 'X');
                }
            } else if (i == LAN_LEVEL - 3) {
                // Add 't' at index and index + 1
                currentLevel.add(addIndex - 1, '-');
                currentLevel.add(addIndex, blockType.getValue());
                currentLevel.add(addIndex + 1, '-');
            } else {
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, '-');
                }
            }
        }
    }

    public void addPipe(int offsetFromStart, int offsetFromFlag, int total){
        for (int k = 0; k < total; k++) {
            int height = rand.nextInt(2, 5);
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            while (true){
                if(levels.get(GROUND_1_LEVEL).get(addIndex) != '-'){
                    break;
                }
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
            }
            for (int i = 0; i < levels.size(); i++) {
                // DeteroffsetFromStarte if this level should be a pipe
                boolean isPipe = (i >= LAN_LEVEL - (height - 1) && i <= LAN_LEVEL);

                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(i);

                // Add the characters based on the conditions
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    // Add 'X' at index and index + 1
                    currentLevel.add(addIndex, 'X');
                    currentLevel.add(addIndex + 1, 'X');
                } else if (isPipe) {
                    // Add 't' at index and index + 1
                    currentLevel.add(addIndex, 't');
                    currentLevel.add(addIndex + 1, 't');
                } else {
                    // Add '-' at index and index + 1
                    currentLevel.add(addIndex, '-');
                    currentLevel.add(addIndex + 1, '-');
                }
            }
        }
    }
}
