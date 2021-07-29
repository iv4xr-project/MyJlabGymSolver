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

	public MyTestingAI() {
	}

	//static boolean DEBUG_MODE = true ;
	static boolean DEBUG_MODE = false ;
	
	
	static void pressEnter() {
		if(! DEBUG_MODE) return ;
		System.out.println("Hit RETURN to continue.");
		new Scanner(System.in).nextLine();
	}

	static GoalStructure explored() {
		Goal explored = goal("exploring").toSolve((BeliefState S) -> false).withTactic(FIRSTof(explore(), ABORT()));
		return FIRSTof(explored.lift(), SUCCESS());
	}
	
	static void log(String s) {
		System.out.println(s) ;
	}

	LabRecruitsTestAgent agent ;
	Set<String> doors = new HashSet<>();
	Set<String> buttons = new HashSet<>();
	Set<Pair<String, String>> connections = new HashSet<>();
	int turn = 0 ;
	
	void registerFoundGameObjects() {
		for(WorldEntity e : agent.getState().knownButtons()) {
			if(! buttons.contains(e.id)) {
				log(">> registering " + e.id) ;
				buttons.add(e.id) ;
			}
		}
		for(WorldEntity e : agent.getState().knownDoors()) {
			if(! doors.contains(e.id)) {
				log(">> registering " + e.id) ;
				doors.add(e.id) ;						
			}
		}
	}
	
	void registerConnection(WorldEntity b, WorldEntity d) {
		log(">> registering connection " + b.id + " --> " + d.id) ;
		connections.add(new Pair(b.id, d.id)) ;	
	}
	
	public static int BUDGET_PER_TASK = 150 ;
	
	// FRAGILE!
	WorldEntity lastInteractedButton = null ;
	
	void solveGoal(String goalDesc, GoalStructure G) throws InterruptedException {
		log("*** Deploying a goal: " + goalDesc) ;
		agent.getState().clearGoalLocation();
		agent.getState().clearStuckTrackingInfo();
		agent.setGoal(G) ;
		int i=0 ;
		//WorldEntity lastInteractedButton = null ;
		while (G.getStatus().inProgress()) {
			log("*** " + turn + ", " + agent.getState().id + " @" + agent.getState().worldmodel.position);
			Thread.sleep(50);
			i++; turn++ ;
			agent.update();
			// register newly found game-objects:
			registerFoundGameObjects() ;
			// check if a button is just interacted:
			for(WorldEntity e: agent.getState().changedEntities) {
				if(e.type.equals("Switch") && e.hasPreviousState()) {
					log(">> detecting interaction with " + e.id) ;
					lastInteractedButton = e ;					
				}
			}
			// check doors that change state, and add connections to lastInteractedButton:
			if(lastInteractedButton != null) {
				for(WorldEntity e: agent.getState().changedEntities) {
					if(e.type.equals("Door") && e.hasPreviousState()) {
						registerConnection(lastInteractedButton,e) ;
					}	
				}
			}
			
			if (i > BUDGET_PER_TASK) {
				break;
			}
		}
		// agent.printStatus();	
		log("*** Goal " + goalDesc + " terminated. Consumed turns: " + i + ". Status: " + G.getStatus()) ;
	}
	
	void doExplore() throws InterruptedException {
		solveGoal("Exploring", explored()) ;
	}
	
	
	Set<String> getConnectedButtons(String door) {
		Set<String> candidates = new HashSet<>() ;
		for(var c : connections) {
			if(c.snd.equals(door)) {
				candidates.add(c.fst) ;
			}
		}
		return candidates ;
	}
	
	boolean doorIsReachable(String door) {
		LabEntity d = agent.getState().worldmodel.getElement(door) ;
		return doorIsReachable(d) ;
	}
	
	boolean doorIsReachable(LabEntity door) {
		Boolean originalState = door.getBooleanProperty("isOpen") ;
		// pretend that the door is open:
		door.properties.put("isOpen",true) ;
		// check its reachability:
		var entity_location = door.getFloorPosition() ;
		var entity_sqcenter = new Vec3((float) Math.floor((double) entity_location.x - 0.5f) + 1f,
	    		entity_location.y,
	    		(float) Math.floor((double) entity_location.z - 0.5f) + 1f) ;
		var path = agent.getState().findPathTo(entity_sqcenter,true) ;
		// restore the original state:
		door.properties.put("isOpen",originalState) ;
		return path != null ;		
	}
	
	boolean buttonIsReachable(String button) {
		LabEntity b = agent.getState().worldmodel.getElement(button) ;
		var path = agent.getState().findPathTo(b.getFloorPosition(),true) ;
		return path != null ;
	}
	
	/**
	 * Given an unreachable door d, find a closed door d2 that if it is open would make
	 * d reachable.
	 */
	String findAEnablingClosedDoor(String door) {
		for(String d2 : doors) {
			if (d2.equals(door)) continue ;
			if(!agent.getState().isOpen(d2) && doorIsReachable(d2)) {
				LabEntity d2_ = agent.getState().worldmodel.getElement(d2) ;
				d2_.properties.put("isOpen",true) ;
				if(doorIsReachable(door)) {
					d2_.properties.put("isOpen",false) ;
					return d2_.id ;
				}
				d2_.properties.put("isOpen",false) ;
			}
		}
		return null ;
	}
	
	boolean doorIsOpen(String door) {
		return agent.getState().isOpen(door) ;
	}
	
	void openDoor(String door) throws InterruptedException {
		Set<String> connectedButtons = getConnectedButtons(door) ;
		
		// Don't use entityStateRefreshed() here as it logic assumes there is a nav-node
		// from where the door can be seen by the agent, which won't be the case if
		// a door becomes closed and completely cut-off the door.
		// Below we will use entityInCloseRange() instead.
		
		for (String button : connectedButtons) {
			if (! buttonIsReachable(button)) continue ;
			GoalStructure G = SEQ(entityInteracted(button), 
					              FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)));
			solveGoal("Toggling " + button + " to open " + door, G);
			/*
			if(! doorIsReachable(door)) {
				// well ... the button also close another door that makes the target door unreachable.
				// reset the button then:
				solveGoal("Re-setting  " + button + " as it makes " + door + " unreachable", entityInteracted(button));
			    continue ;	
			}
			*/
			WorldEntity d_ = agent.getState().worldmodel.getElement(door);
			if (G.getStatus().success() && d_ != null && agent.getState().isOpen(door)) {
				log(">> " + door + " is open.");
				return;
			}
		}
		// no connections known:
		List<WorldEntity> candidates = agent.getState().knownButtons() ; 
		for(WorldEntity button : candidates) {
			if (! buttonIsReachable(button.id)) continue ;
			GoalStructure G = SEQ(entityInteracted(button.id),
					              FIRSTof(entityInCloseRange(door), entityStateRefreshed(door))
					              )  ;
			solveGoal("Toggling " + button.id + " to open " + door, G) ;
			/*
			if(! doorIsReachable(door)) {
				// well ... the button also close another door that makes the target door unreachable.
				// reset the button then:
				solveGoal("Re-setting  " + button + " as it makes " + door + " unreachable", entityInteracted(button.id));
			    continue ;	
			}
			*/
			WorldEntity d_ = agent.getState().worldmodel.getElement(door) ;
			if (G.getStatus().success() && d_ != null && agent.getState().isOpen(door)) {
				log(">> " + door + " is open.") ;
				return ;
			}
		}
	}
	
	void explorationAlg() throws InterruptedException {
		Set<String> closedSet = new HashSet<>() ;
		List<String> openSet = new LinkedList<>() ;
		doExplore() ;
		openSet.addAll(doors) ;
		while (! openSet.isEmpty()) {
			String nextDoorToOpen = openSet.get(0) ;
			if (! doorIsOpen(nextDoorToOpen)) {
				// if the door is closed try to open it
				
				// But firstly, if this door is not even reachable, find first
				// a closed door that would make the door reachable:
				if (! doorIsReachable(nextDoorToOpen)) {
					String enablingDoor = findAEnablingClosedDoor(nextDoorToOpen) ;
					if(enablingDoor != null) {
						if(openSet.contains(enablingDoor)) {
							openSet.remove(enablingDoor) ;
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
				
				int numberOfFoundButtons0 = buttons.size() ;
				openDoor(nextDoorToOpen) ;
				agent.getState().pathfinder.wipeOutMemory();
				doExplore() ;
				for(String d : doors) {
					if(!closedSet.contains(d) && ! openSet.contains(d)) {
						openSet.add(d) ;
					}
				}
				if(numberOfFoundButtons0 < buttons.size()) {
					// we found new buttons --> then put back closed doors that were already processed
					// back in the open-set:
					List<String> toBePutBack = new LinkedList<>() ;
					for(String d : closedSet) {
						if(! agent.getState().isOpen(d)) {
							// D is closed, and we found new buttons
							toBePutBack.add(d) ;
						}
					}
					for(String d : toBePutBack) {
						openSet.add(d) ;
						closedSet.remove(d) ;
					}
					
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
				.attachState(new BeliefState())
				.attachEnvironment(environment);

		Thread.sleep(500) ;

		try {
			pressEnter();
			
			explorationAlg() ;

			pressEnter();
		}
		catch(Exception e) {
			// when the thread crashes of interrupted due to timeout:
		}

		return connections;
	}

}
