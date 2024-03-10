package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.utils.Pair;
import world.LabEntity;

/**
 * Implementing evolutionary-search algorithm.
 */
public class Evolutionary extends BaseSearchAlgorithm {

	public float mutationProbability  = 0.2f ;
	public float insertionProbability = 0.2f ;
	
	/**
	 * When true, then the extend-operation insets a gene that is not already in
	 * the target chromosome. Default is true.
	 */
	public boolean onlyExtendWithNewGene = true ;
	
	public int maxPopulationSize = 30 ;
	
	public int numberOfElitesToKeepDuringSelection = 10 ;
	
	public int maxChromosomeLength = 8 ;
	
	public int generationNr = 0 ;
	
	public float maxFitness = 10000 ;
	
	List<String> knownButtons = new LinkedList<>() ;
	
	public Population myPopulation = new Population() ;
	
	Function <Void,LabRecruitsTestAgent> agentConstructor ;
	
	Evolutionary() { super() ; }
	
	public Evolutionary(int budget_per_task,
			int explorationBudget,
			Function <Void,LabRecruitsTestAgent> agentConstructor) {
		this() ;
		this.budget_per_task = budget_per_task ;
		this.explorationBudget = explorationBudget ;
		this.agentConstructor = agentConstructor ;
		myPopulation.rnd = this.rnd ;
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
		
		boolean memberOf(List<String> tau) {
			return population.stream().anyMatch(CI -> tau.equals(CI.chromosome)) ;
		}
		
		/**
		 * Shrink the population to the given target size, keeping the specified
		 * number of the best genes.
		 * @param targetSize
		 * @param numberOfElitesToKeep
		 */
		void applySelection(int targetSize, int numberOfElitesToKeep) {
			if (numberOfElitesToKeep >= targetSize) 
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
	
	void createInitialPopulation() throws Exception {
		
		int remainingBudget = this.remainingSearchBudget ;
		long t0 = System.currentTimeMillis() ;
		
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
		
		
		if (knownButtons.size() == 1)  {
			List<String> tau = new LinkedList<>() ;
			tau.add(knownButtons.get(0)) ;
			myPopulation.add(fitnessValue(tau));
		}
		else if (knownButtons.size() == 2 && maxChromosomeLength >= 2)  {
			List<String> tau = new LinkedList<>() ;
			tau.add(knownButtons.get(0)) ;
			tau.add(knownButtons.get(1)) ;
			myPopulation.add(fitnessValue(tau));
			if (maxPopulationSize > 1) {
				tau.add(knownButtons.get(1)) ;
				tau.add(knownButtons.get(0)) ;
				myPopulation.add(fitnessValue(tau));	
			}
		}
		else {
		   // other cases:		
		   for (int k = 0; k < maxPopulationSize; k++) {
			   List<String> tau = new LinkedList<>();
			   int maxNumberOfAttempts = 50;
			   for (int a = 0; a < maxNumberOfAttempts; a++) {
				   for (int i = 0; i < maxChromosomeLength; i++) {
					   var B = knownButtons.get(rnd.nextInt(knownButtons.size()));
					   tau.add(B);
				   }
				   if (!myPopulation.memberOf(tau))
					   break;
			   }
			   if (tau.isEmpty())
				   break;
		   myPopulation.add(fitnessValue(tau)); 
		   }
		}
		generationNr++ ;
		printStatus() ;
		// override the calculation of remaining budget:
		long time = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = remainingBudget - (int) time ;
	}
	
	void evolve() throws Exception {
		// create mutation/extension:
		int remainingBudget = this.remainingSearchBudget ;
		long t0 = System.currentTimeMillis() ;
		List<List<String>> newBatch = new LinkedList<>() ;
		for (var CI : myPopulation.population) {
			var sigma = CI.chromosome ;
			// mutate or extend:
			float r = rnd.nextFloat() ;
			if (r <= mutationProbability) {
				var tau = mutate(sigma) ;
				if (tau!= null && ! myPopulation.memberOf(tau))
					newBatch.add(tau) ;
			}
			else if (sigma.size() < maxChromosomeLength 
					&& mutationProbability < r
					&& r <= mutationProbability + insertionProbability) {
				var tau = extend(sigma) ;
				if (tau != null && ! myPopulation.memberOf(tau))
				    newBatch.add(tau) ;
			}
		}
		// create cross-overs
		if (myPopulation.population.size() >= 2) {
			int half = myPopulation.population.size() / 2 ;
			List<List<String>> parents = new LinkedList<>() ;
			parents.addAll(myPopulation.population.stream().map(CI -> CI.chromosome).toList()) ;
			for (int k=0; k<half; k++) {
				var sigma1 = parents.remove(rnd.nextInt(parents.size())) ;
				var sigma2 = parents.remove(rnd.nextInt(parents.size())) ;
				var tau = crossOver(sigma1,sigma2) ;
				if (tau != null && ! myPopulation.memberOf(tau))
				   newBatch.add(tau) ;
			}
		}
		
		for (var tau : newBatch) {
			var info = fitnessValue(tau) ;
			myPopulation.add(info);
			if (info.fitness >= maxFitness) 
				// found a solution!
				break ;
		}
		
		myPopulation.applySelection(maxPopulationSize, numberOfElitesToKeepDuringSelection);
		generationNr++ ;
		printStatus() ;
		// override the calculation of remaining budget:
		long time = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = remainingBudget - (int) time ;

	}
	
	
	List<String> copy(List<String> chromosome) {
		var S = new LinkedList<String>() ;
		S.addAll(chromosome) ;
		return S ;
	}
	/**
	 * Return a new chromosome, obtained by randomly mutating a location in 
	 * the given chromosome.
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
	
	List<String> extend(List<String> chromosome) {
		
		var S = copy(chromosome) ;
		
		int insertionPoint = rnd.nextInt(S.size()) ;
		// insert a toggle that is not already in the chromosome:
		List<String> candidates = knownButtons ;
				
		if (onlyExtendWithNewGene) 
			candidates = knownButtons.stream()
				.filter(A -> ! S.contains(A))
				.toList() ;
		
		if (candidates.isEmpty()) return null ;
		
		String E =  candidates.get(rnd.nextInt(candidates.size())) ;
		
		S.add(insertionPoint,E) ;
		return S ;	
	}
	
	List<String> crossOver(List<String> chromosome1, List<String> chromosome2) {
		if (chromosome1.isEmpty() || chromosome2.isEmpty())
			return null ;
		
		List<String> shorter = chromosome1 ;
		List<String> longer  = chromosome2 ;
		if (shorter.size() > longer.size()) {
			shorter = chromosome2 ;
			longer = chromosome1 ;
		}
		if (shorter.size() == 1) {
			var S = copy(shorter) ;
			S.addAll(longer) ;
			return S ;
		}
		
		int crossPoint = shorter.size()/2 ;
		
		var S = new LinkedList<String>() ;
		int k = 0 ;
		while (k<crossPoint) {
			S.add(shorter.get(k)) ;
			k++ ;
		}
		while (k < longer.size()) {
			S.add(longer.get(k)) ;
			k++ ;
		}
		return S ;
	}
	
	
	void instantiateAgent() throws InterruptedException {
		agent = agentConstructor.apply(null) ;
		// add a wait, just to make sure that the level is loaded and the agent
		// is connected to the SUT
		Thread.sleep(500) ;
	}
	
	void closeEnv() {
		agent.env().close() ;
	}
	
	/**
	 * Calculate the fitness-value of the chromosome. This is done by converting
	 * the chromosome to a sequence of goals, and have an agent to execute it. 
	 * The execution stops when a gene (as a goal) fails, and the fitness will be
	 * calculated at the state that results from the execution so far.
	 * 
	 */
	ChromosomeInfo fitnessValue(List<String> chromosome) throws Exception {
		instantiateAgent() ;
		int remainingBudget = this.remainingSearchBudget ;
		long t0 = System.currentTimeMillis() ;
		System.out.println(">>> evaluating chromosome: " + chromosome);
		
		boolean goalPredicateSolved = false ;
		
		int k = 0 ;
		for (var button : chromosome) {
			 var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
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
			 var S = getBelief() ;
			 if (goalPredicate != null && goalPredicate.test(S)) {
				// the search-goal is solved
				 goalPredicateSolved = true ;
				 break ;
			}
			k++ ;
		}
				
		var S = getBelief() ;
		float fitness = 0 ;
		
		if (goalPredicateSolved) {
			fitness = maxFitness ;
			// drop the trailing part of the chromosome:
			int tobeRemoved = chromosome.size() - k - 1 ;
			while (tobeRemoved > 0) {
				chromosome.remove(chromosome.size()-1) ;
				tobeRemoved -- ;
			}
		}
		else {
			//System.out.println(">>> #DOORS=" + S.knownDoors().size()) ;
			//for (var D : S.knownDoors()) {
			//	if (S.isOpen(D.id)) value++ ;
			//}
			
			
			
			// let's use the number of discovered connections + the number of
			// open doors as fitness val:
			fitness = S.getConnections().size() + S.getNumberOfOpenDoors() ;
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
		closeEnv() ;
		// override the calculation of remaining budget:
		long time = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = remainingBudget - (int) time ;
		return new ChromosomeInfo(chromosome,fitness,S) ;
	}
	
	
	/**
	 * The same as {@link BaseSearchAlgorithm#terminationConditionIsReached()},
	 * but ignore whether the agent is dead or alive. This is for deciding the
	 * termination of the whole Evo-iteraion. The agent's status of dead/alive
	 * is less relevant here
	 */
	boolean evoTerminationConditionIsReached() {
		if (myPopulation.population.size() > 0) {
			var best = myPopulation.getBest() ;
			if (this.remainingSearchBudget <= 0 || best.fitness >= maxFitness)
				return true ;
			if (goalPredicate != null && goalPredicate.test(best.belief))
				return true ;
		}
		return false ;
	}
	
	
	@Override
	public void runAlgorithm() throws Exception {
		long time = System.currentTimeMillis() ;
		createInitialPopulation() ;
		myPopulation.print(); 
		while (! evoTerminationConditionIsReached()) {
			evolve() ;
			System.out.println(">>> EVOLUTION gen:" + generationNr) ;
		}
		time = System.currentTimeMillis() - time ;
		System.out.println("** EVO") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		System.out.print("** Search-goal: ") ;
		if (goalPredicate == null) {
			System.out.println(" none specified") ;
		}
		else {
			System.out.println(isGoalSolved() ? "ACHIEVED" : "NOT-achieved") ;
		}
		printStatus() ;
	}
	
	@Override
	public Set<Pair<String,String>> getDiscoveredConnections() {
		var B = myPopulation.getBest().belief ;
		return B.getConnections();
	}
	
	@Override
	public boolean isGoalSolved() {
		if (goalPredicate != null) 
			return goalPredicate.test(myPopulation.getBest().belief) ;
		return false ;
	}
}
