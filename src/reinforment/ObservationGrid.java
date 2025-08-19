package reinforment;

public class ObservationGrid {
    private  byte[] solid;
    private byte[] semiSolid;
    private byte[] collectible;

    private byte[] stompableEnemy;
    private byte[] unstompableEnemy;

    private byte[] flags;
    private byte[] visited;
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

    public byte[] getSemiSolid() {
        return semiSolid;
    }
    public byte[] getFlag() {
        return flags;
    }
    public byte[] getVisited() {
        return visited;
    }

    public byte[] getBlocks() {
        return blocks;
    }

    public byte[] getCoins() {
        return coins;
    }

    public ObservationGrid(byte[] solid, byte[] semiSolid, byte[] collectible, byte[] flags, byte[] visited,
                           byte[] blocks, byte[] coins, byte[] stompableEnemy, byte[] unstompableEnemy){
        this.solid = solid;
        this.semiSolid = semiSolid;
        this.collectible = collectible;
        this.flags = flags;
        this.visited = visited;
        this.blocks = blocks;
        this.coins = coins;
        this.stompableEnemy = stompableEnemy;
        this.unstompableEnemy = unstompableEnemy;
    }
}
