package elasticsearch;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractGreaterThanFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static user_interface.server.SimilarPatentServer.SORT_DIRECTION_FIELD;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    @Getter
    private static TransportClient client = MyClient.get();
    private static final String INDEX_NAME = DataIngester.INDEX_NAME;
    private static final String TYPE_NAME = DataIngester.TYPE_NAME;
    private static final int PAGE_LIMIT = 10000;
    private static final boolean debug = false;

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap) {
        return searchForAssets(attributes,filters,comparator,sortOrder,maxLimit,nestedAttrNameMap,item->item,true);
    }

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<AbstractFilter> filters, String _comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, ItemTransformer transformer, boolean merge) {
        try {
            if(debug) {
                attributes.forEach(attr->{
                   System.out.println("Root name for "+attr.getFullName()+": "+attr.getRootName());
                });
                filters.forEach(filter->{
                    System.out.println("Root name for filter "+filter.getName()+": "+filter.getAttribute().getRootName());
                });
            }
            // Run elasticsearch
            String comparator = _comparator == null ? "" : _comparator;
            boolean isOverallScore = comparator.equals(Constants.SIMILARITY);
            SortBuilder sortBuilder;
            // only pull ids by setting first parameter to empty list
            if(isOverallScore) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else if (Constants.FILING_ATTRIBUTES_SET.contains(comparator)||(comparator.contains(".")&&Constants.FILING_ATTRIBUTES_SET.contains(comparator.substring(0,comparator.indexOf("."))))) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else {
                sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
            }

            System.out.println("Filtering by score: "+isOverallScore);
            float similarityThreshold = 0f;
            for(AbstractFilter filter : filters) {
                if((filter instanceof AbstractGreaterThanFilter) && filter.getPrerequisite().equals(Constants.SIMILARITY)) {
                    Number threshold = ((AbstractGreaterThanFilter)filter).getLimit();
                    if(threshold!=null) {
                        similarityThreshold = threshold.floatValue();
                        System.out.println("Setting custom minimum score: " + similarityThreshold);
                        break;
                    }
                }
            }

            //String[] attrArray = attributes.stream().flatMap(attr->SimilarPatentServer.attributeNameHelper(attr,"").stream()).toArray(size -> new String[size]);
            AtomicReference<SearchRequestBuilder> request = new AtomicReference<>(client.prepareSearch(INDEX_NAME)
                    .setScroll(new TimeValue(60000))
                    .setTypes(TYPE_NAME)
                    .setFetchSource(true)
                    .setSize(Math.min(PAGE_LIMIT,maxLimit))
                    .setMinScore(isOverallScore&&similarityThreshold>0f?similarityThreshold:0f)
                    .setFrom(0));
            if(!comparator.isEmpty()) {
                request.set(request.get().addSort(sortBuilder));
            }
            AtomicReference<BoolQueryBuilder> filterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> queryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentFilterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentQueryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            // filters
            if(debug)System.out.println("Starting ES filters...");
            for (AbstractFilter filter : filters) {
                if(filter.getParent()==null) {
                    if(debug)System.out.println("  filter: "+filter.getName());
                    AtomicReference<BoolQueryBuilder> currentQuery;
                    AtomicReference<BoolQueryBuilder> currentFilter;
                    if(Constants.FILING_ATTRIBUTES_SET.contains(filter.getAttribute().getRootName())) {
                        System.out.println("IS FILING: "+filter.getAttribute().getRootName());
                        currentQuery = parentQueryBuilder;
                        currentFilter = parentFilterBuilder;
                    } else {
                        System.out.println("NOT FILING: "+filter.getAttribute().getRootName());
                        currentQuery = queryBuilder;
                        currentFilter = filterBuilder;
                    }
                    if (filter.contributesToScore() && isOverallScore) {
                        currentQuery.set(currentQuery.get().must(filter.getFilterQuery().boost(10)));
                    } else if (isOverallScore && filter instanceof AbstractNestedFilter) {
                        AbstractNestedFilter nestedFilter = (AbstractNestedFilter)filter;
                        QueryBuilder scoreQuery = nestedFilter.getScorableQuery();
                        QueryBuilder nonScoreQuery = nestedFilter.getNonScorableQuery();
                        if(scoreQuery!=null) {
                            currentQuery.set(currentQuery.get().must(scoreQuery.boost(10)));
                        }
                        if(nonScoreQuery!=null) {
                            currentFilter.set(currentFilter.get().must(nonScoreQuery.boost(0)));
                        }
                    } else {
                        currentFilter.set(currentFilter.get().must(filter.getFilterQuery().boost(0)));
                    }
                }
            }
            System.out.println("Starting ES attributes...");
            AtomicReference<InnerHitBuilder> innerHitBuilder = new AtomicReference<>(new InnerHitBuilder().setSize(1).setFrom(0).setFetchSourceContext(new FetchSourceContext(true)));
            for(AbstractAttribute attribute : attributes) {
                boolean isFilingType = Constants.FILING_ATTRIBUTES_SET.contains(attribute.getRootName());
                AtomicReference<BoolQueryBuilder> queryBuilderToUse = isFilingType ? parentQueryBuilder : queryBuilder;
                if(debug)System.out.println("  attribute: " + attribute.getName());
                if(attribute instanceof NestedAttribute) {
                    ((NestedAttribute) attribute).getAttributes().forEach(childAttr->{
                        handleAttributesHelper(childAttr, comparator, isOverallScore, queryBuilderToUse, request, innerHitBuilder);
                    });
                } else {
                    handleAttributesHelper(attribute, comparator, isOverallScore, queryBuilderToUse, request, innerHitBuilder);
                }

            }


            System.out.println("Combining Query...");
            // Add filter to query
            queryBuilder.set(queryBuilder.get().filter(filterBuilder.get()));
            parentQueryBuilder.set(parentQueryBuilder.get().filter(parentFilterBuilder.get()));
            queryBuilder.set(queryBuilder.get().must(
                    new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,parentQueryBuilder.get(),true)
                            .innerHit(innerHitBuilder.get())
            ));

            // Set query
            if(debug)System.out.println("\"query\": "+queryBuilder.get().toString());

            request.set(request.get().setQuery(queryBuilder.get()));

            SearchResponse response = request.get().get();

            return iterateOverSearchResults(response, hit->transformer.transform(hitToItem(hit,nestedAttrNameMap, isOverallScore)), maxLimit, merge);

        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during keyword search: "+e.getMessage());
        }
    }

    public static Item[] iterateOverSearchResults(SearchResponse response, Function<SearchHit,Item> hitTransformer, int maxLimit, boolean merge) {
        //Scroll until no hits are returned
        Item[] items = new Item[]{};
        long count = 0;
        do {
            System.out.println("Starting new batch. Num items = " + count);
            Stream<SearchHit> searchHitStream = Arrays.stream(response.getHits().getHits());
            if(!merge) {
                // run in parallel
                searchHitStream = searchHitStream.parallel();
            }
            Item[] newItems = searchHitStream.map(hit->hitTransformer.apply(hit)).toArray(size->new Item[size]);
            count+=newItems.length;
            if(merge) items=merge(items,newItems);
            response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(60000)).execute().actionGet();
        } while(response.getHits().getHits().length != 0 && (maxLimit < 0 || count < maxLimit)); // Zero hits mark the end of the scroll and the while loop.
        return items;
    }

    private static void handleAttributesHelper(@NonNull AbstractAttribute attribute, @NonNull String comparator, boolean usingScore, AtomicReference<BoolQueryBuilder> queryBuilder, AtomicReference<SearchRequestBuilder> request, AtomicReference<InnerHitBuilder> innerHitBuilder) {
        boolean isParentAttr = Constants.FILING_ATTRIBUTES_SET.contains(attribute.getRootName());
        boolean componentOfScore = (usingScore && Constants.OVERALL_SCORE_ATTRIBUTES.contains(attribute.getFullName()))
                || (attribute.getFullName().equals(comparator) && isParentAttr);
        if (attribute instanceof AbstractScriptAttribute) {
            System.out.println("  Script Component... " + attribute.getFullName());
            AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute) attribute;
            Script script = scriptAttribute.getScript();
            if (script != null) {
                if (isParentAttr) {
                    innerHitBuilder.set(innerHitBuilder.get().addScriptField(scriptAttribute.getName(), script));
                    System.out.println("Adding script to inner hit builder: " + script.getIdOrCode());

                } else {
                    request.set(request.get().addScriptField(scriptAttribute.getFullName(), script));
                    System.out.println("Adding script to main request: " + script.getIdOrCode());
                }
                // add script to query
                if (componentOfScore) {
                    System.out.println("Component of script score...");
                    // try adding custom sort script
                    QueryBuilder sortScript = scriptAttribute.getSortScript();
                    if (sortScript != null) {
                        if (attribute.getParent() != null && !attribute.getParent().isObject()) {
                            System.out.println("Is nested");
                            queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), sortScript, ScoreMode.Avg)));
                        } else {
                            System.out.println("Not nested.");
                            queryBuilder.set(queryBuilder.get().must(sortScript));
                        }
                    }
                    System.out.println("Is Script and Component of score: "+sortScript);
                }
            }
        } else if (componentOfScore) {
            System.out.println("  Score Component... " + attribute.getFullName());
            // add default sort
            if (isParentAttr) {
                String sortScript = "doc['" + attribute.getFullName() + "'].empty ? 0 : ("+(usingScore ? "_score *":"")+" doc['" + attribute.getFullName() + "'].value)";
                System.out.println("Using custom score component on filings: " + sortScript);
                QueryBuilder query = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(new Script(ScriptType.INLINE, "expression", sortScript, Collections.emptyMap())));
                if(attribute.getParent() != null && !attribute.getParent().isObject()) {
                    System.out.println("Is nested");
                    queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), query, ScoreMode.Avg)));
                } else {
                    System.out.println("Not nested");
                    queryBuilder.set(queryBuilder.get().must(query));
                }
            } else {
                System.out.println("Adding score field value factor for: " + attribute.getFullName());
                QueryBuilder query = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(attribute.getFullName()).missing(0));
                if(attribute.getParent() != null && !attribute.getParent().isObject()) {
                    System.out.println("Is nested");
                    queryBuilder.set(queryBuilder.get().must(QueryBuilders.nestedQuery(attribute.getRootName(), query, ScoreMode.Avg)));
                } else {
                    System.out.println("Not nested");
                    queryBuilder.set(queryBuilder.get().must(query));
                }
            }
        }
    }

    private static Item[] merge(Item[] v1, Item[] v2) {
        return (Item[]) ArrayUtils.addAll(v1, v2);
    }

    private static Item hitToItem(SearchHit hit, Map<String,NestedAttribute> nestedAttrNameMap, boolean isUsingScore) {
        Item item = new Item(hit.getId());
        if(debug) {
            System.out.println("fields: "+new Gson().toJson(hit.getFields()));
            System.out.println("source: "+new Gson().toJson(hit.getSource()));
        }

        hit.getSource().forEach((k,v)->{
            hitToItemHelper(k,v,item.getDataMap(),nestedAttrNameMap);
        });
        handleFields(item, hit);

        SearchHits innerHit = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
        if(innerHit != null) {
            SearchHit[] innerHits = innerHit.getHits();
            if(innerHits != null && innerHits.length > 0) {
                innerHits[0].getSource().forEach((k,v)->{
                    hitToItemHelper(k,v,item.getDataMap(),nestedAttrNameMap);
                });
                handleFields(item, innerHits[0]);
                if(debug) {
                    System.out.println("  inner fields: " + new Gson().toJson(innerHits[0].getFields()));
                    System.out.println("  inner source: " + new Gson().toJson(innerHits[0].getSource()));
                }
            }
        }
        if(isUsingScore) {
            item.addData(Constants.SIMILARITY, hit.getScore());
        }
        return item;
    }

    private static void handleFields(Item item, SearchHit hit) {
        hit.getFields().forEach((k,v)->{
            Object val = v.getValue();
            if(val!=null) {
                // check for date field
                if(k.endsWith("Date") && val instanceof Number) {
                    long longValue = ((Number)val).longValue();
                    val = Instant.ofEpochMilli(longValue).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE);
                }
                item.addData(k,val);
            }
        });
    }

    private static Map<String,Object> mapAccumulator(List<Map<String,Object>> maps) {
        Map<String,Object> newMap = new HashMap<>();
        Collection<String> keys = maps.stream().flatMap(m->m.keySet().stream()).distinct().collect(Collectors.toList());
        keys.forEach(key->{
            newMap.put(key, String.join("; ", maps.stream().map(m->m.getOrDefault(key," ").toString()).collect(Collectors.toList())).trim());
        });
        return newMap;
    }

    private static void hitToItemHelper(String attrName, Object v, Map<String,Object> itemDataMap, Map<String,NestedAttribute> nestedAttrNameMap) {
        if (v == null) return;
        if (v instanceof Map) {
            // get attr
            NestedAttribute attr = nestedAttrNameMap.get(attrName);
            if (attr != null) {
                for (AbstractAttribute nestedAttr : attr.getAttributes()) {
                    Object v2 = ((Map) v).get(nestedAttr.getName());
                    if (v2 != null) {
                        hitToItemHelper(attrName + "." + nestedAttr.getName(), v2, itemDataMap, nestedAttrNameMap);
                    }
                }
            }

        } else if (v instanceof List || v instanceof Object[]) {
            if (v instanceof Object[]) v = Arrays.stream((Object[]) v).collect(Collectors.toList());
            if (nestedAttrNameMap.containsKey(attrName)) {
                // add nested object data
                List<Map<String,Object>> nestedDataMaps = new ArrayList<>();
                for (Object obj : (List) v) {
                    if (obj instanceof Map) {
                        Map<String,Object> nestedDataMap = new HashMap<>();
                        hitToItemHelper(attrName, obj, nestedDataMap, nestedAttrNameMap);
                        nestedDataMaps.add(nestedDataMap);
                    }
                }
                if(nestedDataMaps.size()>0) {
                    Map<String,Object> reduction = mapAccumulator(nestedDataMaps);
                    if(reduction!=null && reduction.size()>0) itemDataMap.putAll(reduction);
                }
            } else {
                // add as normal list
                if (v != null) {
                    hitToItemHelper(attrName, String.join("; ", (List<String>) ((List) v).stream().map(v2 -> v2.toString()).collect(Collectors.toList())), itemDataMap, nestedAttrNameMap);
                }
            }
        } else {
            if (v != null) {
                itemDataMap.put(attrName, v);
            }
        }
    }
}
