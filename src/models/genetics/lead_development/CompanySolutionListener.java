package models.genetics.lead_development;

import models.genetics.Listener;
import models.genetics.Solution;

import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 2/23/17.
 */
public class CompanySolutionListener implements Listener {
    @Override
    public void print(Solution _solution) {
        CompanySolution solution = (CompanySolution)_solution;
        List<Map.Entry<String,Double>> scores = solution.getCompanyScores();
        System.out.println("Top 15 companies: ");
        scores.stream().limit(15).forEach(entry->{
            System.out.println("    ["+entry.getValue()+"] "+entry.getKey());
        });
    }
}
