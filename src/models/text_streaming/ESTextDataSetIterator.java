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
    public static void main(String[] args) throws Exception {
        final int limit = 5000000;
        final int numTest = 30000;
        final int minWords = 10;

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
                BufferedWriter writer;
                boolean flush = false;
                // pick reader
                if(textIdx.get()<nonTrainingTypes.length) {
                    FileTextDataSetIterator.Type type = nonTrainingTypes[textIdx.get()];
                    AtomicInteger cntOfType = typeToCountMap.get(type);
                    if(cntOfType.getAndIncrement()>=numTest-1) {
                        System.out.println("Finished dataset: "+type.toString());
                        textIdx.getAndIncrement();
                        flush=true;
                    }
                    writer = typeToWriterMap.get(type);
                } else {
                    // train
                    writer = typeToWriterMap.get(FileTextDataSetIterator.Type.TRAIN);
                    if(cnt.getAndIncrement()%10000==9999) {
                        System.out.println("FInished train: "+cnt.get());
                    }
                }
                try {
                    synchronized (ESTextDataSetIterator.class) {
                        writer.write(line);
                        if(flush) writer.flush();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };

        iterateOverDocuments(limit,consumer,null);

        typeToWriterMap.forEach((type,writer)->{
            try {
                writer.flush();
                writer.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static Collection<String> collectWordsFrom(SearchHit hit) {
        String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString();
        String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString();
        String text = preProcess(String.join(" ", Stream.of(inventionTitle, abstractText).filter(s->s!=null&&s.length()>0).collect(Collectors.toList())));
        return preProcessToList(text);
    }

    public static String preProcess(String text) {
        return text.toLowerCase().replace("-"," ").replace(","," ").replace("."," ").replaceAll("[^a-z ]","").trim();
    }

    public static List<String> preProcessToList(String text) {
        return Stream.of(text.split("\\s+")).collect(Collectors.toList());
    }

    public static void iterateOverAllDocuments(Consumer<Pair<String,Collection<String>>> consumer) {
        iterateOverDocuments(-1,consumer,null);
    }

    public static void iterateOverDocuments(int limit, Consumer<Pair<String,Collection<String>>> consumer, Function<Void,Void> finallyDo) {
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(2039852)));
        if(limit>0) {
            BoolQueryBuilder innerFilter =  QueryBuilders.boolQuery().must(
                    QueryBuilders.boolQuery()
                            .must(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
            );
            query = query.filter(innerFilter);
        }

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(query);
        if(limit>0) {
            request = request.addSort(SortBuilders.scoreSort());
        }


        Function<SearchHit,Item> transformer = hit -> {
            String asset = hit.getId();
            consumer.accept(new Pair<>(asset, collectWordsFrom(hit)));
            return null;
        };
        SearchResponse response = request.get();
        DataSearcher.iterateOverSearchResults(response, transformer, limit, false);
        System.out.println("Finished iterating ES.");
        if(finallyDo!=null)finallyDo.apply(null);
    }
}
