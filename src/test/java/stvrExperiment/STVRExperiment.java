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

public class STVRExperiment {
	
	// ===== common parameters
	
	static String projectRootDir = System.getProperty("user.dir") ;
	
	static String levelsDir = Paths.get(projectRootDir, "src", "test", "resources", "levels", "STVR").toString() ;
	
	static String dataDir =  Paths.get(projectRootDir,"data").toString() ;
	
	static String[] availableAlgorithms = { 
			//"Random"
			//, "Evo"
			 "MCTS"
			, "Q"
	} ;
	
	//static int repeatNumberPerRun = 10 ;
	//static int repeatNumberPerRun = 5 ;
	static int repeatNumberPerRun = 2 ;

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
			,"BM2021_diff3_R4_2_2"    // minimum solution: 0
			,"BM2021_diff3_R4_2_2_M"  // minimum solution: 4
			,"BM2021_diff3_R7_3_3" // minimum solution: 2
	} ;
	
	// runtime of Samira's alg, in seconds:
	static int[] ATEST_SAruntime = { 
			68, 84, 139, 140, 
			146, 60, 144, 254 } ;
	
	/*
	static int[] ATEST_SAruntime = { 
			250, 250, 250, 250, 
			250, 250, 250, 400 } ;
	*/
	
	static String[] ATEST_targetDoors = {
			"door1", "door6", "door5", "door4", 
			"door6", "door6", "door3", "door6"
		} ;
	
	// specifying search-depth:
	static int[] ATEST_episode_length = {
			5,5,5,5,
			5,5,5,5
	} ;
	
	// ================ DDO levels =================

	static String[] DDO_levels = { "sanctuary_1"
			// , "durk_1"
			} ;
	static int[] DDO_SAruntime = { 1492, 2680 } ;
	static String[] DDO_targetDoors = { "doorEntrance", "doorKey4",  } ;
	// specifying search-depth:
	static int[] DDO_episode_length = { 9 , 5 } ;
	

	// ================ Large-Random level =================

	static String[] LargeRandom_levels = { 
	  "FBK_largerandom_R9_cleaned"   // F1
	  // "FBK_largerandom_R9_cleaned",   // F2
	  //"FBK_largerandom_R9_cleaned",   // F3
	  //"FBK_largerandom_R9_cleaned",  // F4
	  //"FBK_largerandom_R9_cleaned",  // F5
	  //"FBK_largerandom_R9_cleaned",  // F6
	  //"FBK_largerandom_R9_cleaned",  // F7
	  //"FBK_largerandom_R9_cleaned",  // F8  unsolvable by SA
	  //"FBK_largerandom_R9_cleaned",  // F9  unsolvable by SA
	  //"FBK_largerandom_R9_cleaned",  // F10
	  //"FBK_largerandom_R9_cleaned"   // F11
	  } ;
	
	static int[] LargeRandom_SAruntime = { 
	   //14,   // F1
	   //113,  // F2
	   //954,  // F3
	   //1045, // F4
	   1076, // F5
	   1827, // F6 
	   1532, 
	   3000, // one hrs (3000 x 1.2)
	   3000, // one hrs
	   1420, // time unknown!
	   1420 			
	} ;
	
	static String[] LargeRandom_targetDoors = {
	  //"door26",  // F1
	  //"door5",   // F2
	  //"door39",  // F3
	  //"door33",  // F4
	  "door16",  // F5
	  "door37",  // F6
	  "door34", "door3", "door21", "door22", "door38"}  ;
	
	// specifying search-depth:
	static int[] LargeRandom_episode_length = { 
			//2,  // F1
			//5,  // F2
			//6,  // F3
			//8,  // F4
			11, // F5
			15, // F6
			14, 12, 14, 20, 21		
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
        var useGraphics = true; // set this to false if you want to run the game without graphics
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
		MyConfig.budget_per_task = 150 ;
		MyConfig.explorationBudget = 200 ;
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
		int covered = (int) alg.algorithm.getCoveredTiles2D().stream().filter(tile -> walkableTiles.contains(tile)).count() ;
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
	 * <p>The timeBudget is in msec.
	 */
	void runAlgorithms(
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
		List<Result1> algresults = new LinkedList<>() ;
		
		String dir = Paths.get(dataDir, exerimentName).toString() ;
		writelnToFile(dir,resultFileName,"*********************",true) ;
		
		for (int a=0 ; a < availableAlgorithms.length; a++) {
			// iterate over the algorithms: Evo/MCTS/Q
			String algName = availableAlgorithms[a] ;
			algresults.clear();
			for (int runNumber=0; runNumber<numberOfRepeat; runNumber++) { 
			    // repeated runs
				var R = runAlgorithm(algName,level,targetDoor,agentId,runNumber,randomSeeds[runNumber],
						             timeBudget,episodeLength,
						 			 budget_per_task, exploration_budget,
						             dir) ;
				algresults.add(R) ;
			}
			writeResultsToFile(level,targetDoor,algName,dir,resultFileName,algresults) ;
		}	
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
	 
		// Agent-constructor now launch LR
		//launchLabRcruits() ;
		long t0 = System.currentTimeMillis() ;
		for (var lev=0; lev<targetLevels.length; lev++) {
			int baseTime = base_SARuntime[lev] ;
			
			// time budget is specified to 1.2x Samira's alg:
			int timeBudget12 = (int) (1.2f * (float) baseTime * 1000) ;
			
			runAlgorithms(experimentName,
					targetLevels[lev],
					targetDoors[lev],
					agentId,timeBudget12,
					episodeLengths[lev],
					budget_per_task,
					exploration_budget,
					repeatNumberPerRun
					) ;
		}	
		long totTime = (System.currentTimeMillis()  - t0)/1000 ;
		writelnToFile(dir,resultFileName,"*********************",true) ;	
		writelnToFile(dir,resultFileName,">>>> END experiment. Tot. time: " + totTime + " secs",true) ;
	
		// now this is every run's repsonsibility:
		//labRecruitsBinding.close();
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
	
	//@Test
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
