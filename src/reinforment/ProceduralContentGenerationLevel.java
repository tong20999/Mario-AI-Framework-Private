package reinforment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LevelObject {
    private static final Random rand = new Random();
    private static final int LAN_LEVEL = 13;
    private static final int GROUND_1_LEVEL = 14;
    private static final int GROUND_2_LEVEL = 15;
    private static final int FLAG_LEVEL = 12;
    private final Map<Integer, ArrayList<Character>> levels = new HashMap<>();
    private final int _indexOfFlag;

    public String toContent(){
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

        return contentBuilder.toString();
    }

    public LevelObject(String content){
        String[] contents = content.split("\r\n");
        _indexOfFlag = contents[FLAG_LEVEL].indexOf('F');
        for (int i = 0; i < contents.length; i++) {
            ArrayList<Character> line = new ArrayList<>();
            for (int j = 0; j < contents[i].length(); j++) {
                line.add(contents[i].charAt(j));
            }
            levels.put(i, line);
        }

    }

    public void addEmpty(int index){

    }

    public void addEnemy(int index){
        for (int i = 0; i < levels.size(); i++) {
            if(i == GROUND_1_LEVEL || i == GROUND_2_LEVEL){
                levels.get(i).add(index, 'X');
            } else if (i == LAN_LEVEL) {
                levels.get(i).add(index, 'g');
            } else {
                levels.get(i).add(index, '-');
            }
        }
    }

    public void addPit(int index, int maxWidth){

        for (int i = 0; i < levels.size(); i++) {
            // Get the current level's list once and reuse it
            ArrayList<Character> currentLevel = levels.get(i);
            currentLevel.add(index, '-');
            currentLevel.add(index + 1, '-');
        }
    }

    public void addPipe(int min, int minFromFlag){
        int height = rand.nextInt(2, 5);
        int maxIndex = levels.get(0).size() - (levels.get(0).size() - _indexOfFlag) - minFromFlag;
        int addIndex = rand.nextInt(min, maxIndex);
        for (int i = 0; i < levels.size(); i++) {
            // Determine if this level should be a pipe
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
