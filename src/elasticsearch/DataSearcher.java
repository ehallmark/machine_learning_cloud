package elasticsearch;

import com.google.gson.Gson;
import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
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
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static user_interface.server.SimilarPatentServer.SORT_DIRECTION_FIELD;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    private static TransportClient client = MyClient.get();
    private static final String INDEX_NAME = DataIngester.INDEX_NAME;
    private static final String TYPE_NAME = DataIngester.TYPE_NAME;
    private static final int PAGE_LIMIT = 10000;
    private static final boolean debug = false;

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<? extends AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap) {
        return searchForAssets(attributes,filters,comparator,sortOrder,maxLimit,nestedAttrNameMap,item->item,true);
    }

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<? extends AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, ItemTransformer transformer, boolean merge) {
        try {
            // Run elasticsearch
            boolean isOverallScore = comparator.equals(Constants.OVERALL_SCORE);
            SortBuilder sortBuilder;
            // only pull ids by setting first parameter to empty list
            boolean usingScore;
            if(isOverallScore||comparator.equals(Constants.SIMILARITY)||Constants.FILING_ATTRIBUTES_SET.contains(comparator)) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
                usingScore = true;
            } else {
                sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
                usingScore = false;
            }
            //String[] attrArray = attributes.stream().flatMap(attr->SimilarPatentServer.attributeNameHelper(attr,"").stream()).toArray(size -> new String[size]);
            SearchRequestBuilder request = client.prepareSearch(INDEX_NAME)
                    .setScroll(new TimeValue(60000))
                    .setTypes(TYPE_NAME)
                    .setFetchSource(true)
                    .addSort(sortBuilder)
                    .setSize(Math.min(PAGE_LIMIT,maxLimit))
                    .setFrom(0);
            AtomicReference<BoolQueryBuilder> filterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> queryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentFilterBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            AtomicReference<BoolQueryBuilder> parentQueryBuilder = new AtomicReference<>(QueryBuilders.boolQuery());
            // filters
            System.out.println("Starting ES filters...");
            for (AbstractFilter filter : filters) {
                if(filter.getParent()==null) {
                    System.out.println("  filter: "+filter.getName());
                    AtomicReference<BoolQueryBuilder> currentQuery;
                    AtomicReference<BoolQueryBuilder> currentFilter;
                    if(Constants.FILING_ATTRIBUTES_SET.contains(filter.getPrerequisite())) {
                        currentQuery = parentQueryBuilder;
                        currentFilter = parentFilterBuilder;
                    } else {
                        currentQuery = queryBuilder;
                        currentFilter = filterBuilder;
                    }
                    if (filter.contributesToScore() && isOverallScore) {
                        currentQuery.set(currentQuery.get().must(filter.getFilterQuery().boost(10)));
                    } else {
                        currentFilter.set(currentFilter.get().must(filter.getFilterQuery().boost(0)));
                    }
                }
            }
            System.out.println("Starting ES attributes...");
            InnerHitBuilder innerHitBuilder = new InnerHitBuilder().setSize(1).setFrom(0).setFetchSourceContext(new FetchSourceContext(true));
            for(AbstractAttribute attribute : attributes) {
                System.out.println("  attribute: "+attribute.getName());
                boolean componentOfScore = usingScore && (attribute.getName().equals(comparator) || (comparator.equals(Constants.OVERALL_SCORE) && Constants.OVERALL_SCORE_ATTRIBUTES.contains(attribute.getName())));
                if(attribute instanceof AbstractScriptAttribute) {
                    System.out.println("  Script Component...");
                    AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute)attribute;
                    Script script = scriptAttribute.getScript();
                    if(script!=null) {
                        boolean isParentAttr = Constants.FILING_ATTRIBUTES_SET.contains(scriptAttribute.getName());
                        if(isParentAttr) {
                            innerHitBuilder = innerHitBuilder.addScriptField(scriptAttribute.getName(),script);
                        } else {
                            request = request.addScriptField(scriptAttribute.getName(), script);
                        }
                        // add script to query
                        if(componentOfScore) {
                            // try adding custom sort script
                            QueryBuilder sortScript = scriptAttribute.getSortScript();
                            if (sortScript != null) {
                                if (isParentAttr) {
                                    parentQueryBuilder.set(parentQueryBuilder.get().must(sortScript));
                                } else {
                                    queryBuilder.set(queryBuilder.get().must(sortScript));
                                }
                            }
                        }
                    }
                } else if(componentOfScore) {
                    System.out.println("  Score Component...");
                    // add default sort
                    if(Constants.FILING_ATTRIBUTES_SET.contains(attribute.getName())) {
                        parentQueryBuilder.set(parentQueryBuilder.get().must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(
                                new Script(ScriptType.INLINE, "expression", "doc['" + attribute.getName() + "'].empty ? 0 : (_score * doc['" + attribute.getName() + "'].value)", Collections.emptyMap())
                        ))));
                    } else {
                        queryBuilder.set(queryBuilder.get().must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(attribute.getName()).missing(0))));
                    }
                }
            }


            System.out.println("Combining Query...");
            // Add filter to query
            queryBuilder.set(queryBuilder.get().filter(filterBuilder.get()));
            parentQueryBuilder.set(parentQueryBuilder.get().filter(parentFilterBuilder.get()));
            queryBuilder.set(queryBuilder.get().must(
                    new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,parentQueryBuilder.get(),true)
                            .innerHit(innerHitBuilder)
            ));

            // Set query
            System.out.println("\"query\": "+queryBuilder.get().toString());

            request = request.setQuery(queryBuilder.get());

            SearchResponse response = request.get();
            //Scroll until no hits are returned
            Item[] items = new Item[]{};
            do {
                System.out.println("Starting new batch. Num items = " + items.length);
                Item[] newItems = Arrays.stream(response.getHits().getHits()).map(hit->transformer.transform(hitToItem(hit,nestedAttrNameMap))).toArray(size->new Item[size]);
                if(merge) items=merge(items,newItems);
                response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(60000)).execute().actionGet();
            } while(response.getHits().getHits().length != 0 && items.length < maxLimit); // Zero hits mark the end of the scroll and the while loop.
            return items;
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during keyword search: "+e.getMessage());
        }
    }

    private static Item[] merge(Item[] v1, Item[] v2) {
        return (Item[]) ArrayUtils.addAll(v1, v2);
    }

    private static Item hitToItem(SearchHit hit, Map<String,NestedAttribute> nestedAttrNameMap) {
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
        item.addData(Constants.OVERALL_SCORE,hit.getScore());
        return item;
    }

    private static void handleFields(Item item, SearchHit hit) {
        hit.getFields().forEach((k,v)->{
            Object val = v.getValue();
            if(val!=null) {
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
