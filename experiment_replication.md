## Replication Instructions

Below we will explain to to re-run our "stvr-experiment". The experiment runs several algorithms to evaluate their performance in solving a set of testing tasks on the game [Lab Recruits](https://github.com/iv4xr-project/labrecruits).
To run the experiment **you will need the executable of LabRecruits**; the instructions to get them are in a subsection below.

The algorithms evaluated by this experiment are:

   * _Random_.
   * _Evolutionary_ algorithm.
   * _Monte Carlo Search Tree_ (MTCS).
   * _Q-learning_.

See the project [README](./README.md) for more details about these algorithms.

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

If you get this project from a replication-zip, it will already contain the Lab Recuits game. Else, you need to first install the game Lab Recuits. You can get a pre-compiled executable from the github home of [Lab Recruits](https://github.com/iv4xr-project/labrecruits). We need version 2.3.3.
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

### Saved results

Results from our own runs can be found in `./savedata`.
