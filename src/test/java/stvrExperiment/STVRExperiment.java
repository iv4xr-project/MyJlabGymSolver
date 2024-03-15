package stvrExperiment;

import java.nio.file.Files;
import java.nio.file.Path;
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

public class STVRExperiment {
	
	static String projectRootDir = System.getProperty("user.dir") ;
	
	static String levelsDir = projectRootDir + "/src/test/resources/levels";
	
	static String dataDir = projectRootDir + "/data" ;
	
	static LabRecruitsTestServer labRecruitsBinding;
	
	//static String[] targetLevels = { "buttons_doors_1", "samira_8room" } ;
	static String[] targetLevels = { 
			"BM2021_diff1_R3_1_1_H",  // minimum solution: 2
			"BM2021_diff1_R4_1_1",    // minimum solution: 4
			"BM2021_diff1_R4_1_1_M",  // minimum solution: 3
			"BM2021_diff2_R5_2_2_M",  // minimum solution: 2
			"BM2021_diff2_R7_2_2",    // minimum solution: 4
			"BM2021_diff3_R4_2_2",    // minimum solution: 0
			"BM2021_diff3_R4_2_2_M",  // minimum solution: 4
			"BM2021_diff3_R7_3_3"} ;  // minimum solution: 2
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
	
	static class Result {
		String level ;
		String alg ;
		int numberOfConnection ;
		List<Integer> runtime = new LinkedList<>() ;
		List<Boolean> goalsolved = new LinkedList<>() ;
		List<Integer> connectionsInferred = new LinkedList<> () ;
		List<Integer> correctConnections = new LinkedList<> () ;
		List<Integer> wrongConnections = new LinkedList<> () ;
	    
		int numbeOfTimesGoalSolved() {
			return (int) goalsolved.stream().filter(v -> v == true).count() ;
		}
		
		float avrgRuntime() {
			double a = runtime.stream().collect(Collectors
								  .averagingDouble(t -> (double) t)) ;
			return (float) a ;
		}
		
		float avrgInferredConnections() {
			double a = connectionsInferred.stream().collect(Collectors
								  .averagingDouble(t -> (double) t)) ;
			return (float) a ;
		}
		
		float avrgCorrect() {
			double a = correctConnections.stream().collect(Collectors
								  .averagingDouble(t -> (double) t)) ;
			return (float) a ;
		}
		
		float avrgWrong() {
			double a = wrongConnections.stream().collect(Collectors
								  .averagingDouble(t -> (double) t)) ;
			return (float) a ;
		}
	}
	
	Map<String,Result> evo12_results = new HashMap<>() ;
	Map<String,Result> evo5min_results = new HashMap<>() ;
	Map<String,Result> evo10min_results = new HashMap<>() ;
	
	Map<String,Result> mcts12_results = new HashMap<>() ;
	Map<String,Result> mcts5min_results = new HashMap<>() ;
	Map<String,Result> mcts10min_results = new HashMap<>() ;
	
	Map<String,Result> q12_results = new HashMap<>() ;
	Map<String,Result> q5min_results = new HashMap<>() ;
	Map<String,Result> q10min_results = new HashMap<>() ;
	
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
	void writeToFile(String dir, String fname, String s) throws IOException {
	    Files.writeString(
	        Path.of(dir, fname),
	        s + "\n",
	        CREATE, APPEND
	    );
	}
	
	String mkFileName(String levelName) {
		return levelName + "_result.txt" ;
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
	
	
	public void runAlgorithms(int levelNr, int timeBudget, int numberOfRepeat) {
		String level = targetLevels[levelNr] ;
		// EVO 
		for (int k=0; k<numberOfRepeat; k++) { 
			 // repeated runs
			int rndSeed = randomSeeds[k] ;
			// run evo
		}
		// MCTS 
		for (int k=0; k<numberOfRepeat; k++) { 
			int rndSeed = randomSeeds[k] ;
			// run mcts
		}
		// Q 
		for (int k=0; k<numberOfRepeat; k++) { 
			int rndSeed = randomSeeds[k] ;
			// run Q
		}
	}
	
	public void runExpeiment_Test() {
		long t0 = System.currentTimeMillis() ;
		for (var lev=0; lev<targetLevels.length; lev++) {
			var level = targetLevels[lev] ;
			int baseTime = SAruntime[lev] ;
			
			int timeBudget12 = (int) (1.2f * (float) baseTime) ;
			
			int time5min  = 300000 ;
			int time10min = 600000 ;
			
			runAlgorithms(lev,timeBudget12,repeatNumberPerRun) ;
			runAlgorithms(lev,time5min,repeatNumberPerRunGroup2) ;
			runAlgorithms(lev,time10min,repeatNumberPerRunGroup2) ;
		}
		float totTime = (float) (System.currentTimeMillis() - t0) ;
		totTime = Math.round(totTime / 6000f)/10f ; // time in minutes
		System.out.println("** TOT-runtime: " + totTime + " min") ;
		
	}
	
	//@Test
	public void testWriteFile() throws IOException {
		String level = targetLevels[0] ;
		writeToFile(dataDir,mkFileName(level),">>> " + LocalTime.now()) ;
		writeToFile(dataDir,mkFileName(level),"Another line") ;
	}

}
