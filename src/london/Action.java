package london;

/** An action turns relative to the current bearing, then attempts one road. */
public enum Action {
    FORWARD(0), LEFT(-1), RIGHT(1), BACKWARD(2);

    private final int turn;
    Action(int turn) { this.turn = turn; }
    public Direction intended(Direction facing) { return facing.turn(turn); }

    public static Action towards(Direction facing, Direction target) {
        for (Action action : values()) if (action.intended(facing) == target) return action;
        throw new IllegalArgumentException("Unknown bearing");
    }

    @Override public String toString() {
        String s = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
