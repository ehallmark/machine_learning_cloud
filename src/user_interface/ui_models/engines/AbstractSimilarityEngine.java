package user_interface.ui_models.engines;

import lombok.Getter;
import lombok.Setter;
import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;

import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.*;


/**
 * Created by ehallmark on 2/28/17.
 */
public abstract class AbstractSimilarityEngine extends AbstractAttribute implements DependentAttribute<AbstractSimilarityEngine> {
    @Setter
    protected AbstractSimilarityModel similarityModel;
    protected Collection<String> inputs;
    @Getter
    protected INDArray avg;
    protected static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

    public AbstractSimilarityEngine(AbstractSimilarityModel similarityModel) {
        super(Collections.emptyList());
        this.similarityModel=similarityModel;
    }

    protected abstract Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes);

    public void extractRelevantInformationFromParams(Request req) {
        List<String> resultTypes = SimilarPatentServer.extractArray(req,  Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        inputs = getInputsToSearchFor(req,resultTypes);
        if(inputs!=null&&inputs.size()>0) {
            SimilarPatentFinder finder = new SimilarPatentFinder(inputs);
            if (finder.getItemList().length > 0) {
                avg = finder.computeAvg();
            }
        }
    }


    public String getOptionGroup() {
        return Constants.SIMILARITY;
    }


    @Override
    public String getType() {
        throw new UnsupportedOperationException("getType unsupported on similarity engines");
    }
}
