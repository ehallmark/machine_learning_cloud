package dl4j_neural_nets.phrase_tokenization;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 12/17/16.
 */
public class Word {
    private static Map<String,Word> stringToWordMap = new HashMap<>();
    private double totalCount;
    private Map<String,AtomicDouble> coOccurenceFrequencies;
    private String text;
    private Word(String text) {
        this.text=text;
        totalCount=0.0;
        coOccurenceFrequencies=new HashMap<>();
    }

    public static void reset() {
        stringToWordMap.clear();
    }

    public static Collection<Word> getWords() {
        return stringToWordMap.values();
    }

    private static Word findOrCreateWord(String word) {
        if(stringToWordMap.containsKey(word)) {
            return stringToWordMap.get(word);
        } else {
            Word w = new Word(word);
            stringToWordMap.put(word,w);
            return w;
        }
    }

    public static void increaseCountAndCoOccurence(String _w1, String _w2) {
        Word w1 = findOrCreateWord(_w1);
        w1.totalCount+=1.0;
        if(!w1.coOccurenceFrequencies.containsKey(_w2)) {
            w1.coOccurenceFrequencies.put(_w2,new AtomicDouble(1.0));
        } else {
            w1.coOccurenceFrequencies.get(_w2).getAndAdd(1.0);
        }
    }

    public String getText() {
        return text;
    }

    public Collection<String> getNextWords() {
        return coOccurenceFrequencies.keySet();
    }

    public double score(String otherWord) {
        double count_i = this.totalCount;

        if(!stringToWordMap.containsKey(otherWord)||!coOccurenceFrequencies.containsKey(otherWord)) {
            return Double.NEGATIVE_INFINITY;
        }

        double count_j = stringToWordMap.get(otherWord).totalCount;
        double count_ij = coOccurenceFrequencies.get(otherWord).get();

        if(count_ij >= 1.0) {
            return (count_ij-PhraseDeterminator.delta)
                    / (count_i*count_j);
        } else {
            return Double.NEGATIVE_INFINITY;
        }
    }

}
