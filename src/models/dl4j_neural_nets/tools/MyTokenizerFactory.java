package models.dl4j_neural_nets.tools;

import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.InputStream;

/**
 * Created by ehallmark on 12/1/16.
 */
public class MyTokenizerFactory implements TokenizerFactory {
    private TokenPreProcess tokenPreProcess;

    public MyTokenizerFactory() {
        super();
        setTokenPreProcessor(new MyPreprocessor());
    }

    @Override
    public Tokenizer create(String toTokenize) {
        DefaultTokenizer t =  new DefaultTokenizer(toTokenize);
        t.setTokenPreProcessor(tokenPreProcess);
        return t;
    }

    @Override
    public Tokenizer create(InputStream toTokenize) {
        throw new UnsupportedOperationException("Must tokenize from string not an inputstream");
    }

    @Override
    public void setTokenPreProcessor(TokenPreProcess preProcessor) {
        this.tokenPreProcess = preProcessor;
    }

    /**
     * Returns TokenPreProcessor set for this TokenizerFactory instance
     *
     * @return TokenPreProcessor instance, or null if no preprocessor was defined
     */
    @Override
    public TokenPreProcess getTokenPreProcessor() {
        return tokenPreProcess;
    }
}
