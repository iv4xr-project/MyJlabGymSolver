package algorithms;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import eu.iv4xr.framework.mainConcepts.WorldEntity;

/**
 * Lack of better name, for now I'll just call this Algorithm-one.
 */
public class AlgorithmOne extends BaseSearchAlgorithm {
	
	public AlgorithmOne(LabRecruitsTestAgent agent) { 
		super(agent) ;
	}

	/**
	 * This is the search algorithm of this class. It works as follows:
	 * 
	 * <p>Maintain two sets: done-set and todo-list. Intially they are empty.
	 * 
	 *   <ul>
	 *   <li>(1) Explore. Append newly discovered doors to the todo-list (at the back).
	 *   <li>(2)If the todo-list is empty we are DONE. 
	 *      Else, remove the first door D from the todo-list:
	 *         <ul>
	 *         <li>(2a) if D is already open, move it to the done-set. Go back to 1.
	 *         <li>(2b) Else try to open D. If D is not even reachable, try to do
	 *              something (e.g. opening a door) to make D reachable.
	 *              Note that we don't know if D can be opened at all, so we may
	 *              put some time out here.
	 *              At the end, whether or not we can open D, we are done with it
	 *              and put it in the done-set.
	 *              Go back to 1.
	 *         </ul>
	 *    </ul>   
	 *             
	 * In step 2b, to open D we will have to try out various buttons.
	 */
	void search() throws Exception {
		
		long t0 = System.currentTimeMillis() ;
		
		Set<String> doneSet = new HashSet<>() ;
		
		List<String> todoSet = new LinkedList<>() ;
		
		doExplore(explorationBudget) ;
		
		int numOfDoorsTargetted = 0 ;
		
		todoSet.addAll(getBelief().knownDoors().stream().map(D -> D.id).collect(Collectors.toList())) ;
		
		while (!todoSet.isEmpty() && !terminationConditionIsReached()) {

			String nextDoorToOpen = todoSet.get(0) ;
			if (! getBelief().isOpen(nextDoorToOpen)) {
				// if the door is closed try to open it
				
				// But firstly, if this door is not even reachable, find first
				// a closed door that would make the door reachable:
				if (! getBelief().doorIsReachable(nextDoorToOpen)) {
					
					// if the agent is locked in the current room try to open a door first:
					// unlockWhenAgentBecomesTrapped() 
					
					String enablingDoor = getBelief().findAEnablingClosedDoor(nextDoorToOpen) ;
					if(enablingDoor != null) {
						if(todoSet.contains(enablingDoor)) {
						   todoSet.remove(enablingDoor) ;
						}
						if(doneSet.contains(enablingDoor)) {
							doneSet.remove(enablingDoor) ;
						}
						todoSet.add(0,enablingDoor) ;
						nextDoorToOpen = enablingDoor ;	
					}
					else { 
						// if we can't find an enabling door, then put nextDoorToOpen to the back
						// of the openSet, if it has more than one element:
						if (todoSet.size()>0) {
							todoSet.remove(0) ;
							todoSet.add(nextDoorToOpen) ;
							nextDoorToOpen = todoSet.get(0) ;
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

				openDoor(nextDoorToOpen,budget_per_task) ;
				//agent.getState().pathfinder.wipeOutMemory();  --> let's not do this as it makes things more complicated
				numOfDoorsTargetted++ ;

				doExplore(explorationBudget) ;

				for(WorldEntity d : getBelief().knownDoors()) {
					if(!doneSet.contains(d.id) && ! todoSet.contains(d.id)) {
						todoSet.add(d.id) ;
					}
				}
				if(numberOfFoundButtons0 < getBelief().knownButtons().size()) {
					// we found new buttons --> then put back doors that were already processed
					// back in the open-set:
					todoSet.addAll(doneSet) ;
					doneSet.clear();
				}
			}
			todoSet.remove(0) ;
			doneSet.add(nextDoorToOpen) ;
		}
		
		var time = System.currentTimeMillis() - t0 ;
		System.out.println("** Alg-ONE") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.println("** #doors targetted=" + numOfDoorsTargetted) ;
		System.out.print("** The agent is ") ;
		System.out.println(getBelief().worldmodel().health > 0 ? "ALIVE" : "DEAD") ;
		System.out.print("** Search-goal: ") ;
		if (goalPredicate == null) {
			System.out.println(" none specified") ;
		}
		else {
			System.out.println(goalPredicate.test(getBelief()) ? "ACHIEVED" : "NOT-achieved") ;
		}
	}
	
	@Override
	public void runAlgorithm() throws Exception {
		search() ;
	}
	


}
