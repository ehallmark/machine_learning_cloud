package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
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
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
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
import org.nd4j.linalg.ops.transforms.Transforms;
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
public class WordToCPCIterator {
    private int batchSize;
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private Map<String,AtomicInteger> wordToDocCountMap;
    private int numInputs;
    @Setter
    private int limit;
    private int seed;
    //private StanfordCoreNLP pipeline;
    private int minWordCount;
    private CPCSimilarityVectorizer cpcVectorizer;
    private List<DataSet> testDataSets = null;
    private int iter;
    private Set<String> testAssets = Collections.synchronizedSet(new HashSet<>());
    @Setter
    private MultiLayerNetwork network;
    public WordToCPCIterator(MultiLayerNetwork network, int batchSize, int limit, int seed, int minWordCount, boolean binarize) {
        //Properties props = new Properties();
        //props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        //pipeline = new StanfordCoreNLP(props);
        this.network=network;
        this.batchSize=batchSize;
        this.limit=limit;
        this.minWordCount=minWordCount;
        this.seed=seed;
        cpcVectorizer = new CPCSimilarityVectorizer(binarize);
    }

    public double test() {
        int numTests = 25000;
        if(testDataSets==null) {
            int prevBatchSize = this.batchSize;
            this.batchSize = 5000;
            List<String> assets = new ArrayList<>(Database.getCopyOfAllPatents());
            Random rand = new Random(seed);
            for(int i = 0; i < numTests; i++) {
                testAssets.add(assets.get(rand.nextInt(assets.size())));
            }
            testDataSets = Collections.synchronizedList(new ArrayList<>());
            iterateOverDocuments(true, list->{
                if(list==null) return;
                DataSet ds = dataSetFromPair(list);
                if(ds!=null) {
                    testDataSets.add(ds);
                }
            });
            this.batchSize=prevBatchSize;
        }
        AtomicDouble totalError = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        testDataSets.forEach(ds->{
            INDArray actualOutput = ds.getLabels();
            INDArray modelOutput = network.activateSelectedLayers(0,network.getnLayers()-1,ds.getFeatures());
            double similarity = NDArrayHelper.sumOfCosineSimByRow(actualOutput,modelOutput);
            totalError.getAndAdd(similarity);
            cnt.getAndAdd(actualOutput.rows());
        });
        return 1d - (totalError.get()/cnt.get());
    }

    public void buildVocabMap() {
        wordToDocCountMap = Collections.synchronizedMap(new HashMap<>());
        Consumer<List<Pair<String,Collection<String>>>> consumer = list -> {
            list.forEach(pair-> {
                pair.getSecond().forEach(word -> {
                    synchronized (wordToDocCountMap) {
                        wordToDocCountMap.putIfAbsent(word, new AtomicInteger(0));
                    }
                    wordToDocCountMap.get(word).getAndIncrement();
                });
            });
        };
        iterateOverDocuments(false, consumer);
        System.out.println("Vocab size before: "+wordToDocCountMap.size());
        AtomicInteger idx = new AtomicInteger(0);
        wordToIdxMap = Collections.synchronizedMap(wordToDocCountMap.entrySet().parallelStream().filter(e->{
            return e.getValue().get()>=minWordCount;
        }).collect(Collectors.toMap(e->e.getKey(),e->idx.getAndIncrement())));
        System.out.println("Vocab size after: "+wordToIdxMap.size());
        this.numInputs=wordToIdxMap.size();
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
        } else if(batch.get()<batchSize) {
            return Nd4j.create(Arrays.copyOf(vecs,batch.get()));
        } else {
            return Nd4j.create(vecs);
        }
    }

    public void trainNetwork() {
        Consumer<List<Pair<String,Collection<String>>>> consumer = list -> {
            if(list!=null&&list.size()>0) {
                DataSet ds = dataSetFromPair(list);
                if(ds!=null) {
                    synchronized (network) {
                        iter++;
                        network.fit(ds);
                    }
                }
            }
        };
        iterateOverDocuments(false, consumer);
        System.out.println("Trained on "+iter+" mini-batches.");
    }

    private DataSet dataSetFromPair(List<Pair<String,Collection<String>>> pairs) {
        pairs = pairs.stream().filter(p->cpcVectorizer.getAssetToIdxMap().containsKey(p.getFirst())).collect(Collectors.toList());
        INDArray input = createVector(pairs.stream().map(p->p.getSecond()));
        if(input == null) return null;
        INDArray output = Nd4j.vstack(pairs.stream().map(p->cpcVectorizer.vectorFor(p.getFirst())).collect(Collectors.toList()));
        return new DataSet(input,output);
    }

    public void iterateOverDocuments(boolean test, Consumer<List<Pair<String,Collection<String>>>> consumer) {
        System.out.println("STARTING NEW ITERATOR: testing="+test);
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(seed)));


        if(limit>0) {
            BoolQueryBuilder innerFilter =  QueryBuilders.boolQuery().must(
                    QueryBuilders.boolQuery() // avoid dup text
                            .should(QueryBuilders.termQuery(Constants.GRANTED,false))
                            .should(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
                            .minimumShouldMatch(1)
            );
            if (testAssets != null && testAssets.size() > 0) {
                System.out.println("Num test assets: " + testAssets.size());
                QueryBuilder idQuery = QueryBuilders.termsQuery(Constants.NAME, testAssets);
                if (test) {
                    innerFilter = innerFilter.must(idQuery);
                } else {
                    innerFilter = innerFilter.mustNot(idQuery);
                }
            }
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

}
