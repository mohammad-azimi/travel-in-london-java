package london;

import java.util.Random;

/** A stationary policy, including the uniform-random comparison controller. */
public interface Policy {
    String name();
    double probability(State state, Action action);

    default Action choose(State state, Random random) {
        for (Action action : Action.values()) if (probability(state, action) == 1.0) return action;
        double draw = random.nextDouble(), sum = 0;
        for (Action action : Action.values()) {
            sum += probability(state, action);
            if (draw < sum) return action;
        }
        throw new IllegalStateException("Invalid action probabilities");
    }

    static Policy shortestPath(CityMap map) {
        int[] distances = map.distancesToGoal();
        return new Policy() {
            public String name() { return "Shortest path (BFS)"; }
            private Action best(State state) {
                for (Direction direction : Direction.values()) {
                    int next = map.neighbour(state.station(), direction);
                    if (next >= 0 && distances[next] == distances[state.station()] - 1)
                        return Action.towards(state.facing(), direction);
                }
                return Action.FORWARD; // Only queried at the terminal by generic tables.
            }
            public double probability(State state, Action action) { return best(state) == action ? 1 : 0; }
        };
    }

    static Policy random() {
        return new Policy() {
            public String name() { return "Uniform random"; }
            public double probability(State state, Action action) { return 0.25; }
        };
    }
}
