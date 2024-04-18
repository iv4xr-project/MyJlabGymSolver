package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.* ;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.utils.Pair;

public class QAlg extends BaseSearchAlgorithm {
	
	public static class ActionInfo {
		// public float avrgReward ;
		// let's only use max reward:
		public float maxReward ; 
	}
	
	/**
	 * A representation of LR-state for the Q-table. 
	 */
	public static class LRQstate {
		
		List<String> onButtons = new LinkedList<>() ;
		List<String> offButtons = new LinkedList<>() ;
		//int numberOfFoundConnections = 0 ;
		boolean alive = true ;
		
		LRQstate() { }
		LRQstate(XBelief belief) {
			onButtons = belief.knownButtons().stream()
								.filter(b -> belief.isOn(b))
								.map(e -> e.id)
								.collect(Collectors.toList());
			onButtons.sort((s1,s2) -> s1.compareTo(s2)) ;
			
			offButtons = belief.knownButtons().stream()
								.filter(b -> ! belief.isOn(b))
								.map(e -> e.id)
								.collect(Collectors.toList());
			offButtons.sort((s1,s2) -> s1.compareTo(s2)) ;
			//numberOfFoundConnections = belief.getConnections().size() ;
			alive = belief.worldmodel().health > 0 ;
		}
		
		@Override
		public boolean equals(Object o) {
			if (! (o instanceof LRQstate)) return false ;
			LRQstate o_ = (LRQstate) o ;
			return this.onButtons.equals(o_.onButtons)
					&& this.offButtons.equals(o_.offButtons)
					//&& this.numberOfFoundConnections == o_.numberOfFoundConnections 
					&& this.alive == o_.alive ;
		}
		
		@Override
	    public int hashCode() {
	        return onButtons.hashCode() 
	        		+ 31*offButtons.hashCode() 
	        		//+ 31*numberOfFoundConnections 
	        		+ (alive?1:0) ;
	    }
		
	}
	
	public Map<LRQstate,Map<String,ActionInfo>> qtable = new HashMap<>() ;
	
	public int maxdepth = 8 ;
	
	public float exploreProbability = 0.2f ;
	
	public float maxReward = 10000 ;
	
	/**
	 * Learning rate
	 */
	public float alpha = 0.8f ;
	
	/**
	 * Discount factor
	 */
	public float gamma = 0.99f ;
	
	public boolean singleSearchMode = true ;
	public List<String> winningplay = null ;
	public Set<Pair<String,String>> discoveredConnections = new HashSet<>() ; 
	
	List<String> trace = new LinkedList<>() ;
	//String compressedTrace = "" ;
	
	/**
	 * Create an agent, with a state, and connected to the SUT. The function may
	 * also re-launch the SUT (you decide).
	 */
	Function <Void,LabRecruitsTestAgent> agentConstructor ;

	
	QAlg() { }
	
	public QAlg(Function <Void,LabRecruitsTestAgent> agentConstructor) {
		this.agentConstructor = agentConstructor ;
	}
	
	void instantiateAgent() throws InterruptedException {
		agent = agentConstructor.apply(null) ;
		// add a wait, just to make sure that the level is loaded and the agent
		// is connected to the SUT
		Thread.sleep(500) ;
	}
	
	@Override
	boolean terminationConditionIsReached() {
		if (remainingSearchBudget <= 0) {
			DebugUtil.log("*** TOTAL BUDGET IS EXHAUSTED.") ;
			//System.out.println("*** TOTAL BUDGET IS EXHAUSTED.") ;
			return true ;
		}
		if (singleSearchMode && isTopGoalSolved()) {
			DebugUtil.log("*** The search FOUND its global-goal. YAY!") ;
			return true ;
		}
		return false ;
	}
	
	float valueOfCurrentGameState() {
		var S = this.getBelief() ;
		if (topGoalPredicate != null && topGoalPredicate.test(S)) {
			return maxReward ;
		}
		return 3*S.getConnections().size() + S.getNumberOfOpenDoors() ;
	}
	
	float playEpisode() throws Exception {
		
		var t0 = System.currentTimeMillis() ;
		instantiateAgent() ;
		var duration = System.currentTimeMillis() - t0 ;
		// add this back to the time accounting, as we won't count LR initialization as exec-time:
		this.remainingSearchBudget += (int) duration ;
		
		var qstate = new LRQstate(getBelief()) ;
		trace.clear();
		
		getBelief().pathfinder().wipeOutMemory();
		doExplore(explorationBudget) ;
		
		var initialButtons = getBelief().reachableButtons() ;
		Map<String,ActionInfo> initialActions = new HashMap<>() ;
		for (var b : initialButtons) {
			 var info = new ActionInfo() ;
			 info.maxReward = 0 ;
			 initialActions.put(b.id, info) ;
		}
		qtable.put(qstate,initialActions) ;
				
		float totalEpisodeReward = 0 ;
		
		while (trace.size() < maxdepth && winningplay == null) {
			
			System.out.println(">>> TRACE: " + trace) ;
			var candidateActions = qtable.get(qstate) ;			
			if (candidateActions.isEmpty()) 
				// no further actions is possible, so we stop the episode
				break ;
			
			String chosenAction = null ;
			if (rnd.nextFloat() <= exploreProbability) {
				// explore:
				var actions = candidateActions.keySet().stream().toList() ;
				chosenAction = actions.get(rnd.nextInt(actions.size())) ;
			}
			else {
				float bestVal = Float.NEGATIVE_INFINITY ;
				//System.out.println(">>> cadidates : " + candidateActions.size()) ;
				
				for (var a : candidateActions.entrySet()) {
					//System.out.println(">>> " + a.getKey()  + ", " + a.getValue().maxReward) ;
					if (a.getValue().maxReward > bestVal) {
						bestVal = a.getValue().maxReward ;
					}
				}
				// get the actions with the best value (we could have multiple)
				final float bestVal_ = bestVal ;
				var bestCandidates = candidateActions.entrySet()
						.stream()
						.filter(e -> e.getValue().maxReward >= bestVal_)
						.toList() ;
				
				chosenAction = bestCandidates.get(rnd.nextInt(bestCandidates.size())).getKey() ;
			}
			var button = chosenAction ;
			var info = candidateActions.get(chosenAction) ;
		    System.out.println(">>> chosen-action : " + chosenAction + ", info:" + info.maxReward) ;
		    // now, execute the action:
		    var value0 = valueOfCurrentGameState() ;
		    trace.add(chosenAction) ;
		    var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
			var newQstate = new LRQstate(getBelief()) ;
			// if the agent is dead, break:
			if (agent.getState().worldmodel().health <= 0) {
				 info.maxReward = -100 ;
				 return totalEpisodeReward ;
			}
			// also break the execution if a button fails:
			if (!status.success()) {
				 info.maxReward = -100 ;
				 return totalEpisodeReward ;
			}
			 
			 getBelief().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
			 // we are now at the "next state" T reached after executing the chosen action,
			 // and exploration is done to evaluate the reward of that state.
			 var T = getBelief() ;
			 var value1 = valueOfCurrentGameState() ;
			 // define rewad as the diff between the value of the new and previous states:
			 var reward = value1 - value0 ;
			 totalEpisodeReward += reward ;

			 
			 if (value1 >= maxReward) {
				 // goal state is reached
				 if (singleSearchMode) {
					 winningplay = new LinkedList<String>() ;
					 winningplay.addAll(trace) ; 
				 }
				 totalEpisodeReward = value1 ;
				 info.maxReward = totalEpisodeReward ;
				 return totalEpisodeReward ;	 
			 }
			 // else :
			 // calculate the maximum rewards if we continue from newQstate:
			 // note that the trace is already extendced with the last action taken
			 var nextnextActions = qtable.get(newQstate) ;
			 float S_maxNextReward = -100 ;
			 if (nextnextActions == null) {
				 var reachableButtons = T.reachableButtons() ;
				 nextnextActions = new HashMap<>() ;
				 for (var b : reachableButtons) {
					 var info2 = new ActionInfo() ;
					 info2.maxReward = 0 ;
					 nextnextActions.put(b.id, info2) ;
				 }
				 qtable.put(newQstate, nextnextActions) ;
				 S_maxNextReward = 0 ;
			 }
			 else {
				 for (var a : nextnextActions.entrySet()) {
						if (a.getValue().maxReward > S_maxNextReward) {
							S_maxNextReward = a.getValue().maxReward ;
						}
				 }
			 }
			 // calculate the new qvalue of (qstate,a):
			 info.maxReward = (1 - alpha) * info.maxReward
					           + alpha * (reward + gamma * S_maxNextReward) ;
			 
			 qstate = newQstate;
			
		}
		closeEnv() ;
		return totalEpisodeReward ;
	}
	
	@Override
	public void runAlgorithm() throws Exception {
		long time = System.currentTimeMillis() ;
		int numOfEpisodes = 0 ;
		float totEpisodeAward = 0 ;
		while (! terminationConditionIsReached()) {
			long time2 =  System.currentTimeMillis() ;
			System.out.println(">>> episode : " + numOfEpisodes) ;
			var episodeAward = playEpisode() ;
			var cons = getBelief().getConnections() ;
			for (var c : cons) {
				discoveredConnections.add(c) ;
			}
			totEpisodeAward += episodeAward ;
			numOfEpisodes++ ;
			totNumberOfRuns++ ;
			long duration = System.currentTimeMillis() - time2 ;
			remainingSearchBudget = remainingSearchBudget - (int) duration ;
		}
		time =  System.currentTimeMillis() - time ;
		System.out.println("** Q-learning") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.println("** #plays=" + numOfEpisodes) ;
		System.out.println("** avrg episode reward=" + totEpisodeAward/(float) numOfEpisodes) ;
		System.out.print("** Search-goal: ") ;
		if (topGoalPredicate == null) {
			System.out.println(" none specified") ;
		}
		else {
			System.out.println(isTopGoalSolved() ? "ACHIEVED by " + this.winningplay 
					: "NOT-achieved") ;
		}
	}
	
	@Override
	public Set<Pair<String,String>> getDiscoveredConnections() {
		return this.discoveredConnections ;
	}
	
	/**
	 * Only relevant for single-search-mode.
	 */
	@Override
	public boolean isTopGoalSolved() {
		return winningplay != null ;
	}

}
