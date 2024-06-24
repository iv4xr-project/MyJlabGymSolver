package stvrExperiment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static java.nio.file.StandardOpenOption.* ;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import agents.LabRecruitsTestAgent;
import algorithms.XBelief;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import environments.SocketReaderWriter;
import game.LabRecruitsTestServer;
import game.Platform;
import gameTestingContest.MyConfig;
import gameTestingContest.MyTestingAI;
import leveldefUtil.LRFloorMap;
import leveldefUtil.LRconnectionLogic;
import nl.uu.cs.aplib.utils.Pair;


/**
 * This test-class is an experiment-runner. It will run several algorithms to evaluate 
 * their performance in solving a set of testing tasks on the game Lab Recruits. 
 * The algorithms:
 * <ol>
 *    <li> Random
 *    <li> Evolutionary algorithm.
 *    <li> Monte Carlo Search Tree (MTCS)
 *    <li> Q-learning
 * </ol>
 * All algorithms operate on top of automated navigation and exploration provided by
 * the underlying iv4xr/aplib library. This means these algorithms only need to specify
 * which button/door it wants to go and interact with; the underlying path-finding
 * algorithm will guide the test agent to the target item, provided the item's location
 * is known to the agent (e.g. it saw it few minutes ago), and the agent believes that
 * the path to the item is clear (e.g. not blocked by a closed door, or a door the agent
 * believes to be closed).
 * 
 * <p>The set of testing tasks are grouped in three groups: ATEST (seven), DDO (two), and 
 * Large-Random (ten). See the paper for descriptions of these groups.
 * 
 * <p>In principle you can set various experiment parameters yourself; they are configured
 * in this class. The setup below is to run the algorithm with the budget of 10 minutes per
 * task on ATEST tasks, and one hour per task on DDO and Large-Random.
 * These are set in {@link #ATEST_SAruntime}, {@link #DDO_SAruntime},
 * and {@link #LargeRandom_SAruntime}.
 * Each run is set to be repeated 5 times. This is set in {@link #repeatNumberPerRun}. 
 * You can change this to e.g. 10 times, or just 2 times if you want to get faster results.
 * 
 * <p>For convenience, the experiments are not scripted as a main-method, but as a set
 * of Junit tests, so that you can run them separately e.g. using Maven test from the
 * command-line, or from an IDE like Eclipse. The test-methods are:
 * 
 * <ol>
 *    <li>{@link #run_ATEST_experiment_Test()}: this will run the algorithms on the
 *    ATEST tasks.
 *    <li>{@link #run_DDO_experiment_Test()}: this will run the algorithms on the
 *    DDO tasks.
 *    <li>{@link #run_LargeRandom_experiment_Test()}: this will run the algorithms on the
 *    Large-Random tasks.
 * </ol>
 * 
 * Results can be found in the data dir in the project-root.
 * 
 * <p>By default, the algorithms will run the game Lab Recruits without graphics. If
 * you want to see the graphics, set the variable useGraphics to true, in the method
 * {@link #launchLabRcruits()}.
 * 
 */
public class STVRExperiment {
	
	// ===== common parameters
	
	static String projectRootDir = System.getProperty("user.dir") ;
	
	static String levelsDir = Paths.get(projectRootDir, "src", "test", "resources", "levels", "STVR").toString() ;
	
	static String dataDir =  Paths.get(projectRootDir,"data").toString() ;
	
	static String[] availableAlgorithms = { 
			"Random"
			,"Evo"
			,"MCTS"
			,"Q"
	} ;
	
	//static int repeatNumberPerRun = 10 ;
	static int repeatNumberPerRun = 5 ;
	//static int repeatNumberPerRun = 2 ;

	static int[] randomSeeds = { 
		13, 3713, 255, 24, 999,
		4919, 1023, 1, 100, 10001 }  ;
	
	
	// ================ ATEST levels =================
	//static String[] targetLevels = { "buttons_doors_1", "samira_8room" } ;
	static String[] ATEST_levels = { 
		 "BM2021_diff1_R3_1_1_H"   // minimum solution: 2
		,"BM2021_diff1_R4_1_1"    // minimum solution: 4
		,"BM2021_diff1_R4_1_1_M"  // minimum solution: 3
		,"BM2021_diff2_R5_2_2_M"  // minimum solution: 2
		,"BM2021_diff2_R7_2_2"    // minimum solution: 4
		//,"BM2021_diff3_R4_2_2"    // minimum solution: 0
		,"BM2021_diff3_R4_2_2_M"  // minimum solution: 4
		,"BM2021_diff3_R7_3_3" // minimum solution: 2
	} ;
	
	static String[] ATEST_targetDoors = {
		"door3", // "door1", 
		"door6", "door5", "door4", 
		"door6", 
		//"door6", 
		"door3", "door6"
	} ;
	
	// 10-mim runtime, 500sec, which is then 600s after 1.2 multiplier:
	static int[] ATEST_SAruntime = { 
			500, 500, 500, 500, 
			500, 500, 500, 500 } ;
	
	// specifying search-depth:
	static int[] ATEST_episode_length = {
		5,5,5,5,
		5,5,5,5
	} ;
	
	// ================ DDO levels =================

	static String[] DDO_levels = { "sanctuary_1", "durk_1" } ;
	static String[] DDO_targetDoors = { "doorEntrance", "doorKey4",  } ;
	// 1-hr runtime, 3000sec, which is then 3600s after 1.2 multiplier:
	static int[] DDO_SAruntime = { 3000, 3000  } ;
	
	// specifying search-depth:
	static int[] DDO_episode_length = { 9 , 5 } ;
	

	// ================ Large-Random level =================

	static String[] LargeRandom_levels = { 
	  "FBK_largerandom_R9_cleaned", 
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned", 
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned",  
	  "FBK_largerandom_R9_cleaned"
	  } ;
	
	/*
	// Targets of the original experiment in the ATEST-paper:
	static String[] LargeRandom_targetDoors = {
			  //"door26",  // F1
			  //"door5",   // F2
			  //"door39",  // F3
			  //"door33",  // F4
			  "door16",  // F5
			  "door37",  // F6
			  "door34",  // F7
			  "door3",   // F8  
			  "door21",  // F9  
			  "door22",  // F10
			  "door38"   // F11
			  }  ;
	*/
	
	static String[] LargeRandom_targetDoors = { // new targets, 10x
		"door17",  
		"door12",   
		"door5",  
		"door39",  
		"door2",
		// ----
		"door33",  
		"door16",  
		"door30",   
		"door15",  
		"door9"  
	} ;
	
	// 1-hr runtime, 3000sec, which is then 3600s after 1.2 multiplier:
    static int[] LargeRandom_SAruntime = { 
	 		3000, 3000, 3000, 3000, 3000,
	        3000, 3000, 3000, 3000, 3000 } ;
	
	static int[] LargeRandom_episode_length = { 
			2,   // d17 solved
			4,   // d12 solved
			5,   // d5  solved
			6,   // d39 solved
			11,  // d2  solved
			// ==
			8,   // d33 solved
			11,  // d16 solved
			13,  // d30 mostly solved
			15,  // d15 not solved
			15   // d9  mostly solved
	    } ;

		
	static LabRecruitsTestServer labRecruitsBinding;
	
	static class Result1 {
		String level ;
		String alg ;
		int numberOfConnections ;
		int runtime ;
		int numberOfTurns ;
		boolean goalsolved ;
		int connectionsInferred ;
		int correctConnections ;
		int wrongConnections ;
		int numberOfEpisodes ;
		float areaCoverage ;
		/**
		 * Well... for the LargeRandom experiment we need to bubble up area coverage
		 * information :(  Piggy-backing it here.
		 */
		Set<Pair<Integer,Integer>> visitedTiles ;
		
		@Override
		public String toString() {
			String z = "== level:" + level ;
			z +=     "\n== alg :" + alg ;
			z +=     "\n== goal:" + (goalsolved ? "ACHIEVED" : "X") ;
			z +=     "\n== runtime(sec):" + runtime ;
			z +=     "\n== #turns      :" + numberOfTurns ;
			z +=     "\n== #episodes   :" + numberOfEpisodes ;
			z +=     "\n== #connections:" + numberOfConnections ;
			z +=     "\n==    inferred :"    + connectionsInferred ;
			z +=     "\n==    correct  :"     + correctConnections ;
			z +=     "\n==    wrong    :"       + wrongConnections ;
			z +=     "\n== area-cov    :"     + areaCoverage ;
			return z ;
		}
	}
	
	static int numbeOfTimesGoalSolved(List<Result1> rss) {
		return (int) rss.stream().filter(v -> v.goalsolved == true).count() ;
	}
	
	static float avrgRuntime(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.runtime).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgTurns(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.numberOfTurns).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgNumberOfEpisodes(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.numberOfEpisodes).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgInferredConnections(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.connectionsInferred).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgCorrect(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.correctConnections).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgWrong(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.wrongConnections).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static float avrgAreaCoverage(List<Result1> rss) {
		double a = rss.stream().map(r -> (double) r.areaCoverage).collect(Collectors
							  .averagingDouble(t -> t)) ;
		return (float) a ;
	}
	
	static void launchLabRcruits() {
        var useGraphics = false ; // set this to false if you want to run the game without graphics
        SocketReaderWriter.debug = false;
        labRecruitsBinding = new LabRecruitsTestServer(
        		useGraphics,
                Platform.PathToLabRecruitsExecutable(projectRootDir));
        try {
        	// waiting 10secs:
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        //labRecruitsBinding.waitForGameToLoad();
    }
	
	/**
	 * Append the given string to a file. Create the file if it does not 
	 * exists.
	 */
	void writelnToFile(String dir, String fname, String s, boolean echo) throws IOException {
	    Files.writeString(
	        Path.of(dir, fname),
	        s + "\n",
	        CREATE, APPEND
	    );
	    if (echo) {
	    	System.out.println(s) ;
	    }
	}
	
	/**
	 * Create an instance of a search algorithm (Evo/Q/MCTS). It will be wrapped inside 
	 * a MyTestingAI object. 
	 * 
	 * <p>The timeBudget is in msec.
	 */
	MyTestingAI createAnAlgorithm(String algorithmName, 
			String levelName, 
			String targetDoor,
			String agentId,
			int rndSeed,
			int timeBudget,
			int episodeLength,
			int budget_per_task,
			int exploration_budget
			) {
		
		// Configure the algorithm:
		MyConfig.ALG = algorithmName ;
		//MyConfig.solutionLengthUpperBound = maxSearchDepth ;
		MyConfig.agentId = agentId ;
		MyConfig.randomSeed = rndSeed ;
		MyConfig.searchbuget = timeBudget ;
		MyConfig.target = targetDoor ;
		MyConfig.solutionLengthUpperBound = episodeLength ;
		MyConfig.budget_per_task = budget_per_task ;
		MyConfig.explorationBudget = exploration_budget ;
		
		// config for LR:
		var config = new LabRecruitsConfig(levelName,levelsDir);        
		
        // instantiating the algoritm, using the params as in MyConfig:
        MyTestingAI myTestingAI = new MyTestingAI() ;
        
        myTestingAI.agentConstructor = dummy -> {
        	// create an instance of LabRecruitsEnvironment; it will bind to the
            // Lab Recruits instance you launched above. It will also load the
            // level specified in the passed LR-config:
        	System.out.println(">>>> Launching LR") ;
        	launchLabRcruits() ;
        	LabRecruitsEnvironment env = new LabRecruitsEnvironment(config);
        	LabRecruitsTestAgent agent = new LabRecruitsTestAgent(agentId) // matches the ID in the CSV file
    				.attachState(new XBelief())
    				.attachEnvironment(env);
    		return agent ;
        } ;
        
        return myTestingAI ;
	}
	
	
	/**
	 * Run the specified algorithm on the specified LR level. The result will be printed
	 * to a file and returned as an instance of Result1.
	 * <p>The timeBudget is in msec.
	 */
	Result1 runAlgorithm(String algorithmName,
			String level, 
			String targetDoor, 
			String agentId,
			int runNumber,
			int rndSeed,
			int timeBudget,
			int episodeLength,
			int budget_per_task,
			int exploration_budget,
			String dirToSaveResult) throws Exception {
		String levelFile = Paths.get(levelsDir, level + ".csv").toString() ;
		var referenceLogic = LRconnectionLogic.parseConnections(levelFile) ;
		var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
		
		// instantiate the algorithm:
		var alg = createAnAlgorithm(algorithmName,level,targetDoor,agentId,rndSeed,
					timeBudget,episodeLength,
					budget_per_task, exploration_budget) ;
		
		alg.closeSUT = dummy -> {
			if (labRecruitsBinding != null) {
				labRecruitsBinding.close();
				labRecruitsBinding = null ;
			}
        	System.out.println(">>>> Closing LR") ;
			return null ;
		} ;
		
		// run the algorithm:
		long t0 = System.currentTimeMillis() ;
		var discoveredConnections = alg.exploreLRLogic() ;
		// runtime in second
		long runtime = (System.currentTimeMillis() - t0)/1000 ;
		
		// just to make sure that LR is closed:
		if (labRecruitsBinding != null) {
			labRecruitsBinding.close();
			labRecruitsBinding = null ;
			Thread.sleep(3000);
		}
		
		Result1 R = new Result1() ;
		R.alg = algorithmName ;
		R.level = level ;
		R.runtime = (int) runtime ;
		R.numberOfTurns = alg.algorithm.turn ;
		R.goalsolved = alg.algorithm.isTopGoalSolved() ;
		var Z = LRconnectionLogic.compareConnection(referenceLogic, discoveredConnections) ;
		R.numberOfConnections = Z.get("#connections") ;
		R.connectionsInferred = Z.get("#inferred") ;
		R.correctConnections = Z.get("#correct") ;
		R.wrongConnections = Z.get("#wrong") ;
		R.numberOfEpisodes = alg.algorithm.totNumberOfRuns ;
		// calculate area coverage:
		R.visitedTiles = alg.algorithm.getCoveredTiles2D() ;
		int covered = (int) R.visitedTiles.stream().filter(tile -> walkableTiles.contains(tile)).count() ;
		R.areaCoverage = (float) covered / (float) walkableTiles.size() ;
		// write the result to a result file:
		System.out.println(R.toString()) ;
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
		LocalDateTime now = LocalDateTime.now();  
		String resultFileName = level + "_" + algorithmName 
				+ "_" + targetDoor
				+ "_result.txt" ;	
		writelnToFile(dirToSaveResult,resultFileName,"================== run " 
				+ runNumber + ", " +  dtf.format(now) 
				+ ":", true) ;
		writelnToFile(dirToSaveResult,resultFileName,R.toString(),true) ;
		return R ;
	}
	
	void writeResultsToFile(
			String levelName,
			String targetDoor,
			String algName,
			String dir,
			String resultFileName,
			List<Result1> algresults
			) throws IOException {
		
		String levelFile = Paths.get(levelsDir, levelName + ".csv").toString() ;
		var referenceLogic = LRconnectionLogic.parseConnections(levelFile) ;
		System.out.println("*********************") ;
		writelnToFile(dir,resultFileName, "====== " + levelName 
				+ " " + targetDoor
				+ " with " + algName, true) ;
		writelnToFile(dir,resultFileName, "== avrg runtime:" + avrgRuntime(algresults), true) ;
		writelnToFile(dir,resultFileName, "== avrg #turns:" + avrgTurns(algresults), true) ;
		writelnToFile(dir,resultFileName, "== avrg #episodes:" + avrgNumberOfEpisodes(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #solved:" + numbeOfTimesGoalSolved(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #connections:" + referenceLogic.size(), true) ;
		float inferred = avrgInferredConnections(algresults) ;
		float correct = avrgCorrect(algresults) ;
		writelnToFile(dir,resultFileName, "== #inferred:" + inferred, true) ;
		writelnToFile(dir,resultFileName, "== #correct:" + correct, true) ;
		writelnToFile(dir,resultFileName, "== #wrong:" + avrgWrong(algresults), true) ;
		float precision = 0 ;
		float recall = 0 ;
		if (correct > 0) {
			precision = correct/inferred ;
		}
		if (correct > 0) {
			recall = correct / (float) referenceLogic.size() ;
		}
		writelnToFile(dir,resultFileName, "== precision:" + precision, true) ;
		writelnToFile(dir,resultFileName, "== recall:" + recall, true) ;
		writelnToFile(dir,resultFileName, "== area-cov:" + avrgAreaCoverage(algresults), true) ;
	}
	
	/**
	 * Run all algorithms on the given target level. Write the result=summary of every 
	 * algorithm to a file.
	 * 
	 * <p>It returns a map, that maps every algorithm to the set tiles visited during
	 * the runs. For algorithm A, this tiles-visit is organized as a list V, where
	 * V(i) is the set of all tiles visited by run-i of A on the given level.
	 * 
	 * <p>The timeBudget is in msec.
	 */
	Map<String,List<Set<Pair<Integer,Integer>>>> runAlgorithms(
			String exerimentName,
			String level, 
			String targetDoor,
			String agentId, 
			int timeBudget, 
			int episodeLength,
			int budget_per_task,
			int exploration_budget,
			int numberOfRepeat) throws Exception {
		
		String resultFileName = exerimentName + "_results.txt" ;
		
		String dir = Paths.get(dataDir, exerimentName).toString() ;
		writelnToFile(dir,resultFileName,"*********************",true) ;
		
		List<Result1> algresults = new LinkedList<>() ;
		Map<String,List<Set<Pair<Integer,Integer>>>> allVisitedTilesInfo = new HashMap<>() ;
		
		for (int a=0 ; a < availableAlgorithms.length; a++) {
			// iterate over the algorithms: Evo/MCTS/Q
			String algName = availableAlgorithms[a] ;
			List<Set<Pair<Integer,Integer>>> visitedTilesByAlg = new LinkedList<>() ;
			algresults.clear();
			for (int runNumber=0; runNumber<numberOfRepeat; runNumber++) { 
			    // repeated runs
				var R = runAlgorithm(algName,level,targetDoor,agentId,runNumber,randomSeeds[runNumber],
						             timeBudget,episodeLength,
						 			 budget_per_task, exploration_budget,
						             dir) ;
				algresults.add(R) ;
				visitedTilesByAlg.add(R.visitedTiles) ;
			}
			writeResultsToFile(level,targetDoor,algName,dir,resultFileName,algresults) ;
			allVisitedTilesInfo.put(algName, visitedTilesByAlg) ;
		}
		return allVisitedTilesInfo ;
	}
	
	/**
	 * Run the algorithms on a given bench-mark set.
	 */
	public void runExperiment(
				String benchmarkSetName,
				String[] targetLevels,
				String[] targetDoors,
				String agentId,
				int[] base_SARuntime,
				int[] episodeLengths,
				int budget_per_task,
				int exploration_budget
			) 
		throws Exception {
		String experimentName = benchmarkSetName ;
		Path dir_ = Paths.get(dataDir, experimentName) ;
		String dir = dir_.toString() ;
		// create the dir if it does not exists:
		if (Files.notExists(dir_)) {
			Files.createDirectories(dir_) ;
		}
		String resultFileName = experimentName + "_results.txt" ;
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
		LocalDateTime now = LocalDateTime.now();  
		//System.out.println(dtf.format(now));  	
		writelnToFile(dir,resultFileName,">>>> START experiment " + experimentName
				+ dtf.format(now),
				true) ;	
	 
		
		List<Map<String,List<Set<Pair<Integer,Integer>>>>> visitedTilesInfoGrrrr = new LinkedList<>() ;
		
		// Agent-constructor now launch LR
		//launchLabRcruits() ;
		long t0 = System.currentTimeMillis() ;
		for (var lev=0; lev<targetLevels.length; lev++) {
			int baseTime = base_SARuntime[lev] ;
			
			// time budget is specified to 1.2x Samira's alg:
			int timeBudget12 = (int) (1.2f * (float) baseTime * 1000) ;
			
			var tilesVisits = runAlgorithms(experimentName,
					targetLevels[lev],
					targetDoors[lev],
					agentId,timeBudget12,
					episodeLengths[lev],
					budget_per_task,
					exploration_budget,
					repeatNumberPerRun
					) ;
			visitedTilesInfoGrrrr.add(tilesVisits) ;
		}	
		long totTime = (System.currentTimeMillis()  - t0)/1000 ;
		writelnToFile(dir,resultFileName,"*********************",true) ;	
		
		if (Arrays.stream(targetLevels).allMatch(L -> L.equals(targetLevels[0]))) {
			// calculate total area-cov for largeRandom:
			calculateTotalAreaCoverage(targetLevels[0],dir,resultFileName,visitedTilesInfoGrrrr) ;	
		}
		
		float hrs = (float) totTime / 3600f ;
		writelnToFile(dir,resultFileName,">>>> END experiment. Tot. time: " + totTime + " secs ("
				+ hrs + " hrs)", true) ;

		// now this is every run's repsonsibility:
		//labRecruitsBinding.close();
	}
	
	// only for LargeRandom...
	private void calculateTotalAreaCoverage(
			String levelName,
			String dirOfResultFile,
			String resultFileName ,
			List<Map<String,List<Set<Pair<Integer,Integer>>>>> visitedTilesInfoGrrrr ) 
			throws IOException 
	{
		
		// Pff extra logic for calculating total area coverage for LargeRandom-case:

		Map<String,List<Set<Pair<Integer,Integer>>>> totalVisits_perAlg = new HashMap<>() ;
		for (int a=0; a<availableAlgorithms.length; a++) {
			var alg = availableAlgorithms[a] ;
			// visits of target0:
			totalVisits_perAlg.put(alg, visitedTilesInfoGrrrr.get(0).get(alg)) ;
			// for each other target:
			for (var targetNr=1; targetNr < visitedTilesInfoGrrrr.size(); targetNr++) {
				var visits_on_lev = visitedTilesInfoGrrrr.get(targetNr).get(alg) ;
				// for each run on the target:
				for (int runNr=0; runNr<repeatNumberPerRun; runNr++) {
					// visits of alg's run-r on target 0:
					var V0 = totalVisits_perAlg.get(alg).get(runNr) ;
					// visits of alg's run-r on target targetNr:
					var V1 = visits_on_lev.get(runNr) ;
					// add V on that of target-0:
					V0.addAll(V1) ;
				}
			}
		}
		// Now totalVisits_perAlg should contain a mapping from alg A
		// to a list L, where L(k) is the total visits of every run-k
		// all all targets combined.

		String levelFile = Paths.get(levelsDir, levelName + ".csv").toString() ;
		var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ; 
		for (int a=0; a<availableAlgorithms.length; a++) {
			var alg = availableAlgorithms[a] ;
			var visits_by_alg = totalVisits_perAlg.get(alg) ;
			//float[] totalAreaCoverage = new float[repeatNumberPerRun] ;
			float sum = 0;
			for (int runNr=0; runNr < repeatNumberPerRun; runNr++) {
				var visits = visits_by_alg.get(runNr) ;
				int covered = (int) visits.stream().filter(tile -> walkableTiles.contains(tile)).count() ;
				//totalAreaCoverage[runNr] = (float) covered / (float) walkableTiles.size() ;
				float totalAreaCoverage_of_runNr = (float) covered / (float) walkableTiles.size() ;
				sum += totalAreaCoverage_of_runNr ;
			}
			float avrgTotalAreaCoverage = sum / (float) repeatNumberPerRun ;
			writelnToFile(dirOfResultFile,resultFileName,"== tot. area cov of " + alg + " " + avrgTotalAreaCoverage,true) ;	
		}		
	}
	
	void hitReturnToContinue() {
		System.out.println(">>>> hit RETURN to continue") ;
		Scanner scanner = new Scanner(System.in);
		scanner.nextLine() ;
		//scanner.close();
	}
	
	//@Test
	public void test_launch_and_close_LR() {
		launchLabRcruits() ;
		hitReturnToContinue() ;
		labRecruitsBinding.close(); 
		hitReturnToContinue() ;
	}
	
	@Test
	public void run_ATEST_experiment_Test() throws Exception {
		runExperiment("ATEST", ATEST_levels, ATEST_targetDoors, "agent0", 
				ATEST_SAruntime, 
				ATEST_episode_length,
				//MyConfig.budget_per_task,   // using default budget per task, 150
				//MyConfig.explorationBudget  // using default exploration budget, 150
				500,
				500
				) ;
	}
	
	//@Test
	public void run_DDO_experiment_Test() throws Exception {
		runExperiment("DDO", DDO_levels, DDO_targetDoors,  "agent1", 
				DDO_SAruntime, 
				DDO_episode_length,
				800,  // per-task budget
				800   // exploration budget
				) ;
	}
	
	@Test
	public void run_LargeRandom_experiment_Test() throws Exception {
		runExperiment("LargeRandom", LargeRandom_levels, LargeRandom_targetDoors,  "agent1", 
				  LargeRandom_SAruntime, 
				  LargeRandom_episode_length,
				  800, // per-task budget
				  800  // exploration budget
				  ) ;
	}
	
	//@Test
	public void testWriteFile() throws IOException {
		String level = ATEST_levels[0] ;
		String resultfile = level + "_result.txt" ;
		writelnToFile(dataDir,resultfile,">>> " + LocalTime.now(),true) ;
		writelnToFile(dataDir,resultfile,"Another line",true) ;
	}

}
