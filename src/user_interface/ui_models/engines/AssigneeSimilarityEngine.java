package user_interface.ui_models.engines;

import j2html.tags.Tag;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine {
    private static Function<Collection<String>,INDArray> newAssigneeFunction(String tableName, String attrName, String vecName) {
        return inputs -> {
            int inputSize = inputs.size();
            Map<String,INDArray> vecMap = Database.loadAssigneeVectorsFor(tableName,attrName,vecName,new ArrayList<>(inputs));
            if(inputSize>0 && vecMap.isEmpty()) {
                throw new RuntimeException("Unable to compute similarity for assignees: "+String.join("; ", inputs));
            }
            if (vecMap.size() > 0) {
                return Nd4j.vstack(vecMap.values()).mean(0);
            } else return null;
        };
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SIMILARITY;
    }

    public AssigneeSimilarityEngine(String tableName) {
        super(newAssigneeFunction(tableName, "name", Attributes.ENC));
        this.tableName=tableName;
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        List<String> inputsToSearchFor = preProcess(extractString(req, getId(), ""), "\\n", "[^0-9A-Za-z ]");
        System.out.println("Found "+inputsToSearchFor.size()+" assignees to search for: "+String.join("; ", inputsToSearchFor));
        return inputsToSearchFor;
    }

    @Override
    public String getId() {
        return ASSIGNEES_TO_SEARCH_FOR_FIELD;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 assignee name per line.").withId(getId()).withName(getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new AssigneeSimilarityEngine(tableName);
    }
}
