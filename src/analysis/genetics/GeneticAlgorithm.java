package analysis.genetics;

import tools.SimpleTimer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class GeneticAlgorithm {
    private List<Solution> population;
    private int maxPopulationSize;
    private static Random random = new Random(69);
    private final double startingScore;
    private double bestScoreSoFar;
    private Solution bestSolutionSoFar;
    private double currentScore;

    public GeneticAlgorithm(SolutionCreator creator, int maxPopulationSize) {
        this.maxPopulationSize=maxPopulationSize;
        population=new ArrayList<>(maxPopulationSize);
        for(int i = 0; i < 2*maxPopulationSize; i++) { population.add(creator.nextRandomSolution()); }
        calculateSolutionsAndKillOfTheWeak();
        startingScore=currentScore;
        bestScoreSoFar=startingScore;
        System.out.println("Starting score: "+startingScore);
    }

    public void simulate(int numEpochs, double probMutation, double probCrossover) {
        SimpleTimer timer = new SimpleTimer();
        for(int n = 0; n < numEpochs; n++) {
            System.out.println("Starting Epoch: "+n);
            timer.start();
            simulateEpoch(probMutation,probCrossover);
            timer.finish();
            System.out.println("Time to complete: "+(timer.getElapsedTime()/1000)+ " seconds");
            System.out.println("Starting Avg Score: "+startingScore);
            System.out.println("Best Avg Score: "+bestScoreSoFar);
            System.out.println("Current Avg Score: "+currentScore);
            System.out.println("Best Solution: "+bestSolutionSoFar.fitness());
        }
    }

    private void simulateEpoch(double probMutation, double probCrossover) {
        assertValidProbability(probMutation);
        assertValidProbability(probCrossover);

        AtomicInteger mutationCounter = new AtomicInteger(0);
        AtomicInteger crossoverCounter = new AtomicInteger(0);

        Collection<Solution> children = new ArrayList<>();
        // mutate
        population.forEach(solution->{
            if(random.nextDouble()<probMutation) {
                children.add(solution.mutate());
                mutationCounter.getAndIncrement();
            }
        });
        System.out.println(Integer.valueOf(mutationCounter.get())+" mutations");

        // crossover
        for(int i = 0; i < population.size(); i++) {
            for(int j = i+1; j < population.size(); j++) {
                if(random.nextDouble()<probCrossover) {
                    children.add(population.get(i).crossover(population.get(j)));
                    crossoverCounter.getAndIncrement();
                }
            }
        }
        System.out.println(Integer.valueOf(crossoverCounter.get())+" crossovers");
        population.addAll(children);

        // evaluate
        calculateSolutionsAndKillOfTheWeak();
    }


    private void calculateSolutionsAndKillOfTheWeak() {
        population.forEach(solution->solution.calculateFitness());
        population=population.stream().sorted(Collections.reverseOrder()).limit(maxPopulationSize).collect(Collectors.toList());
        calculateAveragePopulationScores();
        setBestSolution();
    }

    private void setBestSolution() {
        if(!population.isEmpty()) {
            Solution currentBestSolution = population.get(0);
            if(bestSolutionSoFar==null||(bestSolutionSoFar.fitness()<currentBestSolution.fitness())) bestSolutionSoFar=currentBestSolution;
        }
    }

    private void calculateAveragePopulationScores() {
        currentScore = 0.0;
        for(Solution solution: population) {
            currentScore+=solution.fitness();
        }
        if(population.size()>0)currentScore/=population.size();
        if(currentScore>bestScoreSoFar)bestScoreSoFar=currentScore;
    }

    private static void assertValidProbability(double toValidate) {
        if(toValidate<0.0||toValidate>1.0) throw new RuntimeException("Invalid probability: "+toValidate);
    }

}
