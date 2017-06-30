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
    protected boolean searchEntireDatabase;
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
        System.out.println(" ... Similarity model");
        // Get similarity model
        firstFinder = searchEntireDatabase ? finderPrototype : finderPrototype.duplicateWithScope(SimilarPatentServer.findItemsByName(inputsToSearchIn));
        if (firstFinder == null || firstFinder.numItems() == 0) {
            throw new RuntimeException("No patents or assignees found in scope.");
        }
        return firstFinder;
    }

    protected Collection<String> getInputsToSearchIn(Request req) {
        String searchType = SimilarPatentServer.extractString(req, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);

        if(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").isEmpty()&&extractString(req, PATENTS_TO_SEARCH_IN_FIELD, "").isEmpty()) {
            searchEntireDatabase=true;
            return Collections.emptyList();
        } else {
            Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]");
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_IN_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            searchEntireDatabase=false;
            // Get scope of search
            Collection<String> inputsToSearchIn = new HashSet<>();
            if (portfolioType.equals(PortfolioList.Type.patents)) {
                inputsToSearchIn.addAll(patents);
                assignees.forEach(assignee -> inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
            } else {
                patents.forEach(patent -> inputsToSearchIn.addAll(Database.assigneesFor(patent)));
                assignees.forEach(assignee -> inputsToSearchIn.addAll(Database.possibleNamesForAssignee(assignee)));

            }
            return inputsToSearchIn;
        }
    };

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        preFilters = preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).filter(model->model!=null&&model.isActive()).collect(Collectors.toList());
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        setPrefilters(req);
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(extractString(req,SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString()));
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(extractString(req,SIMILARITY_MODEL_FIELD,Constants.PARAGRAPH_VECTOR_MODEL)+"_"+portfolioType.toString());

        System.out.println("Found finder prototype...");
        // first finder
        Collection<String> toSearchIn = getInputsToSearchIn(req);
        System.out.println("Found to search in...");
        setFirstFinder(finderPrototype,toSearchIn);
        System.out.println("Set first finder...");

        List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = engines.stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());

        System.out.println("Getting second finders...");
        // second finder
        relevantEngines.forEach(engine->{
            Collection<String> toSearchFor = engine.getInputsToSearchFor(req);
            if(toSearchFor.isEmpty()) throw new RuntimeException("Unable to find patents or assignees to search for.");
            engine.setSecondFinder(finderPrototype,toSearchFor);
        });

        System.out.println("Starting to run model...");
        portfolioList = new PortfolioList(firstFinder.getItemList());
        boolean dupScope = false;
        if(!preFilters.isEmpty()) {
            System.out.println("Should duplicate scope 1...");
            portfolioList.applyFilters(preFilters);
            System.out.println("Applied prefilters...");
            dupScope=true;
        }

        if(!comparator.equals(Constants.SIMILARITY)) {
            System.out.println("Should duplicate scope 2...");
            portfolioList.init(comparator,limit);
            dupScope=true;
        }

        // check similarity threshold filter
        if(!relevantEngines.isEmpty()) {
            // if scope was reduced
            if(dupScope){
                System.out.println("Duplicating scope...");
                firstFinder = firstFinder.duplicateWithScope(portfolioList.getItemList());
            }

            List<AbstractFilter> similarityFilters = extractArray(req, SIMILARITY_FILTER_ARRAY_FIELD).stream().map(filterStr->similarityFilterModelMap.get(filterStr)).collect(Collectors.toList());

            System.out.println("Running similarity model...");
            // run full similarity model
            portfolioList = relevantEngines.parallelStream()
                    .map(engine->firstFinder.similarFromCandidateSet(engine.secondFinder, limit, similarityFilters))
                    .reduce((list1,list2)->list1.merge(list2,comparator,limit))
                    .get();
        }
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        throw new UnsupportedOperationException();
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
