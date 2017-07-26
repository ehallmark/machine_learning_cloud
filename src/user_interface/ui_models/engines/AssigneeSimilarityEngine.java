package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.AbstractSimilarityModel;
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

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SIMILARITY;
    }

    public AssigneeSimilarityEngine(AbstractSimilarityModel model) {
        super(model);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
        for(String resultType : resultTypes) {
            PortfolioList.Type searchType = PortfolioList.Type.valueOf(resultType);
            if (searchType.equals(PortfolioList.Type.patents)) {
                assignees.forEach(assignee -> {
                    inputsToSearchFor.addAll(Database.selectPatentNumbersFromAssignee(assignee));
                });
            } else if (searchType.equals(PortfolioList.Type.applications)) {
                assignees.forEach(assignee -> {
                    inputsToSearchFor.addAll(Database.selectApplicationNumbersFromAssignee(assignee));
                });
            } else {
                assignees.forEach(assignee -> {
                    inputsToSearchFor.addAll(Database.possibleNamesForAssignee(assignee));
                });
            }
        }
        System.out.println("Found assignees to search for.");
        return inputsToSearchFor;
    }


    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 assignee per line").withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD)
        );
    }
}
