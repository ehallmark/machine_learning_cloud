package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import spark.Request;
import ui_models.filters.AbstractFilter;
import ui_models.filters.LabelFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

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
    private PortfolioList.Type portfolioType;
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
        preFilters = new ArrayList<>(preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));

        // get labels to remove (if any)
        Collection<String> labelsToRemove = new HashSet<>();
        if(portfolioType.equals(PortfolioList.Type.patents)) {
            // remove any patents in the search for category
            Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
            labelsToRemove.addAll(patents);
        } else {
            // remove any assignees
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            labelsToRemove.addAll(assignees);
        }

        if(labelsToRemove.size()>0) {
            preFilters.add(new LabelFilter(labelsToRemove));
        }

        preFilters = preFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        String searchType = SimilarPatentServer.extractString(req, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        portfolioType = PortfolioList.Type.valueOf(searchType);
        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);

        List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = engines.stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());

        // check degenerate case
        if(relevantEngines.isEmpty() && comparator.equals(Constants.SIMILARITY)) {
            // bad
            portfolioList = new PortfolioList(new Item[]{});
            return;
        }

        setPrefilters(req);
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(extractString(req,SIMILARITY_MODEL_FIELD,Constants.PARAGRAPH_VECTOR_MODEL)+"_"+portfolioType.toString());

        System.out.println("Found finder prototype...");

        // first finder
        Collection<String> toSearchIn = getInputsToSearchIn(req);
        System.out.println("Found to search in...");
        setFirstFinder(finderPrototype,toSearchIn);
        System.out.println("Finished setting first finder...");

        // handle keywords
        String keywordsToRequire = extractString(req,Constants.REQUIRE_KEYWORD_FILTER,null);
        if(keywordsToRequire!=null) {
            keywordsToRequire=keywordsToRequire.toLowerCase();
            System.out.println("Handling keywords to require...");
            try {
                Database.setupSeedConn();
                firstFinder=firstFinder.duplicateWithScope(
                        SimilarPatentServer.findItemsByName(Database.patentsWithKeywords(Arrays.stream(firstFinder.getItemList()).map(item -> item.getName()).collect(Collectors.toList()), portfolioType, keywordsToRequire))
                );
            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    Database.seedConn.close();
                } catch(Exception e) {
                    System.out.println("Error closing seedConn.");
                }
            }
        }


        System.out.println("Getting second finders...");
        // second finder
        relevantEngines.forEach(engine->{
            Collection<String> toSearchFor = engine.getInputsToSearchFor(req, portfolioType);
            engine.setSecondFinder(finderPrototype,toSearchFor);
        });

        if(relevantEngines.size()>0 && relevantEngines.stream().map(engine->engine.secondFinder.numItems()).collect(Collectors.summingInt(i->i))==0) {
            throw new RuntimeException("Unable to find patents or assignees to search for.");
        }

        // only keep relevant engines with items
        relevantEngines = relevantEngines.stream().filter(engine->engine.secondFinder.numItems()>0).collect(Collectors.toList());

        System.out.println("Starting to run model...");
        portfolioList = new PortfolioList(firstFinder.getItemList());
        boolean dupScope = false;
        if(!preFilters.isEmpty()) {
            portfolioList.applyFilters(preFilters);
            System.out.println("Applied prefilters...");
            dupScope=true;
        }

        if(!comparator.equals(Constants.SIMILARITY)) {
            System.out.println("Non similarity comparator...");
            portfolioList.init(comparator,limit);
            dupScope=true;
        }

        // check similarity threshold filter
        List<AbstractFilter> similarityFilters = extractArray(req, SIMILARITY_FILTER_ARRAY_FIELD).stream().map(filterStr->similarityFilterModelMap.get(filterStr)).collect(Collectors.toList());
        if(!relevantEngines.isEmpty()) {
            // if scope was reduced
            if(dupScope){
                System.out.println("Duplicating scope...");
                firstFinder = firstFinder.duplicateWithScope(portfolioList.getItemList());
            }

            System.out.println("Running similarity model...");
            // run full similarity model
            portfolioList = relevantEngines.parallelStream()
                    .map(engine->firstFinder.similarFromCandidateSet(engine.secondFinder, limit, similarityFilters))
                    .reduce((list1,list2)->list1.merge(list2,comparator,limit))
                    .get();
        } else if(!similarityFilters.isEmpty()) {
            throw new RuntimeException("Applying a similarity filter without a similarity engine.");
        }
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, PortfolioList.Type searchType) {
        throw new UnsupportedOperationException();
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
