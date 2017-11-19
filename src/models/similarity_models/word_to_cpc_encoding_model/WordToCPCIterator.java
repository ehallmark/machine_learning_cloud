package models.similarity_models.word_to_cpc_encoding_model;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import lombok.Getter;
import models.text_streaming.BOWToCPCVectorIterator;
import models.text_streaming.ESTextDataSetIterator;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.signatures.CPCSimilarityVectorizer;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.primitives.Pair;
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
public class WordToCPCIterator extends BOWToCPCVectorIterator {
    private static Function<String,Collection<String>> defaultTokenizer = str->Stream.of(str.split("\\s+")).filter(w->!Constants.STOP_WORD_SET.contains(w)).map(w->new Stemmer().stem(w)).filter(w->!Constants.STOP_WORD_SET.contains(w)).collect(Collectors.toList());
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private Map<String,AtomicInteger> wordToDocCountMap;
    public WordToCPCIterator(LabelAwareIterator documentIterator, int batchSize) {
        super(batchSize,documentIterator,null,defaultTokenizer,null,-1);
    }

    public WordToCPCIterator(LabelAwareIterator documentIterator, Map<String, INDArray> cpcEncodings, Map<String,Integer> wordToIdxMap, int batchSize, boolean binarize, boolean normalize, boolean probability) {
        super(batchSize,documentIterator,wordToIdxMap,defaultTokenizer,new CPCSimilarityVectorizer(cpcEncodings, binarize, normalize, probability),CPCVariationalAutoEncoderNN.VECTOR_SIZE);
        this.wordToIdxMap=wordToIdxMap;
    }

    public void buildVocabMap(int minDocCount, int maxDocCount) {
        wordToDocCountMap = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger cnt = new AtomicInteger(0);
        Consumer<Pair<String,Collection<String>>> consumer = pair -> {
            if(cnt.getAndIncrement()%10000==9999) System.out.println("Seen vocab for: "+cnt.get());
            pair.getSecond().forEach(word -> {
                synchronized (wordToDocCountMap) {
                    wordToDocCountMap.putIfAbsent(word, new AtomicInteger(0));
                }
                wordToDocCountMap.get(word).getAndIncrement();
            });
        };

        while(documentIterator.hasNext()) {
            LabelledDocument doc = documentIterator.nextDocument();
            if(doc!=null&&doc.getLabels().size()>0&&doc.getContent()!=null&&doc.getContent().length()>0) {
                consumer.accept(new Pair<>(doc.getLabels().get(0), defaultTokenizer.apply(doc.getContent())));
            }
        }
        documentIterator.reset();

        System.out.println("Vocab size before: "+wordToDocCountMap.size());
        AtomicInteger idx = new AtomicInteger(0);
        wordToIdxMap = Collections.synchronizedMap(wordToDocCountMap.entrySet().parallelStream().filter(e->{
            return e.getValue().get()>=minDocCount&&e.getValue().get()<maxDocCount;
        }).collect(Collectors.toMap(e->e.getKey(),e->idx.getAndIncrement())));
        System.out.println("Vocab size after: "+wordToIdxMap.size());
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

}
