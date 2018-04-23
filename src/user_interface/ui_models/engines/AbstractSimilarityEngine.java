package user_interface.ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import models.similarity_models.DefaultSimilarityModel;
import org.elasticsearch.index.query.QueryBuilder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.server.SimilarPatentServer;
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
    protected Collection<String> inputs;
    @Getter
    protected INDArray avg;
    protected static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected Function<Collection<String>,INDArray> inputsToAvgVectorFunction;
    public AbstractSimilarityEngine(Function<Collection<String>,INDArray> inputsToAvgVectorFunction) {
        super(Collections.emptyList());
        this.inputsToAvgVectorFunction=inputsToAvgVectorFunction;
    }

    private static Function<Collection<String>,INDArray> oldFunction = inputs -> {
         DefaultSimilarityModel finder = new DefaultSimilarityModel(inputs);
         if (finder.getItemList().length > 0) {
             return finder.computeAvg();
         } else return null;
    };

    private static Function<Collection<String>,INDArray> newFunction(String tableName, String fieldName) {
        return inputs -> {
            Map<String,INDArray> vecMap = Database.loadVectorsFor(tableName,fieldName,new ArrayList<>(inputs));
            if (vecMap.size() > 0) {
                return Nd4j.vstack(vecMap.values()).mean(0);
            } else return null;
        };
    }

    @Deprecated
    public AbstractSimilarityEngine() {
        this(oldFunction);
    }

    public AbstractSimilarityEngine(String tableName, String fieldName) {
        this(newFunction(tableName,fieldName));
    }

    protected abstract Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes);

    public void extractRelevantInformationFromParams(Request req) {
        List<String> resultTypes = SimilarPatentServer.extractArray(req,  Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        inputs = getInputsToSearchFor(req,resultTypes);
        if(inputs!=null&&inputs.size()>0) {
            avg = inputsToAvgVectorFunction.apply(inputs);
        }
    }

    public abstract String getId();

    @Override
    public Collection<AbstractFilter> createFilters() {
        return Arrays.asList(new AbstractFilter(this, AbstractFilter.FilterType.Include) {
            @Override
            public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
                return AbstractSimilarityEngine.this.getOptionsTag(userRoleFunction);
            }

            @Override
            public String getId() {
                return AbstractSimilarityEngine.this.getId();
            }

            @Override
            public String getOptionGroup() {
                return Constants.SIMILARITY_FAST;
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
