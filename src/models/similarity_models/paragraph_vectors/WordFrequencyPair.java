package models.similarity_models.paragraph_vectors;


import org.nd4j.linalg.primitives.PairBackup;

/**
 * Created by ehallmark on 9/5/16.
 */
public class WordFrequencyPair<T,V extends Comparable<V>> implements Comparable< WordFrequencyPair<T,V>> {
    private T first;
    private V second;
    public WordFrequencyPair(T first, V second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }

    @Override
    public int compareTo(WordFrequencyPair<T,V> other) {
        return getSecond().compareTo(other.getSecond());
    }

    public PairBackup<T,V> toPair() {
        return new PairBackup<>(first,second);
    }

    @Override
    public String toString() {
        return "["+first+","+second+"]";
    }
}
