package models.similarity_models.deep_word_to_cpc_encoding_model;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import lombok.Getter;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.signatures.CPCSimilarityVectorizer;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import tools.Stemmer;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class DeepWordToCPCIterator implements DataSetIterator {
    private int batchSize;
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private Map<String,AtomicInteger> wordToDocCountMap;
    //private StanfordCoreNLP pipeline;
    private CPCSimilarityVectorizer cpcVectorizer;
    private ArrayBlockingQueue<DataSet> queue;
    private AtomicBoolean started;
    private AtomicBoolean finished;
    private int limit;
    public DeepWordToCPCIterator(int limit, int batchSize) {
        //Properties props = new Properties();
        //props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        //pipeline = new StanfordCoreNLP(props);
        // constructor for VOCAB iterator
        this.batchSize=batchSize;
        this.limit=limit;
    }

    public DeepWordToCPCIterator(int limit, Map<String, INDArray> cpcEncodings, Map<String,Integer> wordToIdxMap, int batchSize, boolean binarize, boolean normalize, boolean probability) {
        // constructor for main iterator
        this.wordToIdxMap=wordToIdxMap;
        this.batchSize=batchSize;
        this.limit=limit;
        this.started = new AtomicBoolean(false);
        this.finished = new AtomicBoolean(false);
        int queueCapacity = 1000;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        cpcVectorizer = new CPCSimilarityVectorizer(cpcEncodings, binarize, normalize, probability);
    }

    private void start() {
        if(started.get()) throw new RuntimeException("Iterator has already started...");
        this.started.set(true);
        Consumer<List<Pair<String,Collection<String>>>> consumer = list -> {
            if(list!=null&&list.size()>0) {
                DataSet ds = dataSetFromPair(list);
                if(ds!=null) {
                    try {
                        System.out.print("-");
                        queue.put(ds);
                    } catch(Exception e) {
                        System.out.println("Error adding to queue.");
                    }
                }
            }
        };
        new RecursiveAction() {
            @Override
            protected void compute() {
                iterateOverDocuments(System.currentTimeMillis(),limit, consumer, (fin)->{finished.set(true); return null;});
            }
        }.fork();
    }


    public void buildVocabMap(int minDocCount, int maxDocCount) {
        wordToDocCountMap = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger cnt = new AtomicInteger(0);
        Consumer<List<Pair<String,Collection<String>>>> consumer = list -> {
            list.forEach(pair-> {
                if(cnt.getAndIncrement()%10000==9999) System.out.println("Seen vocab for: "+cnt.get());
                pair.getSecond().forEach(word -> {
                    synchronized (wordToDocCountMap) {
                        wordToDocCountMap.putIfAbsent(word, new AtomicInteger(0));
                    }
                    wordToDocCountMap.get(word).getAndIncrement();
                });
            });
        };
        iterateOverDocuments(0, limit, consumer, null);
        System.out.println("Vocab size before: "+wordToDocCountMap.size());
        AtomicInteger idx = new AtomicInteger(0);
        wordToIdxMap = Collections.synchronizedMap(wordToDocCountMap.entrySet().parallelStream().filter(e->{
            return e.getValue().get()>=minDocCount&&e.getValue().get()<maxDocCount;
        }).collect(Collectors.toMap(e->e.getKey(),e->idx.getAndIncrement())));
        System.out.println("Vocab size after: "+wordToIdxMap.size());
    }

    private INDArray createVector(Stream<Collection<String>> wordStream) {
        return createBagOfWordsVector(wordStream,wordToIdxMap,batchSize);
    }

    public static INDArray createBagOfWordsVector(Stream<Collection<String>> wordStream, Map<String,Integer> wordToIdxMap, int batchSize) {
        AtomicInteger batch = new AtomicInteger(0);
        int numInputs = wordToIdxMap.size();
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
        if(batch.get()==0) {
            return null;
        } else if(batch.get()<batchSize) {
            return Nd4j.create(Arrays.copyOf(vecs,batch.get()));
        } else {
            return Nd4j.create(vecs);
        }
    }

    private DataSet dataSetFromPair(List<Pair<String,Collection<String>>> pairs) {
        List<Pair<INDArray,Collection<String>>> vecPairs = pairs.stream().map(p->new Pair<>(cpcVectorizer.vectorFor(p.getFirst()),p.getSecond())).filter(p->p.getFirst()!=null).collect(Collectors.toList());
        INDArray input = createVector(vecPairs.stream().map(p->p.getSecond()));
        if(input == null) return null;
        INDArray output = Nd4j.vstack(vecPairs.stream().map(p->p.getFirst()).collect(Collectors.toList()));
        return new DataSet(input,output);
    }

    public void iterateOverDocuments(long seed, int limit, Consumer<List<Pair<String,Collection<String>>>> consumer, Function<Void,Void> finallyDo) {
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(seed)));
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

        AtomicInteger cnt = new AtomicInteger(0);
        AtomicReference<List<Pair<String,Collection<String>>>> dataBatch = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));
        Function<SearchHit,Item> transformer = hit -> {
            String asset = hit.getId();
            synchronized (dataBatch) {
                int i = cnt.getAndIncrement();
                //System.out.println("batch: "+i);
                dataBatch.get().add(new Pair<>(asset, collectWordsFrom(hit)));
                if(i>=batchSize-1) {
                   // System.out.println("Completed batch!!!");
                    cnt.set(0);
                    consumer.accept(dataBatch.get());
                    dataBatch.set(Collections.synchronizedList(new ArrayList<>()));
                }
            }
            return null;
        };
        SearchResponse response = request.get();
        DataSearcher.iterateOverSearchResults(response, transformer, limit, false);
        System.out.println("Finished iterating ES.");
        if(finallyDo!=null)finallyDo.apply(null);
    }

    private Collection<String> collectWordsFrom(SearchHit hit) {
        String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase().trim();
        String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase().trim();
        String text = String.join(". ",Stream.of(inventionTitle, abstractText).filter(s->s!=null&&s.length()>0).collect(Collectors.toList())).replaceAll("[^a-z ]","");
        return Stream.of(text.split("\\s+")).parallel().filter(word->!Constants.STOP_WORD_SET.contains(word)).distinct().map(word->new Stemmer().stem(word)).collect(Collectors.toList());
        /*Annotation doc = new Annotation(text);
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
                    String pos;
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
        return appeared;*/
    }

    @Override
    public DataSet next(int i) {
        if(!started.get()) {
            start();
        }
        try {
            return queue.take();
        } catch(Exception e) {
            return null;
        }
    }

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return wordToIdxMap.size();
    }

    @Override
    public int totalOutcomes() {
        return CPCVariationalAutoEncoderNN.VECTOR_SIZE;
    }

    @Override
    public boolean resetSupported() {
        return false;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        // do nothing
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        return Math.max(limit,0);
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        if(queue.isEmpty() && finished.get()) return false;
        return true;
    }

    @Override
    public DataSet next() {
        return next(batch());
    }
}
