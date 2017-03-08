package analysis.genetics.lead_development;

import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.Solution;
import analysis.genetics.keyword_analysis.ProbabilityHelper;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;
import value_estimation.PortfolioSizeEvaluator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/25/2017.
 */
public class  CompanySolution implements Solution {
    private double fitness;
    private List<Map.Entry<String,Double>>  companyScores;
    private List<Attribute> attributes;
    private Set<String> companyNames;

    public CompanySolution( List<Map.Entry<String,Double>>  companyScores, List<Attribute> attributes) {
        this(companyScores,attributes,new HashSet<>());
    }

    private CompanySolution(List<Map.Entry<String,Double>>  companyScores, List<Attribute> attributes, Set<String> companyNames) {
        this.companyScores=companyScores;
        this.attributes=attributes;
        this.companyNames=companyNames;
        fitness=companyScores.stream().collect(Collectors.averagingDouble(e->e.getValue()));
    }

    public List<Map.Entry<String,Double>> getCompanyScores() { return companyScores; }

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

        for(int i = 0; i < Math.max(1,companyScores.size()/10); i++) {
            int rand = ProbabilityHelper.getHighNumberWithMaxUpTo(newScores.size());
            newScores.remove(rand);
        }

        fillAssigneesBack(newScores,this.companyNames);

        Collections.sort(newScores,(e1,e2)->(e2.getValue().compareTo(e1.getValue())));
        return new CompanySolution(newScores,attributes,companyNames);
    }

    private void fillAssigneesBack(List<Map.Entry<String,Double>> newScores, Set<String> alreadyIncludedSet) {
        while(newScores.size()<companyScores.size()) {
            String randomAssignee = Database.getRandomAssignee();
            if(!alreadyIncludedSet.contains(randomAssignee)) {
                alreadyIncludedSet.add(randomAssignee);
                double score = getScoreFromCompanyAndAttrs(randomAssignee,attributes);
                newScores.add(Maps.immutableEntry(randomAssignee,score));
            }
        }
    }
    private void addToMap(List<Map.Entry<String,Double>> list, List<Map.Entry<String,Double>> newScores, Set<String> alreadyIncludedSet) {
        Set<Integer> alreadyAddedMap = new HashSet<>();
        for(int i = 0; i < list.size()/2; i++) {
            int num = ProbabilityHelper.getLowNumberWithMaxUpTo(list.size());
            if(!alreadyAddedMap.contains(new Integer(num))) {
                alreadyAddedMap.add(new Integer(num));
                Map.Entry<String,Double> entry = list.get(num);
                if(!alreadyIncludedSet.contains(entry.getKey())) {
                    alreadyIncludedSet.add(entry.getKey());
                    newScores.add(entry);
                }
            }
        }
    }

    @Override
    public Solution crossover(Solution _other) {
        CompanySolution other = (CompanySolution)_other;
        List<Map.Entry<String,Double>> newScores = new ArrayList<>(companyScores.size());
        addToMap(companyScores,newScores,companyNames);
        addToMap(other.companyScores,newScores,companyNames);
        fillAssigneesBack(newScores,companyNames);
        Collections.sort(newScores,(e1,e2)->(e2.getValue().compareTo(e1.getValue())));
        return new CompanySolution(newScores,attributes,companyNames);
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

    public static void main(String[] args) {
        // test
        Attribute attr = new ValueAttribute("Portfolio Size",1,new PortfolioSizeEvaluator());
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new CompanySolutionCreator(Arrays.asList(attr),10,10),10,new CompanySolutionListener(),10);
        algorithm.simulate(10000,0.5,0.5);
    }
}
