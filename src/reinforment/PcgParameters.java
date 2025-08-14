package reinforment;

public class PcgDto {
    private String content;
    private int width;

    public int getWidth() {
        return width;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public boolean isRandomBlockHeight() {
        return randomBlockHeight;
    }

    public int getEnemyCount() {
        return enemyCount;
    }

    public boolean isRandomEnemyHeight() {
        return randomEnemyHeight;
    }

    public int getCoinCount() {
        return coinCount;
    }

    public boolean isRandomCoinHeight() {
        return randomCoinHeight;
    }

    public int getPitCount() {
        return pitCount;
    }

    public int getPipeCount() {
        return pipeCount;
    }

    public int getRampCount() {
        return rampCount;
    }

    public int getWidthMin() {
        return widthMin;
    }

    public int getWidthMax() {
        return widthMax;
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

    public String getContent() {
        return content;
    }

    public PcgDto(String content, int width, int blockCount, boolean randomBlockHeight, int enemyCount, boolean randomEnemyHeight, int coinCount, boolean randomCoinHeight, int pitCount, int pipeCount, int rampCount, int widthMin, int widthMax) {
        this.content = content;
        this.width = width;
        this.blockCount = blockCount;
        this.randomBlockHeight = randomBlockHeight;
        this.enemyCount = enemyCount;
        this.randomEnemyHeight = randomEnemyHeight;
        this.coinCount = coinCount;
        this.randomCoinHeight = randomCoinHeight;
        this.pitCount = pitCount;
        this.pipeCount = pipeCount;
        this.rampCount = rampCount;
        this.widthMin = widthMin;
        this.widthMax = widthMax;
    }

    public PcgDto(String content, int width){
        this.content = content;
        this.width = width;
    }
}
