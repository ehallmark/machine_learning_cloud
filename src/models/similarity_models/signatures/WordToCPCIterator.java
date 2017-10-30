package models.similarity_models.signatures;

import cpc_normalization.CPC;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import lombok.Getter;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.stages.Stage;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.ai_db_updater.tools.Helper;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class WordToCPCIterator implements DataSetIterator {
    private int batchSize;
    private Map<String,Integer> wordToIdxMap;
    private int numInputs;
    private int limit;
    private int seed;
    private StanfordCoreNLP pipeline;
    @Getter
    private Iterator<DataSet> iterator;
    public WordToCPCIterator(int batchSize, Map<String,Integer> wordToIdxMap, int limit, int seed) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        pipeline = new StanfordCoreNLP(props);
        this.batchSize=batchSize;
        this.limit=limit;
        this.seed=seed;
        this.numInputs=wordToIdxMap.size();
        this.wordToIdxMap=wordToIdxMap;
        reset();
    }

    private INDArray createVector(Stream<Collection<String>> wordStream) {
        AtomicInteger batch = new AtomicInteger(0);
        double[][] vecs = new double[batchSize][numInputs];
        wordStream.forEach(words->{
            double[] vec = new double[numInputs];
            words.forEach(word->{
                Integer idx = wordToIdxMap.get(word);
                if(idx!=null) {
                    vec[idx] = 1d;
                }
            });
            vecs[batch.get()] = vec;
            batch.getAndIncrement();
        });
        if(batch.get()<batch()) {
            System.out.println("Did not find a full batch");
            return Nd4j.create(Arrays.copyOf(vecs,batch.get()));
        } else {
            return Nd4j.create(vecs);
        }
    }

    @Override
    public DataSet next(int i) {
        return iterator.next();
    }

    private Iterator<DataSet> getWordVectorStream() {
        QueryBuilder query;
        if(limit>0) {
            query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(seed)));
        } else {
            query = QueryBuilders.matchAllQuery();
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

        ArrayBlockingQueue<DataSet> queue = new ArrayBlockingQueue<>(batch()*20);
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicReference<INDArray> inputBatch = new AtomicReference<>(Nd4j.create(batch(),inputColumns()));
        AtomicReference<INDArray> outputBatch = new AtomicReference<>(Nd4j.create(batch(),totalOutcomes()));
        CPCSimilarityVectorizer cpcVectorizer = new CPCSimilarityVectorizer();
        Function<SearchHit,Item> transformer = hit -> {
            String asset = hit.getId();
            INDArray cpcEncoding = cpcVectorizer.vectorFor(asset);
            if(cpcEncoding!=null) {
                INDArray vec = createVector(Stream.of(collectWordsFrom(hit)));
                int i = cnt.getAndIncrement();
                synchronized (inputBatch) {
                    inputBatch.get().putRow(i, vec);
                    outputBatch.get().putRow(i, cpcEncoding);
                    if (i >= batch()-1) {
                        // finished
                        if(!queue.offer(new DataSet(inputBatch.get(),outputBatch.get()))); {
                            System.out.println("Queue is full...");
                            try {
                                TimeUnit.MILLISECONDS.sleep(500);
                            } catch(Exception e) {
                                
                            }
                        }
                        inputBatch.set(Nd4j.create(batch(),inputColumns()));
                        outputBatch.set(Nd4j.create(batch(),totalOutcomes()));
                    }
                }
            }
            return null;
        };
        DataSearcher.iterateOverSearchResults(request.get(),transformer,limit,false);

        return new Iterator<DataSet>() {
            private DataSet next;
            @Override
            public boolean hasNext() {
                try {
                    next = queue.poll(60, TimeUnit.SECONDS);
                    return next!=null;
                } catch(Exception e) {
                    System.out.println("No more items found.");
                    return false;
                }
            }

            @Override
            public DataSet next() {
                return next;
            }
        };
    }

    private Collection<String> collectWordsFrom(SearchHit hit) {
        String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase().trim();
        String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase().trim();
        String text = String.join(". ",Stream.of(inventionTitle, abstractText).filter(s->s!=null&&s.length()>0).collect(Collectors.toList()));
        Annotation doc = new Annotation(text);
        pipeline.annotate(doc);
        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        Set<String> appeared = Collections.synchronizedSet(new HashSet<String>());
        for(CoreMap sentence: sentences) {
            // traversing the words in the current sentence
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                boolean valid = true;
                for(int i = 0; i < word.length(); i++) {
                    if(!Character.isAlphabetic(word.charAt(i))) {
                        valid = false;
                    }
                }
                if(!valid) continue;

                // could be the stem
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                if(Constants.STOP_WORD_SET.contains(lemma)||Constants.STOP_WORD_SET.contains(word)) {
                    continue;
                }

                try {
                    String stem = new Stemmer().stem(lemma);
                    String pos = null;
                    if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                        // this is the POS tag of the token
                        pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                        if (Stage.validPOS.contains(pos)) {
                            // don't want to end in adjectives (nor past tense verb)
                            if (!Stage.adjectivesPOS.contains(pos) && !((!pos.startsWith("N"))&&(word.endsWith("ing")||word.endsWith("ed")))) {
                                appeared.add(word);
                            }
                        }
                    }

                } catch(Exception e) {
                    System.out.println("Error while stemming: "+lemma);
                }
            }
        }
        return appeared;
    }

    @Override
    public int totalExamples() {
        return limit;
    }

    @Override
    public int inputColumns() {
        return numInputs;
    }

    @Override
    public int totalOutcomes() {
        return inputColumns();
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        iterator = getWordVectorStream();
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException("cursor()");
    }

    @Override
    public int numExamples() {
        return totalExamples();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {

    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public List<String> getLabels() {
        return null;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DataSet next() {
        return next(batch());
    }
}
