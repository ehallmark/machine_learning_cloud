package models.similarity_models.paragraph_vectors;


import org.nd4j.linalg.primitives.Pair;

/**
 * Created by ehallmark on 9/5/16.
 */
public class FloatFrequencyPair<T> implements Comparable<FloatFrequencyPair<T>> {
    private T first;
    private float second;
    public FloatFrequencyPair(T first, float second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public float getSecond() {
        return second;
    }

    @Override
    public int compareTo(FloatFrequencyPair<T> other) {
        return Float.compare(getSecond(),other.getSecond());
    }

    @Override
    public String toString() {
        return "["+first+","+second+"]";
    }
}
