package dl4j_neural_nets.tools;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;


/**
 * Created by ehallmark on 6/28/16.
 */
public class MyPreprocessor implements TokenPreProcess, SentencePreProcessor {
    @Override
    public String preProcess(String token) {
        return token;
    }
}
