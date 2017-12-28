package models.text_streaming;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import org.deeplearning4j.berkeley.Triple;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static boolean debug = false;
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

        Random random = new Random(235);

        AtomicInteger cnt = new AtomicInteger(0);
        Consumer<Triple<String,LocalDate,Collection<String>>> consumer = triple -> {
            if(triple.getThird().size()>=minWords) {
                String line = triple.getFirst() + "," + triple.getSecond().toString()+","+ String.join(" ", triple.getThird())+"\n";
                BufferedWriter writer;
                boolean flush = false;
                // pick reader
                synchronized (ESTextDataSetIterator.class) {
                    int rand = random.nextInt(nonTrainingTypes.length+1);
                    if(rand < nonTrainingTypes.length && typeToCountMap.containsKey(nonTrainingTypes[rand])) {
                        FileTextDataSetIterator.Type type = nonTrainingTypes[rand];
                        AtomicInteger cntOfType = typeToCountMap.get(type);
                        if (cntOfType.getAndIncrement() >= numTest - 1) {
                            System.out.println("Finished dataset: " + type.toString());
                            typeToCountMap.remove(type);
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

    public static void iterateOverSentences(int limit, Consumer<Triple<String,LocalDate,Collection<String>>> consumer, Function<Void,Void> finallyDo) {
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(2039852)))
                .filter(QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery(Constants.GRANTED,false))
                        .should(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
                        .minimumShouldMatch(1)
                ).filter(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME,QueryBuilders.matchAllQuery(),false).innerHit(new InnerHitBuilder().setFetchSourceContext(new FetchSourceContext(true,new String[]{Constants.FILING_DATE},new String[]{}))));

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .addDocValueField("_parent")
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE, Constants.CLAIMS+"."+Constants.CLAIM},new String[]{})
                .setQuery(query)
                .addSort(SortBuilders.scoreSort());


        Function<SearchHit,Item> transformer = hit -> {
            //String asset = hit.getId();
            String filing = hit.getField("_parent").getValue();
            SearchHits parentHit = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object filingDate;
            if(parentHit!=null) {
                if(parentHit.getHits()!=null&&parentHit.getHits().length>0) {
                    filingDate = parentHit.getHits()[0].getSource().get(Constants.FILING_DATE);
                } else filingDate = null;
            } else filingDate = null;

            if(filing != null && filingDate!=null) {
                collectDocumentsFrom(hit).forEach(doc->{
                    consumer.accept(new Triple<>(filing, LocalDate.parse(filingDate.toString(), DateTimeFormatter.ISO_DATE), toList(doc)));
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
