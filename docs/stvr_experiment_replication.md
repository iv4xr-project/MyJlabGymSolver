## Replication Instructions

Below we will explain to to re-run our "stvr-experiment". The experiment runs several algorithms to evaluate their performance in solving a set of testing tasks on the game [Lab Recruits](https://github.com/iv4xr-project/labrecruits).
To run the experiment you will need the executable of LabRecruits; the instructions to get them are in a subsection below.

The algorithms evaluated by this experiment are:

   * _Random_.
   * _Evolutionary_ algorithm.
   * _Monte Carlo Search Tree_ (MTCS).
   * _Q-learning_.

All these algorithms are configured to operate on top of automated navigation and exploration provided by the underlying iv4xr/aplib library. This means these algorithms only need to specify which button/door it wants to go and interact with; the underlying path-finding algorithm will guide the test agent to the target item, provided the item's location is known to the agent (e.g. it saw it few minutes ago), and the agent believes that the path to the item is clear (e.g. not blocked by a closed door, or a door the agent believes to be closed).

The set of testing tasks are grouped in three groups: _ATEST_ (seven), _DDO_ (two), and _Large-Random_ (ten). See the paper for descriptions of these groups.

The experiment runner can be found in the class [`STVRExperiment`](./src/test/java/stvrExperiment/STVRExperiment.java).
In principle you can set various experiment parameters yourself; they are configured in this class. The pre-configured setup is to run the algorithm with the budget of 10 minutes per task on ATEST tasks, and one hour per task on DDO and Large-Random.
These are set in the variable `ATEST_SAruntime`, `DDO_SAruntime`, and `LargeRandom_SAruntime`.
Each run is set to be repeated 5 times. This is set in the variable `repeatNumberPerRun`. You can change this to e.g. 10 times, or just 2 times if you want to get faster results.

For convenience, the experiments are not scripted as a main-method, but as a set of Junit tests, so that you can run them separately e.g. using Maven test from the command-line, or from an IDE like Eclipse. The test-methods are:

  * `run_ATEST_experiment_Test()` will run the algorithms on the ATEST tasks. To run this from command-line, using Maven, you can do:

  ```
  > mvn test -Dtest=STVRExperiment#run_ATEST_experiment_Test
  ```

  * `run_DDO_experiment_Test()` will run the algorithms on the DDO tasks. To run this from command-line, using Maven, you can do:

  ```
  > mvn test -Dtest=STVRExperiment#run_DDO_experiment_Test
  ```

  * `run_LargeRandom_experiment_Test()` will run the algorithms on the Large-Random tasks. To run this from command-line, using Maven, you can do:

  ```
  > mvn test -Dtest=STVRExperiment#run_LargeRandom_experiment_Test
  ```

Results can be found in the data dir in the project-root.

By default, the algorithms will run the game Lab Recruits without graphics. If you want to see the graphics, set the variable `useGraphics` to true, in the method `launchLabRcruits()` in the class [`STVRExperiment`](./src/test/java/stvrExperiment/STVRExperiment.java).

### Installing Lab Recuits

If you get this project from a replication-zip, it will already contain the Lab Recuits game. Else you can get it from the github home of [Lab Recruits](https://github.com/iv4xr-project/labrecruits). We need version 2.3.3.

In the project root, create a directory named `gym`, if it is not already there. Then we need to put Lab Recruits' executable there:

   * Windows: put  LabRecruits.exe and related files in `gym/Windows/bin`.
   * Mac: put `LabRecruits.app` in `gym/Mac/bin`.
   * Linux: put LabRecruits executable and related files in `gym/Linux/bin`.

After this, you are good to go.
