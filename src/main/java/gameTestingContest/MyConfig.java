package gameTestingContest;

import nl.uu.cs.aplib.mainConcepts.GoalStructure;

public class MyConfig {
	
	static public boolean DEBUG_MODE = false ;
	
	static public Integer randomSeed = null ;
	
	//static public String ALG = "AOne" ;
	//static public String ALG = "Random" ;
	//static public String ALG = "Evo" ;
	//static public String ALG = "MCTS" ;
	 static public String ALG = "Q" ;
	
	//static public String target = null ;

	//static public String target = "door3" ;
	static public String target = "door5" ;
	//static public String target = "door1" ;
	
	static public String targetType = "door" ; // "door" or "flag"
	
	//static public String agentId = null ;

	static public String agentId = "agent1" ;
	//static public String agentId = "agent0" ;
	
	/**
	 * Total search budget in ms.
	 */
	//static public int searchbuget = 180000 ;
	static public int searchbuget = 1000000 ;
	
	/**
	 * A number K such that there exists a sequence of button-interactions of
	 * length at most K, that solve the goal, if a goal is given.
	 */
	//static public int solutionLengthUpperBound = 5 ;
	static public int solutionLengthUpperBound = 6 ;
	
	/**
	 * Budget for doing a single "task", typically for finding and toggling a button.
	 * Expressed in number of agent's update cycles.
	 */
	static public int budget_per_task = 150 ;
	
	/**
	 * Budget doing single round of exploration. Expressed in number of 
	 * agent's update cycles.
	 */
	static public int explorationBudget = 150 ;
	
	/**
	 * The delay (in ms) added between the agent's update cycles when executing a goal. 
	 * The default is 50ms.
	 */
	static public int delayBetweenAgentUpateCycles = 50 ;
	
}
