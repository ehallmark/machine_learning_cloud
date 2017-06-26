package ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.attributes.classification.SimilarityGatherTechTagger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine {

    public AssigneeSimilarityEngine() {
        super(Constants.ASSIGNEE_SIMILARITY);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]").forEach(patent->{
            inputsToSearchFor.addAll(Database.assigneesFor(patent));
        });
        preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> {
            inputsToSearchFor.addAll(Database.possibleNamesForAssignee(assignee));
        });
        return inputsToSearchFor;
    }


    @Override
    public Tag getOptionsTag() {
        return div().with(
                label("Assignees"),br(),
                textarea().withClass("form-control").withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD)
        );
    }
}
