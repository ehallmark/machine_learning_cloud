package analysis.genetics.lead_development;

import analysis.genetics.Solution;
import analysis.genetics.keyword_analysis.KeywordSolution;
import analysis.genetics.keyword_analysis.ProbabilityHelper;
import analysis.genetics.keyword_analysis.Word;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/25/2017.
 */
public class CompanySolution implements Solution {
    private double fitness;
    private List<Map.Entry<String,Double>>  companyScores;
    private List<Attribute> attributes;

    public CompanySolution( List<Map.Entry<String,Double>>  companyScores, List<Attribute> attributes) {
        this.companyScores=companyScores;
        this.attributes=attributes;
        fitness=companyScores.stream().collect(Collectors.averagingDouble(e->e.getValue()));
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        //alreadyCalculated
    }

    @Override
    public Solution mutate() {
        List<Map.Entry<String,Double>> newScores = new ArrayList<>(companyScores);
        int rand = ProbabilityHelper.getHighNumberWithMaxUpTo(newScores.size());
        newScores.remove(rand);

        String randomAssignee = Database.getRandomAssignee();
        double score = getScoreFromCompanyAndAttrs(randomAssignee,attributes);
        newScores.add(Maps.immutableEntry(randomAssignee,score));
        Collections.sort(newScores,(e1,e2)->(e2.getValue().compareTo(e1.getValue())));
        return new CompanySolution(newScores,attributes);
    }

    @Override
    public Solution crossover(Solution other) {
        return null;
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness);
    }

    public static double getScoreFromCompanyAndAttrs(String company, List<Attribute> attributes) {
         AtomicDouble score = new AtomicDouble(0d);
         AtomicDouble denom = new AtomicDouble(0d);
         attributes.forEach(attr->{
             if(attr.importance>0) {
                 score.addAndGet(attr.scoreAssignee(company)*attr.importance);
                 denom.addAndGet(attr.importance);
             }
         });
         if(denom.get()>0d) {
             return score.get()/denom.get();
         } else {
             return 0d;
         }
    }
}
