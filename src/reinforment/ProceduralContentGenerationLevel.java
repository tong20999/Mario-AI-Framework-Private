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

    private String pcgName = null;
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

    public String getPcgName() {
        return pcgName;
    }

    private int blockCount;
    private boolean randomBlockHeight;
    private int enemyCount;
    private boolean randomEnemyHeight;
    private int coinCount;
    private boolean randomCoinHeight;
    private int pitCount;

    private int pipeCount;
    private int rampCount;
    private int widthMin;
    private int widthMax;

    public static ProceduralContentGenerationLevel parseLevel(String pcgName) {
        Map<String, String> params = Helper.parseParameter(pcgName);
        int blockCount = Integer.parseInt(params.getOrDefault("blocks", "0"));
        boolean randomBlockHeight = Boolean.parseBoolean(params.getOrDefault("random_block_height", "false"));
        int enemyCount = Integer.parseInt(params.getOrDefault("enemies", "0"));
        boolean randomEnemyHeight = Boolean.parseBoolean(params.getOrDefault("random_enemy_height", "false"));
        int coinCount = Integer.parseInt(params.getOrDefault("coins", "0"));
        boolean randomCoinHeight = Boolean.parseBoolean(params.getOrDefault("random_coin_height", "false"));
        int pitCount = Integer.parseInt(params.getOrDefault("pits", "0"));
        int pipeCount = Integer.parseInt(params.getOrDefault("pipes", "0"));
        int rampCount = Integer.parseInt(params.getOrDefault("ramps", "0"));
        int widthMin = Integer.parseInt(params.getOrDefault("width_min", "15"));
        int widthMax = Integer.parseInt(params.getOrDefault("width_max", "15"));

        return doParseLevel(pcgName, widthMin, widthMax, blockCount, randomBlockHeight, coinCount, randomCoinHeight, enemyCount, randomEnemyHeight, pitCount, pipeCount, rampCount);
    }

    private static ProceduralContentGenerationLevel doParseLevel(String pcgName, int widthMin, int widthMax, int blockCount, boolean randomBlockHeight, int coinCount, boolean randomCoinHeight, int enemyCount, boolean randomEnemyHeight, int pitCount, int pipeCount, int rampCount){

        try{
            ProceduralContentGenerationLevel pcgLevel = ProceduralContentGenerationLevel.randomWidth(widthMin,widthMax);
            pcgLevel.blockCount = blockCount;
            pcgLevel.randomBlockHeight = randomBlockHeight;
            pcgLevel.coinCount = coinCount;
            pcgLevel.randomCoinHeight = randomCoinHeight;
            pcgLevel.enemyCount = enemyCount;
            pcgLevel.randomEnemyHeight = randomEnemyHeight;
            pcgLevel.pitCount = pitCount;
            pcgLevel.pipeCount = pipeCount;
            pcgLevel.rampCount = rampCount;
            pcgLevel.widthMin = widthMin;
            pcgLevel.widthMax = widthMax;
            pcgLevel.pcgName = pcgName;
            if(rampCount > 0){
                pcgLevel.addRamp(10,2,rampCount);
            }

            if(pitCount > 0){
                pcgLevel.addPit(10,2, 2, 5, pitCount);
            }

            if(pipeCount > 0){
                pcgLevel.addPipe(10, 2, pipeCount);
            }

            if(enemyCount > 0){
                pcgLevel.addEnemy(10,2, enemyCount, randomEnemyHeight);
            }

            if(blockCount > 0){
                pcgLevel.addBlock(6, 2, blockCount, randomBlockHeight);
            }

            if(coinCount > 0){
                pcgLevel.addCoin(10,2, coinCount, randomCoinHeight);
            }

            return pcgLevel;
        }
        catch (IllegalArgumentException ex){
            System.out.println(ex.getMessage());
            System.out.println(MessageFormat.format( "Retry with new width min {0} max {1}", widthMin * 2, widthMax * 2));
            return doParseLevel(pcgName, widthMin * 2, widthMax * 2, blockCount, randomBlockHeight, coinCount, randomCoinHeight, enemyCount, randomEnemyHeight, pitCount, pipeCount, rampCount);
        }
    }

    public PcgParameters getPcgDto(){
        return new PcgParameters(content, width, blockCount, randomBlockHeight, enemyCount,
                randomEnemyHeight,  coinCount, randomCoinHeight, pitCount, pipeCount, rampCount, widthMin, widthMax);
    }

    public static ProceduralContentGenerationLevel randomWidth(int min, int max) {
        int width = rand.nextInt(min, max + 1);
        ProceduralContentGenerationLevel pcg = new ProceduralContentGenerationLevel(width);
        pcg.width = width;
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
            if (isInvalidAtIndex(addIndex)) {
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

    private static final Set<Character> forbiddenChars = new HashSet<>(Arrays.asList('#', 't', '!', '@', 'o'));

    private boolean isInvalidAtIndex(int idx) {
        // Check '-' on GROUND_1_LEVEL at idx and neighbors
        if (isCharAt(GROUND_1_LEVEL, idx, '-') || isCharAt(GROUND_1_LEVEL, idx - 1, '-') || isCharAt(GROUND_1_LEVEL, idx + 1, '-')) {
            return true;
        }

        // Check SPECIAL_HARDCODED_CHAR at LAN_LEVEL (only idx - 1)
        if (isCharAt(LAN_LEVEL, idx - 1, 'M')) {
            return true;
        }

        // Check forbiddenChars at LAN_LEVEL on idx and neighbors
        if (forbiddenChars.stream().anyMatch(c ->
                isCharAt(LAN_LEVEL, idx - 1, c) ||
                        isCharAt(LAN_LEVEL, idx, c) ||
                        isCharAt(LAN_LEVEL, idx + 1, c))) {
            return true;
        }

        return false;
    }

    private boolean isCharAt(int level, int index, char c) {
        if (index < 0 || index >= levels.get(level).size()) return false;
        return levels.get(level).get(index) == c;
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

    public void addEnemy(int offsetFromStart, int offsetFromFlag, int total, boolean randomEnemyHeight) throws IllegalArgumentException {
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            boolean randomHeight = randomEnemyHeight && rand.nextInt(2) == 0;
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

    public void addBlock(int offsetFromStart, int offsetFromFlag, int total, boolean randomBlockHeight) throws IllegalArgumentException {
        int k = 0;
        while (k < total) {
            int randomBlock = rand.nextInt(0, blocks.length);
            EnumBlockType blockType = blocks[randomBlock];
            doAddBlock(blockType, offsetFromStart, offsetFromFlag, randomBlockHeight);
//            if((k + 1) % 4 == 0){
//                doAddBlock(EnumBlockType.NORMAL_BLOCK, offsetFromStart, offsetFromFlag, randomBlockHeight);
//            }
            k++;
        }
    }

    private void doAddBlock(EnumBlockType blockType, int offsetFromStart, int offsetFromFlag, boolean randomBlockHeight) throws IllegalArgumentException {
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
        int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        while (true){
            if(isValidToAdd(addIndex)){
                break;
            }
            addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        }
        int height = LAN_LEVEL - 3;
        if(randomBlockHeight){
            height = rand.nextInt(LAN_LEVEL - 8, LAN_LEVEL - 3 + 1);
            if(height == 8){
                height = 7;
            }
        }
        boolean needPlatform = height < 9;
        boolean randomNormalBlockLeft = rand.nextInt(2) == 0;
        boolean randomNormalBlockRight = rand.nextInt(2) == 0;
        randomNormalBlockLeft = true;
        for (int i = 0; i < levels.size(); i++) {
            ArrayList<Character> currentLevel = levels.get(i);
            if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, 'X');
                }
            } else if (i == height) {
                currentLevel.add(addIndex - 1, randomNormalBlockLeft ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                currentLevel.add(addIndex, blockType.getValue());
                currentLevel.add(addIndex + 1, randomNormalBlockRight ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
            } else if (i == LAN_LEVEL - 3) {
                if(needPlatform){
                    currentLevel.add(addIndex - 1, randomNormalBlockLeft ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    currentLevel.add(addIndex, EnumBlockType.NORMAL_BLOCK.getValue());
                    currentLevel.add(addIndex + 1, randomNormalBlockRight ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                }
                else {
                    currentLevel.add(addIndex - 1, '-');
                    currentLevel.add(addIndex, '-');
                    currentLevel.add(addIndex + 1, '-');
                }
            } else {
                for (int j = -1; j < 2; j++) {
                    currentLevel.add(addIndex + j, '-');
                }
            }
        }
    }

    private void addCoin(int offsetFromStart, int offsetFromFlag, int total, boolean randomCoinHeight) {
        for (int k = 0; k < total; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            // rand.nextInt(5, 11)
            int height = LAN_LEVEL - 3;
            if(randomCoinHeight){
                height = rand.nextInt(LAN_LEVEL - 8, LAN_LEVEL - 3 + 1);
            }
            boolean needPlatform = height < 9;
            for (int i = 0; i < levels.size(); i++) {
                ArrayList<Character> currentLevel = levels.get(i);
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    currentLevel.add(addIndex, 'X');
                } else if (i == height) {
                    currentLevel.add(addIndex, 'o');
                } else if(i == LAN_LEVEL - 3){
                    if(needPlatform){
                        currentLevel.add(addIndex, EnumBlockType.NORMAL_BLOCK.getValue());
                    } else {
                        currentLevel.add(addIndex, '-');
                    }
                }
                else {
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
