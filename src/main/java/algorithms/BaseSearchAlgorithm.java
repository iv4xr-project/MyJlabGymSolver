package algorithms;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import agents.tactics.GoalLib;
import algorithms.Rooms.Room;
import environments.LabRecruitsEnvironment;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;
import examples.Example1;
import nl.uu.cs.aplib.agents.State;
import nl.uu.cs.aplib.mainConcepts.*;
import static nl.uu.cs.aplib.AplibEDSL.*;
import nl.uu.cs.aplib.utils.Pair;
import static agents.tactics.GoalLib.*;
import static agents.tactics.TacticLib.*;
import world.BeliefState;
import world.LabEntity;

/**
 * A search algorithm. It drives the agent, which in turns drives the SUT, to
 * get the SUT to a state satisfying {@link #topGoalPredicate}. If this goal-predicate
 * is left unspecified, the agent will keep running until its {@link #totalSearchBudget} is
 * exhausted.
 * 
 * <p>This class is meant to be a template to be subclassed, but it implements a form of
 * random-search. This algorithm alternate between exploring and randomly picking a pair
 * (b,d) of button and door. It toggles b, then travels to d to observe its updated state
 * (if it changes).
 * 
 * <p>The algorithm will also keep track of found connections between buttons and buttons.
 * 
 */
public class BaseSearchAlgorithm {

	/**
	 * To keep track the number of agent.updates() done so far.
	 */
	public int turn = 0 ;
	
	/**
	 * The test-agent that will be used to run the exploration algorithm.
	 */
	public LabRecruitsTestAgent agent ;
	
	/**
	 * If defined, this closes the SUT.
	 */
	public Function <Void,Void> closeSUT ;
	
	/**
	 * Available total search-budget in ms. The default is 3-min.
	 */
	int totalSearchBudget = 180000 ;
	
	/**
	 * Remaining total-search budget in ms.
	 */
	int remainingSearchBudget ;
	
	/**
	 * Contain the locations visited by the agent during the search.
	 */
	public List<Vec3> visitedLocations = new LinkedList<>() ;
	
	/**
	 * For the purpose of calculating area coverage ({@see #coveredTiles2D}), we pretend the
	 * agent to be a rectangle of size 2 x assumedExtentOfAgent.
	 */
	public float assumedExtentOfAgent = 1 ;
	
	/**
	 * The delay (in ms) added between the agent's update cycles when executing a goal,
	 * in {@link #solveGoal(String, GoalStructure, int)}. The default is 50ms.
	 */
	public int delayBetweenAgentUpateCycles = 50 ;
	
	/**
	 * If true, this will stop the execution of {@link #solveGoal(String, GoalStructure, int)}
	 * if stuck is detected. The detection is performed by periodically sampling the
	 * agent position. If the last 10 samples show the same position, the agent is considered
	 * as "stuck", and the execution of solveGoal is terminated.
	 * 
	 * <p>Default is true.
	 */
	public boolean forceGoalExecutionToTerminateWhenStuckIsDetected = true ;
	
	
	public int getTotalSearchBudget() { 
		return totalSearchBudget ;
	}
	
	public int getRemainingSearchBudget() {
		return remainingSearchBudget ;
	}
	
	public void setTotalSearchBudget(int budget) {
		totalSearchBudget = budget ;
		remainingSearchBudget = totalSearchBudget ;
	}
	
	public Set<Pair<String,String>> getDiscoveredConnections() {
		return getBelief().getConnections();
	}
	
	/**
	 * If {@link #topGoalPredicate} is specified, this checks if it is achieved.
	 */
	public boolean isTopGoalSolved() {
		if (topGoalPredicate != null) 
			return topGoalPredicate.test(getBelief()) ;
		return false ;
	}
	
	/**
	 * If not null, the predicate specified when the search is considered completed.
	 * The predicate is evaluated on the agent's state. If the predicate is not
	 * given (null), the agent will search until the search-budget is exhausted.
	 */
	public Predicate<State> topGoalPredicate ;
	
	public Random rnd = new Random() ;

	/**
	 * The max. number of turns that each goal-based task will be allowed. If this is
	 * exceeded the task will be dropped.
	 */
	public int budget_per_task = 150 ;
	
	/**
	 * The max. number of turns spent on exploration. 
	 */
	public int explorationBudget = 150 ;
	
	/**
	 * To keep track which button the agent toggled last.
	 */
	// FRAGILE!
	WorldEntity lastInteractedButton = null;

	/**
	 * Just for collecting statistics. It represents the total number of "runs" the algorithm
	 * does. With "runs" being loosely defined. For the Random algorithm implemented
	 * here, ut is the total number of pairs door-button tried.
	 */
	public int totNumberOfRuns = 0 ;

    BaseSearchAlgorithm() { }
    
    public BaseSearchAlgorithm(LabRecruitsTestAgent agent) { 
    	this.agent = agent ;
    	var state = agent.getState() ;
    	if (state == null) 
    		throw new IllegalArgumentException("Expecting an agent that already has a state.") ;
    	if (!(state instanceof XBelief)) 
    		throw new IllegalArgumentException("Expecting an agent with a state of type " + XBelief.class.getName()) ;   	
    }
    
	public void setRndSeed(int seed) {
		rnd = new Random(seed) ;
	}
  
	/**
	 * Just returning the BeliefState of the test agent.
	 */
	XBelief getBelief() {
		return (XBelief) agent.getState() ;
	}
	
	
	/**
	 * Register all buttons and doors currently in the agent's belief to the models
	 * of rooms and connections that it keeps track.
	 */
	void registerFoundGameObjects() {
		for(WorldEntity e : getBelief().knownButtons()) {
			getBelief().registerButton(e.id);
		}
		for(WorldEntity e : agent.getState().knownDoors()) {
			getBelief().registerDoor(e.id);
		}
	}
	
	/**
	 * Define the termination condition of this algorithm.
	 */
	boolean terminationConditionIsReached() {
		if (remainingSearchBudget <= 0) {
			DebugUtil.log("*** TOTAL BUDGET IS EXHAUSTED.") ;
			return true ;
		}
		if (isTopGoalSolved()) {
			DebugUtil.log("*** The search FOUND its global-goal. YAY!") ;
			return true ;
		}
		if(getBelief().worldmodel.timestamp > 0 && getBelief().worldmodel().health <= 0) {
			DebugUtil.log(">>> THE AGENT DIED. Aaaaaw.");
			return true ;
		}
		return false ;
	}
	
	
	Pair<Integer,Integer> get2DTileLocation(Vec3 p) {
		int x = (int) Math.floor(p.x + 0.5) ;
		int y = (int) Math.floor(p.z + 0.5) ;
		return new Pair<>(x,y) ;
	}
	
	Set<Pair<Integer,Integer>> get2DTilesAroundAgent(Vec3 p) {
		Set<Pair<Integer,Integer>> S = new HashSet<>() ;
		S.add(get2DTileLocation(p)) ;
		float xMin = p.x - assumedExtentOfAgent ;
		float xMax = p.x + assumedExtentOfAgent ;
		float yMin = p.z - assumedExtentOfAgent ;
		float yMax = p.z + assumedExtentOfAgent ;
		float x = xMin ;
		while (x < xMax) {
			float y = yMin ;
			while (y < yMax) {
				S.add(new Pair<Integer,Integer>((int) x, (int) y)) ;
				y += 1f ;
			}
			x += 1f ;
		}
		return S ;
	}

	/**
	 * Assign the given goal-structure to the test-agent and runs the agent to solve this goal.
	 * The agent stops when the given goal is reached, or when the general {{@link #terminationConditionIsReached()}
	 * becomes true.
	 * 
	 * <p>The budget-parameter, if specified, specifies the maximum number of turns available 
	 * to solve the goal. When this maximum is reached, the will stop pursuing the goal.
	 * There is an overall computation-budget (in milli-second). If this is exhausted, the agent
	 * will stop as well. If the goal-level budget is 0 or negative, then it is ignored. Only
	 * the total budget matters then.
	 * 
	 * <p>The method returns the status of the given goal at the end of the methid (success/fail
	 * or in-progress).
	 * 
	 * <p>As the agent executes, it will also update the rooms and connections models on the fly based
	 * on what it observes (e.g. when it sees a button, or when it interacts on a button, or when it
	 * notices that a door changes state).
	 * 
	 * <p>Discovered connections are kept track as we go, as follows: 
	 * 
	 * <ol>
	 * <li> we keep track which button B was last interacted by the agent.
	 * <li> whenever the agent notices that a door D changes state, this must be caused by
	 *     the interaction on B. So it adds the connection B->D to its memory.
	 * </ol>
	 */
	ProgressStatus solveGoal(String goalDesc, GoalStructure G, int budget) throws Exception {
		DebugUtil.log("*** Deploying a goal: " + goalDesc) ;
		getBelief().clearGoalLocation();
		getBelief().clearStuckTrackingInfo();
		agent.setGoal(G) ;
		
		// positions sampled every 10 cycles, to facilitate stuck detection
		List<Vec3> sampledPositions = new LinkedList<>() ;			
		int i=0 ;
		//WorldEntity lastInteractedButton = null ;
		while (G.getStatus().inProgress() && !terminationConditionIsReached()) {
			if (budget>0 && i >= budget) {
				DebugUtil.log("*** Goal-level budget (" + budget + " turns) is EXHAUSTED.") ;
				break ;
			}
			var pos = agent.getState().worldmodel.position ;
			DebugUtil.log("*** " + turn + ", " + agent.getState().id + " @" + pos);
			// track locations visited by the agent:
			visitedLocations.add(pos) ;
			Thread.sleep(delayBetweenAgentUpateCycles);
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
			// stuck detection:
			if (forceGoalExecutionToTerminateWhenStuckIsDetected) {
				if (i % 100 == 0 && sampledPositions.size() >= 9) {
					var p0 = sampledPositions.get(0) ;
					if (sampledPositions.stream().allMatch(p -> Vec3.distSq(p,p0) <= 1f)) {
						// agent seems to be stuck!
						DebugUtil.log(">> The agent seems to be stuck. Terminating its run.");
						break ;
					}
					sampledPositions.clear();
				}
				if (i % 10 == 0) {
					sampledPositions.add(getBelief().worldmodel.position) ;
				}
			}
		}
		// agent.printStatus();	
		DebugUtil.log("*** Goal " + goalDesc + " terminated. Consumed turns: " + i + ". Status: " + G.getStatus()) ;
		
		return G.getStatus() ;
	}
	
	/**
	 * Instruct the agent to explore the level. By this we mean exploring still 
	 * unvisited (but reachable) nav-nodes. In addition to the stop-conditions the same as in
	 * {@link #solveGoal(String, GoalStructure, int)}, in this method the agent also
	 * stops when there is no more navigation node it can explore to.
	 */
	void doExplore(int budget) throws Exception {
		Goal explored = goal("exploring").toSolve((BeliefState S) -> false)
				.withTactic(FIRSTof(explore(), 
						    ABORT()));
		var G =  FIRSTof(explored.lift(), SUCCESS());
		solveGoal("Exploring", G, budget) ;
	}
	
	/**
	 * Move the agent towards a door to get its actual current state. This assumes the door
	 * is reachable.
	 * @throws InterruptedException 
	 */
	boolean getActualDoorState(String door, int budget) throws Exception {
		GoalStructure G = FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)) ;
		solveGoal("Sampling the state of " + door, G, budget);
		return getBelief().isOpen(door) ;	      
	}
	
	
	/**
	 * Toggle the button, then check the door state. 
	 * Pre-condition: the door should be reachable from the current agent location, and the door
	 * is closed.
	 * @throws InterruptedException 
	 */
	void checkButtonDoorPair(String button, String door, int budget) throws Exception {
		// Don't use entityStateRefreshed() here as it logic assumes there is a nav-node
		// from where the door can be seen by the agent, which won't be the case if
		// a door becomes closed and completely cut-off the door.
		// Below we will use entityInCloseRange() as first option instead.
		GoalStructure G = SEQ(
				  entityInteracted(button), 
	              FIRSTof(entityInCloseRange(door), entityStateRefreshed(door)));
		boolean buttonOldState = getBelief().isOn(button) ;
        solveGoal("Toggling " + button + " to open " + door, G, budget);
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
        return ;
	}
	
	/**
	 * Try to open the given door. This assumes that the door is reachable from the agent current
	 * position, and is currently closed.
	 * To open the door, the agent interacts with buttons. Buttons that are know
	 * to be connected with the door are tried first. If none opens the door, other buttons,
	 * whose connectivity to the door is still unknown are tried
	 * <p> The agent stops when the door becomes open.
	 * 
	 * <p>The method returns true if the door is open, and else false.
	 * It also returns false if it has no candidate button to try.
	 */
	boolean openDoor(String door, int budget) throws Exception {
		
		// we will first try doors that are known to be connected to the door, then we add the buttons
		// that the agents don't know if they are connected to the door.
		List<String> candidates = getBelief().getConnectedButtons(door) ;
		candidates.addAll(getBelief().getUnexploredButtons(door)) ;
		
		if (candidates.isEmpty()) {
			DebugUtil.log(">>>> the agent tries to open " + door + ", but it does know any button that can be a candidate to do that.");
			return false ;
		}
		
		ProgressStatus status = null ;
		for (String button : candidates) {	
			unlockWhenAgentBecomesTrapped(budget)  ;
			checkButtonDoorPair(button,door, budget) ;
			if(getBelief().isOpen(door)) {
				DebugUtil.log(">>>> " + door + " is open.");
				return true ;
			}
		}
		return false ;
	}
	
	/**
	 * Copy a list, and randomly shuffling the result.
	 */
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
	void unlockWhenAgentBecomesTrapped(int budget) throws Exception {
		if(getBelief().rooms.isLockedInCurrentRoopm()) {
			DebugUtil.log(">>>> The agent is LOCKED is a room!");
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
					checkButtonDoorPair(connectedButtons.get(0),d0, budget) ;
					break ;
				}
			}
		}
	}
	
	/**
	 * Run this algorithm. This implementation performs a form of random search. 
	 * The algorithm alternates between exploring and randomly picking a pair
     * (b,d) of button and door. It toggles b, then travels to d to observe its updated state
     * (if it changes).
	 */
	public void runAlgorithm() throws Exception {
		long t0 = System.currentTimeMillis() ;
		int p = 0 ;
		while (! terminationConditionIsReached()) {
			long t1 = System.currentTimeMillis() ;
			doExplore(explorationBudget) ;
			var buttons = getBelief().knownButtons() ;
			var doors = getBelief().knownDoors() ;
			if(buttons.isEmpty()  || doors.isEmpty()) {
				long duration = System.currentTimeMillis() - t1 ;
				this.remainingSearchBudget = this.remainingSearchBudget - (int) duration ;
				break ;
			}
			WorldEntity B0 = buttons.get(rnd.nextInt(buttons.size())) ;
			WorldEntity D0 = doors.get(rnd.nextInt(doors.size())) ;
			checkButtonDoorPair(B0.id,D0.id,budget_per_task) ;
			totNumberOfRuns++ ;
			long duration = System.currentTimeMillis() - t1 ;
			this.remainingSearchBudget = this.remainingSearchBudget - (int) duration ;
			p++ ;
		}	
		var time = System.currentTimeMillis() - t0 ;
		System.out.println("** RANDOM") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.println("** #pairs tried=" + p) ;
		System.out.print("** The agent is ") ;
		System.out.println(getBelief().worldmodel().health > 0 ? "ALIVE" : "DEAD") ;
		System.out.print("** Search-goal: ") ;
		if (topGoalPredicate == null) {
			System.out.println(" none specified") ;
		}
		else {
			System.out.println(topGoalPredicate.test(getBelief()) ? "ACHIEVED" : "NOT-achieved") ;
		}
		closeEnv() ;
	}
	
	/**
	 * Close the agen's connection to the SUT. If {@link #closeSUT} is defined,
	 * it will also be invoked to close the SUT.
	 * @throws InterruptedException 
	 */
	void closeEnv() throws InterruptedException {
		var t0 = System.currentTimeMillis() ;
		agent.env().close() ;
		if (closeSUT != null) {
			closeSUT.apply(null) ;
		}
		Thread.sleep(3000);
		var duration = System.currentTimeMillis() - t0 ;
		// add this back to the time accounting, as we won't count LR closing as exec-time:
		this.remainingSearchBudget += (int) duration ;		
	}
	
	/**
	 * For keeping track of spatial coverage. 
	 *
	 * <p>The method returns the set of tiles visited by the agent during it search
	 * {@link #runAlgorithm()}.
	 * For now, the method assumes that the searched LR level has
	 * just one floor. So, we ignore the y-coordinate of the agent position. The first
	 * floor is though to be divided into 1x1-tiles, with center at the location
	 * (x,-,y) where (x,y) is a whole integer. So, the tile would have (x - 0.5, -, y - 0.5)
	 * bottom-left corner, and (x + 0.5, -, y + 0.5) to right corner.
	 * 
	 * <p>The tile is covered by the agent, if the agent current location p in is inside the 
	 * tile. 
	 */
	public Set<Pair<Integer,Integer>> getCoveredTiles2D() {		
		Set<Pair<Integer,Integer>> covered = new HashSet<>() ;
		for (var pos : visitedLocations) {
			if (pos != null) {
				if (assumedExtentOfAgent <= 0.5)
					covered.add(get2DTileLocation(pos)) ;
				else {
					covered.addAll(get2DTilesAroundAgent(pos)) ;
				}
			}
		}
		return covered ;	
	}

}
