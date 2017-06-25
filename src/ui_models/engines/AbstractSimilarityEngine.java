package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import spark.Request;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.value.ValueMapNormalizer;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.br;
import static j2html.TagCreator.label;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public abstract class AbstractSimilarityEngine extends ValueAttr {
    private AbstractSimilarityModel firstFinder;

    private AbstractSimilarityModel secondFinder;
    @Getter
    protected PortfolioList portfolioList;
    private Collection<AbstractFilter> preFilters;

    public AbstractSimilarityEngine(String name) {
        super(ValueMapNormalizer.DistributionType.None, name);
    }

    protected void setPortolioList(Request req, Collection<String> inputsToSearchFor, Collection<String> inputsToSearchIn) {
        // Check whether to search entire database
        boolean searchEntireDatabase = inputsToSearchIn.isEmpty();

        String similarityModel = extractString(req, SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);

        String searchType = SimilarPatentServer.extractString(req, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);

        System.out.println(" ... Similarity model");
        // Get similarity model
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModel + "_" + portfolioType.toString());
        firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(SimilarPatentServer.findItemsByName(inputsToSearchIn));
        secondFinder = finderPrototype.duplicateWithScope(SimilarPatentServer.findItemsByName(inputsToSearchFor));
        if (firstFinder == null || firstFinder.numItems() == 0) {
            throw new RuntimeException("Unable to find any results to search in.");
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        if (secondFinder == null || secondFinder.numItems() == 0 || !comparator.equals(Constants.SIMILARITY)) {
            portfolioList = new PortfolioList(firstFinder.getItemList());
            try {
                portfolioList.applyFilters(preFilters);
            } catch(Exception e) {
                throw new RuntimeException("Error on filters: "+e.getMessage());
            }
        } else {
            int limit = extractInt(req, LIMIT_FIELD, 10);
            // run limit
            portfolioList = runPatentFinderModel(firstFinder, secondFinder, limit, preFilters);
        }
    }

    protected abstract Collection<String> getInputsToSearchFor(Request req);

    protected Collection<String> getInputsToSearchIn(Request req) {
        String searchType = SimilarPatentServer.extractString(req, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);

        // Get scope of search
        Collection<String> inputsToSearchIn = new HashSet<>();
        if(portfolioType.equals(PortfolioList.Type.patents)) {
            inputsToSearchIn.addAll(preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]"));
            preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
        } else {
            preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]").forEach(patent->inputsToSearchIn.addAll(Database.assigneesFor(patent)));
            preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> inputsToSearchIn.addAll(Database.possibleNamesForAssignee(assignee)));

        }
        return inputsToSearchIn;
    };


    public void setPrefilters(Request req) {
        System.out.println("Collecting inputs to search in...");
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        preFilters = preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).collect(Collectors.toList());
    }

    public void extractRelevantInformationFromParams(Request req) {
        setPrefilters(req);
        Collection<String> toSearchFor = getInputsToSearchFor(req);
        Collection<String> toSearchIn = getInputsToSearchIn(req);
        setPortolioList(req,toSearchFor,toSearchIn);
    }

    @Override
    public double evaluate(String item) {
        if(secondFinder==null||secondFinder.numItems()==0) return ValueMapNormalizer.DEFAULT_START;
        return secondFinder.similarityTo(item) * 100d;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return Collections.emptyList();
    }

    @Override
    public Double attributesFor(Collection<String> portfolio, int limit) {
        return evaluate(portfolio.stream().findAny().get());
    }

}
