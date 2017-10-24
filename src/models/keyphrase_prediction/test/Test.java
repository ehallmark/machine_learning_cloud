package models.keyphrase_prediction.test;

import cpc_normalization.CPCHierarchy;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import seeding.Database;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/17.
 */
public class Test {
    public static void main(String[] args) {
        // test cpcs
        Collection<String> allCPCs = Database.getClassCodes();

        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();

        System.out.println("All CPCS: "+allCPCs.size());
        System.out.println("All Titles: "+cpcToTitleMap.size());



        Collection<String> missingTitles = allCPCs.parallelStream()
                .filter(cpc->cpcToTitleMap.containsKey(cpc))
                .collect(Collectors.toList());

        missingTitles.stream().sorted().forEach(cpc->{
            System.out.println("CPC Missing: "+cpc);
        });

        System.out.println("Missing titles: "+missingTitles.size()+ " / "+allCPCs.size());

    }
}
