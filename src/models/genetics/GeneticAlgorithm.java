package models.genetics;

import models.genetics.keyword_analysis.ProbabilityHelper;
import org.deeplearning4j.berkeley.Pair;
import tools.SimpleTimer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class GeneticAlgorithm<T extends Solution> {
    private List<T> population;
    private int maxPopulationSize;
    private static Random random = new Random(69);
    private final double startingScore;
    private final int numThreads;
    private T bestSolutionSoFar;
    private double currentScore;
    private Listener listener;
    private AtomicInteger mutationCounter = new AtomicInteger(0);
    private AtomicInteger crossoverCounter = new AtomicInteger(0);

    public GeneticAlgorithm(SolutionCreator<T> creator, int maxPopulationSize, Listener listener, int numThreads) {
        this.maxPopulationSize=maxPopulationSize;
        this.numThreads=numThreads;
        this.listener=listener;
        population=new ArrayList<>(maxPopulationSize);
        population.addAll(creator.nextRandomSolutions(maxPopulationSize));
        calculateSolutionsAndKillOfTheWeak();
        startingScore=currentScore;
    }

    public T getBestSolution() {
        return bestSolutionSoFar;
    }

    public Collection<Solution> getAllSolutions() { return new HashSet<>(population); }

    public void simulate(long timeLimit, double probMutation, double probCrossover) {
        RecursiveAction action = new RecursiveAction() {
            @Override
            protected void compute() {
                SimpleTimer timer = new SimpleTimer();
                double globalTimer = 0d;
                AtomicInteger epochCounter = new AtomicInteger(0);
                Double lastCurrentScore = null;
                Double lastLastCurrentScore = null;
                while(globalTimer<=timeLimit) {
                    timer.start();
                    Pair<Integer,Integer> counts = simulateEpoch(probMutation,probCrossover);
                    timer.finish();
                    globalTimer+=timer.getElapsedTime();

                    if(bestSolutionSoFar!=null) {
                        System.out.println("Best Solution:      "+bestSolutionSoFar.fitness());
                    }
                    // listener
                    if(bestSolutionSoFar!=null&&listener!=null) {
                        System.out.println("EPOCH ["+epochCounter.getAndIncrement()+"]");
                        System.out.println("Total time elapsed: "+globalTimer/1000+ " seconds");
                        System.out.println("Starting Avg Score: "+startingScore);
                        System.out.println("Current Avg Score:  "+currentScore);
                        System.out.println("Mutations so far:   "+mutationCounter.addAndGet(counts.getFirst()));
                        System.out.println("Crossovers so far:  "+crossoverCounter.addAndGet(counts.getSecond()));
                        listener.print(bestSolutionSoFar);
                        clearScreen();
                    }

                    double epsilon = 0.00001;

                    if(lastCurrentScore!=null&&lastLastCurrentScore!=null&&Math.abs(lastCurrentScore-currentScore)/maxPopulationSize<epsilon&&Math.abs(lastLastCurrentScore-currentScore)/maxPopulationSize<epsilon) {
                        System.out.println("Converged!");
                        break;
                    }
                    lastLastCurrentScore=lastCurrentScore;
                    lastCurrentScore=currentScore;
                }
            }
        };

        ForkJoinPool pool = new ForkJoinPool();
        pool.execute(action);
        pool.shutdown();
        try {
            pool.awaitTermination(timeLimit, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            System.out.println( "TIME LIMIT REACHED!");
            try {
                action.cancel(true);
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    private Pair<Integer,Integer> simulateEpoch(double probMutation, double probCrossover) {
        assertValidProbability(probMutation);
        assertValidProbability(probCrossover);

        AtomicInteger mutationCounter = new AtomicInteger(0);
        AtomicInteger crossoverCounter = new AtomicInteger(0);

        ForkJoinPool pool = new ForkJoinPool(numThreads);

        Collection<T> children = Collections.synchronizedList(new ArrayList<>());
        // mutate
        for(int i = 0; i < population.size(); i++) {
            if(random.nextDouble()<probMutation/Math.log(Math.E+mutationCounter.get())) {
                Solution solution = population.get(i);
                RecursiveAction action = new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            Solution child = solution.mutate();
                            if (child == null) {
                                //System.out.println("Mutation Failed");
                                return;
                            }
                            children.add((T)child);
                            mutationCounter.getAndIncrement();
                        } catch(Exception e) {
                            e.printStackTrace();
                            System.out.println("Mutation failed with exception");
                        }
                    }
                };
                pool.execute(action);
            }
        }


        // crossover
        for(int i = 0; i < population.size(); i++) {
            if(random.nextDouble()<probCrossover) {
                Solution x = population.get(i);
                // get a random solution near the front
                int j = ProbabilityHelper.getLowNumberWithMaxUpTo(population.size());
                Solution y = population.get(j);
                RecursiveAction action = new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            Solution child = x.crossover(y);
                            if (child == null) {
                                //System.out.println("Crossover Failed");
                                return;
                            }
                            children.add((T)child);
                            crossoverCounter.getAndIncrement();
                        } catch(Exception e) {
                            e.printStackTrace();
                            System.out.println("Crossover failed with exeception");
                        }
                    }
                };
                pool.execute(action);
            }
        }

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println();
        System.out.println();
        if(crossoverCounter.get()==0) System.out.println("Warning no crossovers");
        if(mutationCounter.get()==0) System.out.println("Warning no mutations");
        population.addAll(children);
        // evaluate
        calculateSolutionsAndKillOfTheWeak();

        return new Pair<>(mutationCounter.get(),crossoverCounter.get());
    }


    private void calculateSolutionsAndKillOfTheWeak() {
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        population.forEach(solution->{
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    solution.calculateFitness();
                }
            };
            pool.execute(action);
        });

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }

        population=population.stream().sorted().limit(maxPopulationSize).collect(Collectors.toList());
        calculateAveragePopulationScores();
        setBestSolution();
    }

    private void setBestSolution() {
        if(!population.isEmpty()) {
            T currentBestSolution = population.get(0);
            if(bestSolutionSoFar==null||(bestSolutionSoFar.fitness()<currentBestSolution.fitness())) bestSolutionSoFar=currentBestSolution;
        }
    }

    private void calculateAveragePopulationScores() {
        currentScore = 0.0;
        for(Solution solution: population) {
            currentScore+=solution.fitness();
        }
        if(population.size()>0)currentScore/=population.size();
    }

    private static void assertValidProbability(double toValidate) {
        if(toValidate<0.0||toValidate>1.0) throw new RuntimeException("Invalid probability: "+toValidate);
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }


}
