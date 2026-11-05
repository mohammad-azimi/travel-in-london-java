package london;

/** Cardinal bearings, clockwise. Screen coordinates grow downwards. */
public enum Direction {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }

    public Direction turn(int quarterTurns) {
        return values()[Math.floorMod(ordinal() + quarterTurns, 4)];
    }

    @Override public String toString() {
        String s = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
