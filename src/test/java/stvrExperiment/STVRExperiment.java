package stvrExperiment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;

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
import leveldefUtil.LRconnectionLogic;

public class STVRExperiment {
	
	static String projectRootDir = System.getProperty("user.dir") ;
	
	static String levelsDir = projectRootDir + "/src/test/resources/levels";
	
	static String dataDir = projectRootDir + "/data" ;
	
	static LabRecruitsTestServer labRecruitsBinding;
	
	//static String[] targetLevels = { "buttons_doors_1", "samira_8room" } ;
	static String[] targetLevels = { 
			"BM2021_diff1_R3_1_1_H"   // minimum solution: 2
			,"BM2021_diff1_R4_1_1"    // minimum solution: 4
			,"BM2021_diff1_R4_1_1_M"  // minimum solution: 3
			,"BM2021_diff2_R5_2_2_M"  // minimum solution: 2
			,"BM2021_diff2_R7_2_2"    // minimum solution: 4
			,"BM2021_diff3_R4_2_2"    // minimum solution: 0
			,"BM2021_diff3_R4_2_2_M"  // minimum solution: 4
			,"BM2021_diff3_R7_3_3"} ; // minimum solution: 2
	// Durk, Sanctuary too?
	
	// runtime of Samira's alg, in seconds:
	static int[] SAruntime = { 
			68, 84, 139, 140, 
			146, 60, 144, 254 } ;
	
	static String[] targetDoors = {
			"door1", "door6", "door5", "door4", 
			"door6", "door6", "door3", "door6"
		} ;
	
	
	//static int repeatNumberPerRun = 10 ;
	static int repeatNumberPerRun = 2 ;
	//static int repeatNumberPerRunGroup2 = 3 ;
	static int repeatNumberPerRunGroup2 = 2 ;
	static int[] randomSeeds = { 
			   13, 3713, 255, 24, 999,
			   4919, 1023, 1, 100, 10001 }  ;
	
	// Bounding the search depth to this:
	static int maxSearchDepth = 5 ;
	
	static class Result1 {
		String level ;
		String alg ;
		int numberOfConnections ;
		int runtime ;
		boolean goalsolved ;
		int connectionsInferred ;
		int correctConnections ;
		int wrongConnections ;
		
		@Override
		public String toString() {
			String z = "== level:" + level ;
			z +=     "\n== alg:" + alg ;
			z +=     "\n== goal:" + (goalsolved ? "ACHIEVED" : "X") ;
			z +=     "\n== runtime(sec):" + runtime ;
			z +=     "\n== #connections:" + numberOfConnections ;
			z +=     "\n== #inferred:"    + connectionsInferred ;
			z +=     "\n== #correct:"     + correctConnections ;
			z +=     "\n== #wrong:"       + wrongConnections ;
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
	
	Map<String,List<Result1>> evo12_results = new HashMap<>() ;
	Map<String,List<Result1>> evo5min_results = new HashMap<>() ;
	Map<String,List<Result1>> evo10min_results = new HashMap<>() ;
	
	Map<String,List<Result1>> mcts12_results = new HashMap<>() ;
	Map<String,List<Result1>> mcts5min_results = new HashMap<>() ;
	Map<String,List<Result1>> mcts10min_results = new HashMap<>() ;
	
	Map<String,List<Result1>> q12_results = new HashMap<>() ;
	Map<String,List<Result1>> q5min_results = new HashMap<>() ;
	Map<String,List<Result1>> q10min_results = new HashMap<>() ;
	
	static void launchLabRcruits() {
        var useGraphics = true; // set this to false if you want to run the game without graphics
        SocketReaderWriter.debug = false;
        labRecruitsBinding = new LabRecruitsTestServer(
        		useGraphics,
                Platform.PathToLabRecruitsExecutable(projectRootDir));
        labRecruitsBinding.waitForGameToLoad();
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
	
	
	MyTestingAI createAnAlgorithm(String algorithmName, 
			int levelNr, 
			String agentId,
			int rndSeed,
			int timeBudget) {
		
		String levelName = targetLevels[levelNr] ;
		
		// Configure the algorithm:
		MyConfig.ALG = algorithmName ;
		MyConfig.solutionLengthUpperBound = maxSearchDepth ;
		MyConfig.budget_per_task = 150 ;
		MyConfig.explorationBudget = 200 ;
		MyConfig.agentId = agentId ;
		MyConfig.randomSeed = rndSeed ;
		MyConfig.searchbuget = timeBudget ;
		MyConfig.target = targetDoors[levelNr] ;
		
		// config for LR:
		var config = new LabRecruitsConfig(levelName,levelsDir);        
		
        // instantiating the algoritm, using the params as in MyConfig:
        MyTestingAI myTestingAI = new MyTestingAI() ;
        
        myTestingAI.agentConstructor = dummy -> {
        	// create an instance of LabRecruitsEnvironment; it will bind to the
            // Lab Recruits instance you launched above. It will also load the
            // level specified in the passed LR-config:
        	LabRecruitsEnvironment env = new LabRecruitsEnvironment(config);
        	LabRecruitsTestAgent agent = new LabRecruitsTestAgent(agentId) // matches the ID in the CSV file
    				.attachState(new XBelief())
    				.attachEnvironment(env);
    		return agent ;
        } ;
        
        return myTestingAI ;
	}
	
	Result1 runAlgortihm(String algorithmName,
			int levelNr, 
			String agentId,
			int rndSeed,
			int timeBudget,
			String dirToSaveResult) throws Exception {
		String level = targetLevels[levelNr] ;
		String levelFile = Paths.get(levelsDir, level + ".csv").toString() ;
		var referenceLogic = LRconnectionLogic.parseConnections(levelFile) ;
		
		// instantiate the algorithm:
		var alg = createAnAlgorithm(algorithmName,levelNr,agentId,rndSeed,timeBudget) ;
		// run the algorithm:
		long t0 = System.currentTimeMillis() ;
		var discoveredConnections = alg.exploreLRLogic() ;
		// runtime in second
		long runtime = (System.currentTimeMillis() - t0)/1000 ;
		
		Result1 R = new Result1() ;
		R.alg = algorithmName ;
		R.level = level ;
		R.runtime = (int) runtime ;
		R.goalsolved = alg.algorithm.isTopGoalSolved() ;
		var Z = LRconnectionLogic.compareConnection(referenceLogic, discoveredConnections) ;
		R.numberOfConnections = Z.get("#connections") ;
		R.connectionsInferred = Z.get("#inferred") ;
		R.correctConnections = Z.get("#correct") ;
		R.wrongConnections = Z.get("#wrong") ;
		// write the result to a result file:
		System.out.println(R.toString()) ;
		String resultFileName = level + "_" + algorithmName + "_result.txt" ;	
		writelnToFile(dirToSaveResult,resultFileName,"==================",true) ;
		writelnToFile(dirToSaveResult,resultFileName,R.toString(),true) ;
		return R ;
	}
	
	void writeResultsToFile(
			String levelName,
			String algName,
			String dir,
			String resultFileName,
			List<Result1> algresults
			) throws IOException {
		
		String levelFile = Paths.get(levelsDir, levelName + ".csv").toString() ;
		var referenceLogic = LRconnectionLogic.parseConnections(levelFile) ;
		System.out.println("*********************") ;
		writelnToFile(dir,resultFileName, "====== " + levelName + " with " + algName, true) ;
		writelnToFile(dir,resultFileName, "== avrg runtime:" + avrgRuntime(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #solved:" + numbeOfTimesGoalSolved(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #connections:" + referenceLogic.size(), true) ;
		writelnToFile(dir,resultFileName, "== #inferred:" + avrgInferredConnections(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #correct:" + avrgCorrect(algresults), true) ;
		writelnToFile(dir,resultFileName, "== #wrong:" + avrgWrong(algresults), true) ;
	}
	
	
	void runAlgorithms(
			String exerimentName,
			int levelNr, 
			String agentId, 
			int timeBudget, 
			int numberOfRepeat) throws Exception {
		
		String level = targetLevels[levelNr] ;
		String resultFileName = exerimentName + "_results.txt" ;
		writelnToFile(dataDir,resultFileName,"*********************",true) ;
		List<Result1> algresults = new LinkedList<>() ;
		
		String dir = Paths.get(dataDir, exerimentName).toString() ;
		
		// EVO ========================================
		String algName = "Evo" ;
		algresults.clear();
		for (int k=0; k<numberOfRepeat; k++) { 
		    // repeated runs
			var R = runAlgortihm(algName,levelNr,agentId,randomSeeds[k],timeBudget,dir) ;
			algresults.add(R) ;
		}
		writeResultsToFile(level,algName,dir,resultFileName,algresults) ;		

		
		// MCTS ========================================
		algName = "MCTS" ;
		algresults.clear();
		for (int k=0; k<numberOfRepeat; k++) { 
		    // repeated runs
			var R = runAlgortihm(algName,levelNr,agentId,randomSeeds[k],timeBudget,dir) ;
			algresults.add(R) ;
		}
		writeResultsToFile(level,algName,dir,resultFileName,algresults) ;		
		
		// Q ========================================
		algName = "Q" ;
		algresults.clear();
		for (int k=0; k<numberOfRepeat; k++) { 
		    // repeated runs
			var R = runAlgortihm(algName,levelNr,agentId,randomSeeds[k],timeBudget,dir) ;
			algresults.add(R) ;
		}
		writeResultsToFile(level,algName,dir,resultFileName,algresults) ;		
	}
	
	public void runExpeiment12_Test() throws Exception {
		long t0 = System.currentTimeMillis() ;
		for (var lev=0; lev<targetLevels.length; lev++) {
			var level = targetLevels[lev] ;
			int baseTime = SAruntime[lev] ;
			
			int timeBudget12 = (int) (1.2f * (float) baseTime) ;
			
			runAlgorithms("RT12",lev,"agent1",timeBudget12,repeatNumberPerRun) ;
		}		
	}
	
	//@Test
	public void testWriteFile() throws IOException {
		String level = targetLevels[0] ;
		String resultfile = level + "_result.txt" ;
		writelnToFile(dataDir,resultfile,">>> " + LocalTime.now(),true) ;
		writelnToFile(dataDir,resultfile,"Another line",true) ;
	}

}
