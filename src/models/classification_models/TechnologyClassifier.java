package models.classification_models;

import elasticsearch.MyClient;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.Attributes;
import seeding.google.mongo.ingest.IngestPatents;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechnologyClassifier {
    private String fieldName;
    public TechnologyClassifier(String fieldName) {
        this.fieldName=fieldName;
    }

    private List<Pair<String,Double>> wipoHelper(Collection<String> patents, int limit) {
        if(patents.isEmpty()) return Collections.emptyList();
        String[] patentArray = patents.toArray(new String[patents.size()]);
        SearchRequestBuilder builder = MyClient.get().prepareSearch(IngestPatents.INDEX_NAME)
                .setTypes(IngestPatents.TYPE_NAME)
                .setFetchSource(new String[]{fieldName}, new String[]{})
                .setSize(1000)
                .setFrom(0)
                .setQuery(
                        QueryBuilders.boolQuery().filter(
                                QueryBuilders.boolQuery()
                                        .must(QueryBuilders.termQuery(Attributes.COUNTRY_CODE, "US"))
                                        .must(QueryBuilders.boolQuery()
                                                .should(QueryBuilders.termsQuery(Attributes.PUBLICATION_NUMBER, patentArray))
                                                .should(QueryBuilders.termsQuery(Attributes.APPLICATION_NUMBER_FORMATTED, patentArray))
                                        )
                                )
                );
        SearchResponse response = builder.get();
        SearchHit[] hits = response.getHits().getHits();
        return Stream.of(hits).map(item->item.getSource().get(fieldName)).filter(tech->tech!=null).flatMap(a -> a instanceof List ? ((List<String>)a).stream(): Stream.of(a)).collect(Collectors.groupingBy(tech->tech.toString(),Collectors.counting()))
                .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit)
                .map(e->new Pair<>(e.getKey(),e.getValue().doubleValue()/patents.size())).collect(Collectors.toList());
    }

    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return wipoHelper(portfolio,n);
    }

}
