package analysis.genetics.lead_development;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;
import com.google.common.collect.Maps;
import seeding.Database;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

/**
 * Created by Evan on 2/19/2017.
 */
public class CompanySolutionCreator implements SolutionCreator {
    private List<Attribute> attributes;
    private final int numThreads;
    private final int solutionSize;
    private final boolean removeJapanese;
    public CompanySolutionCreator(List<Attribute> attributes, boolean removeJapanese, int solutionSize, int numThreads) {
        this.attributes=attributes;
        this.removeJapanese=removeJapanese;
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
                        if(removeJapanese && Database.isJapaneseAssignee(randomAssignee)) {
                            i--;
                        } else {
                            //System.out.println("Random assignee: "+randomAssignee);
                            companyScores.add(Maps.immutableEntry(randomAssignee, CompanySolution.getScoreFromCompanyAndAttrs(randomAssignee, attributes)));
                        }
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
