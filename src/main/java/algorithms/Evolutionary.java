package algorithms;

import static agents.tactics.GoalLib.entityInteracted;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import agents.LabRecruitsTestAgent;
import nl.uu.cs.aplib.utils.Pair;
import world.LabEntity;

public class Evolutionary extends BaseSearchAlgorithm {

	public float mutationProbability  = 0.2f ;
	public float insertionProbability = 0.2f ;
	
	/**
	 * When true, then the extend-operation insets a gene that is not already in
	 * the target chromosome. Default is true.
	 */
	public boolean onlyExtendWithNewGene = true ;
	
	public int explorationBudget = 150 ;
	
	public int maxPopulationSize = 30 ;
	
	public int numberOfElitesToKeepDuringSelection = 10 ;
	
	public int maxChromosomeLength = 8 ;
	
	public int generationNr = 0 ;
	
	public int maxFitness = 10000 ;
	
	List<String> knownButtons = new LinkedList<>() ;
	
	public Population myPopulation = new Population() ;
	
	Function <Void,LabRecruitsTestAgent> agentConstructor ;
	
	Evolutionary() { }
	
	public Evolutionary(int budget_per_task,
			int explorationBudget,
			Function <Void,LabRecruitsTestAgent> agentConstructor) {
		this.budget_per_task = budget_per_task ;
		this.explorationBudget = explorationBudget ;
		this.agentConstructor = agentConstructor ;
	}
	
	public static class ChromosomeInfo {
		public List<String> chromosome ;
		public float value ;
		public XBelief belief ;
		
		ChromosomeInfo(List<String> chromosome, float value, XBelief belief) {
			this.chromosome = chromosome ;
			this.value = value ;
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
 			if (population.get(population.size() - 1).value >= CI.value) {
 				population.add(CI) ;
 				return ;
 			}
			int k = 0 ;
			for (var M : population) {
				if (M.value < CI.value) {
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
				int k = rnd.nextInt(numberToDrop) ;
				k += numberOfElitesToKeep ;
				population.remove(k) ;
				numberOfElitesToKeep -- ;
			}
		}
				
	}
	
	
	void printStatus() {
		System.out.println("** Generation = " + generationNr) ;
		System.out.println("** #population= " + myPopulation.population.size()) ;
		if ( myPopulation.population.isEmpty()) return ;
		System.out.println("** best-value = " + myPopulation.population.get(0).value) ;
		var avrg = myPopulation.population.stream().collect(Collectors.averagingDouble(CI -> (double) CI.value)) ;
		System.out.println("** avrg-value = " + avrg) ;
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
		
		myPopulation = new Population() ;
		
		if (knownButtons.size() == 1)  {
			List<String> tau = new LinkedList<>() ;
			tau.add(knownButtons.get(0)) ;
			myPopulation.add(value(tau));
		}
		else if (knownButtons.size() == 2 && maxChromosomeLength >= 2)  {
			List<String> tau = new LinkedList<>() ;
			tau.add(knownButtons.get(0)) ;
			tau.add(knownButtons.get(1)) ;
			myPopulation.add(value(tau));
			if (maxPopulationSize > 1) {
				tau.add(knownButtons.get(1)) ;
				tau.add(knownButtons.get(0)) ;
				myPopulation.add(value(tau));	
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
		   myPopulation.add(value(tau)); 
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
				if (! myPopulation.memberOf(tau))
					newBatch.add(tau) ;
			}
			else if (sigma.size() < maxChromosomeLength 
					&& mutationProbability < r
					&& r <= mutationProbability + insertionProbability) {
				var tau = extend(sigma) ;
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
				newBatch.add(tau) ;
			}
		}
		
		for (var tau : newBatch) {
			myPopulation.add(value(tau));
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
		}
		while (k < longer.size()) {
			S.add(longer.get(k)) ;
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
	ChromosomeInfo value(List<String> chromosome) throws Exception {
		instantiateAgent() ;
		int remainingBudget = this.remainingSearchBudget ;
		long t0 = System.currentTimeMillis() ;
		System.out.println(">>> evaluating chromosome: " + chromosome);
		for (var button : chromosome) {
			 var status = solveGoal("Toggling button " + button, entityInteracted(button), budget_per_task) ;
			 // if the agent is dead, break:
			 if (agent.getState().worldmodel.health <= 0)
				 break ;
			 
			 // reset exploration, then do full explore:
			 agent.getState().pathfinder.wipeOutMemory();
			 doExplore(explorationBudget) ;
			 // also break the execution if a button fails:
			 if (!status.success()) 
				 break ;
		}
		
		var agent = agentConstructor.apply(null) ;
		
		var S = agent.getState() ;
		int value = 0 ;
		
		if (goalPredicate != null && goalPredicate.test(S)) {
			// the search-goal is solved
			value = maxFitness ;
		}
		else {
			for (var D : S.knownDoors()) {
				var D_ = (LabEntity) D ;
				if (S.isOpen(D.id)) value++ ;
			}
		}
		System.out.println(">>> chromosome: " 
		   + chromosome
		   + ", VALUE=" + value);
		// also add newly-found buttons to the list of known buttons:
		for (var B : S.knownButtons()) {
			if (! knownButtons.contains(B.id)) {
				knownButtons.add(B.id) ;
			}
		}
		var S_ = this.getBelief() ;
		closeEnv() ;
		// override the calculation of remaining budget:
		long time = System.currentTimeMillis() - t0 ;
		this.remainingSearchBudget = remainingBudget - (int) time ;
		return new ChromosomeInfo(chromosome,value,S_) ;
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
			if (this.remainingSearchBudget <= 0 || best.value >= maxFitness)
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
		while (! evoTerminationConditionIsReached()) {
			evolve() ;
		}
		time = System.currentTimeMillis() - time ;
		System.out.println("** EVO") ;
		System.out.println("** total-runtime=" + time + ", #turns=" + this.turn) ;
		System.out.println("** Total budget=" + this.totalSearchBudget
				+ ", unused=" + Math.max(0,this.remainingSearchBudget)) ;
		printStatus() ;
	}
	
}
