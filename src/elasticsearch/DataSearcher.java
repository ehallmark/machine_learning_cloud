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
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

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

    public static Item[] searchForAssets(Collection<String> attributes, Collection<? extends AbstractFilter> filters, Script similarityScript, String comparator, SortOrder sortOrder, int maxLimit) {
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
            String[] attrArray = attributes.toArray(new String[attributes.size()]);
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
                if (filter.contributesToScore()&&isOverallScore) {
                    query = query.must(filter.getFilterQuery().boost(10));
                } else {
                    filterBuilder = filterBuilder
                            .must(filter.getFilterQuery().boost(0));
                }
            }
            // Add filter to query
            query = query.filter(filterBuilder);
            // scripts
            if (similarityScript != null) {
                QueryBuilder scoreFunction = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(similarityScript).setWeight(100))
                        .boostMode(CombineFunction.AVG)
                        .scoreMode(FiltersFunctionScoreQuery.ScoreMode.AVG);
                // add script to query
                query = query.must(scoreFunction);
                request = request.addScriptField(Constants.SIMILARITY,similarityScript);
            }
            if (isOverallScore) {
                // add in AI Value
                query = query.must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.fieldValueFactorFunction(Constants.AI_VALUE)
                        .missing(0)
                ));
            }
            // Set query
            request = request.setQuery(query);
            //String queryStr = request.toString().replace("\n","").replace("\t","");
            //while(queryStr.contains("  ")) queryStr=queryStr.replace("  "," ");
            //System.out.println("\"query\": "+queryStr);
            SearchResponse response = request.get();
            //Scroll until no hits are returned
            Item[] items = new Item[]{};
            do {
                System.out.println("Starting new batch. Num items = " + items.length);
                items=merge(items,Arrays.stream(response.getHits().getHits()).map(hit->hitToItem(hit)).toArray(size->new Item[size]));
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

    private static Item hitToItem(SearchHit hit) {
        Item item = new Item(hit.getId());
        //System.out.println("Hit id: "+item.getName());
        //System.out.println(" Source: "+hit.getSourceAsString());
        hit.getSource().forEach((k,v)->{
            v = (v instanceof Number) || (v instanceof String) ? v : new Gson().toJson(v);
            /*if(v instanceof String[]) {
                v = String.join("; ", (String[]) v);
            } else if (v instanceof List) {
                v = String.join("; ", (List)v);
            }*/
            item.addData(k,v);
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
}
