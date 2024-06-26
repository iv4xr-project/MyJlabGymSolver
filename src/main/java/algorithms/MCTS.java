package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.agents.State;
import nl.uu.cs.aplib.utils.Pair;

/**
 * Implementation of Monte Carlo Search Tree (MCTS). In this implementation we
 * assume a deterministic target game. That is, playing the same sequence of
 * actions always give the same reward/value.
 * 
 * <p>The algorithm constructs a tree, encoding a winning strategy to play a game.
 * The game is assumed to be adversarial (e.g. like chess). 
 * 
 * <p>In the context of LR as the target game, we will use the algorithm differently.
 * LR is not really adversarial. So, once a rollout find a winning state, we are
 * done. The sequence of actions in that rollout is the solution (to win the given
 * LR level), and the tree has no further use and can be discarded.
 * So, in the context of LR, the MCTS algorithm is used to direct it towards
 * finding such a solving rollout, which is done by giving it intermediate
 * rewards. MCTS is then used to learn to maximize this intermediate reward.
 * 
 * <p>To accommodate the above described use of MCTS in LR, we add a "single-search-mode"
 * to MCTS. When this mode is enabled, the algorithm stops when a winning rollout
 * is found, the winning sequence of action can be obtained.
 */
public class MCTS extends BaseSearchAlgorithm {
	
	static class Node {
		float totalReward ;
		float averageReward ;
		int numberOfPlays = 0 ;
		int depth ;
		
		/**
		 * The action that leads to this node. Null if this is the root node.
		 */
		String action = null ;
		
		/**
		 * A node is fully explored if it is a terminal node, or or all its children are
		 * fully explored.
		 */
		boolean fullyExplored = false ;
		
		/**
		 * A node is a terminal node if no further action is possible, or if max-depth is reached.
		 */
		boolean terminal = false ;
		
		Node parent ;
		
		List<Node> children ;
		
		float ucbValue() {
			if (numberOfPlays == 0) return Float.POSITIVE_INFINITY ;
			
			return averageReward 
					+ 2f * (float) Math.sqrt(Math.log((float) parent.numberOfPlays)
					                         / (float) numberOfPlays) ;	
		}
		
		/**
		 * Recursively back-propagate a reward. Also takes care of marking if
		 * a node is fully explored.
		 */
		void backPropagate(float newReward) {
			numberOfPlays++ ;
			totalReward += newReward ;
			averageReward = totalReward / (float) numberOfPlays ;
			//System.out.println(">>> update avrg rew: " + averageReward) ;
			if (parent != null)
				parent.backPropagate(newReward) ;
		}
		
		/**
		 * Mark this node as fully explored, and propagate the information towards the 
		 * root.
		 */
		void propagateFullyExploredStatus() {
			if (children != null && children.stream().allMatch(ch -> ch.fullyExplored)) {
				fullyExplored = true ;
				if (parent != null)
					parent.propagateFullyExploredStatus() ;
			}
		}
		
		List<Node> getPathLeadingToThisNode() {
			List<Node> path = null ;
			if (parent != null)
				path = parent.getPathLeadingToThisNode() ;
			else
				path = new LinkedList<>() ;
			path.add(this) ;
			return path ;
		}
		
		List<String> getTraceLeadingToThisNode() {
			List<String> tr =  getPathLeadingToThisNode()
					  .stream()
					  .map(nd -> nd.action)
					  .collect(Collectors.toList()) ;
			// first element is null, remove it:
			tr.remove(0) ;
			return tr ;
		}
		
		@Override
		public String toString() {
			return toStringWorker("") ;
		}
		
		public String toStringWorker(String indent) {
			String z = "" + indent + this.action 
					+ ", avrgReward=" +  this.averageReward 
					+ ", fully explored:" + this.fullyExplored ;
			if (children == null) {
				z += " X" ;
				return z ;
			}
			for (var ch : children) {
				var u = ch.toStringWorker("  " + indent) ;
				z += "\n" ;
				z += u ;
			}
			return z ;
		}
		
	}
	
	static class PlayResult {
		/**
		 * The list of actions that were played.
		 */
		List<String> trace ;
		
		/**
		 * The reward obtained at the end of the play.
		 */
		float reward ;	
	}
	
	/**
	 * Create an agent, with a state, and connected to the SUT. The function may
	 * also re-launch the SUT (you decide).
	 */
	Function <Void,LabRecruitsTestAgent> agentConstructor ;
	
	/**
	 * The Monte Carlo Tree.
	 */
	public Node mctree ;
	
	/**
	 * When true, the mcts algorithm will terminates as soon as a winning play is
	 * found. Default is true.
	 */
	public boolean singleSearchMode = true ;
	
	public List<String> winningplay = null ;
	
	Set<Pair<String,String>> discoveredConnections = new HashSet<>() ;
	
	public int maxdepth = 8 ;
	
	public float maxReward = 10000 ;
	
	MCTS() { 
		mctree = new Node() ;
		mctree.depth = 0 ;
	}
	
	public MCTS(Function <Void,LabRecruitsTestAgent> agentConstructor) {
		this() ;
		this.agentConstructor = agentConstructor ;
	}
	
	
	void instantiateAgent() throws InterruptedException {
		agent = agentConstructor.apply(null) ;
		// add a wait, just to make sure that the level is loaded and the agent
		// is connected to the SUT
		Thread.sleep(500) ;
	}
	
	/**
	 * Execute all the actions in the path towards and until the given node. The method
	 * returns true if the whole sequence can be executed, and else false.
	 */
	boolean runPath(Node node, boolean closeEnvAtTheEnd) throws Exception {
		var t0 = System.currentTimeMillis() ;
		instantiateAgent() ;
		var duration = System.currentTimeMillis() - t0 ;
		// add this back to the time accounting, as we won't count LR initialization as exec-time:
		this.remainingSearchBudget += (int) duration ;
		
		var trace = node.getTraceLeadingToThisNode() ;

		System.out.println(">>> executing prefix " + trace);
		
		boolean success = true ;
		
		if (trace.isEmpty()) {
			// special case when the trace is still emoty:
			doExplore(explorationBudget) ;
		}
		
		for (var button : trace) {
			 var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
			 // if the agent is dead, break:
			 if (agent.getState().worldmodel().health <= 0) {
				 success = false ;
				 break ;
			 }
			 // also break the execution if a button fails:
			 if (!status.success()) {
				 success = false ;
				 break ;
			 }
			 
			 // reset exploration, then do full explore:
			 getBelief().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
		}
		
		var cons = getBelief().getConnections() ;
		for (var c : cons) {
			discoveredConnections.add(c) ;
		}
		
		if (closeEnvAtTheEnd)
			closeEnv() ;
		return success ;
	}
	
	/**
	 * Calculate the reward of the current game-state.
	 */
	float rewardOfCurrentGameState() {
		var S = this.getBelief() ;
		if (topGoalPredicate != null && topGoalPredicate.test(S)) {
			return maxReward ;
		}
		return S.getConnections().size() + S.getNumberOfOpenDoors() ;
	}
	
	/**
	 * Play all the actions leading to the given node, then continue to play
	 * the game from that point either until a terminal state is reached, or
	 * a maximum depth is reached.
	 * <p> Return a play-result, which contains the full sequence of actions
	 * of the play, and the reward obtained by the play.
	 * @throws Exception 
	 */
	PlayResult rollout(Node node) throws Exception {
		
		List<String> trace = node.getTraceLeadingToThisNode() ;
		
		var success = runPath(node,false) ;

		if (!success) {
			// if the trace replay is not successful, we don't continue:
			PlayResult R = new PlayResult() ;
			R.trace = trace ;
			R.reward = rewardOfCurrentGameState() ;
			closeEnv();
			return R ;
		}
		
		int depth = trace.size() ;
		//boolean goalPredicateSolved = false ;
		while (depth < maxdepth) {
			var S = getBelief() ;
			var buttons = S.reachableButtons() ;
			if (buttons.isEmpty()) break ;
			var chosen = buttons.get(rnd.nextInt(buttons.size())) ;
			trace.add(chosen.id) ;
			// ask the agent to toggle the button:
			var status = solveGoal("Toggling button " + chosen.id, entityInteracted(chosen.id), budget_per_task) ;
			depth++ ;
			// if the agent is dead, break:
			 if (getBelief().worldmodel().health <= 0)
				 break ;
			 // also break the execution if a button fails:
			 if (!status.success()) 
				 break ;
			 
			 // reset exploration, then do full explore:
			 getBelief().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
			 
			 // check if the goal-predicate if we have one, is solved:
			 if (topGoalPredicate != null && topGoalPredicate.test(getBelief())) {
				// the search-goal is solved
				 //goalPredicateSolved = true ;
				 break ;
			}
			
		}
		
		var cons = getBelief().getConnections() ;
		for (var c : cons) {
			discoveredConnections.add(c) ;
		}
		
		PlayResult R = new PlayResult() ;
		R.trace = trace ;
		R.reward = rewardOfCurrentGameState() ;
		closeEnv();
		return R ;	
	}
		
	List<Node> generateChildren(Node node) throws Exception {
		List<Node> children = new LinkedList<>() ;
		var success = runPath(node,true) ;
		if (success) {
			var S = getBelief() ;
			var buttons = S.reachableButtons() ;
			for (var B : buttons) {
				Node child = new Node() ;
				child.action = B.id ;
				children.add(child) ;
			}
		}
		closeEnv() ;
		return children ;
	}
	
	Node chooseLeaf(Node nd) {
		if (nd.children == null) return nd ;
		if (nd.children.isEmpty())
			throw new IllegalArgumentException() ;
		
		float bestUCB = Float.NEGATIVE_INFINITY ;
		for (var ch : nd.children) {
			float U = ch.ucbValue() ;
			if (U > bestUCB) {
				bestUCB = U ;
			}
		}
		final float bestUCB_ = bestUCB ;
		// we might have multiple maxes. If so, we choose randomly among the maxes:
		var maxes = nd.children.stream().filter(ch -> ch.ucbValue() >= bestUCB_)
				.collect(Collectors.toList()) ;
		Node bestChild = maxes.get(rnd.nextInt(maxes.size())) ;
		return  chooseLeaf(bestChild) ;
	}
	
	/**
	 * The MCTS run.
	 */
	void mcts() throws Exception {
		while (! terminationConditionIsReached()) {
			long t0 = System.currentTimeMillis() ;
			Node leaf = chooseLeaf(mctree) ;
			evaluateLeaf(leaf) ;
			System.out.println(">>> MCTS #plays = " + mctree.numberOfPlays 
					+ ", avrg reward=" + mctree.averageReward) ;
			long time = System.currentTimeMillis() - t0 ;
			this.remainingSearchBudget = this.remainingSearchBudget - (int) time ;
		}	
	}
	
	void evaluateLeaf(Node leaf) throws Exception {
		
		System.out.println(">>> EVAL " + leaf.action) ;
		
		if (leaf.terminal || leaf.fullyExplored) 
			throw new IllegalArgumentException() ;
		
		// the leaf is at the max-depth:
		if (leaf.depth >= maxdepth) {
			leaf.terminal = true ;
			leaf.fullyExplored = true ;
			runPath(leaf,true) ;
			totNumberOfRuns++ ;
			var R = rewardOfCurrentGameState() ;
			leaf.backPropagate(R);
			if (leaf.parent != null) 
				leaf.parent.propagateFullyExploredStatus();
			// the case when the state after this node is a winning state:
			if (singleSearchMode && R >= maxReward) {
				winningplay = leaf.getTraceLeadingToThisNode() ;
				discoveredConnections = getBelief().getConnections()  ;
			}
			closeEnv() ;
			return ;			
		}
		
		// leaf is not at max-depth and has not been sampled/played before:
		if (leaf.numberOfPlays == 0) {
			System.out.println(">>> ROLLOUT") ;
			var R = rollout(leaf) ;
			totNumberOfRuns++ ;
			leaf.backPropagate(R.reward) ;
			if (singleSearchMode && R.reward >= maxReward) {
				winningplay = R.trace ;
				discoveredConnections = getBelief().getConnections()  ;
			}
			return ;
		}
		
		// last case is that the leaf has been sampled. In this case we expand:
		leaf.children = generateChildren(leaf) ;
		if (leaf.children.isEmpty()) {
			// no further actions from the leaf is possible, mark it as terminal:
			leaf.terminal = true ;
			leaf.fullyExplored = true ;
			if (leaf.parent != null) 
				leaf.parent.propagateFullyExploredStatus();
			return ;
		}
		
		System.out.println(">>> EXPAND") ;

		// else, go to a random child, and evaluate it:
		for (var ch : leaf.children) {
			ch.parent = leaf ;
			ch.depth = leaf.depth+1 ;
		}
		evaluateLeaf(leaf.children.get(rnd.nextInt(leaf.children.size()))) ;
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
		if (mctree.fullyExplored) {
			DebugUtil.log("*** The search tree is fully explored.") ;
			//System.out.println("*** The search tree is fully explored.") ;
			return true ;
		}
		return false ;
	}
	
	
	@Override
	public void runAlgorithm() throws Exception {
		long time = System.currentTimeMillis() ;
		mcts() ;
		time = System.currentTimeMillis() - time ;
		System.out.println("** MCTS") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.println("** #plays=" + mctree.numberOfPlays) ;
		System.out.println("** avrg reward=" + mctree.averageReward) ;
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
