package london;

/** Independent check: solve (I-P_pi) J_pi = 1 by pivoted elimination. */
public final class PolicyEvaluation {
    private PolicyEvaluation() {}

    public static double[] exact(NavigationModel model, Policy policy) {
        int n = model.map().stateCount();
        double[][] transitions = new double[n][n];
        boolean[][] reverse = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            State state = State.fromIndex(i);
            if (model.map().terminal(state)) continue;
            double sum = 0;
            for (Action action : Action.values()) {
                double pa = policy.probability(state, action); sum += pa;
                if (!Double.isFinite(pa) || pa < 0) throw new IllegalArgumentException("Invalid policy probability");
                for (var outcome : model.outcomes(state, action)) {
                    double probability = pa * outcome.probability();
                    int next = outcome.next().index(); transitions[i][next] += probability;
                    if (probability > 0) reverse[next][i] = true;
                }
            }
            if (Math.abs(sum - 1) > 1e-12) throw new IllegalArgumentException("Invalid policy");
        }
        // In a finite chain, a state has infinite positive total cost if it can
        // reach a closed nonterminal class. First find states with no route to
        // any goal state, then propagate that failure backwards.
        boolean[] reachesGoal = new boolean[n];
        var queue = new java.util.ArrayDeque<Integer>();
        for (int i = 0; i < n; i++) if (model.map().terminal(State.fromIndex(i))) { reachesGoal[i] = true; queue.add(i); }
        propagate(reverse, reachesGoal, queue);
        boolean[] infinite = new boolean[n];
        for (int i = 0; i < n; i++) if (!reachesGoal[i]) { infinite[i] = true; queue.add(i); }
        propagate(reverse, infinite, queue);
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            State state = State.fromIndex(i);
            a[i][i] = 1;
            if (model.map().terminal(state) || infinite[i]) continue;
            a[i][n] = 1;
            for (int j = 0; j < n; j++) a[i][j] -= transitions[i][j];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++)
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row;
            if (Math.abs(a[pivot][col]) < 1e-13)
                throw new IllegalStateException("Policy has infinite or ill-conditioned expected cost");
            double[] tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp;
            double scale = a[col][col];
            for (int j = col; j <= n; j++) a[col][j] /= scale;
            for (int row = 0; row < n; row++) if (row != col) {
                double factor = a[row][col];
                for (int j = col; j <= n; j++) a[row][j] -= factor * a[col][j];
            }
        }
        double[] result = new double[n];
        for (int i = 0; i < n; i++) result[i] = infinite[i] ? Double.POSITIVE_INFINITY : a[i][n];
        return result;
    }

    private static void propagate(boolean[][] reverse, boolean[] marked, java.util.ArrayDeque<Integer> queue) {
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int i = 0; i < marked.length; i++) if (reverse[current][i] && !marked[i]) {
                marked[i] = true; queue.add(i);
            }
        }
    }
}
