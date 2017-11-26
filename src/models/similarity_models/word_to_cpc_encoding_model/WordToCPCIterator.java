package models.similarity_models.word_to_cpc_encoding_model;

import lombok.Getter;
import models.text_streaming.WordVectorizerToCPCVectorIterator;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import tools.Stemmer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class WordToCPCIterator extends WordVectorizerToCPCVectorIterator {
    @Getter
    private static final Function<String,Collection<String>> defaultTokenizer = str->Stream.of(str.toLowerCase().split("\\s+")).filter(w->!Constants.STOP_WORD_SET.contains(w)).map(w->new Stemmer().stem(w)).filter(w->!Constants.STOP_WORD_SET.contains(w)).collect(Collectors.toList());
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private Map<String,AtomicInteger> wordToDocCountMap;

    public WordToCPCIterator(LabelAwareIterator documentIterator, int batchSize) {
        super(batchSize,documentIterator,null,defaultTokenizer,null,-1,false);
    }

    public WordToCPCIterator(LabelAwareIterator documentIterator, Map<String, INDArray> cpcEncodings, Map<String,Integer> wordToIdxMap, int batchSize, boolean binarize, boolean normalize, boolean probability) {
        super(batchSize,documentIterator,wordToIdxMap,defaultTokenizer,new CPCSimilarityVectorizer(cpcEncodings, binarize, normalize, probability),CPCVariationalAutoEncoderNN.VECTOR_SIZE,false);
        this.wordToIdxMap=wordToIdxMap;
    }

    public WordToCPCIterator(LabelAwareIterator documentIterator, Map<String, INDArray> cpcEncodings, Map<String,Integer> wordToIdxMap, Map<String,Integer> docCountMap, int totalNumDocuments, int batchSize, boolean binarize, boolean normalize, boolean probability) {
        super(batchSize,documentIterator,wordToIdxMap,docCountMap,totalNumDocuments,defaultTokenizer,new CPCSimilarityVectorizer(cpcEncodings, binarize, normalize, probability),CPCVariationalAutoEncoderNN.VECTOR_SIZE);
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

}
