package analysis.genetics;

/**
 * Created by Evan on 2/19/2017.
 */
public interface Solution extends Comparable<Solution> {
    double fitness();

    void calculateFitness();

    Solution mutate();

    Solution crossover(Solution other);
}
