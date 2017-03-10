package analysis.genetics.keyword_analysis;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 3/10/17.
 */
public class Word {
    AtomicInteger docsAppearedInCounter;
    private String word;
    private Double score;
    public Word(String word, int docsAppearedIn) {
        this.word=word;
        docsAppearedInCounter=new AtomicInteger(docsAppearedIn);
    }

    public String getWord() {
        return word;
    }

    public double tfidfScore(int elementFrequency) {
        if(score==null) {
            score=elementFrequency-Math.log(Math.E+docsAppearedInCounter.get());
        }
        return score;
    }

    @Override
    public boolean equals(Object other) {
        return word.equals(((Word)other).word);
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }
}
