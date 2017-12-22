package user_interface.ui_models.engines;

import elasticsearch.DataSearcher;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
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
    @Setter
    private Set<String> chartPrerequisites;
    @Getter @Setter
    private static List<AbstractSimilarityEngine> allEngines;
    public SimilarityEngineController() {
        if(allEngines==null) throw new NullPointerException("allEngines list (static)");
    }

    public SimilarityEngineController dup() {
        return new SimilarityEngineController();
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
        if(limit <= 0) limit = 1; // VERY IMPORTANT!!!!! limit < 0 means it will scroll through EVERYTHING
        int maxResultLimit = 100000;
        if(limit > maxResultLimit) {
            throw new RuntimeException("Error: Maximum result limit is "+maxResultLimit+ " which is less than "+limit);
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        setPrefilters(req);

        Set<String> attributesRequired = new HashSet<>();
        attributesRequired.add(comparator);

        List<String> attributesFromUser = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
        attributesRequired.addAll(attributesFromUser);
        attributesFromUser.forEach(attr->{
            attributesRequired.addAll(extractArray(req, attr+"[]"));
        });

        // add chart prerequisites
        if(chartPrerequisites!=null) {
            attributesRequired.addAll(chartPrerequisites);
        }

        System.out.println("Required attributes: "+String.join("; ",attributesRequired));

        SortOrder sortOrder = SortOrder.fromString(extractString(req,SORT_DIRECTION_FIELD,"desc"));
        Collection<AbstractAttribute> topLevelAttributes = SimilarPatentServer.getAllTopLevelAttributes()
                .stream()
                .filter(attr->{
                    if(attr instanceof NestedAttribute) {
                        Collection<AbstractAttribute> children = ((NestedAttribute) attr).getAttributes();
                        return children.stream().anyMatch(child->attributesRequired.contains(child.getFullName()));
                    } else {
                        return attributesRequired.contains(attr.getName());
                    }
                }).map(attr->{
                    if(attr instanceof DependentAttribute) {
                        System.out.println("Extracting info for dependent attribute: "+attr.getName());
                        AbstractAttribute attrDup =  ((DependentAttribute) attr).dup();
                        ((DependentAttribute)attrDup).extractRelevantInformationFromParams(req);
                        return attrDup;
                    } else return attr;
                }).collect(Collectors.toList());

        boolean useHighlighter = extractBool(req, USE_HIGHLIGHTER_FIELD);
        boolean filterNestedObjects = extractBool(req, FILTER_NESTED_OBJECTS_FIELD);

        List<Item> scope = DataSearcher.searchForAssets(topLevelAttributes, preFilters, comparator, sortOrder, limit, SimilarPatentServer.getNestedAttrMap(), useHighlighter, filterNestedObjects);
        System.out.println("Elasticsearch found: "+scope.size()+ " assets");

        portfolioList = new PortfolioList(scope);
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return allEngines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
