package reinforment;

import engine.helper.TileFeature;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Objects;

public class Point {
    private int x;
    private int y;

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Point(int x, int y){
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        // 1. Check if it's the exact same object instance.
        if (this == obj) {
            return true;
        }
        // 2. Check if the other object is null or of a different class.
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        // 3. Cast the object to a Point and compare the x and y values.
        Point point = (Point) obj;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        // Use the modern Objects.hash() utility to generate a hash code
        // based on the values of the x and y fields.
        return Objects.hash(x, y);
    }
}
