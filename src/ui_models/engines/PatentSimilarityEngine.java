package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import spark.Request;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.value.ValueMapNormalizer;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static server.SimilarPatentServer.*;

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
