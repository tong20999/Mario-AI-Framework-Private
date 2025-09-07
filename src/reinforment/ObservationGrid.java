package reinforment;

public class ObservationGrid {
    private  byte[] solid;
    private byte[] collectible;

    private byte[] stompableEnemy;
    private byte[] unstompableEnemy;

    private byte[] flags;
    private byte[] blocks;
    private byte[] coins;

    public byte[] getStompableEnemy() {
        return stompableEnemy;
    }

    public byte[] getUnstompableEnemy() {
        return unstompableEnemy;
    }

    public byte[] getSolid() {
        return solid;
    }

    public byte[] getCollectible() {
        return collectible;
    }

    public byte[] getFlag() {
        return flags;
    }

    public byte[] getBlocks() {
        return blocks;
    }

    public byte[] getCoins() {
        return coins;
    }

    public ObservationGrid(byte[] solid, byte[] collectible, byte[] flags,
                           byte[] blocks, byte[] coins, byte[] stompableEnemy, byte[] unstompableEnemy){
        this.solid = solid;
        this.collectible = collectible;
        this.flags = flags;
        this.blocks = blocks;
        this.coins = coins;
        this.stompableEnemy = stompableEnemy;
        this.unstompableEnemy = unstompableEnemy;
    }
}
