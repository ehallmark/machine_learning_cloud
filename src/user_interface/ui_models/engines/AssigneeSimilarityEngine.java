package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.HashSet;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine {

    public AssigneeSimilarityEngine() {
        super(Constants.ASSIGNEE_SIMILARITY);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, PortfolioList.Type searchType) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
        if(searchType.equals(PortfolioList.Type.patents)) {
            assignees.forEach(assignee -> {
                inputsToSearchFor.addAll(Database.selectPatentNumbersFromAssignee(assignee));
            });
        } else {
            assignees.forEach(assignee -> {
                inputsToSearchFor.addAll(Database.possibleNamesForAssignee(assignee));
            });
        }
        return inputsToSearchFor;
    }


    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 assignee per line").withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD)
        );
    }
}
