package reinforment;

import java.text.MessageFormat;
import java.util.*;

public class ProceduralContentGenerationLevel {
    private static final Random rand = new Random();
    private static final int LAN_LEVEL = 13;
    private static final int GROUND_1_LEVEL = 14;
    private static final int GROUND_2_LEVEL = 15;
    private static final int FLAG_LEVEL = 12;
    private final Map<Integer, ArrayList<Character>> levels = new HashMap<>();

    private EnumBlockType[] blocks = {
            EnumBlockType.COIN_QUESTION_BLOCK,
            //EnumBlockType.COIN_BLOCK,
            EnumBlockType.MUSHROOM_QUESTION_BLOCK,
            //EnumBlockType.MUSHROOM_BLOCK
    };

    private int width;

    public int getWidth() {
        return width;
    }

    public String content = null;

    public static ProceduralContentGenerationLevel parseLevel(String pcgName) {
        Map<String, String> params = Helper.parseParameter(pcgName);
        int blockCount = Integer.parseInt(params.getOrDefault("blocks", "0"));
        int coinCount = Integer.parseInt(params.getOrDefault("coins", "0"));
        int enemyCount = Integer.parseInt(params.getOrDefault("enemies", "0"));
        int pitCount = Integer.parseInt(params.getOrDefault("pits", "0"));
        int pipeCount = Integer.parseInt(params.getOrDefault("pipes", "0"));
        int rampCount = Integer.parseInt(params.getOrDefault("ramps", "0"));
        int widthMin = Integer.parseInt(params.getOrDefault("width_min", "15"));
        int widthMax = Integer.parseInt(params.getOrDefault("width_max", "15"));

        return doParseLevel(widthMin, widthMax, blockCount, coinCount, enemyCount, pitCount, pipeCount, rampCount);
    }

    private static ProceduralContentGenerationLevel doParseLevel(int widthMin, int widthMax, int blockCount, int coinCount, int enemyCount, int pitCount, int pipeCount, int rampCount){

        try{
            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel.randomWidth(widthMin,widthMax);

            if(rampCount > 0){
                pcgLevel.addRamp(8,2,rampCount);
            }

            if(pitCount > 0){
                pcgLevel.addPit(6,2, 2, 5, pitCount);
            }

            if(pipeCount > 0){
                pcgLevel.addPipe(6, 2, pipeCount);
            }

            if(enemyCount > 0){
                pcgLevel.addEnemy(6,2, enemyCount);
            }

            if(blockCount > 0){
                pcgLevel.addBlock(6, 2, blockCount);
            }

            if(coinCount > 0){
                pcgLevel.addCoin(6,2,coinCount);
            }

            return pcgLevel;
        }
        catch (IllegalArgumentException ex){
            System.out.println(ex.getMessage());
            System.out.println(MessageFormat.format( "Retry with new width min {0} max {1}", widthMin * 2, widthMax * 2));
            return doParseLevel(widthMin * 2, widthMax * 2, blockCount, coinCount, enemyCount, pitCount, pipeCount, rampCount);
        }
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

    private int getRandomOffsetFromStartIndex(int offsetFromStart, int maxIndex) throws IllegalArgumentException {
        int addIndex = rand.nextInt(offsetFromStart, maxIndex);
        int attempt = 0;
        int maxAttempt = 50;
        while (attempt < maxAttempt){
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

            if(levels.get(LAN_LEVEL).get(addIndex - 1) == 'M') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex - 1) == 't') {
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

            if(levels.get(LAN_LEVEL).get(addIndex - 1) == '#') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex) == '#') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }

            if(levels.get(LAN_LEVEL).get(addIndex + 1) == '#') {
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
                continue;
            }
            break;
        }
        if(attempt >= maxAttempt){
            throw new IllegalArgumentException("Level too short attempt more than " + maxAttempt);
        }
        return addIndex;
    }

    public void addPit(int offsetFromStart, int offsetFromFlag, int minWidth, int maxWidth, int total){
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            while (true){
                if(isValidToAdd(addIndex)){
                    break;
                }
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
            }
            int width = rand.nextInt(minWidth, maxWidth + 1);
            for (int height = 0; height < levels.size(); height++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(height);

                if(height == GROUND_1_LEVEL || height == GROUND_2_LEVEL){
                    currentLevel.add(addIndex - 1, 'X');
                    addObject(currentLevel, width, '-', addIndex);
                    // Padding 2 block after
                    currentLevel.add(addIndex + width, 'X');
                } else {
                    currentLevel.add(addIndex - 1, '-');
                    addObject(currentLevel, width, '-', addIndex + 2);
                    // Padding 2 block after
                    currentLevel.add(addIndex + 1 + width, '-');
                }
                // Padding 2 block before

            }
        }
    }

    public void addEnemy(int offsetFromStart, int offsetFromFlag, int total) throws IllegalArgumentException {
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            boolean randomHeight = rand.nextInt(2) == 0;
            int height = LAN_LEVEL;
            if(randomHeight){
                height = rand.nextInt(LAN_LEVEL - 8, LAN_LEVEL + 1);
            }
            for (int i = 0; i < levels.size(); i++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(i);
                // Add the characters based on the conditions
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    // Add 'X' at index and index + 1
                    currentLevel.add(addIndex, 'X');
                } else if (i == height) {
                    // Add 't' at index and index + 1
                    var enemy = k % 2 == 0 && k != 0 ? EnumEnemy.GREEN_KOOPA : EnumEnemy.GOOMBA;
                    currentLevel.add(addIndex, enemy.getValue());
                } else {
                    // Add '-' at index and index + 1
                    currentLevel.add(addIndex, '-');
                }
            }
        }
    }

    public void addBlock(int offsetFromStart, int offsetFromFlag, int total) throws IllegalArgumentException {
        int k = 0;
        while (k < total) {
            int randomBlock = rand.nextInt(0, blocks.length);
            EnumBlockType blockType = blocks[randomBlock];
            doAddBlock(blockType, offsetFromStart, offsetFromFlag);
            if((k + 1) % 4 == 0){
                doAddBlock(EnumBlockType.NORMAL_BLOCK, offsetFromStart, offsetFromFlag);
            }
            k++;
        }
    }

    private void doAddBlock(EnumBlockType blockType, int offsetFromStart, int offsetFromFlag) throws IllegalArgumentException {
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
        int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        while (true){
            if(isValidToAdd(addIndex)){
                break;
            }
            addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        }

        boolean heightBlock = rand.nextInt(2) == 0;
        heightBlock = false;
        for (int i = 0; i < levels.size(); i++) {
            ArrayList<Character> currentLevel = levels.get(i);
            if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, 'X');
                }
            } else if (i == LAN_LEVEL - 7 && heightBlock) {
                int randomNormalBlock = rand.nextInt(2);
                currentLevel.add(addIndex - 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                currentLevel.add(addIndex, blockType.getValue());
                randomNormalBlock = rand.nextInt(2);
                currentLevel.add(addIndex + 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
            } else if (i == LAN_LEVEL - 3) {
                if(!heightBlock){
                    int randomNormalBlock = rand.nextInt(2);
                    currentLevel.add(addIndex - 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    currentLevel.add(addIndex, blockType.getValue());
                    randomNormalBlock = rand.nextInt(2);
                    currentLevel.add(addIndex + 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                }
                else {
                    int randomNormalBlock = rand.nextInt(2);
                    currentLevel.add(addIndex - 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    currentLevel.add(addIndex, EnumBlockType.NORMAL_BLOCK.getValue());
                    randomNormalBlock = rand.nextInt(2);
                    currentLevel.add(addIndex + 1, randomNormalBlock == 0 ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                }
            } else {
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, '-');
                }
            }
        }
    }

    private void addCoin(int offsetFromStart, int offsetFromFlag, int total) {
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            int height = rand.nextInt(LAN_LEVEL - 4, LAN_LEVEL - 3 + 1);
            for (int i = 0; i < levels.size(); i++) {
                ArrayList<Character> currentLevel = levels.get(i);
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    currentLevel.add(addIndex, 'X');
                } else if (i == height) {
                    currentLevel.add(addIndex, 'o');
                } else {
                    currentLevel.add(addIndex, '-');
                }
            }
        }
    }

    private void addRamp(int offsetFromStart, int offsetFromFlag, int total) {
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            while (true){
                if(isValidToAdd(addIndex)){
                    break;
                }
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
            }
            boolean randomPattern = rand.nextInt(2) == 0;
            boolean randomPit = rand.nextInt(2) == 0;
            for (int height = 0; height < levels.size(); height++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(height);
                if(height == GROUND_1_LEVEL || height == GROUND_2_LEVEL) {
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'X');
                    }
                    currentLevel.add(addIndex - 3,'X');
                    currentLevel.add(addIndex - 2,'X');
                    currentLevel.add(addIndex - 1,'X');
                    currentLevel.add(addIndex, 'X');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,randomPit ? '-' : 'X');
                        currentLevel.add(addIndex + 2,randomPit ? '-' : 'X');
                        currentLevel.add(addIndex + 3,'X');
                        currentLevel.add(addIndex + 4,'X');
                        currentLevel.add(addIndex + 5,'X');
                        currentLevel.add(addIndex + 6,'X');
                    }
                } else if (height == LAN_LEVEL - 3) {
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'-');
                    }
                    currentLevel.add(addIndex - 3,'-');
                    currentLevel.add(addIndex - 2,'-');
                    currentLevel.add(addIndex - 1,'#');
                    currentLevel.add(addIndex,'#');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,'-');
                        currentLevel.add(addIndex + 2,'-');
                        currentLevel.add(addIndex + 3,'#');
                        currentLevel.add(addIndex + 4,'-');
                        currentLevel.add(addIndex + 5,'-');
                        currentLevel.add(addIndex + 6,'-');
                    }

                } else if (height == LAN_LEVEL - 2) {
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'-');
                    }
                    currentLevel.add(addIndex - 3,'-');
                    currentLevel.add(addIndex - 2,'#');
                    currentLevel.add(addIndex - 1,'#');
                    currentLevel.add(addIndex,'#');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,'-');
                        currentLevel.add(addIndex + 2,'-');
                        currentLevel.add(addIndex + 3,'#');
                        currentLevel.add(addIndex + 4,'#');
                        currentLevel.add(addIndex + 5,'-');
                        currentLevel.add(addIndex + 6,'-');
                    }
                } else if (height == LAN_LEVEL - 1) {
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'-');
                    }
                    currentLevel.add(addIndex - 3,'#');
                    currentLevel.add(addIndex - 2,'#');
                    currentLevel.add(addIndex - 1,'#');
                    currentLevel.add(addIndex,'#');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,'-');
                        currentLevel.add(addIndex + 2,'-');
                        currentLevel.add(addIndex + 3,'#');
                        currentLevel.add(addIndex + 4,'#');
                        currentLevel.add(addIndex + 5,'#');
                        currentLevel.add(addIndex + 6,'-');
                    }
                } else if (height == LAN_LEVEL) {
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'#');
                    }
                    currentLevel.add(addIndex - 3,'#');
                    currentLevel.add(addIndex - 2,'#');
                    currentLevel.add(addIndex - 1,'#');
                    currentLevel.add(addIndex,'#');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,'-');
                        currentLevel.add(addIndex + 2,'-');
                        currentLevel.add(addIndex + 3,'#');
                        currentLevel.add(addIndex + 4,'#');
                        currentLevel.add(addIndex + 5,'#');
                        currentLevel.add(addIndex + 6,'#');
                    }
                } else{
                    if(randomPattern){
                        currentLevel.add(addIndex - 4,'-');
                    }
                    currentLevel.add(addIndex - 3,'-');
                    currentLevel.add(addIndex - 2,'-');
                    currentLevel.add(addIndex - 1,'-');
                    currentLevel.add(addIndex,'-');
                    if(randomPattern){
                        currentLevel.add(addIndex + 1,'-');
                        currentLevel.add(addIndex + 2,'-');
                        currentLevel.add(addIndex + 3,'-');
                        currentLevel.add(addIndex + 4,'-');
                        currentLevel.add(addIndex + 5,'-');
                        currentLevel.add(addIndex + 6,'-');
                    }
                }
            }
        }
    }

    public void addPipe(int offsetFromStart, int offsetFromFlag, int total){
        for (int k = 0; k < total; k++) {
            int pipeHeight = rand.nextInt(2, 5);
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);

            while (true){
                if(isValidToAdd(addIndex)){
                    break;
                }
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
            }
            for (int height = 0; height < levels.size(); height++) {


                // DeteroffsetFromStarte if this level should be a pipe
                boolean isPipe = (height >= LAN_LEVEL - (pipeHeight - 1) && height <= LAN_LEVEL);

                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevelHeight = levels.get(height);

                // Add the characters based on the conditions
                if (height == GROUND_1_LEVEL || height == GROUND_2_LEVEL) {
                    // Add 'X' at index and index + 1
                    currentLevelHeight.add(addIndex -1, 'X');
                    currentLevelHeight.add(addIndex, 'X');
                    currentLevelHeight.add(addIndex + 1, 'X');
                    currentLevelHeight.add(addIndex + 2, 'X');
                } else if (isPipe) {
                    // Add 't' at index and index + 1
                    currentLevelHeight.add(addIndex -1, '-');
                    currentLevelHeight.add(addIndex, 't');
                    currentLevelHeight.add(addIndex + 1, 't');
                    currentLevelHeight.add(addIndex + 2, '-');
                } else {
                    // Add '-' at index and index + 1
                    addObject(currentLevelHeight, 4, '-', addIndex);
                }
            }
        }
    }

    private boolean isValidToAdd(int addIndex) {
        // check pit level 1
        int checkLength = 4;
        for (int i = -2; i < checkLength-2; i++) {
            if(levels.get(GROUND_1_LEVEL).get(addIndex + i) == '-'){
                return false;
            }
        }

        for (int i = -2; i < checkLength-2; i++) {
            if(levels.get(LAN_LEVEL).get(addIndex + i) == 't'){
                return false;
            }
        }

        for (int i = -2; i < checkLength - 2; i++) {
            if(levels.get(LAN_LEVEL).get(addIndex + i) == '#'){
                return false;
            }
        }
        return true;
    }

    private void addObject(ArrayList<Character> level, int total, char object, int startIndex){
        for (int i = 0; i < total; i++) {
            level.add(startIndex + i, object);
        }
    }
}
