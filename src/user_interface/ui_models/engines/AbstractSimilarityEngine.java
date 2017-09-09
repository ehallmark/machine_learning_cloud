package user_interface.ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;

import org.elasticsearch.index.query.QueryBuilder;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;


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


    @Override
    public Collection<AbstractFilter> createFilters() {
        return Arrays.asList(new AbstractFilter(this, AbstractFilter.FilterType.Include) {
            @Override
            public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
                return AbstractSimilarityEngine.this.getOptionsTag(userRoleFunction);
            }

            @Override
            public String getOptionGroup() {
                return Constants.SIMILARITY;
            }
            @Override
            public String getFullPrerequisite() {
                return getOptionGroup();
            }
            @Override
            public String getPrerequisite() {
                return getOptionGroup();
            }

            @Override
            public String getName() {
                return AbstractSimilarityEngine.this.getName();
            }

            @Override
            public void extractRelevantInformationFromParams(Request params) {}

            @Override
            public QueryBuilder getFilterQuery() {
                return null;
            }

            @Override
            protected String transformAttributeScript(String attributeScript) {
                return null;
            }

            @Override
            public AbstractFilter dup() {
                return this;
            }
        });
    }


    @Override
    public String getType() {
        throw new UnsupportedOperationException("getType unsupported on similarity engines");
    }
}
