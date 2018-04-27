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
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.RelatedAssetsAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.filters.AssetDedupFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngineController {
    private final boolean isBigQuery;
    private Collection<AbstractFilter> preFilters;
    @Getter
    protected PortfolioList portfolioList;
    @Getter
    protected Aggregations aggregations;
    @Getter
    protected long totalCount;
    @Setter
    private Set<String> chartPrerequisites;
    @Getter @Setter
    private static List<AbstractSimilarityEngine> allEngines;
    @Setter
    private List<AggregationBuilder> aggregationBuilders;
    public SimilarityEngineController(boolean bigQuery) {
        this.isBigQuery=bigQuery;
    }

    public SimilarityEngineController dup() {
        return new SimilarityEngineController(isBigQuery);
    }

    private void setPrefilters(Request req) {
        List<String> preFilterModels = SimilarPatentServer.extractArray(req, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);

        Map<String,AbstractFilter> filterModelMap = SimilarPatentServer.preFilterModelMap;
        preFilters = new ArrayList<>(preFilterModels.stream().map(modelName -> {
            AbstractFilter filter = filterModelMap.get(modelName);
            if(filter==null) {
                System.out.println("Unable to find model: "+modelName);
                return null;
            }
            return filter.dup();
        }).filter(i->i!=null).collect(Collectors.toList()));
        preFilters.forEach(filter -> filter.extractRelevantInformationFromParams(req));

        // for big query, user must set what to exclude
        if(!isBigQuery) {
            // get labels to remove (if any)
            Collection<String> labelsToRemove = new HashSet<>();
            Collection<String> assigneesToRemove = new HashSet<>();
            // remove any patents in the search for category
            Collection<String> patents = preProcess(extractString(req, PATENTS_TO_SEARCH_FOR_FIELD, ""), "\\s+", "[^0-9]");
            Collection<String> assignees = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
            labelsToRemove.addAll(patents);
            assigneesToRemove.addAll(assignees);
            if (labelsToRemove.size() > 0) {
                List<String> toRemove = labelsToRemove.stream().collect(Collectors.toList());
                preFilters.add(new AbstractExcludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Exclude, AbstractFilter.FieldType.Text, toRemove));
                preFilters.add(new AbstractExcludeFilter(new FilingNameAttribute(), AbstractFilter.FilterType.Exclude, AbstractFilter.FieldType.Text, toRemove));
            }
            if (assigneesToRemove.size() > 0) {
                // lazily create assignee name filter
                AbstractAttribute assignee = new LatestAssigneeNestedAttribute();
                AbstractNestedFilter assigneeFilter = (AbstractNestedFilter) assignee.createFilters().stream().findFirst().orElse(null);
                if (assigneeFilter != null) {
                    AbstractExcludeFilter assigneeNameFilter = (AbstractExcludeFilter) assigneeFilter.getFilters().stream().filter(attr -> attr.getPrerequisite().equals(Constants.ASSIGNEE) && attr.getFilterType().equals(AbstractFilter.FilterType.Exclude)).findAny().orElse(null);
                    if (assigneeNameFilter != null) {
                        assigneeNameFilter.setLabels(assigneesToRemove.stream().collect(Collectors.toList()));
                        assigneeFilter.setFilterSubset(Arrays.asList(assigneeNameFilter));
                        preFilters.add(assigneeFilter);
                    } else {
                        throw new RuntimeException("Unable to create assignee name filter");
                    }
                }
            }
        }

        preFilters = preFilters.stream().filter(filter->filter.isActive()).collect(Collectors.toList());
    }

    private static int getResultLimitForRole(String role) {
        if(role==null) return 1;
        if(role.equals(SimilarPatentServer.SUPER_USER)) return 10000000;
        if(role.equals(SimilarPatentServer.INTERNAL_USER)) return 100000;
        if(role.equals(SimilarPatentServer.ANALYST_USER)) return 10000;
        return 1;
    }
    public void extractRelevantInformationFromParams(Request req) {
        System.out.println("Beginning extract relevant info...");
        // init
        int limit = extractInt(req, LIMIT_FIELD, 10);
        if (limit <= 0) limit = 1; // VERY IMPORTANT!!!!! limit < 0 means it will scroll through EVERYTHING
        int maxResultLimit = getResultLimitForRole(req.session(false).attribute("role"));
        if (limit > maxResultLimit) {
            throw new RuntimeException("Error: Maximum result limit is " + maxResultLimit + " which is less than " + limit);
        }

        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SCORE);
        System.out.println("Comparing by: " + comparator);
        setPrefilters(req);

        Set<String> attributesRequired = new HashSet<>();
        attributesRequired.add(comparator);

        List<String> attributesFromUser = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
        attributesRequired.addAll(attributesFromUser);
        attributesFromUser.forEach(attr -> {
            attributesRequired.addAll(extractArray(req, attr + "[]"));
        });
        if(isBigQuery) attributesRequired.add(Attributes.FAMILY_ID); // IMPORTANT

        // add chart prerequisites
        if (chartPrerequisites != null) {
            attributesRequired.addAll(chartPrerequisites);
        }

        if (comparator.equals(Constants.SCORE)) {
            if (isBigQuery) {
                attributesRequired.add(Attributes.RNN_ENC);
                attributesRequired.add(Attributes.CPC_VAE);
            } else {
                attributesRequired.add(Constants.SIMILARITY_FAST);
            }
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

        List<Item> scope;
        if(isBigQuery) {
            ElasticSearchResponse response = DataSearcher.searchPatentsGlobal(topLevelAttributes,preFilters,comparator,sortOrder,limit, Attributes.getNestedAttrMap(), item->item,true, useHighlighter, filterNestedObjects, aggregationBuilders);
            System.out.println("Total hits: "+response.getTotalCount());
            scope = response.getItems();
            aggregations = response.getAggregations();
            totalCount = response.getTotalCount();
        } else {
            scope = DataSearcher.searchForAssets(topLevelAttributes, preFilters, comparator, sortOrder, limit, SimilarPatentServer.getNestedAttrMap(), useHighlighter, filterNestedObjects);
        }

        // asset dedupe
        for(AbstractFilter preFilter : preFilters) {
            if(preFilter instanceof AssetDedupFilter) {
                System.out.println("Asset dedupe!");
                if(isBigQuery) {
                    scope = assetDedupeByFamily(scope);
                } else {
                    scope = assetDedupe(scope);
                }
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

    public static List<Item> assetDedupe(List<Item> items) {
        RelatedAssetsAttribute relatedAssetsAttribute = new RelatedAssetsAttribute();
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        Set<String> namesSeenSoFar = new HashSet<>();
        return items.stream().map(item->{
            Item toRet;
            String filing = assetToFilingMap.getPatentDataMap().getOrDefault(item.getName(),assetToFilingMap.getApplicationDataMap().get(item.getName()));
            Collection<String> related = new HashSet<>();
            related.add(item.getName());
            if(filing!=null) {
                related.addAll(Stream.of(filingToAssetMap.getPatentDataMap().getOrDefault(filing,Collections.emptyList()),filingToAssetMap.getApplicationDataMap().getOrDefault(filing,Collections.emptyList())).flatMap(s->s.stream()).distinct().collect(Collectors.toCollection(ArrayList::new)));
                related.add(filing);
            }
            Collection<String> relatedCopy = new HashSet<>(related);
            relatedCopy.forEach(relative->{
                related.addAll(relatedAssetsAttribute.getPatentDataMap().getOrDefault(relative,Collections.emptyList()));
                related.addAll(relatedAssetsAttribute.getApplicationDataMap().getOrDefault(relative,Collections.emptyList()));
                related.addAll(relatedAssetsAttribute.getPatentDataMap().getOrDefault(relative,Collections.emptyList()).stream().map(asset->assetToFilingMap.getPatentDataMap().getOrDefault(asset,assetToFilingMap.getApplicationDataMap().get(asset))).filter(asset->asset!=null).collect(Collectors.toList()));
                related.addAll(relatedAssetsAttribute.getApplicationDataMap().getOrDefault(relative,Collections.emptyList()).stream().map(asset->assetToFilingMap.getPatentDataMap().getOrDefault(asset,assetToFilingMap.getApplicationDataMap().get(asset))).filter(asset->asset!=null).collect(Collectors.toList()));
            });

            if(related.stream().anyMatch(name->namesSeenSoFar.contains(name))) {
                toRet = null;
            } else {
                toRet = item;
            }

            namesSeenSoFar.addAll(related);
         return toRet;
        }).filter(item->item!=null).collect(Collectors.toList());
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
                    ret.add(e.getValue().get(0));
                });
        return ret;
    }
}
