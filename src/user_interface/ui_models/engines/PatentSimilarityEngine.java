package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.AbstractSimilarityModel;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class PatentSimilarityEngine extends AbstractSimilarityEngine {

    public PatentSimilarityEngine(AbstractSimilarityModel model) {
        super(model);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
        for(String resultType : resultTypes) {
            PortfolioList.Type searchType = PortfolioList.Type.valueOf(resultType);
            if (searchType.equals(PortfolioList.Type.applications)) {
                inputsToSearchFor.addAll(patents.stream().filter(patent -> Database.isApplication(patent)).collect(Collectors.toList()));
            } else if (searchType.equals(PortfolioList.Type.patents)) {
                inputsToSearchFor.addAll(patents.stream().filter(patent -> !Database.isApplication(patent)).collect(Collectors.toList()));
            } else {
                inputsToSearchFor.addAll(patents);
            }
        }
        return inputsToSearchFor;
    }


    @Override
    public String getName() {
        return Constants.PATENT_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent or application per line (eg. 800000)").withName(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD)
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }
}
