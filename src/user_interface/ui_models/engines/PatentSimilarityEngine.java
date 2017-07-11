package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class PatentSimilarityEngine extends AbstractSimilarityEngine {

    public PatentSimilarityEngine() {
        super(Constants.PATENT_SIMILARITY);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, PortfolioList.Type searchType) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
        if(searchType.equals(PortfolioList.Type.patents)) {
            inputsToSearchFor.addAll(patents);
        } else {
            patents.forEach(patent->inputsToSearchFor.addAll(Database.assigneesFor(patent)));
        }
        return inputsToSearchFor;
    }


    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent per line (eg. 800000)").withName(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD)
        );
    }
}
