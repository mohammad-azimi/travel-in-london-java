package london;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JPanel;

/** Scalable vector drawing: no image files, browser or network connection required. */
public final class MapPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final CityMap map;
    private State state;
    private int start;
    private List<NavigationModel.Step> trace = List.of();
    private boolean showReference = true;

    public MapPanel(CityMap map) {
        this.map = map;
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 480));
    }

    public void update(State state, int start, List<NavigationModel.Step> trace, boolean reference) {
        this.state = state; this.start = start; this.trace = List.copyOf(trace); this.showReference = reference;
        repaint();
    }

    private static Color routeColor(String color) {
        return switch (color) {
            case "Red" -> new Color(221, 71, 79);
            case "Green" -> new Color(36, 159, 115);
            case "Yellow" -> new Color(217, 169, 26);
            default -> new Color(73, 112, 211);
        };
    }

    private void road(Graphics2D g, int from, int to) {
        var a = map.station(from); var b = map.station(to);
        Direction departure = map.direction(from, to), entry = map.direction(to, from);
        double bend = Math.min(50, Math.hypot(a.x() - b.x(), a.y() - b.y()) * .3);
        Path2D curve = new Path2D.Double(); curve.moveTo(a.x(), a.y());
        curve.curveTo(a.x() + departure.dx * bend, a.y() + departure.dy * bend,
                b.x() + entry.dx * bend, b.y() + entry.dy * bend, b.x(), b.y());
        g.draw(curve);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double scale = Math.min((getWidth() - 34) / 1050.0, (getHeight() - 52) / 530.0);
        scale = Math.max(0.1, scale);
        g.translate((getWidth() - 1050 * scale) / 2, (getHeight() - 530 * scale) / 2 - 4);
        g.scale(scale, scale);
        g.translate(10, 12);

        for (var edge : map.roads()) {
            g.setColor(routeColor(edge.color()));
            g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            road(g, edge.from(), edge.to());
            g.setColor(new Color(255, 255, 255, 150));
            g.setStroke(new BasicStroke(1.7f));
            road(g, edge.from(), edge.to());
        }
        if (showReference) {
            g.setColor(new Color(44, 57, 75, 170));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5, 8}, 0));
            List<Integer> path = map.shortestPath(start);
            for (int i = 1; i < path.size(); i++) road(g, path.get(i - 1), path.get(i));
        }
        g.setColor(new Color(9, 115, 143, 80));
        g.setStroke(new BasicStroke(17, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (var step : trace) if (!step.blocked()) road(g, step.before().station(), step.after().station());

        // The compass represents discrete road bearings, not measured geography.
        g.setColor(new Color(128, 143, 162));
        g.setStroke(new BasicStroke(2));
        g.draw(new Line2D.Double(940, 65, 940, 24));
        g.draw(new Line2D.Double(920, 45, 960, 45));
        g.draw(new Line2D.Double(940, 24, 935, 33));
        g.draw(new Line2D.Double(940, 24, 945, 33));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("N", 935, 17);

        for (var station : map.stations()) {
            if (station.id() == start) {
                g.setColor(new Color(218, 232, 255));
                g.fill(new Ellipse2D.Double(station.x() - 18, station.y() - 18, 36, 36));
            }
            if (station.id() == CityMap.TOWER_HILL) {
                g.setColor(new Color(255, 235, 177));
                g.fill(new Ellipse2D.Double(station.x() - 25, station.y() - 25, 50, 50));
                g.setColor(new Color(209, 156, 35));
                g.setStroke(new BasicStroke(2));
                g.draw(new Ellipse2D.Double(station.x() - 22, station.y() - 22, 44, 44));
            }
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(station.x() - 8.5, station.y() - 8.5, 17, 17));
            g.setColor(new Color(49, 64, 86));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(station.x() - 8.5, station.y() - 8.5, 17, 17));
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            int width = g.getFontMetrics().stringWidth(station.name());
            double x = station.x() + station.labelDx(), y = station.y() + station.labelDy();
            g.setColor(new Color(255, 255, 255, 237));
            g.fill(new RoundRectangle2D.Double(x - 5, y - 17, width + 10, 24, 8, 8));
            g.setColor(new Color(40, 54, 74));
            g.drawString(station.name(), (float) x, (float) y);
        }

        if (state != null) {
            var here = map.station(state.station());
            g.setColor(new Color(17, 148, 174, 45));
            g.fill(new Ellipse2D.Double(here.x() - 26, here.y() - 26, 52, 52));
            g.setColor(new Color(7, 112, 143));
            g.fill(new Ellipse2D.Double(here.x() - 13, here.y() - 13, 26, 26));
            Direction d = state.facing();
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(here.x() + d.dx * 23, here.y() + d.dy * 23);
            arrow.lineTo(here.x() + d.dx * 9 - d.dy * 7, here.y() + d.dy * 9 + d.dx * 7);
            arrow.lineTo(here.x() + d.dx * 9 + d.dy * 7, here.y() + d.dy * 9 - d.dx * 7);
            arrow.closePath(); g.fill(arrow);
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(here.x() - 4, here.y() - 4, 8, 8));
        }
        g.dispose();

        Graphics2D legend = (Graphics2D) graphics.create();
        legend.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        legend.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int x = 18, y = getHeight() - 14;
        for (String color : new String[]{"Red", "Yellow", "Green", "Blue"}) {
            legend.setColor(routeColor(color)); legend.fillRoundRect(x, y - 7, 18, 5, 4, 4);
            legend.setColor(new Color(97, 111, 131)); legend.drawString(color + " road", x + 24, y);
            x += 98;
        }
        legend.setColor(new Color(7, 112, 143)); legend.fillOval(x + 2, y - 9, 8, 8);
        legend.setColor(new Color(97, 111, 131)); legend.drawString("Agent + trail", x + 18, y);
        legend.dispose();
    }
}
