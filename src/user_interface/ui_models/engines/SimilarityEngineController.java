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
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.filters.AdvancedKeywordFilter;
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
                return null;
            }
            return filter.dup();
        }).filter(i->i!=null).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));

        // get labels to remove (if any)
        Collection<String> labelsToRemove = new HashSet<>();
        Collection<String> assigneesToRemove = new HashSet<>();
        // remove any patents in the search for category
        Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
        Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
        labelsToRemove.addAll(patents);
        assigneesToRemove.addAll(assignees);

        if(labelsToRemove.size()>0) {
            preFilters.add(new AbstractExcludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Exclude, AbstractFilter.FieldType.Text, labelsToRemove.stream().collect(Collectors.toList())));
        }
        if(assigneesToRemove.size()>0) {
            // lazily create assignee name filter
            AbstractAttribute assignee = new LatestAssigneeNestedAttribute();
            AbstractNestedFilter assigneeFilter = (AbstractNestedFilter) assignee.createFilters().stream().findFirst().orElse(null);
            if(assigneeFilter != null) {
                AbstractExcludeFilter assigneeNameFilter = (AbstractExcludeFilter) assigneeFilter.getFilters().stream().filter(attr->attr.getPrerequisite().equals(Constants.ASSIGNEE)&&attr.getFilterType().equals(AbstractFilter.FilterType.Exclude)).findAny().orElse(null);
                if(assigneeNameFilter!=null) {
                    assigneeNameFilter.setLabels(assigneesToRemove.stream().collect(Collectors.toList()));
                    assigneeFilter.setFilterSubset(Arrays.asList(assigneeNameFilter));
                    preFilters.add(assigneeFilter);
                } else {
                    throw new RuntimeException("Unable to create assignee name filter");
                }
            }
        }

        preFilters = preFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
    }

    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        int maxResultLimit = 100000;
        if(limit > maxResultLimit) {
            throw new RuntimeException("Error: Maximum result limit is "+maxResultLimit+ " which is less than "+limit);
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        setPrefilters(req);

        SortOrder sortOrder = SortOrder.fromString(extractString(req,SORT_DIRECTION_FIELD,"desc"));
        Collection<AbstractAttribute> topLevelAttributes = SimilarPatentServer.getAllTopLevelAttributes().stream().map(attr->{
            if(attr instanceof DependentAttribute) {
                System.out.println("Extracting info for dependent attribute: "+attr.getName());
                AbstractAttribute attrDup =  ((DependentAttribute) attr).dup();
                ((DependentAttribute)attrDup).extractRelevantInformationFromParams(req);
                return attrDup;
            } else return attr;
        }).collect(Collectors.toList());

        Item[] scope = DataSearcher.searchForAssets(topLevelAttributes, preFilters, comparator, sortOrder, limit, SimilarPatentServer.getNestedAttrMap());
        System.out.println("Elasticsearch found: "+scope.length+ " assets");

        portfolioList = new PortfolioList(scope);
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
