package seeding;


import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/1/16.
 */
public class WordVectorizer {
    private WeightLookupTable<VocabWord> lookupTable;
    private VocabCache<VocabWord> vocab;
    public WordVectorizer(WeightLookupTable<VocabWord> lookupTable, VocabCache<VocabWord> vocab) {
        this.lookupTable=lookupTable;
        this.vocab=vocab;
    }

    public INDArray getVector(String txt) {
        if(txt==null)return null;
        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        List<String> tokens = tokenizerFactory.create(txt).getTokens().stream().filter(t->t!=null&&t.length()>0&&lookupTable.getVocabCache().hasToken(t)).collect(Collectors.toList());
        if(tokens.isEmpty()) return null;
        return VectorHelper.TFIDFcentroidVector(lookupTable, vocab, tokens);
    }

    public static void main(String[] args) throws Exception {
        System.out.println(new WordVectorizer(WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH),true).lookupTable(),WordVectorSerializer.readVocab(new File(Constants.VOCAB_FILE))).getVector("this is a test to see how well this can vectorize words ok cool").toString());
    }
}
