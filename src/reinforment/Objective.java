package reinforment;

import engine.sprites.Enemy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Objective {
    // preserves insertion order
    private final Map<Point, Integer> positionMap = new LinkedHashMap<>();
    private int maxItem = 30;
    public int[] getObjectives() {
        int[] result = new int[maxItem];
        Arrays.fill(result, -1); // default = undefined

        int i = 0;
        for (Integer value : positionMap.values()) {
            if (i >= maxItem) break; // safety
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
        if (positionMap.size() < maxItem) { // enforce max 50
            positionMap.put(point, 1);
        }
    }

    public void addAll(List<Point> points) {
        for (Point p : points) {
            if (positionMap.size() >= maxItem) {
                break;
            }
            positionMap.put(p, 1);
        }
    }

    public void addAllEnemy(List<Enemy> enemies) {
        for (Enemy e : enemies) {
            if (positionMap.size() >= maxItem) {
                break;
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


