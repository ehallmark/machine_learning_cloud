package elasticsearch;

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
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import seeding.Constants;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    private static TransportClient client = MyClient.get();
    private static final String INDEX_NAME = DataIngester.INDEX_NAME;
    private static final String TYPE_NAME = DataIngester.TYPE_NAME;
    private static final int PAGE_LIMIT = 10000;

    public static Item[] searchForAssets(Collection<String> attributes, Collection<? extends AbstractFilter> filters, Script script, SortBuilder comparator, int maxLimit) {
        try {
            String[] attrArray = attributes.toArray(new String[attributes.size()]);
            SearchRequestBuilder request = client.prepareSearch(INDEX_NAME)
                    .setScroll(new TimeValue(60000))
                    .setTypes(TYPE_NAME)
                    .addSort(comparator)
                    .setFetchSource(attrArray, null)
                    .storedFields("_source", "_score")
                    .setSize(Math.min(PAGE_LIMIT,maxLimit))
                    .setFrom(0);
            BoolQueryBuilder filterBuilder = QueryBuilders.boolQuery();
            // filters
            for (AbstractFilter filter : filters) {
                filterBuilder = filterBuilder
                        .must(filter.getFilterQuery());
            }
            // Add filter to query
            BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .filter(filterBuilder);
            // scripts
            if (script != null) {
                QueryBuilder scoreFunction = QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(script))
                        .boostMode(CombineFunction.REPLACE)
                        .scoreMode(FiltersFunctionScoreQuery.ScoreMode.SUM);
                // add script to query
                query = query.must(scoreFunction);
            }
            // Set query
            request = request.setQuery(query);
            String queryStr = request.toString().replace("\n","").replace("\t","");
            while(queryStr.contains("  ")) queryStr=queryStr.replace("  "," ");
            System.out.println("\"query\": "+queryStr);
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
            item.addData(k,v);
        });
        item.addData(Constants.SIMILARITY,hit.getScore()*100f);
        return item;
    }
}
