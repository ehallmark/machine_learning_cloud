package analysis.tech_tagger;

import analysis.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import tools.MinHeap;
import tools.PortfolioList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechTaggerNormalizer extends TechTagger {
    private List<TechTagger> taggers;
    private static TechTagger tagger = new TechTaggerNormalizer(Arrays.asList(new CPCTagger(),SimilarityTechTagger.getAIModelTagger()),Arrays.asList(0.1,1.0));

    public TechTaggerNormalizer(List<TechTagger> taggers, List<Double> weights) {
        this.taggers=taggers;
        for(int i = 0; i < taggers.size(); i++) {
            taggers.get(i).setWeight(weights.get(i));
        }
    }

    @Override
    public int size() {
        return 0;
    }

    public static TechTagger getDefaultTechTagger() {
        return tagger;
    }
    @Override
    public double getTechnologyValueFor(String item, String technology) {
        return taggers.stream().collect(Collectors.averagingDouble(tagger->tagger.getTechnologyValueFor(item,technology)*tagger.getWeight()));
    }


    @Override
    public List<Pair<String, Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return technologyHelper(item,type,n);
    }

    private List<Pair<String,Double>> technologyHelper(Object item, PortfolioList.Type type, int n) {
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Double> technologyScores = new HashMap<>();
        taggers.forEach(tagger->{
            int i = cnt.getAndIncrement();
            List<Pair<String,Double>> data;
            if(item instanceof String) {
                 data = tagger.getTechnologiesFor((String)item,type,tagger.size());
            } else {
                data = tagger.getTechnologiesFor((Collection<String>)item,type,tagger.size());
            }
            if(data!=null&&data.size()>=10) {
                // normalize data
                double mean = data.stream().map(pair->pair.getSecond()).collect(Collectors.averagingDouble(d->d));
                double stddev = Math.sqrt(data.stream().map(pair->pair.getSecond()).collect(Collectors.summingDouble(d->Math.pow(d-mean,2.0)))/(data.size()-1));
                if(stddev>0) {
                    for(Pair<String,Double> pair : data) {
                        double val = ((pair.getSecond() - mean) / stddev) * tagger.getWeight();
                        if(val>0) {
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
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        return technologyHelper(items,type,n);
    }

    @Override
    public Collection<String> getAllTechnologies() {
        Set<String> toReturn = new HashSet<>();
        taggers.forEach(tagger->toReturn.addAll(tagger.getAllTechnologies()));
        return toReturn;
    }
}
