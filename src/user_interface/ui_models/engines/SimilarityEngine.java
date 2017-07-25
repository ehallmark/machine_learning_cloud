package user_interface.ui_models.engines;

import elasticsearch.DataSearcher;
import lombok.Getter;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.IncludeLabelFilter;
import user_interface.ui_models.filters.RemoveAssigneeFilter;
import user_interface.ui_models.filters.RemoveLabelFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngine extends AbstractSimilarityEngine {
    private Collection<AbstractFilter> preFilters;
    private Collection<String> resultTypes;
    @Getter
    protected PortfolioList portfolioList;
    @Getter
    private List<AbstractSimilarityEngine> engines;
    public SimilarityEngine(List<AbstractSimilarityEngine> engines) {
        super(Constants.SIMILARITY);
        this.engines=engines;
    }

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        preFilters = new ArrayList<>(preFilterModels.stream().map(modelName -> SimilarPatentServer.preFilterModelMap.get(modelName)).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));

        // get labels to remove (if any)
        Collection<String> labelsToRemove = new HashSet<>();
        Collection<String> assigneesToRemove = new HashSet<>();
        for(String resultType : resultTypes) {
            PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(resultType);
            if (!portfolioType.equals(PortfolioList.Type.assignees)) {
                // remove any patents in the search for category
                Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
                Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
                labelsToRemove.addAll(patents);
                assignees.forEach(assignee -> assigneesToRemove.addAll(Database.possibleNamesForAssignee(assignee)));
            } else {
                // remove any assignees
                Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
                assignees.forEach(assignee -> labelsToRemove.addAll(Database.possibleNamesForAssignee(assignee)));
            }
        }

        if(labelsToRemove.size()>0) {
            preFilters.add(new RemoveLabelFilter(labelsToRemove));
        }
        if(assigneesToRemove.size()>0) {
            preFilters.add(new RemoveAssigneeFilter(assigneesToRemove));
        }

        preFilters = preFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        resultTypes = SimilarPatentServer.extractArray(req, SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD);
        // what to do if not present?
        if(resultTypes.isEmpty()) {
            resultTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
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

        // Run elasticsearch
        int maxLimit;
        SortBuilder sortBuilder;
        SortOrder sortOrder = SortOrder.fromString(extractString(req,SORT_DIRECTION_FIELD,"desc"));
        if(comparator.equals(Constants.SIMILARITY)) { // need to get more results to do similarity
            maxLimit = 100000;
            sortBuilder = SortBuilders.scoreSort().order(sortOrder);
        } else {
            maxLimit = limit; // don't need extras
            sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
        }
        // only pull ids by setting first parameter to empty list
        Item[] scope = DataSearcher.searchForAssets(Collections.emptyList(), preFilters, sortBuilder, maxLimit);
        System.out.println("Elasticsearch found: "+scope.length+ " assets");
        if(scope.length>0) {
            System.out.println(String.join("; ", scope[0].getDataMap().entrySet().stream().map(e->e.getKey()+": "+e.getValue()).collect(Collectors.toList())));
        }

        String similarityModelStr = extractString(req,SIMILARITY_MODEL_FIELD,Constants.PARAGRAPH_VECTOR_MODEL);
        AtomicReference<AbstractSimilarityModel> finderPrototype = new AtomicReference<>(similarityModelMap.get(similarityModelStr));

        // set scope
        finderPrototype.set(finderPrototype.get().duplicateWithScope(scope));

        System.out.println("Getting second finders...");
        // second finder
        relevantEngines.forEach(engine->{
            Collection<String> toSearchFor = engine.getInputsToSearchFor(req, resultTypes);
            engine.setSecondFinder(finderPrototype.get(),toSearchFor);
        });

        if(relevantEngines.size()>0 && relevantEngines.stream().map(engine->engine.secondFinder.numItems()).collect(Collectors.summingInt(i->i))==0) {
            throw new RuntimeException("Unable to find patents or assignees to search for.");
        }

        // only keep relevant engines with items
        relevantEngines = relevantEngines.stream().filter(engine->engine.secondFinder.numItems()>0).collect(Collectors.toList());

        portfolioList = new PortfolioList(finderPrototype.get().getItemList());
        if(!comparator.equals(Constants.SIMILARITY)) {
            System.out.println("Non similarity comparator...");
            portfolioList.init(comparator,limit);
            finderPrototype.set(finderPrototype.get().duplicateWithScope(portfolioList.getItemList()));
        }

        // check similarity threshold filter
        List<AbstractFilter> similarityFilters = extractArray(req, SIMILARITY_FILTER_ARRAY_FIELD).stream().map(filterStr->similarityFilterModelMap.get(filterStr)).collect(Collectors.toList());
        if(!relevantEngines.isEmpty()) {
            System.out.println("Running similarity model...");
            // run full similarity model
            portfolioList = relevantEngines.parallelStream()
                    .map(engine->finderPrototype.get().similarFromCandidateSet(engine.secondFinder, limit, similarityFilters))
                    .reduce((list1,list2)->list1.merge(list2,comparator,limit))
                    .get();
        } else if(!similarityFilters.isEmpty()) {
            throw new RuntimeException("Applying a similarity filter without a similarity engine.");
        }

        // Now we have the final portfolio list
        // So we need to pull all attributes for this list
        if(portfolioList.getItemList().length > 0) {
            System.out.println("Pulling attributes from elasticsearch...");
            AbstractFilter idFilter = new IncludeLabelFilter(Arrays.stream(portfolioList.getItemList()).map(item->item.getName()).collect(Collectors.toList()));
            portfolioList = new PortfolioList(DataSearcher.searchForAssets(SimilarPatentServer.getAllAttributeNames(),Arrays.asList(idFilter),sortBuilder,limit));
            if(comparator.equals(Constants.SIMILARITY)) portfolioList.init(comparator,limit);
        }
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        throw new UnsupportedOperationException();
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
