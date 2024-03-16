package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.* ;
import java.util.function.Function;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.utils.Pair;

public class QAlg extends BaseSearchAlgorithm {
	
	public static class ActionInfo {
		// public float avrgReward ;
		// let's only use max reward:
		public float maxReward ; 
	}
	
	/** 
	 * A map from states to their visit-counts. 
	 */
	public Map<String,Integer> visitCount = new HashMap<>() ;
	
	public Map<String,Map<String,ActionInfo>> qtable = new HashMap<>() ;
	
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
	String compressedTrace = "" ;
	
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
		if (isTopGoalSolved()) {
			DebugUtil.log("*** The search FOUND its global-goal. YAY!") ;
			return true ;
		}
		return false ;
	}
	
	void addActionToTrace(String action) {
		trace.add(action) ;
		String a = action ;
		if (a.startsWith("button")) {
			a = a.substring(6) ;
		}
		else if (a.startsWith("b"))
			a = a.substring(1) ;
		compressedTrace += a ;
	}
	
	void clearTrace() {
		trace.clear();
		compressedTrace = "" ;
	}
	
	float valueOfCurrentGameState() {
		var S = this.getBelief() ;
		if (topGoalPredicate != null && topGoalPredicate.test(S)) {
			return maxReward ;
		}
		return 3*S.getConnections().size() + S.getNumberOfOpenDoors() ;
	}
	
	float playEpisode() throws Exception {
		
		instantiateAgent() ;
		
		clearTrace() ;
		
		float totalEpisodeReward = 0 ;
		
		while (trace.size() < maxdepth && winningplay == null) {
			System.out.println(">>> TRACE: " + compressedTrace) ;
			Integer visited = visitCount.get(compressedTrace) ;
			if (visited == null) {
				// System.out.println(">>> state not yet visited: " + compressedTrace) ;
				// reset exploration, then do full explore:
				 getBelief().pathfinder().wipeOutMemory();
				 doExplore(explorationBudget) ;
				 var S = getBelief() ;
				 var reachableButtons = S.reachableButtons() ;
				 Map<String,ActionInfo> actions = new HashMap<>() ;
				 for (var b : reachableButtons) {
					 var info = new ActionInfo() ;
					 info.maxReward = 0 ;
					 actions.put(b.id, info) ;
				 }
				 qtable.put(compressedTrace, actions) ;
				 visited = 1 ;
			}
			else visited++ ;
			visitCount.put(compressedTrace, visited) ;
			
			var candidateActions = qtable.get(compressedTrace) ;
			
			
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
			var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
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
			 
			 addActionToTrace(chosenAction) ;
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
			 // calculate the maximum rewards if we continue from that next state T:
			 // note that the trace is already extendced with the last action taken
			 var nextnextActions = qtable.get(compressedTrace) ;
			 float S_maxNextReward = -100 ;
			 if (nextnextActions == null) {
				 var reachableButtons = T.reachableButtons() ;
				 Map<String,ActionInfo> actions = new HashMap<>() ;
				 for (var b : reachableButtons) {
					 var info2 = new ActionInfo() ;
					 info2.maxReward = 0 ;
					 actions.put(b.id, info2) ;
				 }
				 qtable.put(compressedTrace, actions) ;
				 nextnextActions = actions ;
				 visitCount.put(compressedTrace, 1) ;
				 S_maxNextReward = 0 ;
			 }
			 else {
				 for (var a : nextnextActions.entrySet()) {
						if (a.getValue().maxReward > S_maxNextReward) {
							S_maxNextReward = a.getValue().maxReward ;
						}
				 }
			 }
			 // calculate the new reward (prevstate,a):
			 info.maxReward = (1 - alpha) * info.maxReward
					           + alpha * (reward + gamma * S_maxNextReward) ;
			
		}
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
