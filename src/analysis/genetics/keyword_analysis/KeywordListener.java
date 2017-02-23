package analysis.genetics.keyword_analysis;

import analysis.genetics.Listener;
import analysis.genetics.Solution;

import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 2/23/17.
 */
public class KeywordListener implements Listener {
    @Override
    public void print(Solution solution) {
        KeywordSolution keywordSolution = (KeywordSolution)solution;
        Map<String,Set<String>> map = keywordSolution.getTechnologyToWordsMap();
        map.keySet().stream().sorted().limit(20).forEach(tech->{
            System.out.println("Top keywords for "+tech+": "+String.join("; ",keywordSolution.topKeywordsFromTechnology(tech,3)));
        });
    }
}
