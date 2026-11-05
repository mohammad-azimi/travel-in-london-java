# Travel in London - Stochastic Decision Agent

A Java desktop application for the assignment **The motion of the agent in London**. It reproduces the supplied schematic, plans an optimal policy, and simulates unreliable movement until the agent reaches Tower Hill.

## Quick start on Windows / VS Code

1. Extract the ZIP. In VS Code use **File > Open Folder** and select `london-agent-java` itself (the folder containing `src`, `sources.txt` and `run.bat`).
2. Install a **JDK 17 or newer** and VS Code's **Extension Pack for Java** if they are not already installed.
3. Open a **new** VS Code terminal. Verify both commands:

   ```powershell
   java -version
   javac -version
   ```

4. Run:

   ```powershell
   .\run.bat
   ```

   In Command Prompt the equivalent is `run.bat`. This builds the current source and opens the GUI.

5. Alternatively open `src/london/Main.java` and click **Run** above `main`, or select **London Agent - GUI** in Run and Debug and press **F5**.

The included JAR can also run directly without rebuilding:

```powershell
java -jar dist/london-agent.jar
```

JDK 17+ is needed to compile; a Java 17+ runtime with desktop support is enough for the included JAR. No Maven, Gradle, JavaFX, Python, downloads or third-party Java libraries are required by the project.

## Controls

| Control | Meaning |
| --- | --- |
| Start station / Initial heading | Fully observed initial state |
| Automated controller | Optimal MDP, shortest path (BFS), or uniform random |
| Reliability p | Probability of executing the intended bearing |
| Random seed | Reproduce an episode with the same settings and action sequence |
| Apply & reset | Commit settings, solve the model and clear the episode |
| Run agent / Pause | Start or stop the animation |
| One step | Execute one action from the selected automated controller |
| Forward / Left / Right / Backward | Execute a relative manual action, with the same motion uncertainty |
| W / A / D / S | Shortcuts for the four manual actions; ignored while typing into fields |
| Show shortest path reference | Dotted BFS route; this is a reference, not a predicted stochastic trajectory |
| Compare 3 policies | 2,000 episodes per controller from the configured initial state; optional CSV export |
| Export movement log | Save actions, intended/actual bearings, slip/block indicators and configuration |
| Optimal policy tab | Expected moves and best action for all 56 states |

The right control area scrolls on smaller screens. The agent is the teal marker; its arrow shows the arrival heading. A pale teal overlay shows traveled roads, a pale blue ring marks the start, and a gold ring marks Tower Hill.

**Blocked attempts and slips are different.** A correct action can be blocked because no road exists. A sideways slip can follow a valid road and therefore be unblocked. A move can also be both slipped and blocked.

## What the assignment specifies

- The agent knows its position and has the map.
- It selects Forward, Backward, Left or Right at each step.
- Motion is stochastic and actions are unreliable.
- A single destination, called Tower in the text, terminates the interaction.

The map labels that destination **Tower Hill**, which is the terminal station in the program. The assignment gives no probabilities, starting station, initial heading, numerical costs, direction convention or required solution algorithm.

## Explicit implementation assumptions

These are modeling choices, not additional instructions from the teacher.

- **Map:** 14 named stations and 15 bidirectional roads, reconstructed from the four colored chains in the supplied diagram. Interchange symbols are merged into one station. Colors have no effect on cost.
- **State:** `(station, heading)`, with four compass headings. There are 56 state representations, of which the four at Tower Hill are terminal.
- **Default start:** Green Park, facing East; seed 42. Any station and heading can be selected.
- **Action:** turn relative to the current heading and attempt one road in the resulting bearing, in a single step.
- **Motion:** intended bearing with probability `p`; 90 degrees left of it with `(1-p)/2`; 90 degrees right with `(1-p)/2`. Default `p=0.80`. There is no extra direct reverse-slip outcome.
- **Missing road:** position remains unchanged and heading becomes the attempted actual bearing. The attempt still costs one step. Missing roads are boundaries, not exits from the city.
- **Road ports:** each station has at most one outgoing road per compass bearing. Roads can curve. After a successful move the heading points away from the destination's entry port. In particular Moorgate -> Liverpool departs East and arrives facing South; its reverse departs North and arrives facing West. This separates the red approach from the yellow west approach at Liverpool. The UI draws those curves explicitly.
- **Cost:** every attempted move costs 1, including blocked attempts; no cost after reaching Tower Hill. Road distance and travel time are not provided and are not invented.
- **Goal:** Tower Hill is absorbing. Starting there ends immediately with zero moves. The simulator executes no further action after arrival.
- **Limits:** interactive episodes stop after 500 moves; comparison experiments use 5,000. Hitting a limit is a failure, not a successful arrival.

### Reconstructed roads and departure ports

The direction in each endpoint column is the outgoing port **at that endpoint**. Opposite headings are usual, but the curved red Liverpool approach is explicitly different.

| Color | Endpoint A | Port at A | Endpoint B | Port at B |
| --- | --- | --- | --- | --- |
| Red | Baker Street | East | King's Cross | West |
| Red | King's Cross | East | Moorgate | West |
| Red | Moorgate | East | Liverpool Street | North |
| Red | Liverpool Street | South | Tower Hill | North |
| Yellow | Bond Street | East | Oxford Circus | West |
| Yellow | Oxford Circus | East | Chancery Lane | West |
| Yellow | Chancery Lane | East | Bank | West |
| Yellow | Bank | East | Liverpool Street | West |
| Green | Green Park | East | Piccadilly Circus | West |
| Green | Piccadilly Circus | East | Covent Garden | West |
| Green | Covent Garden | North | Chancery Lane | South |
| Green | Chancery Lane | North | King's Cross | South |
| Blue | Old Street | South | Moorgate | North |
| Blue | Moorgate | South | Bank | North |
| Blue | Bank | South | Monument | North |

## Algorithm

This is a fully observable Markov decision process with known transitions, formulated as an **undiscounted stochastic shortest path** problem:

```text
J(Tower Hill, heading) = 0
J(s) = min over actions a [1 + sum over s' P(s' | s,a) J(s')]
```

Value iteration starts with zero values, performs synchronous updates and stops when the largest update is below `1e-10`. The final greedy policy is computed from the converged values. Ties follow the fixed action order Forward, Left, Right, Backward. The maximum is 100,000 sweeps, with an explicit error if convergence fails.

Each nonterminal attempt has a strictly positive cost and a proper goal-reaching policy exists on this finite connected model, including at p=0. Improper policies therefore have infinite total expected cost. `PolicyEvaluation` detects such policies and independently solves `(I-P_policy) J = 1` for finite-cost states using pivoted Gaussian elimination. This checks the planner against a different numerical method.

All four relative actions are available at every heading and turning has no separate cost. Consequently optimal values are equal for different headings at the same station in this model; the best **relative action** still depends on heading.

The program computes a model-based policy. It does not need training episodes or Q-learning because the transition model is provided by the explicit assumptions above.

## Reproduce tests and results

From PowerShell:

```powershell
.\test.bat
java -jar dist/london-agent.jar --demo --seed 42 --output results/demo_trace.csv
java -jar dist/london-agent.jar --experiments --episodes 10000 --seed 42 --output results/comparison.csv
```

On Linux/macOS:

```bash
bash test.sh
bash run.sh
java -jar dist/london-agent.jar --experiments --episodes 10000 --seed 42
```

You can run the tests directly on the included JAR:

```powershell
java -Djava.awt.headless=true -jar dist/london-agent.jar --self-test
```

Command options:

```powershell
java -jar dist/london-agent.jar --help
java -jar dist/london-agent.jar --demo --reliability 1 --start "Green Park" --heading EAST
java -jar dist/london-agent.jar --demo --controller bfs --reliability 0.4 --seed 17
java -jar dist/london-agent.jar --experiments --reliability 0.8 --episodes 2000 --seed 17
```

Experiment runs also write a 56-state optimal-policy CSV for each reliability. All output paths are relative to the opened project folder unless an absolute path is supplied.

### Recorded results

10,000 episodes per configuration, Green Park / East, base seed 42, 5,000-move cap. All 90,000 episodes reached Tower Hill. Per-episode seeds are produced by `Simulation.episodeSeed(baseSeed, episodeIndex)`, a SplitMix64 mixing function, and each episode uses `java.util.Random`. The three policies use the same episode-seed schedule; their sampled draws can diverge after different decisions. GUI comparisons use the same schedule with 2,000 episodes.

| p | Controller | Simulated mean moves | SE | Exact expected moves |
| --- | --- | ---: | ---: | ---: |
| 0.40 | Optimal MDP | 22.7665 | 0.1131 | 22.7396 |
| 0.40 | Shortest path BFS | 24.4684 | 0.1277 | 24.3281 |
| 0.40 | Uniform random | 117.7075 | 0.9548 | 118.9091 |
| 0.80 | Optimal MDP | 8.1854 | 0.0221 | 8.1465 |
| 0.80 | Shortest path BFS | 8.1854 | 0.0221 | 8.1465 |
| 0.80 | Uniform random | 117.6039 | 0.9536 | 118.9091 |
| 1.00 | Optimal MDP | 6.0000 | 0.0000 | 6.0000 |
| 1.00 | Shortest path BFS | 6.0000 | 0.0000 | 6.0000 |
| 1.00 | Uniform random | 118.6618 | 0.9746 | 118.9091 |

At p=0.40 the optimal policy reduces **exact expected** moves by about 6.53% relative to BFS. At p=0.80 and 1.00 both choose equivalent optimal behavior; an improvement over BFS is not claimed there. The random controller's exact value is unchanged across p because uniformly random relative actions produce uniformly random attempted compass bearings.

`mean_capped_steps` averages all attempted-step counts, including capped failures if any occur. Its SE and approximate normal 95% interval refer to this capped statistic. They estimate uncapped completion cost only when truncation is negligible; always inspect `successes` and `success_rate`. The exact expectation is uncapped and may be `Infinity` for an improper policy (for example BFS from Green Park at p=0). The P95 uses the nearest-rank definition.

## Project structure

| File | Responsibility |
| --- | --- |
| `src/london/CityMap.java` | Station graph, road ports and BFS reference |
| `Direction.java`, `Action.java`, `State.java` | Heading, relative turns and observed state |
| `NavigationModel.java` | Transition probabilities and sampled movement |
| `ValueIteration.java` | Bellman updates and optimal policy |
| `Policy.java` | Policy interface, BFS and random baselines |
| `PolicyEvaluation.java` | Independent exact expected-cost evaluation |
| `Simulation.java`, `Csv.java` | Episodes, statistics, reproducible seeds and exports |
| `MapPanel.java`, `LondonPanel.java` | Swing map and controls |
| `Main.java` | Desktop entry point and command-line modes |
| `SelfTest.java` | Executable model and GUI integration checks |
| `dist/london-agent.jar` | Runnable application built from the delivered source |
| `results/` | Actual recorded numerical output and test logs |
| `docs/London_Agent_Report.pdf` | English project report |
| `PRESENTATION_EN.md` | Presentation script and likely questions |
| `QUICK_START_FA.md` | Persian run and demonstration guide |

## Troubleshooting

- **`java` or `javac` is not recognized:** install/configure a JDK, add its `bin` folder to PATH and open a new VS Code terminal. Confirm both version commands show 17 or newer.
- **`UnsupportedClassVersionError`:** the selected Java runtime is older than 17. Select a matching JDK in VS Code and in the terminal.
- **VS Code cannot find `london.Main`:** open `london-agent-java`, not `src` or `src/london`. Use **Java: Clean Java Language Server Workspace** if the extension retains an old folder configuration.
- **Double-clicking the JAR does nothing:** run `java -jar dist/london-agent.jar` in the terminal to see any error. No JAR file association is required.
- **Settings changed but motion buttons are disabled:** click **Apply & reset**.
- **The agent stays in place:** inspect Intended, Actual and Outcome. Blocked movement is part of the stochastic model.
- **Cannot see export/help controls:** scroll the right-hand control panel.

## References

1. Supplied assignment: *The motion of the agent in London*, `3. Travel in London.pdf`.
2. Bertsekas, D. P., and Tsitsiklis, J. N. (1991). *An Analysis of Stochastic Shortest Path Problems*. Mathematics of Operations Research, 16(3), 580-595. [Author-hosted PDF](https://web.mit.edu/dimitrib/www/Stochasticsp.pdf), [DOI](https://doi.org/10.1287/moor.16.3.580).
3. Oracle. [Java SE 17 Swing API and threading policy](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/package-summary.html). All UI updates use the event dispatch thread; the batch comparison runs in a SwingWorker.
