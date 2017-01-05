package tools;

import dl4j_neural_nets.tools.PhrasePreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.util.List;
import java.util.concurrent.RecursiveTask;

/**
 * Created by ehallmark on 9/3/16.
 */
public class GetTokensThread extends RecursiveTask<List<String>> {
    private TokenizerFactory t;
    private String text;
    public GetTokensThread(TokenizerFactory t, String text) {
        this.t=t;
        this.text=text;
    }
    @Override
    protected List<String> compute() {
        return t.create(new PhrasePreprocessor().preProcess(text)).getTokens();
    }
}
