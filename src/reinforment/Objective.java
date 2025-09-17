package reinforment;

import engine.sprites.Enemy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Objective {
    // preserves insertion order
    private final Map<Point, Integer> positionMap = new LinkedHashMap<>();

    public int[] getObjectives() {
        int[] result = new int[50];
        Arrays.fill(result, -1); // default = undefined

        int i = 0;
        for (Integer value : positionMap.values()) {
            if (i >= 50) break; // safety
            result[i++] = value; // 1 (active) or 0 (marked)
        }

        return result;
    }

    @Override
    public Objective clone() {
        Objective copy = new Objective();
        for (Map.Entry<Point, Integer> entry : this.positionMap.entrySet()) {
            // Since Point is immutable (only x,y + getters), we can safely reuse it.
            // If you want true deep copy, you could do: new Point(entry.getKey().getX(), entry.getKey().getY())
            copy.positionMap.put(
                    new Point(entry.getKey().getX(), entry.getKey().getY()),
                    entry.getValue()
            );
        }
        return copy;
    }

    public void add(Point point) {
        if (positionMap.size() < 50) { // enforce max 50
            positionMap.put(point, 1);
        }
    }

    public void addAll(List<Point> points) {
        for (Point p : points) {
            if (positionMap.size() >= 50) {
                break; // enforce max 50
            }
            positionMap.put(p, 1);
        }
    }

    public void addAllEnemy(List<Enemy> enemies) {
        for (Enemy e : enemies) {
            if (positionMap.size() >= 50) {
                break; // enforce max 50
            }
            positionMap.put(new Point((int)e.x, (int)e.y), 1);
        }
    }

    public void mark(Point point) {
        if (positionMap.containsKey(point)) {
            positionMap.put(point, 0);
        } else {
            throw new ArrayIndexOutOfBoundsException("Point not found in objectives: " + point.getX() + "," + point.getY());
        }
    }

    public void mark(String sprintCode) {
        String[] parts = sprintCode.split("_");
        var point = new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        if (positionMap.containsKey(point)) {
            positionMap.put(point, 0);
        } else {
            throw new ArrayIndexOutOfBoundsException("Point not found in objectives: " + point.getX() + "," + point.getY());
        }
    }
}


