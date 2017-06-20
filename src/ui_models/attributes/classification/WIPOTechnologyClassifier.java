package ui_models.attributes.classification;

import classification_models.WIPOHelper;
import j2html.tags.Tag;
import lombok.Getter;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import similarity_models.class_vectors.CPCSimilarityFinder;
import similarity_models.class_vectors.WIPOSimilarityFinder;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import tools.MinHeap;
import ui_models.portfolios.attributes.WIPOClassificationAttribute;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 3/4/2017.
 */
public class WIPOTechnologyClassifier implements ClassificationAttr {
    private static WIPOClassificationAttribute attribute = new WIPOClassificationAttribute();

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    public String getName() {
        return "WIPO Technology Tagger Model";
    }

    @Override
    public void save() {
        // do nothing
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {

    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        return this;
    }

    public int numClassifications() {
        return WIPOHelper.getDefinitionMap().size();
    }

    public Collection<String> getClassifications() { return new ArrayList<>(WIPOHelper.getOrderedClassifications()); }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new WIPOTechnologyClassifier();
    }


    private static List<Pair<String,Double>> wipoHelper(Collection<String> patents, int limit) {
        if(patents.isEmpty()) return Collections.emptyList();
        return patents.stream().map(patent->attribute.attributesFor(Arrays.asList(patent),limit))
                .filter(tech->tech!=null&&!tech.isEmpty()).collect(Collectors.groupingBy(tech->tech,Collectors.counting())).entrySet().stream()
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit).map(e->new Pair<>(e.getKey(),e.getValue().doubleValue()/patents.size())).collect(Collectors.toList());
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return wipoHelper(portfolio,n);
    }

}
