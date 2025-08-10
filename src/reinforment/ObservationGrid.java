package reinforment;

public class ObservationGrid {
    private  byte[] solid;
    private byte[] semiSolid;
    private byte[] collectible;

    private byte[] stompableEnemy;
    private byte[] unstompableEnemy;

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

    public ObservationGrid(byte[] solid, byte[] semiSolid, byte[] collectible, byte[] stompableEnemy, byte[] unstompableEnemy){
        this.solid = solid;
        this.semiSolid = semiSolid;
        this.collectible = collectible;
        this.stompableEnemy = stompableEnemy;
        this.unstompableEnemy = unstompableEnemy;
    }
}
