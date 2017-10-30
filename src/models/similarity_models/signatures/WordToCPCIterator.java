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
import lombok.Setter;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.stages.Stage;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
import seeding.Database;
import seeding.ai_db_updater.tools.Helper;
import tools.Stemmer;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
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
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private Map<String,AtomicInteger> wordToDocCountMap;
    private int numInputs;
    @Setter
    private int limit;
    private int seed;
    //private StanfordCoreNLP pipeline;
    @Getter
    private Iterator<DataSet> iterator;
    private RecursiveAction task;
    private int minWordCount;
    private CPCSimilarityVectorizer cpcVectorizer;
    private List<DataSet> testDataSets = null;
    private Set<String> testAssets = Collections.synchronizedSet(new HashSet<>());
    public WordToCPCIterator(int batchSize, int limit, int seed, int minWordCount, boolean binarize) {
        //Properties props = new Properties();
        //props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        //pipeline = new StanfordCoreNLP(props);
        this.batchSize=batchSize;
        this.limit=limit;
        this.minWordCount=minWordCount;
        this.seed=seed;
        cpcVectorizer = new CPCSimilarityVectorizer(binarize);
    }

    public Iterator<DataSet> getTestIterator() {
        int numTests = 10000;
        if(testDataSets==null) {
            List<String> assets = new ArrayList<>(Database.getCopyOfAllPatents());
            Random rand = new Random(seed);
            for(int i = 0; i < numTests; i++) {
                testAssets.add(assets.get(rand.nextInt(assets.size())));
            }
            testDataSets = Collections.synchronizedList(new ArrayList<>());
            Iterator<DataSet> testIter = getWordVectorIterator(true);
            int idx = 0;
            while(testIter.hasNext()) {
                testDataSets.add(testIter.next());
                if(idx%1000==999)System.out.println("Finished test matrix: "+idx);
                idx++;
            }

            //reset();
        }
        return testDataSets.iterator();
    };

    public void buildVocabMap() {
        wordToDocCountMap = Collections.synchronizedMap(new HashMap<>());
        Iterator<List<Pair<String,Collection<String>>>> iterator = getWordsIterator(false);
        while(iterator.hasNext()) {
            iterator.next().parallelStream().forEach(pair->{
                pair.getSecond().forEach(word->{
                    synchronized (wordToDocCountMap) {
                        wordToDocCountMap.putIfAbsent(word, new AtomicInteger(0));
                    }
                    wordToDocCountMap.get(word).getAndIncrement();
                });
            });
        }
        System.out.println("Vocab size before: "+wordToDocCountMap.size());
        AtomicInteger idx = new AtomicInteger(0);
        wordToIdxMap = Collections.synchronizedMap(wordToDocCountMap.entrySet().parallelStream().filter(e->{
            return e.getValue().get()>=minWordCount;
        }).collect(Collectors.toMap(e->e.getKey(),e->idx.getAndIncrement())));
        System.out.println("Vocab size after: "+wordToIdxMap.size());
        this.numInputs=wordToIdxMap.size();
        reset();
    }

    public void setWordToIdxMap(Map<String,Integer> map) {
        this.wordToIdxMap=map;
        this.numInputs=wordToIdxMap.size();
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
        if(batch.get()==0) {
            return null;
        } else if(batch.get()<batch()) {
            return Nd4j.create(Arrays.copyOf(vecs,batch.get()));
        } else {
            return Nd4j.create(vecs);
        }
    }

    @Override
    public DataSet next(int i) {
        return iterator.next();
    }

    private Iterator<DataSet> getWordVectorIterator(boolean test) {
        Iterator<List<Pair<String,Collection<String>>>> wordsIterator = getWordsIterator(test);
        return new Iterator<DataSet>() {
            DataSet ds;
            @Override
            public boolean hasNext() {
                List<Pair<String, Collection<String>>> pairs = null;
                while(wordsIterator.hasNext()) {
                    pairs = wordsIterator.next();
                    ds = dataSetFromPair(pairs);
                    if(ds!=null) break;
                }
                return ds != null;
            }

            @Override
            public DataSet next() {
                return ds;
            }
        };
    }

    private DataSet dataSetFromPair(List<Pair<String,Collection<String>>> pairs) {
        pairs = pairs.stream().filter(p->cpcVectorizer.getAssetToIdxMap().containsKey(p.getFirst())).collect(Collectors.toList());
        INDArray input = createVector(pairs.stream().map(p->p.getSecond()));
        if(input == null) return null;
        INDArray output = Nd4j.vstack(pairs.stream().map(p->cpcVectorizer.vectorFor(p.getFirst())).collect(Collectors.toList()));
        return new DataSet(input,output);
    }

    public Iterator<List<Pair<String,Collection<String>>>> getWordsIterator(boolean test) {
        System.out.println("STARTING NEW ITERATOR: testing="+test);
        BoolQueryBuilder query;
        if(limit>0) {
            query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(seed)));
        } else {
            query = QueryBuilders.boolQuery();
        }
        BoolQueryBuilder innerFilter =  QueryBuilders.boolQuery().must(
                QueryBuilders.boolQuery() // avoid dup text
                        .should(QueryBuilders.termQuery(Constants.GRANTED,false))
                        .should(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
                        .minimumShouldMatch(1)
        );
        if(testAssets!=null&&testAssets.size()>0) {
            System.out.println("Num test assets: "+testAssets.size());
            QueryBuilder idQuery = QueryBuilders.termsQuery(Constants.NAME,testAssets);
            if (test) {
                innerFilter = innerFilter.must(idQuery);
            } else {
                innerFilter = innerFilter.mustNot(idQuery);
            }
        }
        query = query.filter(innerFilter);

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

        ArrayBlockingQueue<List<Pair<String,Collection<String>>>> queue = new ArrayBlockingQueue<>(batch()*20);
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicReference<List<Pair<String,Collection<String>>>> dataBatch = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));
        Function<SearchHit,Item> transformer = hit -> {
            String asset = hit.getId();
            while(queue.size()>10000) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch(Exception e) {

                }
            }
            synchronized (dataBatch) {
                int i = cnt.getAndIncrement();
                //System.out.println("batch: "+i);
                dataBatch.get().add(new Pair<>(asset, collectWordsFrom(hit)));
                if(i>=batch()-1) {
                   // System.out.println("Completed batch!!!");
                    cnt.set(0);
                    queue.offer(dataBatch.get());
                    dataBatch.set(Collections.synchronizedList(new ArrayList<>()));
                }
            }
            return null;
        };
        AtomicBoolean finishedIteratingElasticSearch = new AtomicBoolean(false);
        SearchResponse response = request.get();
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                DataSearcher.iterateOverSearchResults(response,transformer,limit,false);
                finishedIteratingElasticSearch.set(true);
                System.out.println("Finished iterating ES.");
            }
        };
        task.fork();

        return new Iterator<List<Pair<String,Collection<String>>>>() {
            private List<Pair<String,Collection<String>>> next;
            @Override
            public boolean hasNext() {
                AtomicInteger i = new AtomicInteger(0);
                while(!finishedIteratingElasticSearch.get()) {
                    try {
                        int timeout = 30;
                        next = queue.poll(timeout, TimeUnit.SECONDS);
                        if(next!=null) return true;
                    } catch (Exception e) {
                        next = null;
                        System.out.println("Elasticsearch timed out "+i.getAndIncrement()+" times...");
                    }
                }
                // finished iterating
                try {
                    int timeout = 30;
                    next = queue.poll(timeout, TimeUnit.SECONDS);
                } catch (Exception e) {
                    next = null;
                    System.out.println("Elasticsearch timed out "+i.getAndIncrement()+" times...");
                }
                return next != null;
            }

            @Override
            public List<Pair<String,Collection<String>>> next() {
                return next;
            }
        };
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

    @Override //TODO change back to true
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        if(task!=null) {
            try {
                if(!(task.isDone()||task.isCancelled())) {
                    task.cancel(true);
                }
            } catch(Exception e) {
                System.out.println("Error interrupting ES search during reset...");
                e.printStackTrace();
            } finally {
                task = null;
            }
        }
        iterator = getWordVectorIterator(false);
        System.out.println("Resetting iterator...");
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
