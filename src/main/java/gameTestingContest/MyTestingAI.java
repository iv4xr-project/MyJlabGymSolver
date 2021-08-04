package gameTestingContest;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.util.*;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import agents.tactics.GoalLib;
import alice.tuprolog.InvalidTheoryException;
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

import static gameTestingContest.XBelief.* ;
import static nl.uu.cs.aplib.agents.PrologReasoner.* ;

/**
 * This method provides a single method, checkLRLogic, that you have to
 * implement for the Game Testing Contest. See the documentation of the method
 * below.
 */
public class MyTestingAI {

	/**
	 * To keep track the number of agent.updates() done so far.
	 */
	int turn = 0 ;
	
	/**
	 * The test-agent that will be used to run the exploration algorithm.
	 */
	LabRecruitsTestAgent agent ;
	
	Random rnd = new Random() ;

	/**
	 * The max. number of turns that each goal-based task will be allowed. If this is
	 * exceeded the task will be dropped.
	 */
	public static int BUDGET_PER_TASK = 150 ;
	
	/**
	 * To keep track which button the agent toggled last.
	 */
	// FRAGILE!
	WorldEntity lastInteractedButton = null;

    public MyTestingAI() { }
	
	/**
	 * Just returning the BeliefState of the test agent.
	 */
	private XBelief getBelief() {
		return (XBelief) agent.getState() ;
	}
	
	/**
	 * Register all buttons and doors currently in the agent's belief to the models
	 * of rooms and connections that it keeps track.
	 * @throws InvalidTheoryException 
	 */
	private void registerFoundGameObjects() throws InvalidTheoryException {
		for(WorldEntity e : getBelief().knownButtons()) {
			getBelief().registerButton(e.id);
		}
		for(WorldEntity e : agent.getState().knownDoors()) {
			getBelief().registerDoor(e.id);
		}
	}
	

	/**
	 * Assign the given goal-structure to the test agent and runs the agent to solve this goal.
	 * There is a time budget for this. If the max. number of turns is used up, the goal is dropped.
	 * 
	 * As the agent executes, it will also update the rooms and connections models on the fly based
	 * on what it observes (e.g. when it sees a button, or when it interacts on a button, or when it
	 * notices that a door changes state).
	 */
	void solveGoal(String goalDesc, GoalStructure G) throws Exception {
		DebugUtil.log("*** Deploying a goal: " + goalDesc) ;
		getBelief().clearGoalLocation();
		getBelief().clearStuckTrackingInfo();
		agent.setGoal(G) ;
		int i=0 ;
		//WorldEntity lastInteractedButton = null ;
		while (G.getStatus().inProgress()) {
			DebugUtil.log("*** " + turn + ", " + agent.getState().id + " @" + agent.getState().worldmodel.position);
			Thread.sleep(50);
			i++; turn++ ;
			agent.update();
			// register newly found game-objects:
			registerFoundGameObjects() ;
			// check if a button is just interacted:
			for(WorldEntity e: agent.getState().changedEntities) {
				if(e.type.equals("Switch") && e.hasPreviousState()) {
					DebugUtil.log(">> detecting interaction with " + e.id) ;
					lastInteractedButton = e ;					
				}
			}
			// check doors that change state, and add connections to lastInteractedButton:
			if(lastInteractedButton != null) {
				for(WorldEntity e: agent.getState().changedEntities) {
					if(e.type.equals("Door") && e.hasPreviousState()) {
						getBelief().registerConnection(lastInteractedButton.id,e.id) ;
					}	
				}
			}
			
			if(getBelief().worldmodel.health <= 0) {
				DebugUtil.log(">>>> the agent died. Aaaw.");
				throw new AgentDieException() ;
			}
			
			if (i > BUDGET_PER_TASK) {
				break;
			}
		}
		// agent.printStatus();	
		DebugUtil.log("*** Goal " + goalDesc + " terminated. Consumed turns: " + i + ". Status: " + G.getStatus()) ;
	}
	
	/**
	 * Instruct the agent to explore the level. By this we mean exploring still unvisited (but reachable) nav-nodes.
	 */
	void doExplore() throws Exception {
		Goal explored = goal("exploring").toSolve((BeliefState S) -> false).withTactic(FIRSTof(explore(), ABORT()));
		var G =  FIRSTof(explored.lift(), SUCCESS());
		solveGoal("Exploring", G) ;
	}
	
	/**
	 * Move the agent towards a door to get its actual current state. This assumes the door
	 * is reachable. It returns the state of the door, if the agent manages to observe it.
	 * Else null (e.g. if the path to the door is blocked, so the agent cannot observe it).
	 */
	Boolean getActualDoorState(String door) throws Exception {
		GoalStructure G = FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)) ;
		solveGoal("Sampling the state of " + door, G);
		if(! isCurrentlyObserved(door)) {
			return null ;
		}
		return getBelief().isOpen(door) ;	      
	}
	
	boolean isCurrentlyObserved(String door) {
		WorldEntity d_ = getBelief().worldmodel.getElement(door) ;
		return (d_ != null && d_.timestamp >= getBelief().worldmodel.timestamp) ;
	}
	
	
	/**
	 * Toggle the button, then check the door state. 
	 * Pre-condition: the button and the door should be reachable from the current agent location, and the door
	 * is closed.
	 * 
	 * Returns true if the method manages to complete the task (regardless if the door is in the
	 * end open or not). Else it returns false (e.g. it it times out).
	 */
	boolean checkButtonDoorPair(String button, String door) throws Exception {
		// Don't use entityStateRefreshed() here as it logic assumes there is a nav-node
		// from where the door can be seen by the agent, which won't be the case if
		// a door becomes closed and completely cut-off the door.
		// Below we will use entityInCloseRange() as first option instead.
		
		boolean buttonOldState = getBelief().isOn(button) ;
		GoalStructure G = SEQ(
				entityInteracted(button), 
				IFELSE((XBelief S) -> S.isOn(button) != buttonOldState,
	              FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)),
	              FAIL()));
		solveGoal("Toggling " + button + " to open " + door, G);
                
        boolean isOpen = getBelief().isOpen(door) ;
        
        if(!G.getStatus().success()) return false ;
        
        if(isOpen) {
        	// no need to register the connection. This is registred automatically by solveGoal().
        }
        else {
        	// register the non-connection:
        	getBelief().registerNONConnection(button, door);
        }
        return true ;
	}
	
	/**
	 * Try to open the given door. This assumes that the door is reachable from the agent current
	 * position, and is currently closed.
	 * Return true if it manages to open the door, else false.
	 */
	boolean openDoor(String door) throws Exception {
		
		unlockWhenAgentBecomesTrapped()  ;
		
		List<String> candidates = getBelief().pQueryAll("B",wiredTo.on("B",door)) ;
		candidates.addAll(getBelief().pQueryAll("B",and(isButton.on("B"),not(notWiredTo.on("B",door))))) ;
		
		// DebugUtil.log(">>>> candidates: " + candidates);
		
		if(candidates.isEmpty()) return false ;
		
		if(candidates.size()>1) {
			candidates = candidates.stream()
					. filter(B -> getBelief().buttonIsReachable(B))
					. collect(Collectors.toList())  ;
		}
		
		if(candidates.isEmpty()) return false ;
		
		for (String button : candidates) {	
			var completed = checkButtonDoorPair(button,door) ;
			if(completed && getBelief().isOpen(door)) {
				DebugUtil.log(">>>> " + door + " is open.");
				return true ;
			}
		}
		return false ;
	}
	
	/**
	 * Copy a list, and randomly shuffling the result.
	 */
	private List<String> shuffle(List<String> z) {
		List<String> S = new LinkedList<>() ;
		List<String> R = new LinkedList<>() ;
		S.addAll(z) ;
		int N = S.size() ;
		for (int k=0; k<N; k++) {
			String chosen = S.remove(rnd.nextInt(S.size())) ;
			R.add(chosen) ;
		}
		return R ;
	}
	
	/**
	 * When the agent is trapped in the current room, this will try to open a randomly chosen door in the room.
	 * It returns true if it manages to open a door in that room. Else false is returned.
	 * 
	 * Note that this cannot detect if an agent is trapped within multiple neighboring rooms.
	 */	
	boolean unlockWhenAgentBecomesTrapped() throws Exception {
		if(getBelief().isLockedInCurrentRoom()) {
			DebugUtil.log(">>>> The agent is LOCKED in its room! Trying to unlock.");
			
			String currentRoom = getBelief().getCurrentRoom() ;
			
			var candidates = getBelief().pQueryAll(
					"B", 
					and(isButton.on("B"),
						inRoom.on(currentRoom,"B"),
						isDoor.on("D"),
						inRoom.on(currentRoom,"D"),
						wiredTo.on("B","D")
							)) ;
			
			if (candidates.isEmpty()) {
				DebugUtil.log(">>>> does not know how to unlock...") ;
				return false ;
			}
			
			String chosen = candidates.get(rnd.nextInt(candidates.size())) ;
			
			var doors =  getBelief().pQueryAll(
					"D", 
					and(isDoor.on("D"),
						inRoom.on(currentRoom,"D"),
						wiredTo.on(chosen,"D")
							)) ;
			
			solveGoal("Toggling " + chosen + " unlock the agent.", entityInteracted(chosen));
			
			for(var D : doors) {
				if(isCurrentlyObserved(D)) {
					if(getBelief().isOpen(D)) {
						DebugUtil.log(">>>> unlocked.") ;
						return true ;
					}
				}
				else {
					Boolean open = getActualDoorState(D) ;
					if(open == true) {
						DebugUtil.log(">>>> unlocked.") ;
						return true ;
					}
				}
			}
			// fail to unlock the agent :(
			DebugUtil.log(">>>> cannot unlock...") ;

			return false ;
		}
		return true ;
	}
	
	private void printAlgStatus(Set<String> closedSet, List<String> openSet, int round) {
		if(! MyConfig.DEBUG_MODE) return ;
		DebugUtil.log(">>>> round:" + round) ;
		DebugUtil.log("     open-set  : " + openSet) ;
		DebugUtil.log("     closed-set: " + closedSet) ;
		
	}
	
	/**
	 * This is the exploration algorithm. It works as follows:
	 * 
	 * Maintain two sets: closed-set and open-list. Intially they are empty.
	 * 
	 *   (1) Explore. Append newly discovered doors to the open-list (at the back).
	 *   (2)If the open-list is empty we are DONE. 
	 *      Else, remove the first door D from the open-list:
	 *         (2a) if D is already open, move it to the closed-set. Go back to 1.
	 *         (2b) Else try to open D. If D is not even reachable, try to do
	 *              something (e.g. opening a door) to make D reachable.
	 *              Note that we don't know if D can be opened at all, so we may
	 *              put some time out here.
	 *              At the end, whether or not we can open D, we are done with it
	 *              and put it in the closed-set.
	 *              Go back to 1.
	 *               
	 * In step 2b, to open D we will have to try out various buttons.
	 * 
	 * Discovered connections are kept track as we go, as follows: 
	 * 
	 * (1) we keep track which button B was last interacted by the agent.
	 * (2) whenever the agent notices that a door D changes state, this must be caused by
	 *     the interaction on B. So it adds the connection B->D to its memory.
	 * 
	 */
	void explorationAlgorithm() throws Exception {
		Set<String> closedSet = new HashSet<>() ;
		List<String> openSet = new LinkedList<>() ;
		doExplore() ;
		openSet.addAll(getBelief().knownDoors().stream().map(D -> D.id).collect(Collectors.toList())) ;
		
		int round = 0 ;
		
		while (! openSet.isEmpty()) {
			
			printAlgStatus(closedSet,openSet,round) ;

			String nextDoorToOpen = openSet.get(0) ;
			if (! getBelief().isOpen(nextDoorToOpen)) {
				// if the door is closed try to open it
				
				// But firstly, if this door is not even reachable, find first
				// a closed door that would make the door reachable:
				if (! getBelief().doorIsReachable(nextDoorToOpen)) {
					
					// if the agent is locked in the current room try to open a door first:
					// unlockWhenAgentBecomesTrapped() 
					
					var enablingDoors = getBelief().getEnablingDoors(nextDoorToOpen) ;
					if(!enablingDoors .isEmpty()) {
						// choose one:
						var chosen = enablingDoors.get(rnd.nextInt(enablingDoors.size())) ;
						if(openSet.contains(chosen)) {
						   openSet.remove(chosen) ;
						}
						if(closedSet.contains(chosen)) {
							closedSet.remove(chosen) ;
						}
						openSet.add(0,chosen) ;
						nextDoorToOpen = chosen ;	
					}
					else { 
						// if we can't find an enabling door, then put nextDoorToOpen to the back
						// of the openSet, if it has more than one element:
						if (openSet.size()>0) {
							openSet.remove(nextDoorToOpen) ;
							openSet.add(nextDoorToOpen) ;
							nextDoorToOpen = openSet.get(0) ;
 						}
					}				
					/*
					List<String> alternatives = openSet.stream()
							     .filter(D -> ! doorIsOpen(D) && doorIsReachable(D))
							     .collect(Collectors.toList()) ;	
					if (alternatives.size()>0) {
						String alt = alternatives.get(0) ;
						log(">> considering to open " + nextDoorToOpen + ", but it unreachable. Trying " + alt + " first.") ;
						openSet.remove(alt) ;
						openSet.add(0, alt);
						nextDoorToOpen = alt ;
					}
					*/
				}
				
				int numberOfFoundButtons0 = getBelief().knownButtons().size() ;

				openDoor(nextDoorToOpen) ;
				doExplore() ;

				for(WorldEntity d : getBelief().knownDoors()) {
					if(!closedSet.contains(d.id) && ! openSet.contains(d.id)) {
						openSet.add(d.id) ;
					}
				}
				if(numberOfFoundButtons0 < getBelief().knownButtons().size()) {
					// we found new buttons --> then put back doors that were already processed
					// back in the open-set:
					openSet.addAll(closedSet) ;
					closedSet.clear();
				}
			}
			openSet.remove(0) ;
			closedSet.add(nextDoorToOpen) ;
			
			round++ ;
		}	
	}
	
	
	void randomExplorationAlgorithm() throws Exception {
		int budget = 7500 ;
		while (true) {
			if(turn >= budget) {
				break ;
			}
			doExplore() ;
			var buttons = getBelief().knownButtons() ;
			var doors = getBelief().knownDoors() ;
			if(buttons.isEmpty()  || doors.isEmpty()) {
				break ;
			}
			WorldEntity B0 = buttons.get(rnd.nextInt(buttons.size())) ;
			WorldEntity D0 = doors.get(rnd.nextInt(doors.size())) ;
			checkButtonDoorPair(B0.id,D0.id) ;
		}		
	}

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

		agent = new LabRecruitsTestAgent("agent0") // matches the ID in the CSV file
				.attachState(new XBelief())
				.attachEnvironment(environment);

		Thread.sleep(500) ;

		try {
			DebugUtil.pressEnter();
			
			// Run the exploration algorithm:
			switch(MyConfig.ALG) {
			   case "Random" : randomExplorationAlgorithm() ; break ;
			   
			   default : explorationAlgorithm() ;
			}
			
		}
		catch(AgentDieException e) {
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
