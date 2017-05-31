package ui_models.attributes.classification;

import model_testing.SplitModelData;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
import ui_models.portfolios.AbstractPortfolio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 3/4/2017.
 */
public class SimilarityGatherTechTagger implements ClassificationAttr {
    private List<INDArray> vectors;
    private List<String> names;
    private WeightLookupTable<VocabWord> lookupTable;
    private Map<String,Collection<String>> nameToInputMap;
    private static final SimilarityGatherTechTagger gatherTagger;
    static {
        gatherTagger = new SimilarityGatherTechTagger(SplitModelData.getBroadDataMap(SplitModelData.trainFile),SimilarPatentServer.getLookupTable());
    }

    public static SimilarityGatherTechTagger getAIModelTagger() {
        return gatherTagger;
    }

    public SimilarityGatherTechTagger(Map<String,Collection<String>> nameToInputMap, WeightLookupTable<VocabWord> lookupTable) {
        this.lookupTable = lookupTable;
        train(nameToInputMap);
    }

    @Override
    public void save() {
        // do nothing
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {
        this.nameToInputMap = trainingData;
        this.vectors = new ArrayList<>(trainingData.size());
        this.names = new ArrayList<>(trainingData.size());
        trainingData.forEach((name, inputs) -> {
            List<INDArray> vecs = inputs.stream().map(input -> lookupTable.vector(input)).filter(input -> input != null).collect(Collectors.toList());
            if (vecs.size() > 0) {
                vectors.add(Nd4j.vstack(vecs).mean(0));
                names.add(name);
            }
        });
    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        return this;
    }

    public int numClassifications() {
        return vectors.size();
    }

    public Collection<String> getClassifications() { return new ArrayList<>(names); }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new SimilarityGatherTechTagger(nameToInputMap,lookupTable);
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
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int n) {
        List<INDArray> vecs = portfolio.getTokens().stream().map(input->lookupTable.vector(input)).filter(input->input!=null).collect(Collectors.toList());
        if(vecs.size()>0) {
            return technologiesFor(Nd4j.vstack(vecs).mean(0),n);
        } else {
            return new ArrayList<>();
        }
    }

}
