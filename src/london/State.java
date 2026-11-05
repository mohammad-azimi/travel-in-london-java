package london;

import java.util.Objects;

/** Position alone is insufficient for relative actions; heading is observed too. */
public record State(int station, Direction facing) {
    public State {
        if (station < 0) throw new IllegalArgumentException("Negative station");
        Objects.requireNonNull(facing, "facing");
    }
    public int index() { return 4 * station + facing.ordinal(); }
    public static State fromIndex(int index) {
        return new State(index / 4, Direction.values()[index % 4]);
    }
}
