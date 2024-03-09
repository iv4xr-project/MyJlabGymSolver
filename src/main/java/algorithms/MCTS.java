package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.*;
import java.util.function.Function;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.agents.State;

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
 * <p>To accommodate the above described use of MCTS in LR, we add a "search-mode"
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
			averageReward = averageReward / (float) numberOfPlays ;
			if (children.stream().allMatch(ch -> ch.fullyExplored))
				fullyExplored = true ;
			if (parent != null)
				parent.backPropagate(newReward) ;
		}
		
		/**
		 * Mark this node as fully explored, and propagate the information towards the 
		 * root.
		 */
		void propagateFullyExploredStatus() {
			if (children.stream().allMatch(ch -> ch.fullyExplored))
				fullyExplored = true ;
			if (parent != null)
				parent.propagateFullyExploredStatus() ;
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
					  .toList() ;
			// first element is null, remove it:
			tr.remove(0) ;
			return tr ;
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
	
	Function <Void,LabRecruitsTestAgent> agentConstructor ;
	
	/**
	 * The Monte Carlo Tree.
	 */
	Node mctree ;
	
	/**
	 * When true, the mcts algorithm will terminates as soon as a winning play is
	 * found. Default is true.
	 */
	boolean searchMode = true ;
	
	public int explorationBudget = 500 ;
	
	
	List<String> winningplay = null ;
	
	int maxdepth = 8 ;
	
	float maxReward = 10000 ;
	
	
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
	boolean runPath(Node node) throws Exception {
		
		long t0 = System.currentTimeMillis() ;

		instantiateAgent() ;
		
		var trace = node.getTraceLeadingToThisNode() ;

		System.out.println(">>> executing prefix " + trace);
		
		boolean success = true ;
		
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
			 agent.getState().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
		}
		long execTime = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = this.remainingSearchBudget  - (int) execTime ;
		return success ;
	}
	
	/**
	 * Calculate the reward of the current game-state.
	 */
	float rewardOfCurrentGameState() {
		var S = this.getBelief() ;
		if (goalPredicate != null && goalPredicate.test(S)) {
			return maxReward ;
		}
		return S.getConnections().size() ;
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
		
		var success = runPath(node) ;

		long t0 = System.currentTimeMillis() ;

		if (!success) {
			// if the trace replay is not successful, we don't continue:
			PlayResult R = new PlayResult() ;
			R.trace = trace ;
			R.reward = rewardOfCurrentGameState() ;
			long execTime = System.currentTimeMillis() - t0 ;
			this.remainingSearchBudget = this.remainingSearchBudget  - (int) execTime ;
			return R ;
		}
		
		

		int depth = trace.size() ;
		boolean goalPredicateSolved = false ;
		while (depth < maxdepth) {
			var S = getBelief() ;
			var buttons = S.reachableButtons() ;
			if (buttons.isEmpty()) break ;
			var chosen = buttons.get(rnd.nextInt(buttons.size())) ;
			trace.add(chosen.id) ;
			// ask the agent to toggle the button:
			var status = solveGoal("Toggling button " + chosen.id, entityInteracted(chosen.id), budget_per_task) ;
			 // if the agent is dead, break:
			 if (agent.getState().worldmodel().health <= 0)
				 break ;
			 // also break the execution if a button fails:
			 if (!status.success()) 
				 break ;
			 
			 // reset exploration, then do full explore:
			 agent.getState().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
			 
			 // check if the goal-predicate if we have one, is solved:
			 S = getBelief() ;
			 if (goalPredicate != null && goalPredicate.test(S)) {
				// the search-goal is solved
				 goalPredicateSolved = true ;
				 break ;
			}
			
		}
		
		PlayResult R = new PlayResult() ;
		R.trace = trace ;
		R.reward = rewardOfCurrentGameState() ;
		long execTime = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = this.remainingSearchBudget  - (int) execTime ;
		return R ;
		
	}
		
	List<Node> generateChildren(Node node) throws Exception {
		long t0 = System.currentTimeMillis() ;
		List<Node> children = new LinkedList<>() ;
		var success = runPath(node) ;
		if (success) {
			var S = getBelief() ;
			var buttons = S.reachableButtons() ;
			for (var B : buttons) {
				Node child = new Node() ;
				child.action = B.id ;
				children.add(child) ;
			}
		}
		return children ;
	}
	
	Node chooseLeaf(Node nd) {
		if (nd.children == null) return nd ;
		if (nd.children.isEmpty())
			throw new IllegalArgumentException() ;
		
		float bestUCB = Float.NEGATIVE_INFINITY ;
		Node bestChild = null ;
		for (var ch : nd.children) {
			float U = ch.ucbValue() ;
			if (U > bestUCB) {
				bestUCB = U ;
				bestChild = ch ;
			}
		}
		return  chooseLeaf(bestChild) ;
	}
	
	void mcts() throws Exception {
		while (remainingSearchBudget > 0 && !mctree.fullyExplored) {
			Node leaf = chooseLeaf(mctree) ;
			evaluateLeaf(leaf) ;
			// TODO adjust budget
			if (searchMode && winningplay != null)
				break ;
		}	
	}
	
	void evaluateLeaf(Node leaf) throws Exception {
		
		if (leaf.terminal || leaf.fullyExplored) 
			throw new IllegalArgumentException() ;
		
		// the leaf is at the max-depth:
		if (leaf.depth >= maxdepth) {
			leaf.terminal = true ;
			leaf.fullyExplored = true ;
			runPath(leaf) ;
			var R = rewardOfCurrentGameState() ;
			leaf.backPropagate(R);
			// the case when the state after this node is a winning state:
			if (searchMode && R >= maxReward) {
				winningplay = leaf.getTraceLeadingToThisNode() ;
			}
			return ;			
		}
		
		// leaf is not at max-depth and is has not been sampled/played before:
		if (leaf.numberOfPlays == 0) {
			var R = rollout(leaf) ;
			leaf.backPropagate(R.reward) ;
			if (searchMode && R.reward >= maxReward) {
				winningplay = R.trace ;
			}
			return ;
		}
		
		// last case is that the leaf has been sampled. In this case we expand:
		leaf.children = generateChildren(leaf) ;
		if (leaf.children.isEmpty()) {
			// no further actions from the leaf is possible, mark it as terminal:
			leaf.terminal = true ;
			leaf.fullyExplored = true ;
			leaf.propagateFullyExploredStatus();
			return ;
		}
		
		// else, go to the first child, and evaluate it:
		for (var ch : leaf.children) {
			ch.parent = leaf ;
			ch.depth = leaf.depth+1 ;
		}
		evaluateLeaf(leaf.children.get(0)) ;
	}
	
	

}
