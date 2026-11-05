package london;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        Locale.setDefault(Locale.US); // Stable decimal-point entry on English, Russian and Persian systems.
        try {
            if (args.length == 0 || args[0].equals("--gui")) { gui(); return; }
            String command = args[0];
            if (command.equals("--help")) { help(); return; }
            if (command.equals("--self-test")) { SelfTest.run(); return; }
            if (command.equals("--render")) {
                if (args.length != 2) throw new IllegalArgumentException("Usage: --render output.png");
                render(Path.of(args[1])); return;
            }
            Options options = new Options(args);
            if (command.equals("--demo")) demo(options);
            else if (command.equals("--experiments")) experiments(options);
            else throw new IllegalArgumentException("Unknown command: " + command);
        } catch (Exception | AssertionError error) {
            System.err.println("ERROR: " + error.getMessage());
            error.printStackTrace(System.err); System.exit(1);
        }
    }
    private static void gui() {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("No desktop display. Use --demo or --self-test here; run the GUI on your computer.");
        SwingUtilities.invokeLater(() -> {
            LondonPanel.configureTheme(); JFrame frame = new JFrame("Travel in London - Stochastic Decision Agent");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setContentPane(new LondonPanel());
            frame.setMinimumSize(new Dimension(1100, 760)); frame.pack();
            var bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setSize(Math.min(frame.getWidth(), bounds.width), Math.min(frame.getHeight(), bounds.height));
            frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
    private static void render(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
        SwingUtilities.invokeAndWait(() -> {
            LondonPanel.configureTheme(); LondonPanel panel = new LondonPanel();
            for (int i = 0; i < 6; i++) panel.testStep();
            panel.setSize(1380, 900); LondonPanel.layoutTree(panel);
            BufferedImage image = new BufferedImage(1380, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose(); panel.shutdown();
            try { ImageIO.write(image, "png", path.toFile()); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        System.out.println("Rendered the actual Swing application: " + path.toAbsolutePath());
    }
    private static void demo(Options o) throws Exception {
        CityMap map = new CityMap(); double p = o.number("--reliability", .8);
        NavigationModel model = new NavigationModel(map, p); ValueIteration optimal = new ValueIteration(model);
        Policy policy = switch (o.text("--controller", "optimal")) {
            case "optimal" -> optimal; case "bfs" -> Policy.shortestPath(map); case "random" -> Policy.random();
            default -> throw new IllegalArgumentException("controller must be optimal, bfs or random");
        };
        State start = o.start(map); long seed = o.seed();
        int cap = o.integer("--max-steps", 500);
        var episode = Simulation.episode(model, policy, start, seed, cap);
        System.out.printf(Locale.ROOT, "%s | start=%s / %s | p=%.2f | seed=%d%n", policy.name(), map.station(start.station()), start.facing(), p, seed);
        System.out.printf(Locale.ROOT, "Optimal expected moves: %.6f; VI sweeps: %d; residual: %.3e%n", optimal.value(start), optimal.iterations(), optimal.residual());
        int index = 0;
        for (var step : episode.trace()) System.out.printf(Locale.ROOT, "%3d  %-17s %-8s intended=%-5s actual=%-5s -> %-17s%s%s%n",
                ++index, map.station(step.before().station()), step.action(), step.intended(), step.actual(), map.station(step.after().station()),
                step.slipped() ? " SLIP" : "", step.blocked() ? " BLOCKED" : "");
        System.out.printf(Locale.ROOT, "Reached goal: %s | moves=%d | blocked=%d | slips=%d%n", episode.reachedGoal(), episode.steps(), episode.blocked(), episode.slips());
        if (o.has("--output")) Csv.write(Path.of(o.text("--output", "")), Csv.trace(episode.trace(), model, seed, policy.name(), start));
    }
    private static void experiments(Options o) throws Exception {
        CityMap map = new CityMap(); State start = o.start(map); int episodes = o.integer("--episodes", 5000);
        int cap = o.integer("--max-steps", 5000); long seed = o.seed();
        double[] probabilities = o.has("--reliability") ? new double[]{o.number("--reliability", .8)} : new double[]{.4, .8, 1.0};
        Path output = Path.of(o.text("--output", "results/comparison.csv"));
        StringBuilder csv = new StringBuilder("reliability,start,heading,base_seed," + Simulation.CSV_HEADER + "\n");
        System.out.printf("Start: %s / %s; base seed: %d; episodes per configuration: %d; cap: %d%n", map.station(start.station()), start.facing(), seed, episodes, cap);
        for (double p : probabilities) {
            NavigationModel model = new NavigationModel(map, p); ValueIteration optimal = new ValueIteration(model);
            String suffix = String.format(Locale.ROOT, "%.6f", p).replace('.', '_');
            Csv.write(output.toAbsolutePath().getParent().resolve("optimal_policy_p" + suffix + ".csv"), Csv.policy(model, optimal));
            for (Policy policy : List.of(optimal, Policy.shortestPath(map), Policy.random())) {
                var r = Simulation.batch(model, policy, start, seed, episodes, cap);
                csv.append(String.format(Locale.ROOT, "%.2f,%s,%s,%d,", p, map.station(start.station()), start.facing(), seed)).append(r.csv()).append('\n');
                System.out.printf(Locale.ROOT, "p=%.2f  %-20s reached=%5d/%d  mean=%8.4f +/- %.4f (SE)  exact=%8.4f  p95=%5.0f%n", p, policy.name(), r.successes(), r.episodes(), r.mean(), r.se(), r.exact(), r.p95());
            }
        }
        Csv.write(output, csv.toString());
        System.out.println("CSV saved: " + output.toAbsolutePath());
    }
    private static void help() {
        System.out.println("Travel in London - Java 17+ (no external dependencies)\n"
                + "  java -jar dist/london-agent.jar                   Desktop GUI\n"
                + "  java -jar dist/london-agent.jar --self-test       Model + Swing checks\n"
                + "  java -jar dist/london-agent.jar --demo            Console episode\n"
                + "  java -jar dist/london-agent.jar --experiments     Compare 3 policies at p=.4/.8/1\n"
                + "  java -jar dist/london-agent.jar --render ui.png   Render Swing offscreen\n\n"
                + "Demo/experiment options (space-separated values):\n"
                + "  --reliability 0.8  --seed 42  --start 0  --heading EAST\n"
                + "  --max-steps 5000  --output results/example.csv\n"
                + "Demo only: --controller optimal|bfs|random\n"
                + "Experiments only: --episodes 5000\n"
                + "--start also accepts a quoted station name, e.g. \"Green Park\".");
    }
    private static final class Options {
        private final Map<String, String> data = new HashMap<>();
        Options(String[] args) {
            List<String> valid = new ArrayList<>(List.of("--reliability", "--seed", "--start", "--heading", "--max-steps", "--output"));
            if (args[0].equals("--demo")) valid.add("--controller");
            if (args[0].equals("--experiments")) valid.add("--episodes");
            for (int i = 1; i < args.length; i += 2) {
                if (i + 1 == args.length || !valid.contains(args[i])) throw new IllegalArgumentException("Unknown option or missing value: " + args[i]);
                if (data.put(args[i], args[i + 1]) != null) throw new IllegalArgumentException("Repeated option: " + args[i]);
            }
        }
        boolean has(String key) { return data.containsKey(key); }
        String text(String key, String fallback) { return data.getOrDefault(key, fallback); }
        double number(String key, double fallback) { return Double.parseDouble(text(key, Double.toString(fallback))); }
        int integer(String key, int fallback) { return Integer.parseInt(text(key, Integer.toString(fallback))); }
        long seed() { return Long.parseLong(text("--seed", "42")); }
        State start(CityMap map) {
            String raw = text("--start", "0"); int id = -1;
            try { id = Integer.parseInt(raw); } catch (NumberFormatException ignored) {
                for (var station : map.stations()) if (station.name().equalsIgnoreCase(raw)) id = station.id();
            }
            if (id < 0 || id >= map.size()) throw new IllegalArgumentException("Unknown start station: " + raw);
            return new State(id, Direction.valueOf(text("--heading", "EAST").toUpperCase(Locale.ROOT)));
        }
    }
}
