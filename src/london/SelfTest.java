package london;

import java.util.List;
import java.util.Random;
import javax.swing.SwingUtilities;

/** Dependency-free executable checks. Failures produce a nonzero process exit. */
public final class SelfTest {
    private static int checks;
    private SelfTest() {}
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void close(double a, double b, double tolerance, String message) {
        check(Math.abs(a - b) <= tolerance, message + ": " + a + " vs " + b);
    }
    private static void passed(String text) { System.out.println("PASS  " + text); }

    public static void run() throws Exception {
        CityMap map = new CityMap();
        check(map.size() == 14 && map.roads().size() == 15, "Schematic topology");
        int[] distances = map.distancesToGoal();
        check(distances[CityMap.GREEN_PARK] == 6, "Six-edge reference route from Green Park");
        for (int d : distances) check(d >= 0, "Graph is connected to the goal");
        for (var road : map.roads()) {
            Direction direction = map.direction(road.from(), road.to());
            check(map.neighbour(road.to(), map.direction(road.to(), road.from())) == road.from(), "Bidirectional edge");
        }
        // Four colored chains are transcribed explicitly, independently of the constructor order.
        int[][] chains = {{5,4,9,11,13}, {6,7,3,10,11}, {0,1,2,3,4}, {8,9,10,12}};
        for (int[] chain : chains) for (int i = 1; i < chain.length; i++) map.direction(chain[i-1], chain[i]);
        passed("14 stations, 15 bidirectional roads, four source-map chains, connected goal");

        for (Direction facing : Direction.values()) for (Direction target : Direction.values())
            check(Action.towards(facing, target).intended(facing) == target, "Relative action inversion");
        for (double p : new double[]{0, 0.01, 0.4, 0.8, 1}) {
            NavigationModel model = new NavigationModel(map, p);
            for (int i = 0; i < map.stateCount(); i++) for (Action action : Action.values()) {
                State state = State.fromIndex(i); double sum = 0;
                for (var out : model.outcomes(state, action)) {
                    sum += out.probability(); check(out.probability() > 0, "Positive retained transition");
                    if (map.terminal(state)) check(out.next().equals(state), "Absorbing goal, heading unchanged");
                    else {
                        int next = map.neighbour(state.station(), out.actual());
                        check(out.next().station() == (next < 0 ? state.station() : next), "No teleportation");
                        check(out.next().facing() == (next < 0 ? out.actual() : map.direction(next, state.station()).turn(2)), "Arrival heading follows the road's entry port");
                    }
                }
                close(sum, 1, 1e-12, "Normalized transition probabilities");
            }
        }
        NavigationModel deterministic = new NavigationModel(map, 1);
        var blocked = deterministic.sample(new State(5, Direction.EAST), Action.LEFT, new Random(1));
        check(blocked.blocked() && !blocked.slipped() && blocked.after().facing() == Direction.NORTH,
                "Blocked attempt turns and stays; blocked is different from slipped");
        var curved = deterministic.sample(new State(9, Direction.EAST), Action.FORWARD, new Random(1));
        check(curved.after().station() == 11 && curved.after().facing() == Direction.SOUTH,
                "Curved Moorgate-Liverpool road arrives facing south");
        boolean rejected = false;
        try { new NavigationModel(map, Double.NaN); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject invalid probability");
        passed("Relative actions, normalized transitions, blocked moves, observed heading, absorbing goal");

        ValueIteration deterministicPlanner = new ValueIteration(deterministic);
        for (int i = 0; i < map.stateCount(); i++) {
            State state = State.fromIndex(i);
            close(deterministicPlanner.value(state), distances[state.station()], 1e-9, "Deterministic Bellman value equals BFS distance");
            var episode = Simulation.episode(deterministic, deterministicPlanner, state, 42, 100);
            check(episode.reachedGoal() && episode.steps() == distances[state.station()], "Optimal deterministic rollout");
            check(episode.blocked() == 0 && episode.slips() == 0, "No wasted deterministic moves");
        }
        passed("All 56 deterministic states agree with independent breadth-first distances");

        for (double p : new double[]{0, 0.2, 0.4, 0.8, 1}) {
            NavigationModel model = new NavigationModel(map, p);
            ValueIteration planner = new ValueIteration(model);
            double[] exact = PolicyEvaluation.exact(model, planner);
            double[] bfs = PolicyEvaluation.exact(model, Policy.shortestPath(map));
            double[] random = PolicyEvaluation.exact(model, Policy.random());
            check(planner.residual() < 1e-9, "Small Bellman residual");
            for (int i = 0; i < map.stateCount(); i++) {
                State state = State.fromIndex(i);
                close(planner.value(state), exact[i], 2e-7, "Independent linear-equation policy evaluation");
                check(exact[i] <= bfs[i] + 1e-7 && exact[i] <= random[i] + 1e-7, "Optimal policy dominates both baselines");
                for (Action action : Action.values())
                    check(planner.q(state, planner.action(state)) <= planner.q(state, action) + 1e-9, "Policy is Bellman-greedy");
            }
        }
        passed("Optimality and independent (I-P)J=1 check at p=0, 0.2, 0.4, 0.8, 1");
        check(Double.isInfinite(PolicyEvaluation.exact(new NavigationModel(map, 0), Policy.shortestPath(map))[0]),
                "BFS cannot reach the goal when p=0; infinite expectation is represented explicitly");

        NavigationModel stochastic = new NavigationModel(map, .8);
        Random random = new Random(1701);
        int[] frequency = new int[4];
        for (int i = 0; i < 100_000; i++) {
            var step = stochastic.sample(new State(10, Direction.EAST), Action.FORWARD, random);
            frequency[step.actual().ordinal()]++;
        }
        close(frequency[Direction.EAST.ordinal()] / 100_000.0, .8, .008, "Intended frequency");
        close(frequency[Direction.NORTH.ordinal()] / 100_000.0, .1, .008, "Left-slip frequency");
        close(frequency[Direction.SOUTH.ordinal()] / 100_000.0, .1, .008, "Right-slip frequency");
        check(frequency[Direction.WEST.ordinal()] == 0, "No direct backward execution error");
        ValueIteration planner = new ValueIteration(stochastic);
        State start = new State(0, Direction.EAST);
        var a = Simulation.episode(stochastic, planner, start, 42, 5000);
        var b = Simulation.episode(stochastic, planner, start, 42, 5000);
        check(a.equals(b), "Seed reproducibility including full trace");
        var limited = Simulation.episode(stochastic, planner, start, 42, 1);
        check(!limited.reachedGoal() && limited.steps() == 1, "Truncation is reported as a failure");
        var summary = Simulation.batch(stochastic, planner, start, 42, 10_000, 5000);
        check(summary.successes() == 10_000, "All sampled optimal episodes reach the goal");
        close(summary.mean(), summary.exact(), 6 * summary.se() + .03, "Monte Carlo agrees with exact expectation");
        passed(String.format(java.util.Locale.ROOT, "100,000 transition samples; reproducible traces; Monte Carlo mean %.4f vs exact %.4f", summary.mean(), summary.exact()));

        SwingUtilities.invokeAndWait(() -> {
            LondonPanel.configureTheme(); LondonPanel panel = new LondonPanel();
            panel.setSize(1380, 900); LondonPanel.layoutTree(panel);
            panel.testSettings(0, 1); check(panel.moveCount() == 0, "GUI reset clears trace");
            for (int i = 0; i < 6; i++) panel.testStep();
            check(panel.currentState().station() == CityMap.TOWER_HILL && !panel.canMove(), "Actual Swing step button reaches terminal and disables motion");
            panel.testStep(); check(panel.moveCount() == 6, "No post-terminal steps");
            panel.testReset(); check(panel.moveCount() == 0 && panel.canMove(), "GUI reset restores controls");
            panel.testSettings(CityMap.TOWER_HILL, .8);
            check(panel.moveCount() == 0 && !panel.canMove(), "Start-at-goal GUI handling");
            panel.shutdown();
        });
        passed("Headless Swing integration: real step/reset controls, terminal disabling, start at goal");
        System.out.println("ALL CHECKS PASSED (" + checks + " assertions)");
    }
}
