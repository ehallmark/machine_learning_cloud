package analysis.tech_tagger;

import analysis.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
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
    private int[] sizes;
    private static TechTagger tagger = new TechTaggerNormalizer(Arrays.asList(new GatherTagger(),SimilarityTechTagger.getGatherTagger()),Arrays.asList(0.25,1.0));

    public TechTaggerNormalizer(List<TechTagger> taggers, List<Double> weights) {
        this.taggers=taggers;
        sizes=new int[taggers.size()];
        for(int i = 0; i < taggers.size(); i++) {
            taggers.get(i).setWeight(weights.get(i));
            sizes[i]=taggers.get(i).getAllTechnologies().size();
        }
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
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Double> technologyScores = new HashMap<>();
        taggers.forEach(tagger->{
            int i = cnt.getAndIncrement();
            List<Pair<String,Double>> data = tagger.getTechnologiesFor(item,type,sizes[i]);
            if(data!=null&&data.size()>=10) {
                // normalize data
                double mean = data.stream().map(pair->pair.getSecond()).collect(Collectors.averagingDouble(d->d));
                double stddev = Math.sqrt(data.stream().map(pair->pair.getSecond()).collect(Collectors.summingDouble(d->Math.pow(d-mean,2.0)))/(data.size()-1));
                if(stddev>0) {
                    data.forEach(pair -> {
                        double val = ((pair.getSecond() - mean) / stddev) * tagger.getWeight();
                        if(val>0) {
                            if (technologyScores.containsKey(pair.getFirst())) {
                                technologyScores.put(pair.getFirst(), Math.max(technologyScores.get(pair.getFirst()), val));
                            } else {
                                technologyScores.put(pair.getFirst(), val);
                            }
                        }
                    });
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
        throw new RuntimeException("Not implementend yet (getTechnologyValueFor)");
    }

    @Override
    public Collection<String> getAllTechnologies() {
        throw new RuntimeException("Not implementend yet (getAllTechnologies)");
    }
}
