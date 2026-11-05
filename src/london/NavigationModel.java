package london;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Explicit stochastic transition model shared by the planner and simulator. */
public final class NavigationModel {
    public record Outcome(State next, Direction actual, double probability) {}
    public record Step(State before, Action action, Direction intended, Direction actual,
                       State after, boolean slipped, boolean blocked) {}

    private final CityMap map;
    private final double reliability;

    public NavigationModel(CityMap map, double reliability) {
        if (!Double.isFinite(reliability) || reliability < 0 || reliability > 1)
            throw new IllegalArgumentException("Reliability must be between 0 and 1");
        this.map = java.util.Objects.requireNonNull(map);
        this.reliability = reliability;
    }

    public CityMap map() { return map; }
    public double reliability() { return reliability; }

    private State move(State state, Direction bearing) {
        int next = map.neighbour(state.station(), bearing);
        // A blocked attempt turns in place. A curved road can change the arrival
        // heading: face away from the destination's entry port.
        return new State(next < 0 ? state.station() : next,
                next < 0 ? bearing : map.direction(next, state.station()).turn(2));
    }

    public List<Outcome> outcomes(State state, Action action) {
        if (map.terminal(state)) return List.of(new Outcome(state, state.facing(), 1.0));
        Direction intended = action.intended(state.facing());
        var list = new ArrayList<Outcome>(3);
        double sideways = (1.0 - reliability) / 2.0;
        if (reliability > 0) list.add(new Outcome(move(state, intended), intended, reliability));
        if (sideways > 0) {
            list.add(new Outcome(move(state, intended.turn(-1)), intended.turn(-1), sideways));
            list.add(new Outcome(move(state, intended.turn(1)), intended.turn(1), sideways));
        }
        return List.copyOf(list);
    }

    public Step sample(State state, Action action, Random random) {
        if (map.terminal(state)) throw new IllegalStateException("The episode has already finished");
        Direction intended = action.intended(state.facing());
        double draw = random.nextDouble();
        Direction actual = draw < reliability ? intended
                : draw < reliability + (1 - reliability) / 2 ? intended.turn(-1) : intended.turn(1);
        State next = move(state, actual);
        return new Step(state, action, intended, actual, next,
                intended != actual, state.station() == next.station());
    }
}
