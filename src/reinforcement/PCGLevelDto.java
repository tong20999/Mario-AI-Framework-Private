package reinforcement;

import com.google.gson.annotations.SerializedName;

public class PCGLevelDto {
    @SerializedName("WidthMin")
    private final int widthMin;

    @SerializedName("WidthMax")
    private final int widthMax;

    @SerializedName("TimerMin")
    private final int timerMin;

    @SerializedName("TimerMax")
    private final int timerMax;

    @SerializedName("Blocks")
    private final int blocks;

    @SerializedName("BlocksHeightOrigin")
    private final int blocksHeightOrigin;

    @SerializedName("BlocksHeightBound")
    private final int blocksHeightBound;

    @SerializedName("Coins")
    private final int coins;

    @SerializedName("CoinsHeightOrigin")
    private final int coinsHeightOrigin;

    @SerializedName("CoinsHeightBound")
    private final int coinsHeightBound;

    @SerializedName("Enemies")
    private final int enemies;

    @SerializedName("EnemiesHeightOrigin")
    private final int enemiesHeightOrigin;

    @SerializedName("EnemiesHeightBound")
    private final int enemiesHeightBound;

    @SerializedName("Pits")
    private final int pits;

    @SerializedName("PitsMinWidth")
    private final int pitsMinWidth;

    @SerializedName("PitsMaxWidth")
    private final int pitsMaxWidth;

    @SerializedName("Pipes")
    private final int pipes;

    @SerializedName("PipesMinHeight")
    private final int pipesMinHeight;

    @SerializedName("PipesMaxHeight")
    private final int pipesMaxHeight;

    @SerializedName("Ramps")
    private final int ramps;

    @SerializedName("Fps")
    private final int fps;

    @SerializedName("File")
    private final String file;

    @SerializedName("RandomNormalBlockLeft")
    private final boolean randomNormalBlockLeft;

    @SerializedName("RandomNormalBlockRight")
    private final boolean randomNormalBlockRight;

    @SerializedName("SpawnBlockCenter")
    private final boolean spawnBlockCenter;

    @SerializedName("IsTrainFailedLevel")
    private final boolean isTrainFailedLevel;

    @SerializedName("LevelId")
    private final int levelId;

    public PCGLevelDto(int widthMin, int widthMax, int timerMin, int timerMax, int blocks,
                       int blocksHeightOrigin, int blocksHeightBound,
                       boolean randomNormalBlockLeft, boolean randomNormalBlockRight,
                       boolean spawnBlockCenter,
                       int coins, int coinsHeightOrigin,
                       int coinsHeightBound, int enemies, int enemiesHeightOrigin, int enemiesHeightBound,
                       int pits, int pitsMinWidth, int pitsMaxWidth, int pipes, int pipesMinHeight, int pipesMaxHeight,
                       int ramps, int fps, String file, boolean trainFailedLevel, boolean isTest, int levelId) {
        this.widthMin = widthMin;
        this.widthMax = widthMax;
        this.timerMin = timerMin;
        this.timerMax = timerMax;
        this.blocks = blocks;
        this.blocksHeightOrigin = blocksHeightOrigin;
        this.blocksHeightBound = blocksHeightBound;
        this.spawnBlockCenter = spawnBlockCenter;
        this.randomNormalBlockLeft = randomNormalBlockLeft;
        this.randomNormalBlockRight = randomNormalBlockRight;
        this.coins = coins;
        this.coinsHeightOrigin = coinsHeightOrigin;
        this.coinsHeightBound = coinsHeightBound;
        this.enemies = enemies;
        this.enemiesHeightOrigin = enemiesHeightOrigin;
        this.enemiesHeightBound = enemiesHeightBound;
        this.pits = pits;
        this.pitsMinWidth = pitsMinWidth;
        this.pitsMaxWidth = pitsMaxWidth;
        this.pipes = pipes;
        this.pipesMinHeight = pipesMinHeight;
        this.pipesMaxHeight = pipesMaxHeight;
        this.ramps = ramps;
        this.fps = fps;
        this.file = file;
        this.isTrainFailedLevel = trainFailedLevel;
        this.levelId = levelId;
    }

    public int getWidthMin() {
        return widthMin;
    }

    public int getWidthMax() {
        return widthMax;
    }

    public int getTimerMin() {
        return timerMin;
    }

    public int getTimerMax() {
        return timerMax;
    }

    public int getBlocks() {
        return blocks;
    }

    public int getBlocksHeightOrigin() {
        return blocksHeightOrigin;
    }

    public int getBlocksHeightBound() {
        return blocksHeightBound;
    }

    public int getCoins() {
        return coins;
    }

    public int getCoinsHeightOrigin() {
        return coinsHeightOrigin;
    }

    public int getCoinsHeightBound() {
        return coinsHeightBound;
    }

    public int getEnemies() {
        return enemies;
    }

    public int getEnemiesHeightOrigin() {
        return enemiesHeightOrigin;
    }

    public int getEnemiesHeightBound() {
        return enemiesHeightBound;
    }

    public int getPits() {
        return pits;
    }

    public int getPitsMinWidth() {
        return pitsMinWidth;
    }

    public int getPitsMaxWidth() {
        return pitsMaxWidth;
    }

    public int getPipes() {
        return pipes;
    }

    public int getPipesMinHeight() {
        return pipesMinHeight;
    }

    public int getPipesMaxHeight() {
        return pipesMaxHeight;
    }

    public int getRamps() {
        return ramps;
    }

    public int getFps() {
        return fps;
    }

    public String getFile() {
        return file;
    }

    public boolean isRandomNormalBlockLeft() {
        return randomNormalBlockLeft;
    }

    public boolean isRandomNormalBlockRight() {
        return randomNormalBlockRight;
    }

    public boolean isSpawnBlockCenter() {
        return spawnBlockCenter;
    }

    public boolean isTrainFailedLevel() {
        return isTrainFailedLevel;
    }

    public int getLevelId() {
        return levelId;
    }
}