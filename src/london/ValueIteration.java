package london;

/** Undiscounted stochastic shortest path: J(s)=min_a [1 + sum P(s'|s,a)J(s')]. */
public final class ValueIteration implements Policy {
    public static final double TOLERANCE = 1e-10;
    private final NavigationModel model;
    private final double[] values;
    private final Action[] policy;
    private int iterations;
    private double residual;

    public ValueIteration(NavigationModel model) {
        this.model = model;
        values = new double[model.map().stateCount()];
        policy = new Action[values.length];
        solve();
    }

    private double q(State state, Action action, double[] v) {
        if (model.map().terminal(state)) return 0;
        double result = 1;
        for (var outcome : model.outcomes(state, action)) result += outcome.probability() * v[outcome.next().index()];
        return result;
    }

    private void solve() {
        boolean converged = false;
        for (iterations = 1; iterations <= 100_000; iterations++) {
            double[] next = new double[values.length];
            double delta = 0;
            for (int i = 0; i < values.length; i++) {
                State state = State.fromIndex(i);
                if (model.map().terminal(state)) continue;
                double best = Double.POSITIVE_INFINITY;
                for (Action action : Action.values()) best = Math.min(best, q(state, action, values));
                next[i] = best;
                delta = Math.max(delta, Math.abs(next[i] - values[i]));
            }
            System.arraycopy(next, 0, values, 0, values.length);
            if (delta < TOLERANCE) { converged = true; break; }
        }
        if (!converged) throw new IllegalStateException("Value iteration did not converge");
        residual = 0;
        for (int i = 0; i < values.length; i++) {
            State state = State.fromIndex(i);
            double best = Double.POSITIVE_INFINITY;
            Action bestAction = Action.FORWARD;
            for (Action action : Action.values()) {
                double candidate = q(state, action, values);
                // Fixed order makes ties deterministic and reproducible.
                if (candidate < best - 1e-12) { best = candidate; bestAction = action; }
            }
            policy[i] = bestAction;
            residual = Math.max(residual, Math.abs(best - values[i]));
        }
    }

    public String name() { return "Optimal MDP"; }
    public double probability(State state, Action action) { return policy[state.index()] == action ? 1 : 0; }
    public Action action(State state) { return policy[state.index()]; }
    public double value(State state) { return values[state.index()]; }
    public double q(State state, Action action) { return q(state, action, values); }
    public int iterations() { return iterations; }
    public double residual() { return residual; }
}
