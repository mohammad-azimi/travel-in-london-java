package london;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Presentation UI. The model/algorithms also run without any graphics environment. */
public final class LondonPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color INK = new Color(28, 45, 68);
    private static final Color MUTED = new Color(100, 117, 139);
    private static final Color BLUE = new Color(29, 94, 182);
    private static final Color BG = new Color(240, 244, 250);
    private static final int STEP_CAP = 500;
    private final CityMap map = new CityMap();
    private NavigationModel model;
    private ValueIteration optimal;
    private Policy policy;
    private double[] selectedValues;
    private State start, state;
    private Random random;
    private long activeSeed;
    private int blocked, slips;
    private boolean manualOverride;
    private final List<NavigationModel.Step> trace = new ArrayList<>();

    private final JComboBox<CityMap.Station> stationBox = new JComboBox<>();
    private final JComboBox<Direction> facingBox = new JComboBox<>(Direction.values());
    private final JComboBox<String> controllerBox = new JComboBox<>(new String[]{"Optimal MDP", "Shortest path (BFS)", "Uniform random"});
    private final JSpinner reliabilityBox = new JSpinner(new SpinnerNumberModel(0.80, 0.0, 1.0, 0.05));
    private final JSpinner seedBox = new JSpinner(new SpinnerNumberModel(Long.valueOf(42), Long.valueOf(0), Long.valueOf(Long.MAX_VALUE - 20_000), Long.valueOf(1)));
    private final JSlider speed = new JSlider(100, 1400, 600);
    private final JCheckBox reference = new JCheckBox("Show shortest path reference", true);
    private final JButton runButton = new JButton("Run agent");
    private final JButton stepButton = new JButton("One step");
    private final JButton compareButton = new JButton("Compare 3 policies");
    private final List<JButton> manualButtons = new ArrayList<>();
    private final JLabel location = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel[] stats = {new JLabel(), new JLabel(), new JLabel(), new JLabel()};
    private final JLabel solverInfo = new JLabel();
    private final DefaultTableModel logModel = tableModel("#", "From", "Action", "Intended", "Actual", "To", "Outcome");
    private final DefaultTableModel valueModel = tableModel("Station", "Facing", "Optimal expected steps", "Best action", "Intended bearing");
    private final JTable logTable = table(logModel);
    private final MapPanel mapPanel = new MapPanel(map);
    private final Timer timer = new Timer(600, event -> advance(null));

    public LondonPanel() {
        setLayout(new BorderLayout(14, 14));
        setBackground(BG);
        setBorder(new EmptyBorder(18, 20, 14, 20));
        setPreferredSize(new Dimension(1380, 900));
        add(header(), BorderLayout.NORTH);

        JPanel centre = new JPanel(new BorderLayout(14, 0)); centre.setOpaque(false);
        JPanel display = new JPanel(new BorderLayout(0, 12)); display.setOpaque(false);
        JPanel mapCard = card(new BorderLayout(0, 3));
        JPanel mapTitle = new JPanel(new BorderLayout()); mapTitle.setOpaque(false);
        mapTitle.add(label("CITY MAP", 12, Font.BOLD, MUTED), BorderLayout.WEST);
        JLabel goal = label("DESTINATION  /  TOWER HILL", 12, Font.BOLD, new Color(158, 112, 20));
        mapTitle.add(goal, BorderLayout.EAST);
        mapCard.add(mapTitle, BorderLayout.NORTH); mapCard.add(mapPanel, BorderLayout.CENTER);
        location.setFont(new Font("SansSerif", Font.BOLD, 13)); location.setForeground(INK);
        location.setBorder(new EmptyBorder(5, 1, 1, 1)); mapCard.add(location, BorderLayout.SOUTH);
        display.add(mapCard, BorderLayout.CENTER); display.add(statCards(), BorderLayout.SOUTH);
        centre.add(display, BorderLayout.CENTER);
        JScrollPane controls = new JScrollPane(controls());
        controls.setBorder(null); controls.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controls.setPreferredSize(new Dimension(305, 560)); controls.getVerticalScrollBar().setUnitIncrement(16);
        centre.add(controls, BorderLayout.EAST); add(centre, BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(0, 8)); lower.setOpaque(false);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Movement log", scrollTable(logTable));
        tabs.addTab("Optimal policy - all 56 states", scrollTable(table(valueModel)));
        tabs.setPreferredSize(new Dimension(1100, 185)); lower.add(tabs, BorderLayout.CENTER);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12)); status.setForeground(MUTED);
        lower.add(status, BorderLayout.SOUTH); add(lower, BorderLayout.SOUTH);

        for (var station : map.stations()) stationBox.addItem(station);
        stationBox.setSelectedIndex(CityMap.GREEN_PARK); facingBox.setSelectedItem(Direction.EAST);
        seedBox.setEditor(new JSpinner.NumberEditor(seedBox, "0"));
        reliabilityBox.setEditor(new JSpinner.NumberEditor(reliabilityBox, "0.00"));
        ((JSpinner.DefaultEditor) seedBox.getEditor()).getTextField().setColumns(7);
        ((JSpinner.DefaultEditor) reliabilityBox.getEditor()).getTextField().setColumns(4);
        for (JComboBox<?> box : new JComboBox<?>[]{stationBox, facingBox, controllerBox}) box.addActionListener(e -> settingsChanged());
        reliabilityBox.addChangeListener(e -> settingsChanged()); seedBox.addChangeListener(e -> settingsChanged());
        reference.addActionListener(e -> refresh());
        speed.addChangeListener(e -> timer.setDelay(speed.getValue()));
        runButton.addActionListener(e -> { if (timer.isRunning()) stop(); else { timer.start(); runButton.setText("Pause"); } });
        stepButton.addActionListener(e -> { stop(); advance(null); });
        compareButton.addActionListener(e -> compare());
        installKeys(); applySettings();
    }

    public static void configureTheme() {
        Font base = new Font("SansSerif", Font.PLAIN, 13);
        var defaults = UIManager.getDefaults();
        var keys = defaults.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (defaults.get(key) instanceof javax.swing.plaf.FontUIResource)
                UIManager.put(key, new javax.swing.plaf.FontUIResource(base));
        }
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Table.selectionBackground", new Color(222, 235, 253));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TabbedPane.selected", Color.WHITE);
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text); label.setFont(new Font("SansSerif", style, size)); label.setForeground(color); return label;
    }
    private static JPanel card(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setBackground(Color.WHITE);
        panel.setBorder(new javax.swing.border.CompoundBorder(BorderFactory.createLineBorder(new Color(222, 229, 240)), new EmptyBorder(14, 16, 14, 16)));
        return panel;
    }
    private JPanel header() {
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 4)); titles.setOpaque(false);
        titles.add(label("London  /  Decision Agent", 26, Font.BOLD, INK));
        titles.add(label("A fully observable world. An uncertain next move.", 13, Font.PLAIN, MUTED));
        head.add(titles, BorderLayout.WEST);
        JLabel badge = label("JAVA 17+   |   STOCHASTIC PLANNING", 11, Font.BOLD, BLUE);
        head.add(badge, BorderLayout.EAST); return head;
    }
    private JPanel statCards() {
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0)); row.setOpaque(false);
        String[] names = {"MOVES TAKEN", "BLOCKED ATTEMPTS", "SIDEWAYS SLIPS", "EXPECTED MOVES LEFT"};
        for (int i = 0; i < stats.length; i++) {
            JPanel item = card(new BorderLayout(0, 2)); item.add(label(names[i], 10, Font.BOLD, MUTED), BorderLayout.NORTH);
            stats[i].setFont(new Font("SansSerif", Font.BOLD, 25)); stats[i].setForeground(i == 3 ? BLUE : INK);
            item.add(stats[i], BorderLayout.CENTER); row.add(item);
        }
        stats[3].setToolTipText("Expected remaining moves under the selected automated policy, including blocked attempts.");
        return row;
    }
    private JPanel controls() {
        JPanel panel = new WidthPanel(); panel.setLayout(new java.awt.GridBagLayout()); panel.setBackground(Color.WHITE);
        panel.setBorder(new javax.swing.border.CompoundBorder(BorderFactory.createLineBorder(new Color(222, 229, 240)), new EmptyBorder(14, 16, 14, 16)));
        var c = new java.awt.GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(0, 0, 5, 0);
        addRow(panel, c, label("EPISODE SETTINGS", 12, Font.BOLD, MUTED));
        field(panel, c, "Start station", stationBox); field(panel, c, "Initial heading", facingBox);
        field(panel, c, "Automated controller", controllerBox);
        JPanel params = new JPanel(new GridLayout(1, 2, 10, 0)); params.setOpaque(false);
        JPanel p = new JPanel(new BorderLayout(0, 4)); p.setOpaque(false);
        p.add(label("Reliability p", 11, Font.BOLD, MUTED), BorderLayout.NORTH); p.add(reliabilityBox, BorderLayout.CENTER);
        JPanel seed = new JPanel(new BorderLayout(0, 4)); seed.setOpaque(false);
        seed.add(label("Random seed", 11, Font.BOLD, MUTED), BorderLayout.NORTH); seed.add(seedBox, BorderLayout.CENTER);
        params.add(p); params.add(seed); addRow(panel, c, params);
        JLabel slipsHint = label("<html>Sideways probability: (1 - p) / 2 each.<br>Changed settings need Apply & reset.</html>", 11, Font.PLAIN, MUTED);
        addRow(panel, c, slipsHint);
        JButton reset = new JButton("Apply & reset"); reset.addActionListener(e -> applySettings()); addRow(panel, c, reset);
        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0)); actions.setOpaque(false);
        runButton.setBackground(BLUE); runButton.setForeground(Color.WHITE); runButton.setOpaque(true); runButton.setFocusPainted(false);
        actions.add(runButton); actions.add(stepButton); addRow(panel, c, actions);
        speed.setOpaque(false); speed.setToolTipText("Delay between moves: 100 to 1400 ms");
        field(panel, c, "Animation delay (fast to slow)", speed);
        addRow(panel, c, label("MANUAL ACTIONS", 11, Font.BOLD, MUTED));
        JPanel manual = new JPanel(new GridLayout(2, 2, 7, 7)); manual.setOpaque(false);
        String[] keys = {"W", "A", "D", "S"}; int index = 0;
        for (Action action : Action.values()) {
            JButton button = new JButton(action + " (" + keys[index++] + ")");
            button.addActionListener(e -> { stop(); manualOverride = true; advance(action); });
            manual.add(button); manualButtons.add(button);
        }
        addRow(panel, c, manual); reference.setOpaque(false); reference.setFont(new Font("SansSerif", Font.PLAIN, 11));
        addRow(panel, c, reference);
        addRow(panel, c, label("<html>Dotted route ignores uncertainty.<br>Every attempted move costs one step.</html>", 11, Font.PLAIN, MUTED));
        addRow(panel, c, compareButton);
        JButton export = new JButton("Export movement log (CSV)"); export.addActionListener(e -> saveTrace()); addRow(panel, c, export);
        JButton help = new JButton("Explain the model"); help.addActionListener(e -> showHelp()); addRow(panel, c, help);
        solverInfo.setFont(new Font("SansSerif", Font.PLAIN, 10)); solverInfo.setForeground(MUTED); addRow(panel, c, solverInfo);
        c.weighty = 1; panel.add(Box.createVerticalGlue(), c);
        return panel;
    }
    private static void field(JPanel panel, java.awt.GridBagConstraints c, String title, JComponent control) {
        JPanel field = new JPanel(new BorderLayout(0, 4)); field.setOpaque(false);
        field.add(label(title, 11, Font.BOLD, MUTED), BorderLayout.NORTH); field.add(control, BorderLayout.CENTER); addRow(panel, c, field);
    }
    private static void addRow(JPanel panel, java.awt.GridBagConstraints c, Component component) {
        panel.add(component, c); c.gridy++;
    }
    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }
    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model); table.setRowHeight(25); table.setShowVerticalLines(false);
        table.setGridColor(new Color(235, 239, 245)); table.setForeground(INK);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11)); table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setPreferredSize(new Dimension(20, 29));
        return table;
    }
    private static JScrollPane scrollTable(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        return scroll;
    }
    private static final class WidthPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 100; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void settingsChanged() {
        if (model == null) return;
        stop(); setMovementEnabled(false); compareButton.setEnabled(false);
        status.setText("Settings changed. Press Apply & reset to start a new episode.");
    }
    private void applySettings() {
        stop();
        try { seedBox.commitEdit(); reliabilityBox.commitEdit(); }
        catch (ParseException e) { error("Enter a valid seed and a reliability between 0 and 1."); return; }
        activeSeed = ((Number) seedBox.getValue()).longValue();
        model = new NavigationModel(map, ((Number) reliabilityBox.getValue()).doubleValue());
        optimal = new ValueIteration(model);
        policy = switch (controllerBox.getSelectedIndex()) {
            case 1 -> Policy.shortestPath(map); case 2 -> Policy.random(); default -> optimal;
        };
        selectedValues = PolicyEvaluation.exact(model, policy);
        start = new State(((CityMap.Station) stationBox.getSelectedItem()).id(), (Direction) facingBox.getSelectedItem());
        state = start; random = new Random(activeSeed); trace.clear(); blocked = 0; slips = 0; manualOverride = false;
        logModel.setRowCount(0); valueModel.setRowCount(0);
        for (int i = 0; i < map.stateCount(); i++) {
            State s = State.fromIndex(i); Action a = optimal.action(s); boolean terminal = map.terminal(s);
            valueModel.addRow(new Object[]{map.station(s.station()).name(), s.facing(), String.format(Locale.ROOT, "%.4f", optimal.value(s)),
                    terminal ? "Terminal" : a, terminal ? "-" : a.intended(s.facing())});
        }
        solverInfo.setText(String.format(Locale.ROOT, "<html>14 stations / 56 states / 15 roads<br>Value iteration: %d sweeps<br>Bellman residual: %.2e</html>", optimal.iterations(), optimal.residual()));
        status.setText(map.terminal(state) ? "Goal reached at the initial state: zero moves." : "Ready. Run the agent, take one step, or try a manual action. Episode cap: 500 moves.");
        compareButton.setEnabled(true); setMovementEnabled(!map.terminal(state)); refresh();
    }
    private void setMovementEnabled(boolean enabled) {
        runButton.setEnabled(enabled); stepButton.setEnabled(enabled);
        for (JButton button : manualButtons) button.setEnabled(enabled);
    }
    private void stop() { timer.stop(); runButton.setText("Run agent"); }

    private void advance(Action manual) {
        if (!stepButton.isEnabled() || map.terminal(state)) return;
        Action action = manual != null ? manual : policy.choose(state, random);
        var step = model.sample(state, action, random);
        trace.add(step); state = step.after(); if (step.blocked()) blocked++; if (step.slipped()) slips++;
        String outcome = step.slipped() ? "Slip" : "Intended";
        if (step.blocked()) outcome += " / blocked";
        if (map.terminal(state)) outcome += " / GOAL";
        logModel.addRow(new Object[]{trace.size(), map.station(step.before().station()).name(), action,
                step.intended(), step.actual(), map.station(state.station()).name(), outcome});
        logTable.scrollRectToVisible(logTable.getCellRect(logModel.getRowCount() - 1, 0, true));
        status.setText(String.format(Locale.ROOT, "%s: intended %s, actual %s. %s", action, step.intended(), step.actual(),
                step.blocked() ? "No road: position unchanged; heading updated." : "Arrived at " + map.station(state.station()).name() + "."));
        if (map.terminal(state)) {
            stop(); setMovementEnabled(false);
            status.setText("GOAL REACHED - Tower Hill. Total moves: " + trace.size() + ", blocked: " + blocked + ", sideways slips: " + slips + ".");
        } else if (trace.size() >= STEP_CAP) {
            stop(); setMovementEnabled(false); status.setText("Episode stopped at the 500-move cap. The goal was not reached. Reset to try again.");
        }
        refresh();
    }
    private void refresh() {
        if (state == null) return;
        location.setText("Position: " + map.station(state.station()).name() + "     |     Facing: " + state.facing()
                + "     |     " + policy.name() + (manualOverride ? " + manual actions" : ""));
        stats[0].setText(Integer.toString(trace.size())); stats[1].setText(Integer.toString(blocked)); stats[2].setText(Integer.toString(slips));
        stats[3].setText(Double.isInfinite(selectedValues[state.index()]) ? "Infinite" : String.format(Locale.ROOT, "%.2f", selectedValues[state.index()]));
        mapPanel.update(state, start.station(), trace, reference.isSelected());
    }
    private void installKeys() {
        int[] keys = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_S};
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length; i++) {
            String name = actions[i].name(); final Action action = actions[i];
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keys[i], 0), name);
            getActionMap().put(name, new AbstractAction() {
                private static final long serialVersionUID = 1L;
                public void actionPerformed(java.awt.event.ActionEvent event) {
                    Component focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (focus instanceof javax.swing.text.JTextComponent || focus instanceof JComboBox<?>) return;
                    if (stepButton.isEnabled()) { stop(); manualOverride = true; advance(action); }
                }
            });
        }
    }
    private void saveTrace() {
        chooseSave("movement_log.csv", Csv.trace(trace, model, activeSeed,
                policy.name() + (manualOverride ? " + manual actions" : ""), start));
    }
    private void chooseSave(String filename, String content) {
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File(filename));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        if (java.nio.file.Files.exists(path) && JOptionPane.showConfirmDialog(this, "Replace the existing file?", "Confirm replacement", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { Csv.write(path, content); status.setText("Saved: " + path.toAbsolutePath()); }
        catch (IOException e) { error("Could not save CSV: " + e.getMessage()); }
    }
    private void compare() {
        stop(); compareButton.setEnabled(false);
        final NavigationModel batchModel = model; final State batchStart = start;
        final long batchSeed = activeSeed; final ValueIteration batchOptimal = optimal;
        status.setText("Comparing 2,000 episodes per policy from the configured start...");
        new SwingWorker<List<Simulation.Summary>, Void>() {
            protected List<Simulation.Summary> doInBackground() {
                var results = new ArrayList<Simulation.Summary>();
                for (Policy p : List.of(batchOptimal, Policy.shortestPath(map), Policy.random()))
                    results.add(Simulation.batch(batchModel, p, batchStart, batchSeed, 2000, 5000));
                return results;
            }
            protected void done() {
                compareButton.setEnabled(model == batchModel);
                try {
                    var results = get();
                    DefaultTableModel table = tableModel("Policy", "Reached / 2000", "Mean moves", "SE", "P95", "Exact mean");
                    StringBuilder csv = new StringBuilder("reliability,start,heading,seed," + Simulation.CSV_HEADER + "\n");
                    for (var r : results) {
                        table.addRow(new Object[]{r.policy(), r.successes(), String.format(Locale.ROOT, "%.3f", r.mean()),
                                String.format(Locale.ROOT, "%.3f", r.se()), r.p95(), String.format(Locale.ROOT, "%.3f", r.exact())});
                        csv.append(String.format(Locale.ROOT, "%.2f,%s,%s,%d,", batchModel.reliability(), map.station(batchStart.station()).name(), batchStart.facing(), batchSeed)).append(r.csv()).append('\n');
                    }
                    JPanel content = new JPanel(new BorderLayout(0, 12));
                    content.add(new JLabel("<html>Start: " + map.station(batchStart.station()).name() + ", " + batchStart.facing() + "; p=" + batchModel.reliability()
                            + "; base seed " + batchSeed + ", episodes 0..1999.<br>Cap: 5,000 moves. Means include capped failures; inspect the reached count. SE = standard error.</html>"), BorderLayout.NORTH);
                    JScrollPane scroll = scrollTable(table(table)); scroll.setPreferredSize(new Dimension(820, 135)); content.add(scroll, BorderLayout.CENTER);
                    int choice = JOptionPane.showOptionDialog(LondonPanel.this, content, "Policy comparison", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.PLAIN_MESSAGE, null, new String[]{"Close", "Save CSV"}, "Close");
                    if (choice == 1) chooseSave("policy_comparison.csv", csv.toString());
                    if (model == batchModel) status.setText("Comparison complete: 2,000 episodes per policy. All simulation seeds are reproducible.");
                } catch (Exception e) { error("Comparison failed: " + e.getMessage()); }
            }
        }.execute();
    }
    private void showHelp() {
        JOptionPane.showMessageDialog(this,
                "STATE: station and compass heading are fully observed.\n"
                + "ACTION: Forward, Left, Right, or Backward relative to that heading.\n"
                + "MOTION: intended bearing with probability p; each sideways bearing with (1-p)/2.\n"
                + "If there is no road, the agent stays and faces the attempted bearing.\n"
                + "On a curved road, arrival heading faces away from the destination entry port.\n"
                + "Every attempt costs 1, including blocked moves. Tower Hill is absorbing.\n\n"
                + "OPTIMAL MDP: value iteration minimizes expected moves until Tower Hill.\n"
                + "BFS: always aims along a path with the fewest road edges, ignoring slips.\n"
                + "RANDOM: chooses each relative action with equal probability.\n\n"
                + "The assignment does not specify probabilities, bearings, costs or starting state.\n"
                + "These are explicit implementation assumptions; all roads are bidirectional.\n"
                + "The drawing is a schematic; it is not a real London journey planner.\n\n"
                + "Run/Pause: animate. One step: one automated action. W/A/D/S: manual.\n"
                + "Apply & reset reuses the seed for a reproducible episode.\n"
                + "For an uncertainty demonstration, compare p=1.00 with p=0.80 or p=0.40.",
                "Model and controls", JOptionPane.INFORMATION_MESSAGE);
    }
    private void error(String message) { JOptionPane.showMessageDialog(this, message, "London agent", JOptionPane.ERROR_MESSAGE); }

    // Package-private entry points exercise the actual Swing controls in the integration checks.
    State currentState() { return state; }
    int moveCount() { return trace.size(); }
    boolean canMove() { return stepButton.isEnabled(); }
    void testStep() { stepButton.doClick(0); }
    void testReset() { applySettings(); }
    void testSettings(int station, double p) { stationBox.setSelectedIndex(station); reliabilityBox.setValue(p); applySettings(); }
    void shutdown() { stop(); }

    static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container c) layoutTree(c);
    }
}
