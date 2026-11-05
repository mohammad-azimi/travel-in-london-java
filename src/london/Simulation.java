package london;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Simulation {
    public record Episode(boolean reachedGoal, int steps, int blocked, int slips,
                          List<NavigationModel.Step> trace) {}
    public record Summary(String policy, int episodes, int successes, double mean, double sd,
                          double p95, double blockedMean, double slipsMean, double exact, int maxSteps) {
        public double se() { return sd / Math.sqrt(episodes); }
        public double successRate() { return successes / (double) episodes; }
        public String csv() {
            return String.format(java.util.Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.1f,%.6f,%.6f,%.9f,%d",
                    policy, episodes, successes, successRate(), mean, sd, se(), mean - 1.96 * se(),
                    mean + 1.96 * se(), p95, blockedMean, slipsMean, exact, maxSteps);
        }
    }
    public static final String CSV_HEADER = "policy,episodes,successes,success_rate,mean_capped_steps,sd_capped_steps,se_capped_steps,ci95_low,ci95_high,p95_capped_steps,mean_blocked,mean_slips,exact_expected_steps,max_steps";

    private Simulation() {}

    /** SplitMix64 finalizer: diffuse nearby episode indices before seeding Java Random. */
    public static long episodeSeed(long baseSeed, int episodeIndex) {
        long z = baseSeed + 0x9E3779B97F4A7C15L * (episodeIndex + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static Episode episode(NavigationModel model, Policy policy, State start, long seed, int maxSteps) {
        if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps must be positive");
        Random random = new Random(seed);
        State current = start;
        int blocked = 0, slips = 0;
        var trace = new ArrayList<NavigationModel.Step>();
        while (!model.map().terminal(current) && trace.size() < maxSteps) {
            var step = model.sample(current, policy.choose(current, random), random);
            trace.add(step);
            if (step.blocked()) blocked++;
            if (step.slipped()) slips++;
            current = step.after();
        }
        return new Episode(model.map().terminal(current), trace.size(), blocked, slips, List.copyOf(trace));
    }

    public static Summary batch(NavigationModel model, Policy policy, State start,
                                long seed, int episodes, int maxSteps) {
        if (episodes < 2) throw new IllegalArgumentException("At least two episodes are required");
        int[] counts = new int[episodes];
        int successes = 0;
        double sum = 0, blocked = 0, slips = 0;
        for (int i = 0; i < episodes; i++) {
            Episode e = episode(model, policy, start, episodeSeed(seed, i), maxSteps);
            counts[i] = e.steps(); sum += e.steps(); blocked += e.blocked(); slips += e.slips();
            if (e.reachedGoal()) successes++;
        }
        double mean = sum / episodes, squared = 0;
        for (int count : counts) squared += (count - mean) * (count - mean);
        java.util.Arrays.sort(counts);
        double p95 = counts[(int) Math.ceil(0.95 * episodes) - 1];
        double exact = PolicyEvaluation.exact(model, policy)[start.index()];
        return new Summary(policy.name(), episodes, successes, mean, Math.sqrt(squared / (episodes - 1)),
                p95, blocked / episodes, slips / episodes, exact, maxSteps);
    }
}
