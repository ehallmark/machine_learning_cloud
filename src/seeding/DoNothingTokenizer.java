package seeding;


import org.canova.nlp.tokenization.tokenizer.TokenPreProcess;
import org.canova.nlp.tokenization.tokenizer.Tokenizer;

import java.util.Iterator;
import java.util.List;

/**
 * Created by ehallmark on 8/22/16.
 */
public class DoNothingTokenizer implements Tokenizer {
    private List<String> tokens;
    private Iterator<String> tokenIter;
    public DoNothingTokenizer(List<String> tokens) {
        this.tokens=tokens;
        this.tokenIter=tokens.iterator();
    }

    @Override
    public boolean hasMoreTokens() {
        return tokenIter.hasNext();
    }

    @Override
    public int countTokens() {
        return tokens.size();
    }

    @Override
    public String nextToken() {
        return tokenIter.next();
    }

    @Override
    public List<String> getTokens() {
        return tokens;
    }

    @Override
    public void setTokenPreProcessor(TokenPreProcess tokenPreProcess) {

    }

}
