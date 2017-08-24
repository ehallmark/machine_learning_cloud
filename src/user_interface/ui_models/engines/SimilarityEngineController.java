package user_interface.ui_models.engines;

import elasticsearch.DataSearcher;
import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.LatestAssigneeNestedAttribute;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngineController {
    private Collection<AbstractFilter> preFilters;
    private Collection<String> resultTypes;
    @Getter
    protected PortfolioList portfolioList;
    @Getter
    private static List<AbstractSimilarityEngine> engines;
    public SimilarityEngineController(List<AbstractSimilarityEngine> engines) {
        this.engines=engines;
    }

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);

        preFilters = new ArrayList<>(preFilterModels.stream().map(modelName -> {
            AbstractFilter filter = SimilarPatentServer.preFilterModelMap.get(modelName);
            if(filter==null) {
                System.out.println("Unable to find model: "+modelName);
            }
            return filter;
        }).filter(i->i!=null).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));

        // get labels to remove (if any)
        Collection<String> labelsToRemove = new HashSet<>();
        Collection<String> assigneesToRemove = new HashSet<>();
        for(String resultType : resultTypes) {
            // remove any patents in the search for category
            Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            labelsToRemove.addAll(patents);
            assignees.forEach(assignee -> assigneesToRemove.addAll(Database.possibleNamesForAssignee(assignee)));
        }

        if(labelsToRemove.size()>0) {
            preFilters.add(new AbstractExcludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Exclude, AbstractFilter.FieldType.Text, labelsToRemove));
        }
        if(assigneesToRemove.size()>0) {
            preFilters.add(new AbstractExcludeFilter(new LatestAssigneeNestedAttribute(), AbstractFilter.FilterType.Exclude,  AbstractFilter.FieldType.Text, assigneesToRemove, Constants.ASSIGNEE));
        }

        preFilters = preFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
    }

    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        int maxResultLimit = 50000;
        if(limit > maxResultLimit) {
            throw new RuntimeException("Error: Maximum result limit is "+maxResultLimit+ " which is less than "+limit);
        }
        resultTypes = SimilarPatentServer.extractArray(req, Constants.DOC_TYPE);
        // what to do if not present?
        if(resultTypes.isEmpty()) {
            resultTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.OVERALL_SCORE);
        setPrefilters(req);

        SortOrder sortOrder = SortOrder.fromString(extractString(req,SORT_DIRECTION_FIELD,"desc"));
        Item[] scope = DataSearcher.searchForAssets(SimilarPatentServer.getAllAttributes(), preFilters, comparator, sortOrder, limit, SimilarPatentServer.getNestedAttrMap());
        System.out.println("Elasticsearch found: "+scope.length+ " assets");

        portfolioList = new PortfolioList(scope);
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
