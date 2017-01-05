package dl4j_neural_nets.phrase_tokenization;

/**
 * Created by ehallmark on 12/17/16.
 */
public class BiGram implements Comparable<BiGram> {
    private Word w1;
    private String w2;

    public BiGram(Word w1, String w2) { // Only actually need string
        this.w1=w1;
        this.w2=w2;
    }

    public double score() {
        return w1.score(w2);
    }

    @Override
    public String toString() {
        return w1.getText()+"_"+w2;
    }

    @Override
    public int compareTo(BiGram o) {
        return Double.compare(score(),o.score());
    }
}
