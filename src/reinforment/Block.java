package reinforment;

import engine.helper.TileFeature;

import java.util.ArrayList;

public class Block {
    int x;
    int y;

    ArrayList<TileFeature> tileFeatures;

    public ArrayList<TileFeature> getTileFeatures() {
        return tileFeatures;
    }
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Block(int x, int y, ArrayList<TileFeature> tileFeatures){
        this.x = x;
        this.y = y;
        this.tileFeatures = tileFeatures;
    }
}
