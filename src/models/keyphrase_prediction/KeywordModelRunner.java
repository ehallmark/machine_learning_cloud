package models.keyphrase_prediction;

import com.google.gson.Gson;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.scorers.KeywordScorer;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = true;
    public static void main(String[] args) {
        final long Kw = 5000;
        final int k1 = 10;
        final int k2 = 4;


        Collection<MultiStem> keywords = buildVocabulary();

        // apply filter 1
        INDArray F = null; //buildFMatrix(keywords);
        applyFilters(new TechnologyScorer(), F, keywords, Kw * k1, 0, Double.MAX_VALUE);

        // apply filter 2
        INDArray M = null;
        applyFilters(new TechnologyScorer(), M, keywords, Kw * k2, 0, Double.MAX_VALUE);

        // apply filter 3
        INDArray T = null;
        applyFilters(new TechnologyScorer(), T, keywords, Kw, 0, Double.MAX_VALUE);
    }

    private static Collection<MultiStem> applyFilters(KeywordScorer scorer, INDArray matrix, Collection<MultiStem> keywords, long targetNumToKeep, double minThreshold, double maxThreshold) {
        return scorer.scoreKeywords(keywords,matrix).entrySet().stream().filter(e->e.getValue()>=minThreshold&&e.getValue()<=maxThreshold).sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(targetNumToKeep)
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    return e.getKey();
                })
                .collect(Collectors.toList());
    }

    private static Collection<MultiStem> buildVocabulary() {
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.PARENT_TYPE_NAME)
                .addSort(Constants.FILING_DATE, SortOrder.ASC)
                .addDocValueField(Constants.FILING_DATE)
                .setFetchSource(false)
                .setQuery(new HasChildQueryBuilder(
                        DataIngester.TYPE_NAME,
                        QueryBuilders.matchAllQuery(),
                        ScoreMode.Max
                ).innerHit(new InnerHitBuilder().setFetchSourceContext(new FetchSourceContext(false)).addScriptField(Constants.ABSTRACT,new Script(
                        ScriptType.INLINE,
                        "painless",
                        "doc[field].values",
                        Stream.of(Arrays.asList("field",Constants.ABSTRACT)).collect(Collectors.toMap(e->e.get(0),e->e.get(1)))
                ))));
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();

        Function<SearchHit,Item> transformer = hit-> {
            if(debug) {
                System.out.println("Search hit source: " + new Gson().toJson(hit.getFields()));
                System.out.println("Search hit inner fields: " + hit.getInnerHits().values().stream().map(h->h.getHits()[0].getFields()));

            }
            return null;
        };

        DataSearcher.iterateOverSearchResults(response, transformer, Integer.MAX_VALUE, false);
        return null;
    }
}
