package analysis.genetics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class GeneticAlgorithm {
    private List<Solution> population;
    private int populationSize;
    private static Random random = new Random(69);

    public GeneticAlgorithm(SolutionCreator creator, int populationSize) {
        this.populationSize=populationSize;
        population=new ArrayList<>(populationSize);
        for(int i = 0; i < populationSize; i++) { population.add(creator.nextRandomSolution()); }
    }

    public void simulate(int numEpochs, double probMutation, double probCrossover) {
        for(int n = 0; n < numEpochs; n++) {
            System.out.println("Starting Epoch: "+n);
            simulateEpoch(probMutation,probCrossover);
        }
    }

    private void simulateEpoch(double probMutation, double probCrossover) {
        assertValidProbability(probMutation);
        assertValidProbability(probCrossover);

        Collection<Solution> children = new ArrayList<>();
        // mutate
        population.forEach(solution->{
            if(random.nextDouble()<probMutation) {
                children.add(solution.mutate());
            }
        });

        // crossover
        for(int i = 0; i < population.size(); i++) {
            for(int j = i+1; j < population.size(); j++) {
                if(random.nextDouble()<probCrossover) {
                    children.add(population.get(i).crossover(population.get(j)));
                }
            }
        }

        // evaluate
        calculateSolutionsAndKillOfTheWeak();
    }


    private void calculateSolutionsAndKillOfTheWeak() {
        population.forEach(solution->solution.calculateFitness());
        population=population.stream().sorted(Collections.reverseOrder()).limit(populationSize).collect(Collectors.toList());
        System.out.println("Average score: "+averagePopulationScore());
    }

    private double averagePopulationScore() {
        double score = 0.0;
        for(Solution solution: population) {
            score+=solution.fitness();
        }
        return score/populationSize;
    }

    private static void assertValidProbability(double toValidate) {
        if(toValidate<0.0||toValidate>1.0) throw new RuntimeException("Invalid probability: "+toValidate);
    }

    /**
     * Created by Evan on 2/19/2017.
     */
    public static class TFIDFSolution implements Solution {
        private double fitness;


        @Override
        public double fitness() {
            return fitness;
        }

        @Override
        public void calculateFitness() {

        }

        @Override
        public Solution mutate() {
            return null;
        }

        @Override
        public Solution crossover(Solution other) {
            return null;
        }

        @Override
        public int compareTo(Solution o) {
            return 0;
        }
    }
}
