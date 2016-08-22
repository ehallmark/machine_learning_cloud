package seeding;

import org.canova.nlp.tokenization.tokenizer.TokenPreProcess;
import org.canova.nlp.tokenization.tokenizer.Tokenizer;
import org.canova.nlp.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Created by ehallmark on 8/22/16.
 */
public class MyDoNothingTokenizerFactory implements TokenizerFactory {
    @Override
    public Tokenizer create(String s) {
        return null;
    }

    public Tokenizer create(List<String> tokens) {
        return new DoNothingTokenizer(tokens);
    }

    @Override
    public Tokenizer create(InputStream inputStream) {
        return null;
    }

    @Override
    public void setTokenPreProcessor(TokenPreProcess tokenPreProcess) {

    }
}
