package ui_models.attributes.classification;

import similarity_models.paragraph_vectors.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import tools.MinHeap;
import ui_models.attributes.classification.genetics.TechTaggerSolutionCreator;
import ui_models.portfolios.AbstractPortfolio;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechTaggerNormalizer implements ClassificationAttr {
    private List<Pair<ClassificationAttr,Double>> taggerPairs;

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        throw new UnsupportedOperationException("Model not trainable");
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException("Model not saveable");
    }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        throw new UnsupportedOperationException("Model not trainable");
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        // Do nothing
        throw new UnsupportedOperationException("Model not trainable");
    }

    private static ClassificationAttr tagger = new TechTaggerNormalizer(TechTaggerSolutionCreator.getTaggers(),TechTaggerSolutionCreator.getWeights());

    public TechTaggerNormalizer(List<ClassificationAttr> taggers, List<Double> weights) {
        if(taggers.size()!=weights.size()) throw new RuntimeException("Illegal arguments in techtaggernormalizer");
        this.taggerPairs=new ArrayList<>(taggers.size());
        for(int i = 0; i < taggers.size(); i++) {
            this.taggerPairs.add(new Pair<>(taggers.get(i),weights.get(i)));
        }
    }

    public int numClassifications() {
        return taggerPairs.stream().min(Comparator.comparingInt(t->t.getFirst().numClassifications())).get().getFirst().numClassifications();
    }

    public Collection<String> getClassifications() { return taggerPairs.stream().min(Comparator.comparingInt(t->t.getFirst().numClassifications())).get().getFirst().getClassifications(); }

    public static ClassificationAttr getDefaultTechTagger() {
        return tagger;
    }

    private List<Pair<String,Double>> technologyHelper(AbstractPortfolio portfolio, int n) {
        Map<String,Double> technologyScores = new HashMap<>();
        taggerPairs.forEach(taggerPair->{
            ClassificationAttr tagger = taggerPair.getFirst();
            double weight = taggerPair.getSecond();
            List<Pair<String, Double>> data = tagger.attributesFor(portfolio,n);
            if (data != null && data.size() >= 1) {
                for (Pair<String, Double> pair : data) {
                    double val = pair.getSecond() * weight;
                    if (technologyScores.containsKey(pair.getFirst())) {
                        technologyScores.put(pair.getFirst(), Math.max(technologyScores.get(pair.getFirst()), val));
                    } else {
                        technologyScores.put(pair.getFirst(), val);
                    }
                }
            }
        });
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        technologyScores.entrySet().forEach(e->{
            String name = e.getKey();
            double val = e.getValue();
            heap.add(new WordFrequencyPair<>(name, val));
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
