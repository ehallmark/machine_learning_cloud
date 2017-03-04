package analysis.tech_tagger;

import analysis.SimilarPatentFinder;
import analysis.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
import tools.PortfolioList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 3/4/2017.
 */
public class SimilarityTechTagger extends TechTagger {
    List<INDArray> vectors;
    List<String> names;
    WeightLookupTable<VocabWord> lookupTable;
    private static final SimilarityTechTagger gatherTagger;
    static {
        try {
            SimilarPatentServer.loadLookupTable();
        } catch(Exception e) {
            e.printStackTrace();
        }
        gatherTagger = new SimilarityTechTagger(Database.getGatherTechMap(),SimilarPatentServer.getLookupTable());
    }

    public static SimilarityTechTagger getGatherTagger() {
        return gatherTagger;
    }

    private SimilarityTechTagger(Map<String,Collection<String>> nameToInputMap, WeightLookupTable<VocabWord> lookupTable) {
            this.vectors = new ArrayList<>(nameToInputMap.size());
            this.names = new ArrayList<>(nameToInputMap.size());
            this.lookupTable = lookupTable;
            nameToInputMap.forEach((name,inputs)->{
                List<INDArray> vecs = inputs.stream().map(input->lookupTable.vector(input)).filter(input->input!=null).collect(Collectors.toList());
                if(vecs.size()>0) {
                    vectors.add(Nd4j.vstack(vecs).mean(0));
                    names.add(name);
                }
            });
    }

    @Override
    public double getTechnologyValueFor(String item, String technology) {
        int idx = names.indexOf(technology);
        if(idx>=0) {
            INDArray vec = vectors.get(idx);
            INDArray vec2 = lookupTable.vector(item);
            if(vec2!=null) {
                return 3.0+(2.0*Transforms.cosineSim(vec,vec2));
            }
        }
        return 1d;
    }

    private List<Pair<String,Double>> technologiesFor(INDArray vec, int n) {
        if(vec==null)return new ArrayList<>();
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        for(int i = 0; i < vectors.size(); i++) {
            String name = names.get(i);
            INDArray tech = vectors.get(i);
            heap.add(new WordFrequencyPair<>(name,Transforms.cosineSim(tech,vec)));
        }
        List<Pair<String,Double>> data = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Double> pair = heap.remove();
            data.add(0,new Pair<>(pair.getFirst(),pair.getSecond()));
        }
        return data;
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return technologiesFor(lookupTable.vector(item),n);
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        List<INDArray> vecs = items.stream().map(input->lookupTable.vector(input)).filter(input->input!=null).collect(Collectors.toList());
        if(vecs.size()>0) {
            return technologiesFor(Nd4j.vstack(vecs).mean(0),n);
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return new ArrayList<>(names);
    }
}
