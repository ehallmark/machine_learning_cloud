package ui_models.attributes.classification.broad_technology;

import genetics.Listener;
import genetics.Solution;
import ui_models.attributes.classification.genetics.TechTaggerSolution;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 5/24/2017.
 */
public class BroadTechnologySolutionListener implements Listener{

    @Override
    public void print(Solution _solution) {
        BroadTechnologySolution solution = (BroadTechnologySolution)_solution;
        Map<String,String> techMap = solution.getBroadTechMap();
        Set<String> broadSet = new HashSet<>();
        broadSet.addAll(techMap.values());
        System.out.println("---------------------------");
        System.out.println("Score: "+solution.fitness());
        System.out.println("    Tech Map Size: "+techMap.size());
        System.out.println("    Num broad technologies: "+broadSet.size());
        System.out.println("---------------------------");
    }
}
