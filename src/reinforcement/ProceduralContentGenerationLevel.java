package reinforcement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public class ProceduralContentGenerationLevel {
    private static final Random rand = new Random();
    private static final int LAN_LEVEL = 13;
    private static final int GROUND_1_LEVEL = 14;
    private static final int GROUND_2_LEVEL = 15;
    private static final int FLAG_LEVEL = 12;
    private final Map<Integer, ArrayList<Character>> levels = new HashMap<>();

    private ProceduralContentGenerationLevel(PCGLevelDto dto){
        this.pcgLevelDto = dto;
    }

    private String pcgName = null;
    private EnumBlockType[] blocks = {
            EnumBlockType.COIN_QUESTION_BLOCK,
            //EnumBlockType.COIN_BLOCK,
            EnumBlockType.MUSHROOM_QUESTION_BLOCK,
            //EnumBlockType.MUSHROOM_BLOCK
    };

    private int width;
    private PCGLevelDto pcgLevelDto;

    public int getWidth() {
        return width;
    }

    private String content = null;

    public String getPcgName() {
        return pcgName;
    }

    private List<String> failedLevels = new ArrayList<>();

    public static ProceduralContentGenerationLevel parseLevel(PCGLevelDto pcgLevelDto) throws IOException, SQLException {
        if(pcgLevelDto.isTrainFailedLevel()){
            ProceduralContentGenerationLevel pcg = new ProceduralContentGenerationLevel(pcgLevelDto);
            pcg.failedLevels = createFailedLevelsFromDatabase();
            return pcg;
        }
        return doParseLevel(pcgLevelDto);
    }

    private static ArrayList<String> createFailedLevels() throws IOException {
        // Get the working directory
        Optional<File> optional = Helper.getWorkingDir(true);
        String workingDir = optional.get().getAbsolutePath();

        // Paths to the two folders containing JSON files
        String losePath = workingDir + "\\results\\LOSE";
        String timeOutPath = workingDir + "\\results\\TIME_OUT";
        String partialPath = workingDir + "\\results\\PARTIAL_WIN";

        // Create a list to store the pcg strings
        ArrayList<String> pcgStrings = new ArrayList<>();

        // Parse both folders
        parseFolder(losePath, pcgStrings);
        parseFolder(timeOutPath, pcgStrings);
        parseFolder(partialPath, pcgStrings);

        // Return the list of extracted pcg strings
        return pcgStrings;
    }

    private static List<String> createFailedLevelsFromDatabase() throws IOException, SQLException {
        // Get the working directory
        int trainingNumber = Integer.parseInt(Helper.getLatestTrainingNumber());
        return Helper.getPcgContentForLatestTraining(trainingNumber - 1);
    }

    // Helper method to parse a given folder
    private static void parseFolder(String folderPath, ArrayList<String> pcgStrings) throws IOException {
        // Get all the files in the folder
        File folder = new File(folderPath);
        if (folder.exists() && folder.isDirectory()) {
            // List all the files in the directory
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                // Loop through each file in the folder
                for (File file : files) {
                    // Parse each JSON file
                    try (FileReader fileReader = new FileReader(file)) {
                        // Parse the file using Gson
                        JsonObject jsonObject = JsonParser.parseReader(fileReader).getAsJsonObject();

                        // Extract the "pcg" field from the JSON
                        String pcg = jsonObject.has("pcg") ? jsonObject.get("pcg").getAsString() : "";

                        // Add to the result list if it's not empty
                        if (!pcg.isEmpty()) {
                            pcgStrings.add(pcg);
                        }
                    } catch (IOException e) {
                        // Handle any issues reading/parsing the file
                        System.out.println("Error reading file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private static ProceduralContentGenerationLevel doParseLevel(PCGLevelDto pcgLevelDto){
        ProceduralContentGenerationLevel pcg = new ProceduralContentGenerationLevel(pcgLevelDto);
        int width = rand.nextInt(pcgLevelDto.getWidthMin(), pcgLevelDto.getWidthMax());
        pcg.createEmptyLevel(width);

        try{
            pcg.addObstacle(9,2, pcgLevelDto);
            pcg.addPit(9,2, pcgLevelDto);
            pcg.addPipe(9, 2, pcgLevelDto);
            pcg.addEnemy(8,2, pcgLevelDto);
            pcg.addBlock(8, 2, pcgLevelDto);
            pcg.addCoin(8,2, pcgLevelDto);

            return pcg;
        }
        catch (IllegalArgumentException ex){
            System.out.println(ex.getMessage());
            System.out.println(MessageFormat.format( "Retry with new width min {0} max {1}", pcgLevelDto.getWidthMin() * 2, pcgLevelDto.getWidthMax() * 2));
            return doParseLevel(pcgLevelDto);
        }
    }

    public void generate(){
        ArrayList<Character> lanLevel = levels.get(LAN_LEVEL);
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - 2;
        var mapWidth = lanLevel.size() / 2;
        //int spawnMario = rand.nextInt(3 ,maxIndex);
        int spawnMario = rand.nextInt(3 ,4);
        lanLevel.set(spawnMario, 'M');
        levels.replace(LAN_LEVEL, lanLevel);
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
        if(pcgLevelDto.isTrainFailedLevel()){
            int level = rand.nextInt(failedLevels.size());
            return failedLevels.get(level);
        }
        return content;
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
//                    else if(j == width - 5){
//                        line.add('N');
//                    }
                    else {
                        line.add('-');
                    }
                }
            } else if(i == FLAG_LEVEL){
                for (int j = 0; j < width; j++) {
                    if (j == width - 4) {
                        line.add('F');
                    }
//                    else if(j == width - 5){
//                        line.add('N');
//                    }
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
//                    if (j == width - 5) {
//                        line.add('N');
//                    }
//                    else {
//                        line.add('-');
//                    }
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

    public void addPit(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto){
        for (int k = 0; k < pcgLevelDto.getPits(); k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            while (true){
                if(isValidToAdd(addIndex)){
                    break;
                }
                addIndex = rand.nextInt(offsetFromStart, maxIndex);
            }
            int width = rand.nextInt(pcgLevelDto.getPitsMinWidth(), pcgLevelDto.getPitsMaxWidth() + 1);
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

    public void addEnemy(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto) throws IllegalArgumentException {
        if(pcgLevelDto.getEnemies() < 1){
            return;
        }
        var total = pcgLevelDto.getEnemies() == 1 ? 1 : rand.nextInt(1, pcgLevelDto.getEnemies() + 1);
        for (int k = 0; k < pcgLevelDto.getEnemies() ; k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            int height = rand.nextInt(pcgLevelDto.getEnemiesHeightOrigin(), pcgLevelDto.getEnemiesHeightBound());
            boolean randomBrickL = rand.nextInt(2) == 0;
            randomBrickL = false;
            boolean randomBrickC = rand.nextInt(2) == 0;
            boolean randomBrickR = rand.nextInt(2) == 0;
            randomBrickR = false;
            for (int i = 0; i < levels.size(); i++) {
                // Get the current level's list once and reuse it
                ArrayList<Character> currentLevel = levels.get(i);
                // Add the characters based on the conditions
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    currentLevel.add(addIndex - 1, 'X');
                    currentLevel.add(addIndex + 1, 'X');
                    currentLevel.add(addIndex, 'X');
                } else if (i == height) {
                    // Add 't' at index and index + 1
                    var enemy = rand.nextInt(2) == 0 ? EnumEnemy.GREEN_KOOPA : EnumEnemy.GOOMBA;
                    currentLevel.add(addIndex, enemy.getValue());
                    currentLevel.add(addIndex - 1, '-');
                    currentLevel.add(addIndex + 1, '-');
                } else if (i == LAN_LEVEL - 3){
                    currentLevel.add(addIndex, randomBrickC ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    currentLevel.add(addIndex - 1, randomBrickL ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    currentLevel.add(addIndex + 1, randomBrickR ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                }
                else {
                    // Add '-' at index and index + 1
                    currentLevel.add(addIndex, '-');
                    currentLevel.add(addIndex - 1, '-');
                    currentLevel.add(addIndex + 1, '-');
                }
            }
        }
    }

    public void addBlock(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto) throws IllegalArgumentException {
        if(pcgLevelDto.getBlocks() < 1){
            return;
        }
        var total = pcgLevelDto.getBlocks() == 1 ? 1 : rand.nextInt(1, pcgLevelDto.getBlocks() + 1);
        int k = 0;
        while (k < pcgLevelDto.getBlocks()) {
            int randomBlock = rand.nextInt(0, blocks.length);
            EnumBlockType blockType = blocks[randomBlock];
            int height = k % 2 == 0 ? 10 :
                    rand.nextInt(5, 9);
            if(height == 8){
                height = 7;
            }
            doAddBlock(blockType, offsetFromStart, offsetFromFlag, height, pcgLevelDto);
            k++;
        }
    }


    private void addPrefab(String pattern, int index){
        String[] lines = pattern.strip().split("\\R");

        int prefabHeight = lines.length;
        int prefabWidth = lines[0].length();

        for (int y = 0; y < prefabHeight; y++) {
            ArrayList<Character> row = levels.get(y);

            char[] prefabChars = lines[y].toCharArray();

            for (int x = 0; x < prefabWidth; x++) {
                int targetX = index + x;
                // No need to bounds check if you guarantee valid index
                row.set(targetX, prefabChars[x]);
            }
        }
    }

    private void doAddBlock(EnumBlockType blockType, int offsetFromStart, int offsetFromFlag, int height, PCGLevelDto pcgLevelDto) throws IllegalArgumentException {
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;

        int addIndex = pcgLevelDto.isSpawnBlockCenter() ? maxIndex - offsetFromFlag :
            getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        while (true){
            if(isValidToAdd(addIndex)){
                break;
            }
            addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
        }

        boolean needPlatform = height < 9;
        boolean randomNormalBlockLeft = pcgLevelDto.isRandomNormalBlockLeft() && rand.nextInt(2) == 0;
        boolean randomNormalBlockRight = pcgLevelDto.isRandomNormalBlockRight() && rand.nextInt(2) == 0;
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

    private void addCoin(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto) {
        if(pcgLevelDto.getCoins() < 1){
            return;
        }
        var total = pcgLevelDto.getCoins() == 1 ? 1 : rand.nextInt(1, pcgLevelDto.getCoins() + 1);
        for (int k = 0; k < pcgLevelDto.getCoins(); k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = getRandomOffsetFromStartIndex(offsetFromStart, maxIndex);
            int height = k % 2 == 0 ?
                    rand.nextInt(10, 14) :
                    rand.nextInt(5, 9);
            boolean needPlatform = height < 10;
            boolean randomNormalBlockLeft = rand.nextInt(2) == 0;
            boolean randomNormalBlockRight = rand.nextInt(2) == 0;

            for (int i = 0; i < levels.size(); i++) {
                ArrayList<Character> currentLevel = levels.get(i);
                if (i == GROUND_1_LEVEL || i == GROUND_2_LEVEL) {
                    for (int j = -1; j < 2; j++) {
                        currentLevel.add(addIndex + j, 'X');
                    }
                } else if (i == height) {
                    currentLevel.add(addIndex - 1, '-');
                    currentLevel.add(addIndex, 'o');
                    currentLevel.add(addIndex + 1,  '-');
                } else if(i == LAN_LEVEL - 3){
                    if(needPlatform){
                        currentLevel.add(addIndex - 1, randomNormalBlockLeft ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                        currentLevel.add(addIndex, EnumBlockType.NORMAL_BLOCK.getValue());
                        currentLevel.add(addIndex + 1, randomNormalBlockRight ? EnumBlockType.NORMAL_BLOCK.getValue() : '-');
                    } else {
                        for (int j = -1; j < 2; j++) {
                            currentLevel.add(addIndex + j, '-');
                        }
                    }
                }
                else {
                    for (int j = -1; j < 2; j++) {
                        currentLevel.add(addIndex + j, '-');
                    }
                }
            }
        }
    }

    private void addObstacle(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto) {
        for (int k = 0; k < pcgLevelDto.getRamps(); k++) {
            int maxIndex = levels.get(0).size() - (levels.get(0).size() - getFlagIndex()) - offsetFromFlag;
            int addIndex = rand.nextInt(offsetFromStart, maxIndex);
            if(!doIsValidToAdd(addIndex, 4)){
                return;
            }
            int pattern = rand.nextInt(0, 2);
            switch (pattern){
                case 0:
                    doAddRamp1(addIndex);
                    boolean secondPyramid = rand.nextInt(2) == 0;
                    if(secondPyramid){
                        doAddRamp2(addIndex);
                    }
                    continue;
                case 1:
                    doAddObstacle(addIndex);
            }
        }
    }

    private void doAddObstacle(int addIndex) {
        // Random number of vertical obstacles (1 to 4)
        int obstacleCount = rand.nextInt(1, 5);
        int currentIndex = addIndex;

        for (int i = 0; i < obstacleCount; i++) {
            int obstacleHeight = rand.nextInt(9, 13);

            // Create one vertical obstacle at currentIndex
            for (int height = 0; height < levels.size(); height++) {
                ArrayList<Character> currentLevel = levels.get(height);

                if (height == GROUND_1_LEVEL || height == GROUND_2_LEVEL) {
                    currentLevel.add(currentIndex, 'X');
                } else if (height < GROUND_1_LEVEL && height > obstacleHeight) {
                    currentLevel.add(currentIndex, '#');
                } else {
                    currentLevel.add(currentIndex, '-');
                }
            }

            // Move to next obstacle position with random spacing (1–2)
            currentIndex += rand.nextInt(1, 3);
        }
    }

    private void doAddRamp2(int addIndex){
        int maxHeight = rand.nextInt(1,5);
        for (int height = 0; height < levels.size(); height++) {
            // Get the current level's list once and reuse it
            ArrayList<Character> currentLevel = levels.get(height);
            if(height == GROUND_1_LEVEL || height == GROUND_2_LEVEL) {
                currentLevel.add(addIndex + 5,'X');
                currentLevel.add(addIndex + 6,'X');
                currentLevel.add(addIndex + 7,'X');
                currentLevel.add(addIndex + 8,'X');
                currentLevel.add(addIndex + 9,'X');
                currentLevel.add(addIndex + 10,'X');
            } else if (height == LAN_LEVEL - 3 && maxHeight > 3) {
                currentLevel.add(addIndex + 5,'-');
                currentLevel.add(addIndex + 6,'-');
                currentLevel.add(addIndex + 7,'#');
                currentLevel.add(addIndex + 8,'-');
                currentLevel.add(addIndex + 9,'-');
                currentLevel.add(addIndex + 10,'-');
            } else if (height == LAN_LEVEL - 2 && maxHeight > 2) {
                currentLevel.add(addIndex + 5,'-');
                currentLevel.add(addIndex + 6,'-');
                currentLevel.add(addIndex + 7,'#');
                currentLevel.add(addIndex + 8,'#');
                currentLevel.add(addIndex + 9,'-');
                currentLevel.add(addIndex + 10,'-');
            } else if (height == LAN_LEVEL - 1 && maxHeight > 1) {
                currentLevel.add(addIndex + 5,'-');
                currentLevel.add(addIndex + 6,'-');
                currentLevel.add(addIndex + 7,'#');
                currentLevel.add(addIndex + 8,'#');
                currentLevel.add(addIndex + 9,'#');
                currentLevel.add(addIndex + 10,'-');
            } else if (height == LAN_LEVEL && maxHeight > 0) {
                currentLevel.add(addIndex + 5,'-');
                currentLevel.add(addIndex + 6,'-');
                currentLevel.add(addIndex + 7,'#');
                currentLevel.add(addIndex + 8,'#');
                currentLevel.add(addIndex + 9,'#');
                currentLevel.add(addIndex + 10,'#');
            } else{
                currentLevel.add(addIndex + 5,'-');
                currentLevel.add(addIndex + 6,'-');
                currentLevel.add(addIndex + 7,'-');
                currentLevel.add(addIndex + 8,'-');
                currentLevel.add(addIndex + 9,'-');
                currentLevel.add(addIndex + 10,'-');
            }
        }
    }

    private void doAddRamp1(int addIndex){
        boolean shape = rand.nextInt(2) == 0;
        int maxHeight = rand.nextInt(1,5);
        for (int height = 0; height < levels.size(); height++) {
            ArrayList<Character> currentLevel = levels.get(height);
            if(height == GROUND_1_LEVEL || height == GROUND_2_LEVEL) {
                currentLevel.add(addIndex, 'X');
                currentLevel.add(addIndex + 1,'X');
                currentLevel.add(addIndex + 2,'X');
                currentLevel.add(addIndex + 3,'X');
                if(shape)
                {
                    currentLevel.add(addIndex + 4, 'X');
                }
            } else if (height == LAN_LEVEL - 3 && maxHeight > 3) {
                currentLevel.add(addIndex,'-');
                currentLevel.add(addIndex + 1,'-');
                currentLevel.add(addIndex + 2,'-');
                currentLevel.add(addIndex + 3,'#');
                if(shape)
                {
                    currentLevel.add(addIndex + 4,'#');
                }
            } else if (height == LAN_LEVEL - 2 && maxHeight > 2) {
                currentLevel.add(addIndex,'-');
                currentLevel.add(addIndex + 1,'-');
                currentLevel.add(addIndex + 2,'#');
                currentLevel.add(addIndex + 3,'#');
                if(shape)
                {
                    currentLevel.add(addIndex + 4,'#');
                }
            } else if (height == LAN_LEVEL - 1 && maxHeight > 1) {
                currentLevel.add(addIndex,'-');
                currentLevel.add(addIndex + 1,'#');
                currentLevel.add(addIndex + 2,'#');
                currentLevel.add(addIndex + 3,'#');
                if(shape)
                {
                    currentLevel.add(addIndex + 4,'#');
                }
            } else if (height == LAN_LEVEL && maxHeight > 0) {
                currentLevel.add(addIndex,'#');
                currentLevel.add(addIndex + 1,'#');
                currentLevel.add(addIndex + 2,'#');
                currentLevel.add(addIndex + 3,'#');
                if(shape)
                {
                    currentLevel.add(addIndex + 4,'#');
                }
            } else{
                currentLevel.add(addIndex,'-');
                currentLevel.add(addIndex + 1,'-');
                currentLevel.add(addIndex + 2,'-');
                currentLevel.add(addIndex + 3,'-');
                if(shape)
                {
                    currentLevel.add(addIndex + 4,'-');
                }
            }
        }
    }

    public void addPipe(int offsetFromStart, int offsetFromFlag, PCGLevelDto pcgLevelDto){
        for (int k = 0; k < pcgLevelDto.getPipes(); k++) {
            int pipeHeight = rand.nextInt(pcgLevelDto.getPipesMinHeight(), pcgLevelDto.getPipesMaxHeight());
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
        return doIsValidToAdd(addIndex, checkLength);
    }

    private boolean doIsValidToAdd(int addIndex, int checkLength) {
        // check pit level 1
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
