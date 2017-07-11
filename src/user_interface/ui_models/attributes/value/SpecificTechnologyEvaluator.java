package user_interface.ui_models.attributes.value;

import org.deeplearning4j.berkeley.Pair;
import user_interface.ui_models.attributes.classification.ClassificationAttr;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/25/2017.
 */
public class SpecificTechnologyEvaluator extends ValueAttr {
    private ClassificationAttr tagger;
    private String technology;
    public static final String TECHNOLOGY_SUFFIX = " Value";
    public SpecificTechnologyEvaluator(String technology, ClassificationAttr tagger) {
        super(ValueMapNormalizer.DistributionType.Normal,technology+TECHNOLOGY_SUFFIX);
        this.tagger=tagger;
        this.technology=technology;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        //throw new RuntimeException("Model does not need to be loaded...");
        return new ArrayList<>();
    }

    @Override
    public Map<String,Double> getMap() {
        throw new RuntimeException("There is no model map...");
    }

    @Override
    public double evaluate(String token) {
        List<Pair<String,Double>> pairs = tagger.attributesFor(Arrays.asList(token),tagger.numClassifications()/2).stream().filter(p->technology.equals(p.getFirst())).collect(Collectors.toList());
        if(pairs.isEmpty()) return ValueMapNormalizer.DEFAULT_START;
        else return pairs.stream().collect(Collectors.averagingDouble(p->ValueMapNormalizer.DEFAULT_START+(ValueMapNormalizer.DEFAULT_END-ValueMapNormalizer.DEFAULT_START)*p.getSecond()));
    }
}
