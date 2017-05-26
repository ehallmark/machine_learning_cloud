package ui_models.attributes.classification;

import similarity_models.paragraph_vectors.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import tools.MinHeap;
import ui_models.portfolios.AbstractPortfolio;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechTaggerNormalizer extends ClassificationAttr {
    private List<ClassificationAttr> taggers;
    private static ClassificationAttr tagger = new TechTaggerNormalizer(Arrays.asList(CPCGatherTechTagger.get(), NaiveGatherClassifier.get(), SimilarityGatherTechTagger.getAIModelTagger(), GatherSVMClassifier.get()),Arrays.asList(0.1,0.5,0.3,0.1));

    public TechTaggerNormalizer(List<ClassificationAttr> taggers, List<Double> weights) {
        this.taggers=taggers;
        for(int i = 0; i < taggers.size(); i++) {
            taggers.get(i).setWeight(weights.get(i));
        }
    }

    public int numClassifications() {
        return taggers.stream().min(Comparator.comparingInt(t->t.numClassifications())).get().numClassifications();
    }

    public Collection<String> getClassifications() { return taggers.stream().min(Comparator.comparingInt(t->t.numClassifications())).get().getClassifications(); }

    public static ClassificationAttr getDefaultTechTagger() {
        return tagger;
    }

    private List<Pair<String,Double>> technologyHelper(AbstractPortfolio portfolio, int n) {
        Map<String,Double> technologyScores = new HashMap<>();
        taggers.forEach(tagger->{
            if(tagger.getWeight()>0) {
                List<Pair<String, Double>> data = tagger.attributesFor(portfolio,n);
                if (data != null && data.size() >= 10) {
                    // normalize data
                    double mean = data.stream().map(pair -> pair.getSecond()).collect(Collectors.averagingDouble(d -> d));
                    double stddev = Math.sqrt(data.stream().map(pair -> pair.getSecond()).collect(Collectors.summingDouble(d -> Math.pow(d - mean, 2.0))) / (data.size() - 1));
                    if (stddev > 0) {
                        for (Pair<String, Double> pair : data) {
                            double val = ((pair.getSecond() - mean) / stddev) * tagger.getWeight();
                            if (val > 0) {
                                if (technologyScores.containsKey(pair.getFirst())) {
                                    technologyScores.put(pair.getFirst(), Math.max(technologyScores.get(pair.getFirst()), val));
                                } else {
                                    technologyScores.put(pair.getFirst(), val);
                                }
                            } else {
                                break; // since everything is sorted
                            }
                        }
                    }
                }
            }
        });
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        technologyScores.entrySet().forEach(e->{
            String name = e.getKey();
            double val = e.getValue();
            if(val>0) {
                heap.add(new WordFrequencyPair<>(name, val));
            }
        });
        List<Pair<String,Double>> data = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Double> pair = heap.remove();
            data.add(0,new Pair<>(pair.getFirst(),pair.getSecond()));
        }
        return data;
    }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int n) {
        return technologyHelper(portfolio,n);
    }


}
