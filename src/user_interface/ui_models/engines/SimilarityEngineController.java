package user_interface.ui_models.engines;

import elasticsearch.DataSearcher;
import elasticsearch.ElasticSearchResponse;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AssetDedupFilter;
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
    private Collection<AbstractAttribute> topLevelAttributes;
    private String comparator;
    @Getter
    protected PortfolioList portfolioList;
    @Getter
    protected Aggregations aggregations;
    @Getter
    protected long totalCount;
    @Setter
    private Set<String> chartPrerequisites;
    @Getter
    @Setter
    private static List<AbstractSimilarityEngine> allEngines;
    @Setter
    private List<AggregationBuilder> aggregationBuilders;
    @Getter
    private Map<String,Collection<String>> synonymMap;

    public SimilarityEngineController dup() {
        return new SimilarityEngineController();
    }

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);

        Map<String, AbstractFilter> filterModelMap = SimilarPatentServer.preFilterModelMap;
        preFilters = new ArrayList<>(preFilterModels.stream().map(modelName -> {
            AbstractFilter filter = filterModelMap.get(modelName);
            if (filter == null) {
                System.out.println("Unable to find model: " + modelName);
                return null;
            }
            return filter.dup();
        }).filter(i -> i != null).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));
        preFilters = preFilters.stream().filter(filter -> filter.isActive()).collect(Collectors.toList());
        synonymMap = new HashMap<>();
        preFilters.forEach(filter->{
            if(filter.getSynonymMap()!=null) {
                synonymMap.putAll(filter.getSynonymMap());
            }
        });
    }

    private static int getResultLimitForRole(String role) {
        if (role == null) return 1;
        if (role.equals(SimilarPatentServer.SUPER_USER)) return 100000;
        if (role.equals(SimilarPatentServer.INTERNAL_USER)) return 10000;
        if (role.equals(SimilarPatentServer.ANALYST_USER)) return 10000;
        return 1;
    }

    public void buildAttributes(Request req) {
        System.out.println("Beginning extract relevant info...");

        comparator = extractString(req, COMPARATOR_FIELD, Constants.SCORE);
            System.out.println("Comparing by: "+comparator);

        setPrefilters(req);

        Set<String> attributesRequired = new HashSet<>();
        attributesRequired.add(comparator);

        List<String> attributesFromUser = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
        attributesRequired.addAll(attributesFromUser);
        attributesFromUser.forEach(attr -> attributesRequired.addAll(extractArray(req, attr + "[]")));
        attributesRequired.add(Attributes.FAMILY_ID); // IMPORTANT
        attributesRequired.add(Attributes.KIND_CODE); // IMPORTANT

        // add chart prerequisites
        if(chartPrerequisites !=null) {
            attributesRequired.addAll(chartPrerequisites);
        }

        if(comparator.equals(Constants.SCORE)) {
            attributesRequired.add(Attributes.SIMILARITY);
        }

        System.out.println("Required attributes: "+String.join("; ",attributesRequired));

        topLevelAttributes = SimilarPatentServer.getAllTopLevelAttributes()
                .stream()
                .filter(attr -> {
                    if (attr instanceof NestedAttribute) {
                        Collection<AbstractAttribute> children = ((NestedAttribute) attr).getAttributes();
                        return children.stream().anyMatch(child -> attributesRequired.contains(child.getFullName()));
                    } else {
                        return attributesRequired.contains(attr.getName());
                    }
                }).map(attr -> {
                    if (attr instanceof DependentAttribute) {
                        System.out.println("Extracting info for dependent attribute: " + attr.getName());
                        AbstractAttribute attrDup = ((DependentAttribute) attr).dup();
                        ((DependentAttribute) attrDup).extractRelevantInformationFromParams(req);
                        return attrDup;
                    } else return attr;
                }).collect(Collectors.toList());
    }

    public void extractRelevantInformationFromParams(Request req) {
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        if(limit <=0)limit =1; // VERY IMPORTANT!!!!! limit < 0 means it will scroll through EVERYTHING
        int maxResultLimit = getResultLimitForRole(req.session(false).attribute("role"));
        if(limit >maxResultLimit) {
            throw new RuntimeException("Error: Maximum result limit is " + maxResultLimit + " which is less than " + limit);
        }
        boolean useHighlighter = extractBool(req, USE_HIGHLIGHTER_FIELD);
        boolean filterNestedObjects = extractBool(req, FILTER_NESTED_OBJECTS_FIELD);
        SortOrder sortOrder = SortOrder.fromString(extractString(req, SORT_DIRECTION_FIELD, "desc"));

        List<Item> scope;
        ElasticSearchResponse response = DataSearcher.searchPatentsGlobal(topLevelAttributes,preFilters,comparator,sortOrder,limit, Attributes.getNestedAttrMap(),true, useHighlighter, filterNestedObjects, aggregationBuilders);
        System.out.println("Total hits: "+response.getTotalCount());
        scope = response.getItems();
        aggregations = response.getAggregations();
        totalCount = response.getTotalCount();
        String _comparator = DataSearcher.getOrDefaultComparator(comparator);
        boolean isOverallScore = _comparator.equals(Constants.SCORE);
        req.session(false).attribute("searchRequest", response.getRequestBuilder());
        req.session(false).attribute("searchQuery", response.getQuery());
        req.session(false).attribute("filterNestedObjects", filterNestedObjects);
        req.session(false).attribute("isOverallScore", isOverallScore);

        // asset dedupe
        for(AbstractFilter preFilter : preFilters) {
            if(preFilter instanceof AssetDedupFilter) {
                System.out.println("Asset dedupe!");
                scope = assetDedupeByFamily(scope);
                break;
            }
        }

        // dataset names
        List<TermsLookupAttribute> terms = Collections.synchronizedList(new ArrayList<>());
        topLevelAttributes.forEach(attr->{
            if(attr instanceof TermsLookupAttribute) {
                TermsLookupAttribute termsLookupAttribute = (TermsLookupAttribute)attr;
                terms.add(termsLookupAttribute);
            }
        });

        if(terms.size()>0) {
            scope.parallelStream().forEach(item->{
                terms.forEach(term->{
                    item.addData(term.getFullName(),String.join(DataSearcher.ARRAY_SEPARATOR, term.termsFor(item.getName())));
                });
            });
        }

        System.out.println("Elasticsearch retrieved: "+scope.size()+ " assets");

        portfolioList = new PortfolioList(scope);
    }

    private static List<Item> assetDedupeByFamily(List<Item> items) {
        List<Item> ret = new ArrayList<>(items.size());
        items.stream().filter(item->{
            if(item.getData(Attributes.FAMILY_ID).equals("-1")) {
                ret.add(item);
                return false;
            } else {
                return true;
            }
        }).collect(Collectors.groupingBy(e->(String)e.getData(Attributes.FAMILY_ID),Collectors.toList()))
                .entrySet().forEach(e->{
                    ret.add(e.getValue().stream().min((e1,e2)->{
                        int c = ((String)e2.getData(Attributes.KIND_CODE)).compareTo((String)e2.getData(Attributes.KIND_CODE));
                        if(c==0) {
                            c = e2.getName().compareTo(e1.getName());
                        }
                        return c;
                    }).get());
                });
        return ret;
    }
}
