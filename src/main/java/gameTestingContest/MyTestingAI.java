package gameTestingContest;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import agents.tactics.GoalLib;
import algorithms.AlgorithmOne;
import algorithms.BaseSearchAlgorithm;
import algorithms.DebugUtil;
import algorithms.Evolutionary;
import algorithms.Rooms;
import algorithms.XBelief;
import environments.LabRecruitsEnvironment;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;
import examples.Example1;
import nl.uu.cs.aplib.mainConcepts.*;
import static nl.uu.cs.aplib.AplibEDSL.*;
import nl.uu.cs.aplib.utils.Pair;
import static agents.tactics.GoalLib.*;
import static agents.tactics.TacticLib.*;
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
	public Set<Pair<String, String>> exploreLRLogic(LabRecruitsEnvironment environment) throws Exception {

		if (MyConfig.ALG.equals("Evo")) {
			var evo = new Evolutionary(150,150,agentConstructor) ;
			DebugUtil.log("** Using Evolutionary-algorithm") ;
			evo.runAlgorithm() ;
			var B = evo.myPopulation.getBest().belief ;
			DebugUtil.pressEnter() ;
			return B.getConnections();
		}
		
		LabRecruitsTestAgent agent = new LabRecruitsTestAgent("agent0") // matches the ID in the CSV file
				.attachState(new XBelief())
				.attachEnvironment(environment);

		Thread.sleep(500) ;

		try {
			
			// Run the exploration algorithm:
			switch(MyConfig.ALG) {
			
			   case "AOne" : 
				   // using the alg's default budget, which is 180-sec,
				   // and leaving the goal empty.
				   DebugUtil.log("** Using Algorithm-One") ;
				   BaseSearchAlgorithm aOne = new AlgorithmOne(agent) ; 
				   aOne.runAlgorithm() ;
				   break ;
				   
			   case "Random" : 
				   // using the alg's default budget, which is 180-sec,
				   // and leaving the goal empty.
				   DebugUtil.log("** Using the Random-algorithm") ;
				   BaseSearchAlgorithm random = new BaseSearchAlgorithm(agent) ;
				   random.runAlgorithm() ;
				   break ; 
			}
			
		}
		catch(InterruptedException e) {
			DebugUtil.log(">>>> the execution thread was interrupted.") ;
		}
		catch(Exception e) {
			// when the thread crashes of interrupted due to timeout:
			e.printStackTrace() ;
		}

		DebugUtil.pressEnter();
		return ((XBelief) agent.getState()).getConnections();
	}

}
