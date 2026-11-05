# Presentation script: Travel in London

Suggested duration: 3-5 minutes. Keep the application open before starting.

## Opening - show the map

Good afternoon. My project is called Travel in London. I implemented a decision-making agent in Java. The agent moves through the stations on the map and must reach Tower Hill.

The environment is fully observable: the agent knows its current station and heading. However, its movement is stochastic, so an intended action may produce a different movement.

I reconstructed fourteen stations and fifteen bidirectional roads from the assignment diagram. Tower Hill is the only terminal position.

## Explain the model - point to the settings

The state contains the station and the current heading. With four headings at each station, the model has fifty-six state representations.

The agent has four relative actions: forward, backward, left and right. Forward means the current heading, rather than a fixed direction on the screen.

The assignment does not specify numerical motion probabilities. I therefore made them explicit and configurable. With the default setting, the intended bearing occurs with probability zero point eight. Each sideways error has probability zero point one.

If no road exists in the actual direction, the agent stays at the same station and turns to that direction. Every attempt costs one step, including a blocked attempt. Roads can curve, so the arrival heading follows the road's entry at the next station.

## Explain the method - show the optimal-policy tab

I formulated the problem as a Markov decision process and used value iteration. The objective is to minimize the expected number of attempted moves before reaching Tower Hill.

At the destination, the remaining cost is zero. At another state, the algorithm evaluates every action by adding one step to the probability-weighted remaining costs of its possible next states. It selects the action with the smallest expected cost.

Because the map and transition model are known, I can compute the policy directly. I do not need a training phase.

## First demonstration - p=1.00, Green Park, East, seed 42

First, I remove motion uncertainty by setting the reliability to one. After resetting, the agent reaches Tower Hill in six moves. This agrees with the shortest path computed independently using breadth-first search.

The route is Green Park, Piccadilly Circus, Covent Garden, Chancery Lane, Bank, Liverpool Street, and Tower Hill.

## Second demonstration - p=0.80, same start and seed

Now I set the reliability to zero point eight. The action is chosen optimally, but its outcome is still random.

Here, in step six, the agent intends to move south from Liverpool Street. Instead, it moves west to Bank. The log records the intended and actual directions separately. The agent then uses its new state to choose another action and reaches Tower Hill in eight moves.

Using the same seed and action sequence reproduces this episode.

## Numerical results - Compare 3 policies, then show the report

I compared the optimal policy with a shortest-path controller and a uniformly random controller. The report uses ten thousand episodes for each configuration, with the same initial state and reproducible seeds.

At reliability zero point eight, the optimal policy needs about eight point one nine moves on average. The exact model expectation is about eight point one five. The shortest-path controller has the same behavior in this case.

At reliability zero point four, the exact expected cost is about twenty-two point seven four for the optimal policy and twenty-four point three three for the shortest-path controller. This is a reduction of about six point five three percent. The reason is that value iteration considers the side effects of an action, rather than only the next intended road.

The uniformly random controller requires about one hundred and nineteen moves in expectation. All recorded experimental episodes reached the goal. I also report the episode limit and success counts so that a truncated run cannot be mistaken for a successful completion.

## Finish - show the tests

I checked the transition probabilities, relative actions, blocked movement, terminal behavior and reproducibility. I verified deterministic results against breadth-first search, and stochastic values against an independent linear-equation solver. I also tested the actual Swing step and reset controls without a display.

The application therefore demonstrates both optimal decision-making and the difference between knowing the current state and controlling the next outcome.

# Questions and short answers

| Question | Answer |
| --- | --- |
| Why is it an MDP? | The next-state distribution depends on the current station, heading and action. The full state is observed. |
| Why include heading? | Left and right are relative. The same command can mean different compass bearings at the same station. |
| Why are the values equal across headings at one station? | Every compass bearing can be selected by one of the four actions and turning has no additional cost. Relative action labels still change with heading. |
| Why not use only BFS? | BFS minimizes intended road edges and ignores stochastic outcomes. It can be suboptimal when sideways errors matter. |
| Why does BFS match the optimal method at p=0.8? | On this map and under these assumptions, its selected actions are also optimal. An advantage is not guaranteed for every configuration. |
| Why not Q-learning? | The transition model is known and small, so dynamic programming computes the policy without sampling-based training. |
| What is the Bellman equation? | J(s) is the minimum over actions of one plus the expected next-state J value. J is zero at Tower Hill. |
| Is there a discount factor? | No. This is an undiscounted stochastic shortest path problem with positive step cost and a proper goal-reaching policy. |
| What does optimal mean? | Minimum expected total attempted moves under the specified transition model, up to numerical tolerance. A single random episode can still take a detour. |
| Are the probabilities in the teacher's file? | No. They are declared implementation assumptions and can be changed in the interface. |
| What happens at a missing road? | The position stays unchanged, the agent faces the attempted direction, and one step is counted. |
| What happens after the goal? | The state is absorbing and the interface disables further movement. Starting at the goal costs zero. |
| Why does a road change the heading? | The schematic has curved approaches. The arrival heading faces away from the next station's entry port, which keeps the two Liverpool approaches distinct. |
| Why is the random policy's exact cost unchanged with reliability? | Uniform relative actions make attempted compass bearings uniform for every reliability value. |
| Can an action be optimal even if its intended direction is blocked? | Yes. At very low reliability, the sideways outcomes may have lower expected cost. The model evaluates all outcomes. |
| What happens at p=0 for BFS? | It never executes the intended bearing. From Green Park it cannot enter Tower Hill, so its uncapped expected cost is infinite; simulations stop at their cap. |
| How do you know the values are correct? | They agree with BFS distances at p=1 and with independent exact policy evaluation for stochastic settings. Monte Carlo means also agree within sampling error. |
| Which part is written in Java? | The complete application: graph, model, planner, simulator, statistics, exports, GUI and executable tests. |
| What are the limitations? | A small schematic map, assumed motion probabilities and road ports, equal attempt costs, no real travel times, and no traffic observations. |

# Code to show if requested

1. `CityMap.java`: the station list, fifteen roads, and explicit departure ports.
2. `NavigationModel.java`: the three possible motion bearings and blocked-road handling.
3. `ValueIteration.java`: the Bellman update, convergence tolerance and greedy policy.
4. `PolicyEvaluation.java`: independent expected-cost verification and infinite-cost detection.
5. `SelfTest.java`: deterministic, stochastic and Swing integration checks.
