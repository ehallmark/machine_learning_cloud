package analysis.genetics.keyword_analysis;

/**
 * Created by Evan on 2/24/2017.
 */
public class Word implements Comparable<Word> {
    private double score;
    private String word;

    public String getWord() {
        return word;
    }
    public double getScore() {
        return score;
    }

    public Word(String word, double score) {
        this.word=word;
        this.score=score;
    }
    public int compareTo(Word other) {
        if (word.equals(other.getWord()))return 0;
        else {
            int doubleComp = Double.compare(other.score,score);
            if(doubleComp==0){
                return word.compareTo(other.getWord());
            }
            return doubleComp;
        }
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof String) {
            return word.equals((String)other);
        } else {
            return word.hashCode()==((Word)other).word.hashCode();
        }
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }
}
