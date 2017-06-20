package ui_models.engines;

import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import spark.Request;
import ui_models.attributes.classification.SimilarityGatherTechTagger;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngine {
    private AbstractSimilarityModel firstFinder;

    private AbstractSimilarityModel secondFinder;
    @Getter
    private PortfolioList portfolioList;
    private Collection<AbstractFilter> preFilters;

    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Collecting inputs to search in...");
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        preFilters = preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).collect(Collectors.toList());
        String searchType = SimilarPatentServer.extractString(req, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);
        List<String> technologies = SimilarPatentServer.extractArray(req, SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD);

        // Get scope of search
        Collection<String> inputsToSearchIn;
        if (portfolioType.equals(PortfolioList.Type.patents)) {
            inputsToSearchIn = new HashSet<>(preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]"));
            new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]")).forEach(assignee -> inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
        } else {
            inputsToSearchIn = new HashSet<>(preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
        }

        // Check whether to search entire database
        boolean searchEntireDatabase = inputsToSearchIn.isEmpty();

        String similarityModel = extractString(req, SIMILARITY_MODEL_FIELD, Constants.PARAGRAPH_VECTOR_MODEL);
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = new HashSet<>();
        inputsToSearchFor.addAll(preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]"));
        preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]").forEach(assignee -> {
                inputsToSearchFor.addAll(Database.possibleNamesForAssignee(assignee));
        });
        inputsToSearchFor.addAll(technologies.stream().filter(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().containsKey(technology)).flatMap(technology-> SimilarityGatherTechTagger.getParagraphVectorModel().getNameToInputMap().get(technology).stream()).collect(Collectors.toSet()));


        System.out.println(" ... Similarity model");
        // Get similarity model
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModel + "_" + portfolioType.toString());
        firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(inputsToSearchIn);
        secondFinder = finderPrototype.duplicateWithScope(inputsToSearchFor);
        if (firstFinder == null || firstFinder.numItems() == 0) {
            throw new RuntimeException("Unable to find any results to search in.");
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        if (secondFinder == null || secondFinder.numItems() == 0 || !comparator.equals(Constants.SIMILARITY)) {
            portfolioList = new PortfolioList(firstFinder.getTokens().stream().map(token -> new Item(token)).collect(Collectors.toList()));
        } else {
            int limit = extractInt(req, LIMIT_FIELD, 10);
            // run limit
            portfolioList = runPatentFinderModel(firstFinder, secondFinder, limit, preFilters);
        }

    }

    public PortfolioList runModel(PortfolioList portfolioList, int limit) {
        return runPatentFinderModel(firstFinder.duplicateWithScope(portfolioList.getTokens()), secondFinder, limit, preFilters);
    }

}
