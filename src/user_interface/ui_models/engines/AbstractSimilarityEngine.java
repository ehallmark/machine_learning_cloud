package user_interface.ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import org.elasticsearch.index.query.QueryBuilder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
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
    protected String tableName;
    protected Function<Collection<String>,INDArray> inputsToAvgVectorFunction;
    public AbstractSimilarityEngine(Function<Collection<String>,INDArray> inputsToAvgVectorFunction) {
        super(Collections.emptyList());
        this.inputsToAvgVectorFunction=inputsToAvgVectorFunction;
    }

    private static Function<Collection<String>,INDArray> newFunction(String tableName, String attrName, String vecName, boolean join) {
        return inputs -> {
            int sizeInputs = inputs.size();
            Map<String,INDArray> vecMap = Database.loadVectorsFor(tableName,attrName,vecName,new ArrayList<>(inputs),join);
            if(sizeInputs>0 && vecMap.size() == 0) {
                throw new RuntimeException("Unable to compute similarity of: "+String.join("; ", inputs));
            }
            if (vecMap.size() > 0) {
                return Nd4j.vstack(vecMap.values()).mean(0);
            } else return null;
        };
    }

    public AbstractSimilarityEngine(String tableName, String attrName, String vecName, boolean join) {
        this(newFunction(tableName,attrName,vecName,join));
        this.tableName=tableName;
    }

    protected abstract Collection<String> getInputsToSearchFor(Request req);

    public void extractRelevantInformationFromParams(Request req) {
        inputs = getInputsToSearchFor(req);
        if(inputs!=null&&inputs.size()>0) {
            avg = inputsToAvgVectorFunction.apply(inputs);
        }
    }

    public abstract String getId();

    @Override
    public Collection<AbstractFilter> createFilters() {
        return Arrays.asList(new AbstractFilter(this, AbstractFilter.FilterType.Include) {
            @Override
            public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
                return AbstractSimilarityEngine.this.getOptionsTag(userRoleFunction,loadChildren,null);
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
