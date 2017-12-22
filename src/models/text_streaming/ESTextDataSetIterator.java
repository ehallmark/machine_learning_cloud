package models.text_streaming;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 11/19/2017.
 */
public class ESTextDataSetIterator {
    private static boolean debug = true;
    public static void main(String[] args) throws Exception {
        final int limit = -1;
        final int numTest = 20000;
        final int minWords = 5;

        Map<FileTextDataSetIterator.Type,BufferedWriter> typeToWriterMap = Collections.synchronizedMap(new HashMap<>());
        FileTextDataSetIterator.getTypeMap().forEach((type, file)->{
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                typeToWriterMap.put(type,writer);
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
        Map<FileTextDataSetIterator.Type,AtomicInteger> typeToCountMap = Collections.synchronizedMap(new HashMap<>());
        typeToWriterMap.keySet().forEach(type->typeToCountMap.put(type,new AtomicInteger(0)));

        FileTextDataSetIterator.Type[] nonTrainingTypes = new FileTextDataSetIterator.Type[]{
                FileTextDataSetIterator.Type.DEV1,
                FileTextDataSetIterator.Type.DEV2,
                FileTextDataSetIterator.Type.DEV3,
                FileTextDataSetIterator.Type.DEV4,
                FileTextDataSetIterator.Type.TEST
        };

        AtomicInteger textIdx = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);
        Consumer<Pair<String,Collection<String>>> consumer = pair -> {
            if(pair.getSecond().size()>=minWords) {
                String line = pair.getFirst() + "," + String.join(" ", pair.getSecond())+"\n";
                if(debug) System.out.println(line);
                BufferedWriter writer;
                boolean flush = false;
                // pick reader
                synchronized (ESTextDataSetIterator.class) {
                    if (textIdx.get() < nonTrainingTypes.length) {
                        FileTextDataSetIterator.Type type = nonTrainingTypes[textIdx.get()];
                        AtomicInteger cntOfType = typeToCountMap.get(type);
                        if (cntOfType.getAndIncrement() >= numTest - 1) {
                            System.out.println("Finished dataset: " + type.toString());
                            textIdx.getAndIncrement();
                            flush = true;
                        }
                        writer = typeToWriterMap.get(type);
                    } else {
                        // train
                        writer = typeToWriterMap.get(FileTextDataSetIterator.Type.TRAIN);
                        if (cnt.getAndIncrement() % 10000 == 9999) {
                            System.out.println("Finished train: " + cnt.get());
                        }
                    }
                    try {
                        writer.write(line);
                        if (flush) writer.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        iterateOverSentences(limit,consumer,null);

        typeToWriterMap.forEach((type,writer)->{
            try {
                writer.flush();
                writer.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static List<String> collectDocumentsFrom(SearchHit hit) {
        String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString();
        String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString();
        List claims = ((List)hit.getSourceAsMap().getOrDefault(Constants.CLAIMS, Collections.emptyList()));
        String firstClaim = claims.isEmpty() ? "" : ((Map<String,Object>) claims.get(0)).getOrDefault(Constants.CLAIM, "").toString();
        if(debug) {
            System.out.println("Claim: "+firstClaim);
        }
        return Stream.of(inventionTitle, abstractText, firstClaim).map(text->preProcess(text)).filter(s->s!=null&&s.length()>0).collect(Collectors.toList());
    }

    public static String preProcess(String text) {
        return text.toLowerCase().replace("-"," ").replace(","," ").replace("."," ").replaceAll("[^a-z ]","").trim();
    }

    public static List<String> toList(String text) {
        return Stream.of(text.split("\\s+")).collect(Collectors.toList());
    }

    public static void iterateOverSentences(int limit, Consumer<Pair<String,Collection<String>>> consumer, Function<Void,Void> finallyDo) {
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(2039852)))
                .filter(QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery(Constants.GRANTED,false))
                        .should(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
                        .minimumShouldMatch(1)
                );

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                //.addStoredField("_parent")
                .addDocValueField("_parent")
                .setFetchSource(new String[]{"_parent",Constants.ABSTRACT,Constants.INVENTION_TITLE, Constants.CLAIMS+"."+Constants.CLAIM},new String[]{})
                .setQuery(query)
                .addSort(SortBuilders.scoreSort());


        Function<SearchHit,Item> transformer = hit -> {
            //String asset = hit.getId();
            String filing = hit.getField("_parent").getValue();
            Object filingSource = hit.getSource().get("_parent");
            if(debug)System.out.println("Filing field: "+filing);
            if(debug)System.out.println("Filing source: "+filingSource);
            if(filing != null) {
                collectDocumentsFrom(hit).forEach(doc->{
                    consumer.accept(new Pair<>(filing, toList(doc)));
                });
            }
            return null;
        };
        SearchResponse response = request.get();
        DataSearcher.iterateOverSearchResults(response, transformer, limit, false);
        System.out.println("Finished iterating ES.");
        if(finallyDo!=null)finallyDo.apply(null);
    }
}
