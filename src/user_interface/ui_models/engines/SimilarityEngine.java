package user_interface.ui_models.engines;

import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AssigneeFilter;
import user_interface.ui_models.filters.LabelFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.*;

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
            searchEntireDatabase = false;
            // Get scope of search
            Collection<String> inputsToSearchIn = new HashSet<>();
            if (portfolioType.equals(PortfolioList.Type.patents)) {
                inputsToSearchIn.addAll(patents);
                assignees.forEach(assignee -> inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee)));
            } else if (portfolioType.equals(PortfolioList.Type.applications)) {
                inputsToSearchIn.addAll(patents);
                assignees.forEach(assignee -> inputsToSearchIn.addAll(Database.selectApplicationNumbersFromAssignee(assignee)));
            } else if (portfolioType.equals(PortfolioList.Type.assets)) {
                inputsToSearchIn.addAll(patents);
                assignees.forEach(assignee -> {
                    inputsToSearchIn.addAll(Database.selectApplicationNumbersFromAssignee(assignee));
                    inputsToSearchIn.addAll(Database.selectPatentNumbersFromAssignee(assignee));
                });
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
        Collection<String> assigneesToRemove = new HashSet<>();
        if(!portfolioType.equals(PortfolioList.Type.assignees)) {
            // remove any patents in the search for category
            Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            labelsToRemove.addAll(patents);
            assignees.forEach(assignee->assigneesToRemove.addAll(Database.possibleNamesForAssignee(assignee)));
        } else {
            // remove any assignees
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            assignees.forEach(assignee->labelsToRemove.addAll(Database.possibleNamesForAssignee(assignee)));
        }

        if(labelsToRemove.size()>0) {
            preFilters.add(new LabelFilter(labelsToRemove));
        }
        if(assigneesToRemove.size()>0) {
            preFilters.add(new AssigneeFilter(assigneesToRemove));
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
        String similarityModelStr = extractString(req,SIMILARITY_MODEL_FIELD,Constants.PARAGRAPH_VECTOR_MODEL)+"_"+portfolioType.toString();
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModelStr);

        System.out.println("Found finder prototype: "+similarityModelStr);

        // first finder
        Collection<String> toSearchIn = getInputsToSearchIn(req);
        System.out.println("Found to search in...");
        setFirstFinder(finderPrototype,toSearchIn);
        System.out.println("Finished setting first finder...");

        // handle keywords
        String keywordsToRequire = extractString(req,Constants.REQUIRE_KEYWORD_FILTER,null);
        String keywordsToExclude = extractString(req,Constants.EXCLUDE_KEYWORD_FILTER,null);
        String advancedKeywords = extractString(req,Constants.ADVANCED_KEYWORD_FILTER,null);
        if(keywordsToRequire!=null || keywordsToExclude!=null || advancedKeywords != null) {
            keywordsToRequire = keywordsToRequire==null ? "" : keywordsToRequire.toLowerCase();
            keywordsToExclude = keywordsToExclude==null ? "" : keywordsToExclude.toLowerCase();
            advancedKeywords = advancedKeywords==null ? "" : advancedKeywords.toLowerCase();
            System.out.println("Handling keywords to require...");
            try {
                Database.setupSeedConn();
                Item[] scope;
                if(searchEntireDatabase) {
                    scope = SimilarPatentServer.findItemsByName(Database.patentsWithKeywords(null, portfolioType, advancedKeywords, keywordsToRequire, keywordsToExclude));
                } else {
                    Map<String,Item> patentMap = Arrays.stream(firstFinder.getItemList()).collect(Collectors.toMap(e->e.getName(),e->e));
                    scope = Database.patentsWithKeywords(patentMap.keySet(), portfolioType, advancedKeywords, keywordsToRequire, keywordsToExclude).stream()
                            .map(patent->patentMap.get(patent)).toArray(size->new Item[size]);
                }
                firstFinder=firstFinder.duplicateWithScope(scope);

            } catch(Exception e) {
                System.out.println("Error with keywords: "+e.getMessage());
                throw new RuntimeException("Unable to connect to database.");
            } finally {
                try {
                    Database.seedConn.close();
                } catch(Exception e) {
                    System.out.println("Error closing seedConn.");
                }
                Database.seedConn=null;
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
