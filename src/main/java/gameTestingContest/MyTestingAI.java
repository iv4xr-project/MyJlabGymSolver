package gameTestingContest;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.util.*;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import agents.tactics.GoalLib;
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
 * This method provides a single method, checkLRLogic, that you have to
 * implement for the Game Testing Contest. See the documentation of the method
 * below.
 */
public class MyTestingAI {

	/**
	 * To keep track the number of agent.updates() done so far.
	 */
	int turn = 0 ;
	
	Random rnd = new Random() ;

	public static int BUDGET_PER_TASK = 150 ;
	
	public MyTestingAI() { }


	LabRecruitsTestAgent agent ;
	
	XBelief getBelief() {
		return (XBelief) agent.getState() ;
	}
	
	
	void registerFoundGameObjects() {
		for(WorldEntity e : getBelief().knownButtons()) {
			getBelief().registerButton(e.id);
		}
		for(WorldEntity e : agent.getState().knownDoors()) {
			getBelief().registerDoor(e.id);
		}
	}
	
	
	
	// FRAGILE!
	WorldEntity lastInteractedButton = null ;
	
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
	
	void doExplore() throws Exception {
		Goal explored = goal("exploring").toSolve((BeliefState S) -> false).withTactic(FIRSTof(explore(), ABORT()));
		var G =  FIRSTof(explored.lift(), SUCCESS());
		solveGoal("Exploring", G) ;
	}
	
	/**
	 * Move the agent towards a door to get its actual current state. This assumes the door
	 * is reachable.
	 * @throws InterruptedException 
	 */
	boolean getActualDoorState(String door) throws Exception {
		GoalStructure G = FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)) ;
		solveGoal("Sampling the state of " + door, G);
		return getBelief().isOpen(door) ;	      
	}
	
	
	/**
	 * Toggle the button, then check the door state. 
	 * Pre-condition: the door should be reachable from the current agent location, and the door
	 * is closed.
	 * @throws InterruptedException 
	 */
	void checkButtonDoorPair(String button, String door) throws Exception {
		// Don't use entityStateRefreshed() here as it logic assumes there is a nav-node
		// from where the door can be seen by the agent, which won't be the case if
		// a door becomes closed and completely cut-off the door.
		// Below we will use entityInCloseRange() as first option instead.
		GoalStructure G = SEQ(entityInteracted(button), 
	              FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)));
		boolean buttonOldState = getBelief().isOn(button) ;
        solveGoal("Toggling " + button + " to open " + door, G);
        boolean buttonNewState = getBelief().isOn(button) ;
        boolean isOpen = getBelief().isOpen(door) ;
        if(buttonOldState == buttonNewState) return ;
        if(isOpen) {
        	// no need to register the connection. This is registred automatically by solveGoal().
        }
        else {
        	// register the non-connection:
        	getBelief().registerNONConnection(button, door);
        }
	}
	
	/**
	 * Try to open the given door. This assumes that the door is reachable from the agent current
	 * position, and is currently closed.
	 */
	void openDoor(String door) throws Exception {
		
		List<String> candidates = getBelief().getConnectedButtons(door) ;
		candidates.addAll(getBelief().getUnexploredButtons(door)) ;
		
		for (String button : candidates) {	
			unlockWhenAgentBecomesTrapped()  ;
			checkButtonDoorPair(button,door) ;
			if(getBelief().isOpen(door)) {
				DebugUtil.log(">>>> " + door + " is open.");
				return;
			}
		}
	}
	
	List<String> shuffle(List<String> z) {
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
	 * When the agent is trapped in the current room, this will try to open a
	 * randomly chosen door in the room.
	 * @throws Exception 
	 */	
	void unlockWhenAgentBecomesTrapped() throws Exception {
		if(getBelief().rooms.isLockedInCurrentRoopm()) {
			DebugUtil.log(">>>> LOCKED!");
			Rooms.Room R = getBelief().rooms.getCurrentRoom() ;
			List<String> doors = getBelief().rooms.getDoorsOfCurrentRoom() ;
			doors = shuffle(doors) ;
			for(String d0 : doors) {
				List<String> connectedButtons = getBelief()
						.getConnectedButtons(d0)
						.stream()
						.filter(bt -> R.buttons.contains(bt))
						.collect(Collectors.toList());
				if(connectedButtons.size()>0) {
					checkButtonDoorPair(connectedButtons.get(0),d0) ;
					break ;
				}
			}
		}
	}
	void explorationAlg() throws Exception {
		Set<String> closedSet = new HashSet<>() ;
		List<String> openSet = new LinkedList<>() ;
		doExplore() ;
		openSet.addAll(getBelief().knownDoors().stream().map(D -> D.id).collect(Collectors.toList())) ;
		
		while (! openSet.isEmpty()) {

			String nextDoorToOpen = openSet.get(0) ;
			if (! getBelief().isOpen(nextDoorToOpen)) {
				// if the door is closed try to open it
				
				// But firstly, if this door is not even reachable, find first
				// a closed door that would make the door reachable:
				if (! getBelief().doorIsReachable(nextDoorToOpen)) {
					
					// if the agent is locked in the current room try to open a door first:
					// unlockWhenAgentBecomesTrapped() 
					
					String enablingDoor = getBelief().findAEnablingClosedDoor(nextDoorToOpen) ;
					if(enablingDoor != null) {
						if(openSet.contains(enablingDoor)) {
						   openSet.remove(enablingDoor) ;
						}
						if(closedSet.contains(enablingDoor)) {
							closedSet.remove(enablingDoor) ;
						}
						openSet.add(0,enablingDoor) ;
						nextDoorToOpen = enablingDoor ;	
					}
					else { 
						// if we can't find an enabling door, then put nextDoorToOpen to the back
						// of the openSet, if it has more than one element:
						if (openSet.size()>0) {
							openSet.remove(0) ;
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
				//agent.getState().pathfinder.wipeOutMemory();

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
			
			explorationAlg() ;

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
