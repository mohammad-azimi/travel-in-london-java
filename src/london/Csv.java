package london;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Csv {
    private Csv() {}
    public static String policy(NavigationModel model, ValueIteration planner) {
        StringBuilder out = new StringBuilder("station_id,station,heading,reliability,expected_steps,best_action,intended_bearing,terminal\n");
        for (int i = 0; i < model.map().stateCount(); i++) {
            State state = State.fromIndex(i); boolean terminal = model.map().terminal(state);
            out.append(String.format(Locale.ROOT, "%d,%s,%s,%.4f,%.9f,%s,%s,%s%n", state.station(),
                    model.map().station(state.station()).name(), state.facing(), model.reliability(), planner.value(state),
                    terminal ? "" : planner.action(state), terminal ? "" : planner.action(state).intended(state.facing()), terminal));
        }
        return out.toString();
    }
    public static void write(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    public static String trace(List<NavigationModel.Step> steps, NavigationModel model,
                               long seed, String controller, State start) {
        StringBuilder out = new StringBuilder("seed,reliability,controller,start,initial_heading,step,from,heading_before,action,intended,actual,to,heading_after,slipped,blocked\n");
        int i = 0;
        for (var s : steps) out.append(String.format(Locale.ROOT,
                "%d,%.4f,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                seed, model.reliability(), controller, model.map().station(start.station()).name(), start.facing(), ++i,
                model.map().station(s.before().station()).name(), s.before().facing(), s.action(), s.intended(), s.actual(),
                model.map().station(s.after().station()).name(), s.after().facing(), s.slipped(), s.blocked()));
        return out.toString();
    }
}
