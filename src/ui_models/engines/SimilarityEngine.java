package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import spark.Request;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngine extends AbstractSimilarityEngine {
    private AbstractSimilarityModel firstFinder;
    private Collection<AbstractFilter> preFilters;
    @Getter
    protected PortfolioList portfolioList;

    @Getter
    private List<AbstractSimilarityEngine> engines;
    public SimilarityEngine(List<AbstractSimilarityEngine> engines) {
        super(Constants.SIMILARITY);
        this.engines=engines;
    }

    protected AbstractSimilarityModel setFirstFinder(AbstractSimilarityModel finderPrototype, Collection<String> inputsToSearchIn) {
        // Check whether to search entire database
        boolean searchEntireDatabase = inputsToSearchIn.isEmpty();

        System.out.println(" ... Similarity model");
        // Get similarity model
        firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(SimilarPatentServer.findItemsByName(inputsToSearchIn));
        if (firstFinder == null || firstFinder.numItems() == 0) {
            throw new RuntimeException("Unable to find any results to search in.");
        }
        return firstFinder;
    }

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

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        preFilters = preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).collect(Collectors.toList());
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        setPrefilters(req);
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(extractString(req,SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString()));
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(extractString(req,SIMILARITY_MODEL_FIELD,Constants.PARAGRAPH_VECTOR_MODEL)+"_"+portfolioType.toString());

        // first finder
        Collection<String> toSearchIn = getInputsToSearchIn(req);
        setFirstFinder(finderPrototype,toSearchIn);


        List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = engines.stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());

        // second finder
        relevantEngines.forEach(engine->engine.setSecondFinder(finderPrototype,engine.getInputsToSearchFor(req)));


        AtomicReference<PortfolioList> ref = new AtomicReference<>(new PortfolioList(new ArrayList<>()));
        if(!comparator.equals(Constants.SIMILARITY)) {
            // get similarity model
            portfolioList = new PortfolioList(firstFinder.getItemList());
            portfolioList.applyFilters(preFilters);
            portfolioList.init(comparator,limit);
            ref.set(portfolioList);
            firstFinder = firstFinder.duplicateWithScope(portfolioList.getItemList());
        }

        // add similarity filters
        preFilters = new ArrayList<>(preFilters);
        if(extractArray(req,POST_FILTER_ARRAY_FIELD).contains(Constants.SIMILARITY_THRESHOLD_FILTER)) {
            preFilters.add(postFilterModelMap.get(Constants.SIMILARITY_THRESHOLD_FILTER));
        }

        // run full similarity model
        relevantEngines.forEach(engine -> {
            PortfolioList newList = firstFinder.similarFromCandidateSet(engine.secondFinder, limit, preFilters);
            ref.set(newList.merge(ref.get(), comparator, limit));
        });
        portfolioList = ref.get();
        portfolioList.init(comparator,limit);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        throw new UnsupportedOperationException();
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
