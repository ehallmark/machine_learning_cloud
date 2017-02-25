package analysis.genetics.lead_development;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;
import analysis.genetics.keyword_analysis.KeywordSolution;
import analysis.genetics.keyword_analysis.Word;
import com.google.common.collect.Maps;
import seeding.Database;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/19/2017.
 */
public class CompanySolutionCreator implements SolutionCreator {
    private List<Attribute> attributes;
    private final int numThreads;
    private final int solutionSize;
    public CompanySolutionCreator(List<Attribute> attributes, int solutionSize, int numThreads) {
        this.attributes=attributes;
        this.solutionSize=solutionSize;
        this.numThreads=numThreads;
    }
    @Override
    public Collection<Solution> nextRandomSolutions(int num) {
        List<Solution> allSolutions = Collections.synchronizedList(new ArrayList<>(solutionSize));
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        for(int n = 0; n < num; n++) {
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    List<Map.Entry<String, Double>> companyScores = new ArrayList<>(solutionSize);
                    for(int i = 0; i < solutionSize; i++) {
                        String randomAssignee = Database.getRandomAssignee();
                        companyScores.add(Maps.immutableEntry(randomAssignee,CompanySolution.getScoreFromCompanyAndAttrs(randomAssignee,attributes)));
                    }
                    Solution solution = new CompanySolution(companyScores,attributes);
                    allSolutions.add(solution);
                }
            };
            pool.execute(action);
        }
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        return allSolutions;
    }
}
