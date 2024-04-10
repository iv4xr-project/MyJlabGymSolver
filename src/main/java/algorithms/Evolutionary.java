package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.utils.Pair;
import world.LabEntity;

/**
 * Implementing an evolutionary-search algorithm.
 * 
 * <p><b>Chromosome</b>: is a sequence of interactables. For LR, these are buttons.
 * 
 * <p><b>Fitness</b> of a chromosome: the buttons in the chromosome are interacted, in
 * the sequence they appear. This is done by aplib agent. If this execution manages to
 * achieve the top-goal, the fitness will be some max-value. Else the fitness is the 
 * number of connections discovered while executing + the number of open doors at the 
 * end state.
 * 
 * <p>The algorithm does not know what are available interactables. So it starts by
 * exploring LR to collect an initial set of known buttons. Later, whenever a chromosome
 * is executed, exploration is added after every interaction with a button. Newly discovered
 * buttons are added to the set of known buttons, along with newly discovered connections.
 * 
 * <p>The set of initial chromosomes will be singleton chromosomes, each containing one
 * button from the set of known buttons obtained from the initial exploration when the
 * algoroithm starts.
 * 
 * <p>The algorithm proceeds as follows:
 * 
 * <ul>
 *     <li> (1) explore then create the initial population P 
 *     <li> (2) WHILE termination-condition is still false:
 *     <ul>
 *         <li> (3) select a set of parents from P. The used selection scheme is currently set to 
 *              select some K chromosomes with best fitness, and then to fill it with random
 *              selection from the rest of P. The number of selected parents is limited to some
 *              number (we use {{@link #maxPopulationSize}/2).
 *         <li> (4) Randomly choose two parents. Decide whether to keep them, or to do cross-over.
 *              Cross-over of two parents (p1,p2) creates two new chromosomes based on the parents.
 *              They will replace the parents.
 *              After doing this we have a set of new chromosomes; let's call it Q.
 *         <li> (5) Generates new chromosomes from Q by either mutating them or extending them. Mutating
 *              a chromosome ch means we replace one interactable in it with another (from the set
 *              of known interactables!). Extending ch means to randomly insert a new interactable 
 *              somewhere in ch.
 *              We fill Q with these new chromosomes, up to some maximim size ({@link #maxPopulationSize}).
 *         <li> (6) We replace P with Q.
 *         <li> (7) For each chromosome in P we calculate its fitness-value.
 *                   
 *     </ul>
 * </ul>
 */
public class Evolutionary extends BaseSearchAlgorithm {

	public float mutationProbability  = 0.2f ;
	public float insertionProbability = 0.3f ;
	public float crossoverProbability = 0.2f ;
	
	/**
	 * When true, then the extend-operation insets a gene that is not already in
	 * the target chromosome. Default is true.
	 */
	public boolean onlyExtendWithNewGene = true ;
	
	/**
	 * Should be at least four.
	 */
	public int maxPopulationSize = 20 ;
	
	public int numberOfElitesToKeepDuringSelection = 10 ;
	
	public int maxChromosomeLength = 8 ;
	
	public int generationNr = 0 ;
	
	public float maxFitness = 10000 ;
	
	List<String> knownButtons = new LinkedList<>() ;
	
	public Set<Pair<String,String>> discoveredConnections = new HashSet<>() ;
	
	public Population myPopulation = new Population() ;
	
	/**
	 * Create an agent, with a state, and connected to the SUT. The function may
	 * also re-launch the SUT (you decide).
	 */
	Function <Void,LabRecruitsTestAgent> agentConstructor ;
	
	Evolutionary() { 
		myPopulation.rnd = this.rnd ;
	}
	
	public Evolutionary(Function <Void,LabRecruitsTestAgent> agentConstructor) {
		this() ;
		this.agentConstructor = agentConstructor ;
	}
	
	public static class ChromosomeInfo {
		public List<String> chromosome ;
		public float fitness ;
		public XBelief belief ;
		
		ChromosomeInfo(List<String> chromosome, float value, XBelief belief) {
			this.chromosome = chromosome ;
			this.fitness = value ;
			this.belief = belief ;
		}
	}
	
	public static class Population {
		
		Random rnd  ;
				
		List<ChromosomeInfo> population = new LinkedList<>() ;
		
		/**
		 * Add a new chromosome, and keep the population sorted by the chromosomes' values.
		 */
		void add(ChromosomeInfo CI) {
 			if (population.isEmpty()) {
 				population.add(CI) ;
 				return ;
 			}
 			if (population.get(population.size() - 1).fitness >= CI.fitness) {
 				population.add(CI) ;
 				return ;
 			}
			int k = 0 ;
			for (var M : population) {
				if (M.fitness < CI.fitness) {
					break ;
				}
				k++ ;
			}
			population.add(k,CI) ;		
		}
		
		
		public ChromosomeInfo getBest() {
			if (population.isEmpty()) return null ;
			return population.get(0) ;
		}
		
		/**
		 * Get the Chromosome-info of the given chromosome, if it is in the population. Else
		 * return null.
		 */
		public ChromosomeInfo getInfo(List<String> chromosome) {
			for (var CI : population) {
				if (CI.chromosome.equals(chromosome)) {
					return CI ;
				}
			}
			return null ;
		}
		
		boolean memberOf(List<String> tau) {
			return population.stream().anyMatch(CI -> tau.equals(CI.chromosome)) ;
		}
		
		void remove(List<String> tau) {
			int k = 0 ;
			for (var CI : population) {
				if (CI.chromosome.equals(tau)) {
					break ;
				}
				k++ ;
			}
			if (k < population.size())
				population.remove(k) ;
		}
		
		/**
		 * Shrink the population to the given target size, keeping the specified
		 * number of the best chromosomes (elitism). The remaining space is filled
		 * by randomly selecting from those outside the elite set.
		 */
		void applySelection(int targetSize, int numberOfElitesToKeep) {
			if (numberOfElitesToKeep > targetSize) 
				throw new IllegalArgumentException() ;
	
			int numberToDrop = population.size() - targetSize ;
			
			while (numberToDrop > 0) {
				int k = rnd.nextInt(population.size() - numberOfElitesToKeep) ;
				k += numberOfElitesToKeep ;
				population.remove(k) ;
				numberToDrop -- ;
			}
		}
		
		void print() {
			int k=0 ;
			System.out.println("** #chromosomes=" + population.size()) ;
			for (var CI : population) {
				System.out.println("** [" + k + "] val=" + CI.fitness + ", " + CI.chromosome
						+ ", #connections:" + CI.belief.getConnections().size() 
						) ;
				k++ ;
			}
		}
				
	}
	
	@Override
	public void setRndSeed(int seed) {
		super.setRndSeed(seed);
		myPopulation.rnd = rnd ;
	}
	
	void printStatus() {
		System.out.println("** Generation = " + generationNr) ;
		System.out.println("** #population= " + myPopulation.population.size()) ;
		if ( myPopulation.population.isEmpty()) return ;
		System.out.println("** best-fitness-value = " + myPopulation.population.get(0).fitness) ;
		var avrg = myPopulation.population.stream().collect(Collectors.averagingDouble(CI -> (double) CI.fitness)) ;
		System.out.println("** avrg-fitness-value = " + avrg) ;
		myPopulation.print(); 
	}
	
	/**
	 * For creating the initially population of chromosomes. The agent will first explore the game,
	 * to find buttons. Chromosomes of length 1 are then created. Each containing an interaction
	 * with a button.
	 */
	void createInitialPopulation() throws Exception {
		
		if (maxPopulationSize <= 0)
			throw new IllegalArgumentException() ;
		
		if (maxChromosomeLength <= 0)
			throw new IllegalArgumentException() ;

		knownButtons.clear();
		instantiateAgent() ;
		doExplore(explorationBudget) ;
		// add found buttons to the list of known buttons:
		var S = agent.getState() ;
		for (var B : S.knownButtons()) {
			if (! knownButtons.contains(B.id)) {
						knownButtons.add(B.id) ;
			}
		}
		closeEnv() ;
		
		if (knownButtons.isEmpty()) {
			throw new Exception("Cannot create a starting population because the agent cannot find any button.") ;
		}
		
		List<String> buttons = new LinkedList<>() ;
		buttons.addAll(knownButtons) ;
		
		while(buttons.size() > 0 && myPopulation.population.size() < maxPopulationSize) {
			var B = buttons.remove(rnd.nextInt(buttons.size())) ;
			List<String> tau = new LinkedList<>() ; 
			tau.add(B) ;
			myPopulation.add(fitnessValue(tau));
			totNumberOfRuns++ ;
			if (isTopGoalSolved()) break ;	
		}
		
		generationNr = 1  ;
	}
	
	void evolve() throws Exception {
			
		int halfSize = maxPopulationSize/2 ;
		// Apply selection, drop some chromosones to get the population to maxSize/2.
		// If the current population size is less that maxSize/2, then none is dropped. 
		// The obtained selection is called "parents".
		myPopulation.applySelection(halfSize, numberOfElitesToKeepDuringSelection);
		List<List<String>> parents = new LinkedList<>() ;
		parents.addAll(myPopulation.population.stream().map(CI -> CI.chromosome).collect(Collectors.toList())) ;
		
		// Create a new-batch by either applying crossover or by just putting parents in the
		// new batch.
		List<List<String>> newBatch = new LinkedList<>() ;
		while (parents.size() > 1) {
			var p1 = parents.remove(rnd.nextInt(parents.size()-1)) ;
			List<String> p2 = null ;
			if (parents.size() == 1) {
				p2 = parents.remove(0) ;
			}
			else {
				p2 = parents.remove(rnd.nextInt(parents.size()-1)) ;
			}
		
			boolean putBackParents = true ;
			if (rnd.nextFloat() <= crossoverProbability) {
				var offsprings = crossOver(p1,p2) ;
				if (offsprings != null 
						&& ! newBatch.contains(offsprings.fst)
						&& ! newBatch.contains(offsprings.snd)) {
					newBatch.add(offsprings.fst) ;
					newBatch.add(offsprings.snd) ;
					putBackParents = false ;
				}
			}
			if (putBackParents) {
				newBatch.add(p1) ;
				newBatch.add(p2) ;
 			}	
		}
		if (parents.size() == 1) {
			// a single parent remains, just put it back:
			newBatch.add(parents.remove(0)) ;
		}
		
		// fill in the rest of the new-batch with mutated or extended chromosomes:
		
		int N = newBatch.size() ;
		
		for (int i=0; i<N; i++) {
			var sigma = newBatch.get(i) ;
			// mutate or extend:
			boolean extensionIsApplied = false ;
			if (sigma.size() < maxChromosomeLength && rnd.nextFloat() <= insertionProbability) {
				var tau = extend(sigma) ;
				if (tau != null && ! myPopulation.memberOf(tau)) {
				    newBatch.add(tau) ;
				    extensionIsApplied = true ;
				}
			}
			if (!extensionIsApplied && rnd.nextFloat() <= mutationProbability) {
				var tau = mutate(sigma) ;
				if (tau!= null && ! myPopulation.memberOf(tau))
					newBatch.add(tau) ;
			}
		}
		
		// clear the population; keeping only those that also appear in the new batch
		myPopulation.population.removeIf(CI -> ! newBatch.contains(CI.chromosome)) ;
		
		// now calculate the fitness of every member of the new-batch, and add it to the
		// population:
		for (var tau : newBatch) {
			if (myPopulation.memberOf(tau)) {
				// already in the population, no need to evaluate its fitness again
				continue ;
			}
			var info = fitnessValue(tau) ;
			totNumberOfRuns++ ;
			myPopulation.add(info);
			if (isTopGoalSolved()) 
				// found a solution!
				break ;
		}
		
		generationNr++ ;
	}
	
	
	List<String> copy(List<String> chromosome) {
		var S = new LinkedList<String>() ;
		S.addAll(chromosome) ;
		return S ;
	}
	/**
	 * Return a new chromosome, obtained by randomly mutating one location in 
	 * the given chromosome.
	 * It returns null, if the method fails to mutate.
	 */
	List<String> mutate(List<String> chromosome) {
		
		var S = copy(chromosome) ;
		
		int mutationPoint = rnd.nextInt(S.size()) ;
		String B = S.get(mutationPoint) ;
		List<String> mutations = knownButtons.stream().filter(A -> ! A.equals(B))
				.toList() ;
		if (mutations.isEmpty()) return null ;
		String M = mutations.get(rnd.nextInt(mutations.size())) ;
		S.set(mutationPoint, M) ;
		return S ;
	}
	
	/**
	 * Insert a new gene into a chromosome. The method fails if no gene to insert can be found.
	 */
	List<String> extend(List<String> chromosome) {
		
		var seq = copy(chromosome) ;
		
		int insertionPoint = rnd.nextInt(seq.size()) ;
		// insert a toggle that is not already in the chromosome:
		List<String> candidates = knownButtons ;
				
		if (onlyExtendWithNewGene) 
			candidates = knownButtons.stream()
				.filter(A -> ! seq.contains(A))
				.toList() ;
		
		if (candidates.isEmpty()) return null ;
		
		String E =  candidates.get(rnd.nextInt(candidates.size())) ;
		
		seq.add(insertionPoint,E) ;
		return seq ;	
	}
	
	/**
	 * Create two offsprings of the given chromosomes through cross-over.
	 */
	Pair<List<String>,List<String>> crossOver(List<String> chromosome1, List<String> chromosome2) {
		
		if (chromosome1.isEmpty() || chromosome2.isEmpty())
			return null ;
		
		List<String> shorter = new LinkedList<>() ;
		List<String> longer  = new LinkedList<>() ;
		if (chromosome1.size() >= chromosome2.size()) {
			longer.addAll(chromosome1) ;
			shorter.addAll(chromosome2) ;
		}
		else {
			longer.addAll(chromosome2) ;
			shorter.addAll(chromosome1) ;
		}
		if (shorter.size() == 1) {
			shorter.addAll(longer.subList(1, longer.size())) ;
			return new Pair<>(shorter,longer) ;
		}
		
		int crossPoint = shorter.size()/2 ;
		
		var S1 = new LinkedList<String>() ;
		var S2 = new LinkedList<String>() ;
		
		S1.addAll(shorter.subList(0, crossPoint)) ;
		S1.addAll(longer.subList(crossPoint, longer.size())) ;
		
		S2.addAll(longer.subList(0, crossPoint)) ;
		S2.addAll(shorter.subList(crossPoint, shorter.size())) ;
		
		return new Pair<>(S1,S2) ;
 	}
	
	
	void instantiateAgent() throws InterruptedException {
		agent = agentConstructor.apply(null) ;
		// add a wait, just to make sure that the level is loaded and the agent
		// is connected to the SUT
		Thread.sleep(500) ;
	}
	
	/**
	 * Calculate the fitness-value of the chromosome. This is done by converting
	 * the chromosome to a sequence of goals, and have an agent to execute it. 
	 * The execution stops when a gene (as a goal) fails, and the fitness will be
	 * calculated at the state that results from the execution so far.
	 * 
	 */
	ChromosomeInfo fitnessValue(List<String> chromosome) throws Exception {
		var t0 = System.currentTimeMillis() ;
		instantiateAgent() ;
		var duration = System.currentTimeMillis() - t0 ;
		// add this back to the time accounting, as we won't count LR initialization as exec-time:
		this.remainingSearchBudget += (int) duration ;
		
		System.out.println(">>> evaluating chromosome: " + chromosome);
		
		boolean goalPredicateSolved = false ;
		boolean agentIsAlive = true ;
		
		int k = 0 ;
		for (var button : chromosome) {
			 var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
			 // this is the right place for k++, don't move it:
			 k++ ;
			 // if the agent is dead, break:
			 if (agent.getState().worldmodel().health <= 0) {
				 agentIsAlive = false ;
				 break ;
			 }
			 // also break the execution if a button fails:
			 if (!status.success()) 
				 break ;
			 
			 // reset exploration, then do full explore:
			 agent.getState().pathfinder().wipeOutMemory();
			 doExplore(explorationBudget) ;
			 
			 // check if the goal-predicate if we have one, is solved:
			 var S = getBelief() ;
			 if (topGoalPredicate != null && topGoalPredicate.test(S)) {
				// the search-goal is solved
				 goalPredicateSolved = true ;
				 break ;
			}
		}
				
		var S = getBelief() ;
		float fitness = 0 ;
		
		// don't replace this with isGoalSolved():
		if (topGoalPredicate != null && topGoalPredicate.test(S)) {
			fitness = maxFitness ;	
		}
		else {
			//System.out.println(">>> #DOORS=" + S.knownDoors().size()) ;
			//for (var D : S.knownDoors()) {
			//	if (S.isOpen(D.id)) value++ ;
			//}	
			// let's use the number of discovered connections + the number of
			// open doors as fitness val:
			fitness = S.getConnections().size() + S.getNumberOfOpenDoors() ;
			// except when the agent dies:
			if (! agentIsAlive)
				fitness = -1 ;
		}
		// drop the trailing part of the chromosome that were not used (e.g. because the goal is
		// already reached:
		int tobeRemoved = chromosome.size() - k  ;
		while (tobeRemoved > 0) {
			chromosome.remove(chromosome.size()-1) ;
			tobeRemoved -- ;
		}
		System.out.println(">>> chromosome: " 
		   + chromosome
		   + ", FITNESS-VAL=" + fitness);
		// also add newly-found buttons to the list of known buttons:
		for (var B : S.knownButtons()) {
			if (! knownButtons.contains(B.id)) {
				knownButtons.add(B.id) ;
			}
		}
		// add discovered connections:
		var cons = getBelief().getConnections() ;
		for (var c : cons) {
			discoveredConnections.add(c) ;
		}
		
		closeEnv() ;
		// override the calculation of remaining budget:
		return new ChromosomeInfo(chromosome,fitness,S) ;
	}
	
	
	/**
	 * The same as {@link BaseSearchAlgorithm#terminationConditionIsReached()},
	 * but ignore whether the agent is dead or alive. This is for deciding the
	 * termination of the whole Evo-iteration. The agent's status of dead/alive
	 * is less relevant here
	 */
	@Override
	boolean terminationConditionIsReached() {
		if (remainingSearchBudget <= 0) {
			DebugUtil.log("*** TOTAL BUDGET IS EXHAUSTED.") ;
			return true ;
		}
		if (isTopGoalSolved()) {
			DebugUtil.log("*** The search FOUND its global-goal. YAY!") ;
			return true ;
		}
		if (myPopulation.population.size() > 0) {
			var best = myPopulation.getBest() ;
			if (best.fitness >= maxFitness) {
				DebugUtil.log("*** Maximum fitness is reached.") ;
				return true ;
			}
		}
		return false ;
	}
	
	
	@Override
	public void runAlgorithm() throws Exception {
		
		if (maxPopulationSize <= 4)
			throw new IllegalArgumentException("maxPopulationSize should be at least 4.") ;
		
		long time = System.currentTimeMillis() ;
		createInitialPopulation() ;
		printStatus() ;
	    if (knownButtons.isEmpty())
	    	throw new IllegalArgumentException("The algorithm cannot find any action to activate.") ;
		this.remainingSearchBudget = this.remainingSearchBudget - (int) (System.currentTimeMillis()  - time) ;
		while (! terminationConditionIsReached()) {
			long t0 = System.currentTimeMillis() ;
			evolve() ;
			System.out.println(">>> EVOLUTION gen:" + generationNr) ;
			printStatus() ;
			long duration = System.currentTimeMillis() - t0 ;
			this.remainingSearchBudget = this.remainingSearchBudget - (int) duration ;
		}
		time = System.currentTimeMillis() - time ;
		System.out.println("** EVO") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.print("** Search-goal: ") ;
		if (topGoalPredicate == null) {
			System.out.println(" none specified") ;
		}
		else {
			System.out.println(isTopGoalSolved() ? "ACHIEVED" : "NOT-achieved") ;
		}
		printStatus() ;
	}
	
	@Override
	public Set<Pair<String,String>> getDiscoveredConnections() {
		//var B = myPopulation.getBest().belief ;
		//return B.getConnections();
		return discoveredConnections ;
	}
	
	@Override
	public boolean isTopGoalSolved() {
		if (topGoalPredicate != null && !myPopulation.population.isEmpty()) 
			return topGoalPredicate.test(myPopulation.getBest().belief) ;
		return false ;
	}
}
