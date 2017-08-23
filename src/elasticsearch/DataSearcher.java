package elasticsearch;

import com.google.gson.Gson;
import org.apache.commons.lang.ArrayUtils;
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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
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

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<? extends AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap) {
        return searchForAssets(attributes,filters,comparator,sortOrder,maxLimit,nestedAttrNameMap,item->item,true);
    }

    public static Item[] searchForAssets(Collection<AbstractAttribute> attributes, Collection<? extends AbstractFilter> filters, String comparator, SortOrder sortOrder, int maxLimit, Map<String,NestedAttribute> nestedAttrNameMap, ItemTransformer transformer, boolean merge) {
        try {
            // Run elasticsearch
            boolean isOverallScore = comparator.equals(Constants.OVERALL_SCORE);
            SortBuilder sortBuilder;
            // only pull ids by setting first parameter to empty list
            if(isOverallScore||comparator.equals(Constants.SIMILARITY)) {
                sortBuilder = SortBuilders.scoreSort().order(sortOrder);
            } else {
                sortBuilder = SortBuilders.fieldSort(comparator).order(sortOrder);
            }
            String[] attrArray = attributes.stream().flatMap(attr->SimilarPatentServer.attributeNameHelper(attr,"").stream()).toArray(size -> new String[size]);
            SearchRequestBuilder request = client.prepareSearch(INDEX_NAME)
                    .setScroll(new TimeValue(60000))
                    .setTypes(TYPE_NAME)
                    .addSort(sortBuilder)
                    .setFetchSource(attrArray, null)
                    .storedFields("_source", "_score","fields")
                    .setSize(Math.min(PAGE_LIMIT,maxLimit))
                    .setFrom(0);
            BoolQueryBuilder filterBuilder = QueryBuilders.boolQuery();
            BoolQueryBuilder query = QueryBuilders.boolQuery();
            // filters
            for (AbstractFilter filter : filters) {
                if(filter.getParent()==null) {
                    if (filter.contributesToScore() && isOverallScore) {
                        query = query.must(filter.getFilterQuery().boost(10));
                    } else {
                        filterBuilder = filterBuilder
                                .must(filter.getFilterQuery().boost(0));
                    }
                }
            }
            for(AbstractAttribute attribute : attributes) {
                if(attribute instanceof AbstractScriptAttribute) {
                    AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute)attribute;
                    Script script = scriptAttribute.getScript();
                    if(script!=null) {
                        request = request.addScriptField(scriptAttribute.getName(), script);
                        // add script to query
                        QueryBuilder scriptQuery = scriptAttribute.getScriptQuery();
                        if(scriptQuery!=null) {
                            query = query.must(scriptQuery);
                        }
                    }
                }
            }

            // Add filter to query
            query = query.filter(filterBuilder);

            if (isOverallScore) {
                // add in AI Value
                query = query.must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(Constants.AI_VALUE)
                        .missing(0)
                ));
            }
            // Set query
            System.out.println("\"query\": "+query.toString());

            request = request.setQuery(query);
            //String queryStr = request.toString().replace("\n","").replace("\t","");
            //while(queryStr.contains("  ")) queryStr=queryStr.replace("  "," ");
            //System.out.println("\"query\": "+queryStr);

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
        //System.out.println("Hit id: "+item.getName());
        //System.out.println(" Source: "+hit.getSourceAsString());
        hit.getSource().forEach((k,v)->{
            hitToItemHelper(k,v,item.getDataMap(),nestedAttrNameMap);
        });
        SearchHitField similarityField = hit.getField(Constants.SIMILARITY);
        if(similarityField!=null) {
            Number similarityScore = similarityField.getValue();
            if (similarityScore != null) {
                item.addData(Constants.SIMILARITY,similarityScore.floatValue()*100f);
            }
        }
        item.addData(Constants.OVERALL_SCORE,hit.getScore());
        return item;
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
