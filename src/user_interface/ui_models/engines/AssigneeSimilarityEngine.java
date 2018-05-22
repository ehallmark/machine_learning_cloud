package user_interface.ui_models.engines;

import j2html.tags.Tag;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD;
import static user_interface.server.SimilarPatentServer.extractArray;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine {
    private static Function<Collection<String>,INDArray> newAssigneeFunction(String tableName, String attrName, String vecName, boolean join) {
        return inputs -> {
            Map<String,INDArray> vecMap = Database.loadAssigneeVectorsFor(tableName,attrName,vecName,new ArrayList<>(inputs),join);
            if (vecMap.size() > 0) {
                return Nd4j.vstack(vecMap.values()).mean(0);
            } else return null;
        };
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SIMILARITY;
    }

    @Deprecated
    public AssigneeSimilarityEngine() {
        super();
    }

    public AssigneeSimilarityEngine(String tableName) {
        super(newAssigneeFunction(tableName, "name", "cpc_vae", false),true);
        this.tableName=tableName;
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = extractArray(req, ASSIGNEES_TO_SEARCH_FOR_FIELD);

        System.out.println("Found "+inputsToSearchFor.size()+" assignees to search for.");
        return inputsToSearchFor;
    }

    @Override
    public String getId() {
        return ASSIGNEES_TO_SEARCH_FOR_FIELD;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
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
        if(isBigQuery) {
            return new AssigneeSimilarityEngine(tableName);
        } else {
            return new AssigneeSimilarityEngine();
        }
    }
}
