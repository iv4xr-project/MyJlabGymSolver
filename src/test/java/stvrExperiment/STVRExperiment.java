package stvrExperiment;

import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.* ;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class STVRExperiment {
	
	static String[] targetLevels = { "bla", "bla" } ;
	static int[] SAruntime = { 0, 0 } ;
	
	static int repeatNumberPerRun = 10 ;
	static int repeatNumberPerRunGroup2 = 3 ;
	static int[] randomSeeds = { 
			   13, 3713, 255, 24, 999,
			   4919, 1023, 1, 100, 10001 }  ;
	
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

}
