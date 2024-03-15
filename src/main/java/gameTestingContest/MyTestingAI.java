package gameTestingContest;


import java.util.*;
import java.util.function.Function;

import agents.LabRecruitsTestAgent;
import algorithms.AlgorithmOne;
import algorithms.BaseSearchAlgorithm;
import algorithms.DebugUtil;
import algorithms.Evolutionary;
import algorithms.MCTS;
import algorithms.QAlg;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.utils.Pair;
import world.BeliefState;
import world.LabEntity;

/**
 * This method provides a single method, exploreLRLogic, that you have to
 * implement for the Game Testing Contest. See the documentation of the method
 * below.
 */
public class MyTestingAI {

	/**
	 * Agent-constructor. Used for Evo-algorithm.
	 */
	public Function<Void,LabRecruitsTestAgent> agentConstructor = null ;
		    

	/**
	 * IMPLEMENT THIS METHOD.
	 * 
	 * <p>
	 * The input of this method is an instance LabRecruitsEnvironment which is
	 * already connected to a running instance of the Lab Recruits game, with a game
	 * level already loaded. Through this environment, you can observe the game
	 * state and control the player character(s) in the game.
	 * 
	 * <p>
	 * The intent of this method is to explore the loaded game-level to check the
	 * "logic" of this level. The logic of a level is described by how the in-game
	 * "buttons" in the level are connected to the in-game "doors" in the level.
	 * Each button should open the right doors, as the level designer intended.
	 * 
	 * <p>
	 * The method checkLRLogic should report back which buttons are connected to
	 * which doors. A button B is connected to a door D, if toggling B would also
	 * toggle the state of D. If that is not the case, B is unconnected to D. Note
	 * that a single button can be connected to multiple doors, or none. And
	 * likewise, a door can be connected to multiple buttons, or none. The method
	 * only needs to report back; you can imagine that a person or a program will
	 * check the report to infer from it whether the level is correct or otherwise
	 * incorrect. Your task is to come up with an algorithm for checkLRLogic that
	 * would in principle work generically for any Lab Recruits game-level.
	 * 
	 * <p>
	 * For your own debugging, you can manually (or write a script that does it)
	 * compare the report that this method produces with the csv file that defines
	 * the corresponding game file. Do not cheat by giving the knowledge of the csv
	 * file to your algorithm :) In the contest you algorithm will not have access
	 * to the level-files used for benchmarking. Your algorithm should generically
	 * work with whatever game-level that is loaded.
	 * 
	 * <p>
	 * For the contest, the levels used will have most buttons/doors to have the
	 * connection multiplicity of either 1 or 0. A few might have multiplicity of
	 * two.
	 * 
	 * @param environment An instance of LabRecruitsEnvironment, connected to a
	 *                    running instance of the Lab Recruits game, with a
	 *                    game-level loaded.
	 * 
	 * @return A "report" in the form of a list of pairs (b,d) where b is the ID of
	 *         a button and d is the ID of a door. When such a pair is reported, it
	 *         means that your algorithm concludes that the button b and the door d
	 *         are connected. When a pair (b',d') is NOT reported, it means that
	 *         your algorithm concludes that the button b' and the door d' are
	 *         unconnected.
	 * 
	 */
	public Set<Pair<String, String>> exploreLRLogic() throws Exception {
		
		BaseSearchAlgorithm algorithm = null ;
		
		switch(MyConfig.ALG) {
		
		   case "Evo" : 
			  var evo = new Evolutionary(agentConstructor) ;
			  DebugUtil.log("** Using Evolutionary-algorithm") ;
			  evo.maxPopulationSize = 10 ;
			  evo.numberOfElitesToKeepDuringSelection = 3 ;
			  evo.maxChromosomeLength = MyConfig.solutionLengthUpperBound ;
			  algorithm = evo ;
			  break ;
			  
		   case "MCTS" :
			   var mcts = new MCTS(agentConstructor) ;
			   DebugUtil.log("** Using MCTS-algorithm") ;
			   mcts.maxdepth = MyConfig.solutionLengthUpperBound  ;
			   algorithm = mcts ;
			   break ;
			   
		   case "Q" :
			   var Q = new QAlg(agentConstructor) ;
			   DebugUtil.log("** Using Q-learning") ;
			   Q.maxdepth = MyConfig.solutionLengthUpperBound  ;
			   algorithm = Q ;
			   break ;	   
				  
		   case "AOne" :	
			   DebugUtil.log("** Using Algorithm-One") ;
			   LabRecruitsTestAgent agent = agentConstructor.apply(null) ;
			   Thread.sleep(500) ;
			   algorithm = new AlgorithmOne(agent) ; 
			   algorithm.runAlgorithm() ;
			   break ;		   
			   
		   case "Random" :	   
			   DebugUtil.log("** Using the Random-algorithm") ;
			   agent = agentConstructor.apply(null) ;
			   Thread.sleep(500) ;
			   algorithm = new BaseSearchAlgorithm(agent) ;
			   algorithm.runAlgorithm() ;
			   break ; 			   
		}
		
		if (MyConfig.randomSeed != null) {
			algorithm.setRndSeed(MyConfig.randomSeed) ;
		}

		algorithm.setTotalSearchBudget(MyConfig.searchbuget) ;
		algorithm.budget_per_task = MyConfig.budget_per_task ;
		algorithm.explorationBudget = MyConfig.explorationBudget ;

		if (MyConfig.target != null) {
			if (MyConfig.targetType.equals("door")) {
				String doorId = MyConfig.target ;
				algorithm.goalPredicate = S -> {
					var B = (BeliefState) S ;
					return B.worldmodel.elements.get(doorId) != null && B.isOpen(doorId) ;
				} ;
			}
			if (MyConfig.targetType.equals("flag")) {
				String flagId = MyConfig.target ;
				algorithm.goalPredicate = S -> {
					var B = (BeliefState) S ;
					var flag = (LabEntity) B.worldmodel().elements.get(flagId) ;
					return flag != null 
							&& Vec3.distSq(flag.getFloorPosition(), 
							               B.worldmodel().getFloorPosition()) <= 1f  ;
				} ;
			}
			
		}
		
		try {
			algorithm.runAlgorithm() ;
		}
		catch(InterruptedException e) {
			// when the thread is interrupted due to timeout:	
			DebugUtil.log(">>>> the execution thread was interrupted.") ;
		}
		catch(Exception e) {
			DebugUtil.log(">>>> something went wrong...") ;
			e.printStackTrace() ;
		}
		
		algorithm.agent.env().close() ;

		DebugUtil.pressEnter();
		return algorithm.getDiscoveredConnections() ;
	}

}
