package similarity_models.class_vectors.vectorizer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/19/2017.
 */
public class ClassVectorizer {
    public Map<String,? extends Collection<String>> lookupTable;

    public ClassVectorizer(Map<String,? extends Collection<String>> lookupTable) {
        this.lookupTable=lookupTable;
    }

    public double[] classVectorForPatents(Collection<String> patents, List<String> classifications, int cpcDepth) {
        double[] vec = new double[classifications.size()];
        Arrays.fill(vec, 0d);
        Collection<String> thisCPC = getClassifications(patents,cpcDepth,false);
        thisCPC.forEach(cpc -> {
            int idx = classifications.indexOf(cpc);
            if (idx >= 0) {
                vec[idx] += 1d / thisCPC.size();
            }
        });
        return vec;
    }

    public Collection<String> patentToClassification(String patent) {
        if(lookupTable.containsKey(patent)) {
            return lookupTable.get(patent);
        } else {
            return Collections.emptyList();
        }
    }

    public List<String> getClassifications(Collection<String> patents, int classDepth, boolean distinct) {
        Stream<String> classStream = patents.stream().flatMap(p-> patentToClassification(p).stream().map(cpc->(classDepth > 0 ? cpc.substring(0,Math.min(classDepth,cpc.length())) : cpc).trim()));
        if(distinct) classStream = classStream.distinct();
        return classStream.collect(Collectors.toList());
    }
}
