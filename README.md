## JLabGym Door Solvers


For [**experiments replication**, see here](./experiment_replication.md).

This project provides a number of solvers for JLabGym.  `JLabGym` provides a Java-based environment that will allow you to use the game [Lab Recruits](https://github.com/iv4xr-project/labrecruits) as an 'AI Gym'. An _AI Gym_ is an environment where you can try out your AI, or whatever algorithm X, to control agent to perform some tasks in the environment. The Gym itself is included in this project. For its documentation, [see here](./gym.md). In the orginal gym, the task is to discover connections between buttons and doors in a given Lab Recuits level. Here, we change that: the goal is to open a given target door (more precisely, to reach a state where the door is observed to be open).

Provided algorithms:

   * **Random**: repeatedly alternates between exploring to discover game objects, and randomly choosing a pair _(b,d)_ of button and door; it then toggles _b_ to find out if it opens _d_.

   Implementing class: [`BaseSearchAlgorithm`](./src/main/java/algorithms.BaseSearchAlgorithm.java).

   * **Evo** implements an evolutionary algorithm for automated testing. Each gene in a chromosome is a sequence `navigateTo_(o); intertact_(o)` where o is a button. Exploration is implicitly invoked if at the moment o is not known to the agent. The fitness of a chromosome is maximum if it solves the given testing task. Else the fitness is the number of button-door connections found when the chromosome is executed, plus the number of doors that are open at the end of the execution. Chromosomes are generated offline, without taking into account if they can actually be fully executed. If a chromosome is only partially executable, its fitness is calculated after the last executable gene.

   Implementing class: [`Evolutionary`](./src/main/java/algorithms.Evolutionary.java)

   * **MTCS** implements a reinforcement learning algorithm called Monte Carlo tree search. It is popularly used to train computers to play a board game such as the Go and Hex. The search tree is generated online. At each node _N_ in the search tree, possible actions take the form of a sequence `navigateTo(o);intertact(o)` where o is a button. However, unlike _Evo_ (which is offline), only buttons that are reachable from _N_ are considered. So, every path in the search tree is always executable. The reward of a play is defined the same as the fitness value of _Evo_.

   Implementing class: [`MCTS`](./src/main/java/algorithms.MCTS.java)

   The current implementation stops the algorithm as soon as a sequence of steps that completes the given task is found. If you wish to continue the learning, you can modify the termination condition  in the implementation. Note that despite this early termination, each episode still learns from previous ones.

   * **Q** implements the Q-learning algorithm. It is a reinforcement learning algorithm. Each 'state' in the Q-table takes the form of a sequence σ of the buttons interacted so far. The table is _incrementally_ populated as the algorithm proceeds. Like _MTCS_, Q is also online. So, when it is in a current state σ, possible actions again take the form of a sequence _a_ = `navigateTo(o);intertact(o)` where o is a button. However, only buttons that are reachable from σ are considered.
   The direct reward of executing _a_ is a maximum value if it solves the given testing task. Else it is _3C + O_ where _C_ is the number of new button-door connections discovered by _a_ and _O_ the number of open doors in the new state minus the number of open doors in σ.

   Implementing class: [`QAlg`](./src/main/java/algorithms.QAlg.java)

   As in MCTS, the implementation stops the algorithm as soon as a sequence of actions that completes the given task is found. You can modify the termination condition  in the implementation. Note that despite this early termination, each episode still learns from previous ones.

All these algorithms are implemented to operate on top of automated navigation and exploration provided by another library called [`iv4xrDemo`](https://github.com/iv4xr-project/iv4xrDemo), which in turn is based on a BDI agent programming framework called [iv4xr/aplib](https://github.com/iv4xr-project/aplib).
By leveraging this auto-navigation and exploration, the algorithms can operate using 'actions' at a high level. E.g. algorithms only need to specify which button/door it wants to go and interact with; the underlying path-finding algorithm will guide the test agent to the target item, provided the item's location is known to the agent (e.g. it saw it few minutes ago), and the agent believes that the path to the item is clear (e.g. not blocked by a closed door, or a door the agent believes to be closed).

### Installing the game

You need to first install the game Lab Recuits. You can get a pre-compiled executable from the github home of [Lab Recruits](https://github.com/iv4xr-project/labrecruits). We need version 2.3.3.
If the executables are not there anymore, then you will have to build the game yourself using Unity :) See the README of Lab Recruits for the specific version of Unity that you need.

In the project root, create a directory named `gym`, if it is not already there. Then you need to put Lab Recruits' executable there:

```
(project root)
   |-- gym
        |-- Windows
        |   |-- bin
        |-- Mac
            |-- bin
        |-- Linux  
            |-- bin   
```
   * Windows: put  `LabRecruits.exe` and related files in `gym/Windows/bin`.
   * Mac: put `LabRecruits.app` in `gym/Mac/bin`.
   * Linux: put `LabRecruits` executable and related files in `gym/Linux/bin`.

After this, you are good to go.


### Running the algorithms

See the method `createAnAlgorithm()` in the class [`STVRExperiment`](./src/test/java/stvrExperiment/STVRExperiment.java). You can specify which algorithm you want to run, which Lab Recruits level you want to target, etc.
Invoking `createAnAlgorithm()`
returns an instance i of  `MyTestingAI`. Invoking `i.exploreLRLogic()` will run the algorithm.
