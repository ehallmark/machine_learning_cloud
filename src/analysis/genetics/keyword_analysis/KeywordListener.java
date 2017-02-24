package analysis.genetics.keyword_analysis;

import analysis.genetics.Listener;
import analysis.genetics.Solution;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Created by ehallmark on 2/23/17.
 */
public class KeywordListener implements Listener {
    @Override
    public void print(Solution solution) {
        KeywordSolution keywordSolution = (KeywordSolution)solution;
        Map<String,List<Word>> map = keywordSolution.getTechnologyToWordsMap();
        map.keySet().stream().sorted().limit(25).forEach(tech->{
            System.out.println("Top keywords for "+tech+": "+String.join("; ",keywordSolution.topKeywordsFromTechnology(tech,5)));
        });
    }
}
