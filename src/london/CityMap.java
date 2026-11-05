package london;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The assignment's schematic, not a geographically accurate transport network. */
public final class CityMap {
    public record Station(int id, String name, double x, double y, int labelDx, int labelDy) {
        @Override public String toString() { return name; }
    }
    public record Road(int from, int to, String color) {}
    public static final int GREEN_PARK = 0;
    public static final int TOWER_HILL = 13;

    private final List<Station> stations = new ArrayList<>();
    private final List<Road> roads = new ArrayList<>();
    private final int[][] neighbours = new int[14][4];

    public CityMap() {
        for (int[] row : neighbours) Arrays.fill(row, -1);
        add("Green Park", 85, 465, -35, 34);
        add("Piccadilly Circus", 235, 420, -65, 33);
        add("Covent Garden", 405, 365, -88, 30);
        add("Chancery Lane", 485, 255, -65, -29);
        add("King's Cross", 330, 70, -54, -29);
        add("Baker Street", 105, 55, -47, -29);
        add("Bond Street", 85, 235, -42, -29);
        add("Oxford Circus", 265, 250, -52, 34);
        add("Old Street", 595, 70, -38, -29);
        add("Moorgate", 660, 155, 13, -19);
        add("Bank", 710, 255, 13, -18);
        add("Liverpool Street", 875, 205, -47, -29);
        add("Monument", 770, 375, 15, 8);
        add("Tower Hill", 965, 340, -38, 42);

        connect(5, 4, Direction.EAST, "Red");
        connect(4, 9, Direction.EAST, "Red");
        // Two western approaches meet at Liverpool Street. Give the curved red
        // approach its own north port; the yellow approach uses the west port.
        connect(9, 11, Direction.EAST, Direction.NORTH, "Red");
        connect(11, 13, Direction.SOUTH, "Red");
        connect(6, 7, Direction.EAST, "Yellow");
        connect(7, 3, Direction.EAST, "Yellow");
        connect(3, 10, Direction.EAST, "Yellow");
        connect(10, 11, Direction.EAST, "Yellow");
        connect(0, 1, Direction.EAST, "Green");
        connect(1, 2, Direction.EAST, "Green");
        connect(2, 3, Direction.NORTH, "Green");
        connect(3, 4, Direction.NORTH, "Green");
        connect(8, 9, Direction.SOUTH, "Blue");
        connect(9, 10, Direction.SOUTH, "Blue");
        connect(10, 12, Direction.SOUTH, "Blue");
    }

    private void add(String name, double x, double y, int dx, int dy) {
        stations.add(new Station(stations.size(), name, x, y, dx, dy));
    }

    private void connect(int from, int to, Direction bearing, String color) {
        connect(from, to, bearing, bearing.turn(2), color);
    }

    private void connect(int from, int to, Direction bearing, Direction reverse, String color) {
        if (neighbours[from][bearing.ordinal()] != -1 || neighbours[to][reverse.ordinal()] != -1)
            throw new IllegalStateException("Two roads share the same compass bearing");
        neighbours[from][bearing.ordinal()] = to;
        neighbours[to][reverse.ordinal()] = from;
        roads.add(new Road(from, to, color));
    }

    public int size() { return stations.size(); }
    public int stateCount() { return 4 * size(); }
    public Station station(int id) { return stations.get(id); }
    public List<Station> stations() { return List.copyOf(stations); }
    public List<Road> roads() { return List.copyOf(roads); }
    public int neighbour(int station, Direction bearing) { return neighbours[station][bearing.ordinal()]; }
    public boolean terminal(State state) { return state.station() == TOWER_HILL; }

    public Direction direction(int from, int to) {
        for (Direction d : Direction.values()) if (neighbour(from, d) == to) return d;
        throw new IllegalArgumentException("Stations are not connected");
    }

    public int[] distancesToGoal() {
        int[] distances = new int[size()];
        Arrays.fill(distances, -1);
        distances[TOWER_HILL] = 0;
        var queue = new ArrayDeque<Integer>();
        queue.add(TOWER_HILL);
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int next : neighbours[current]) if (next >= 0 && distances[next] < 0) {
                distances[next] = distances[current] + 1;
                queue.add(next);
            }
        }
        return distances;
    }

    public List<Integer> shortestPath(int start) {
        int[] distances = distancesToGoal();
        var path = new ArrayList<Integer>();
        path.add(start);
        while (start != TOWER_HILL) {
            boolean found = false;
            for (Direction d : Direction.values()) {
                int next = neighbour(start, d);
                if (next >= 0 && distances[next] == distances[start] - 1) {
                    start = next;
                    path.add(start);
                    found = true;
                    break;
                }
            }
            if (!found) throw new IllegalStateException("Goal is unreachable");
        }
        return List.copyOf(path);
    }
}
