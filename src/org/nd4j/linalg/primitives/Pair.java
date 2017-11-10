package org.nd4j.linalg.primitives;
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import java.io.Serializable;
import java.util.Comparator;

public class Pair<F, S> implements Serializable, Comparable<Pair<F, S>> {
    static final long serialVersionUID = 42L;
    F first;
    S second;

    public F getFirst() {
        return this.first;
    }

    public S getSecond() {
        return this.second;
    }

    public void setFirst(F pFirst) {
        this.first = pFirst;
    }

    public void setSecond(S pSecond) {
        this.second = pSecond;
    }

    public Pair<S, F> reverse() {
        return new Pair(this.second, this.first);
    }

    public boolean equals(Object o) {
        if(this == o) {
            return true;
        } else if(!(o instanceof Pair)) {
            return false;
        } else {
            boolean var10000;
            label43: {
                label29: {
                    Pair pair = (Pair)o;
                    if(this.first != null) {
                        if(!this.first.equals(pair.first)) {
                            break label29;
                        }
                    } else if(pair.first != null) {
                        break label29;
                    }

                    if(this.second != null) {
                        if(this.second.equals(pair.second)) {
                            break label43;
                        }
                    } else if(pair.second == null) {
                        break label43;
                    }
                }

                var10000 = false;
                return var10000;
            }

            var10000 = true;
            return var10000;
        }
    }

    public int hashCode() {
        int result = this.first != null?this.first.hashCode():0;
        result = 29 * result + (this.second != null?this.second.hashCode():0);
        return result;
    }

    public String toString() {
        return "(" + this.getFirst() + ", " + this.getSecond() + ")";
    }

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public int compareTo(Pair<F, S> o) {
        return (new Pair.DefaultLexicographicPairComparator()).compare(this, o);
    }

    public static <S, T> Pair<S, T> newPair(S first, T second) {
        return new Pair(first, second);
    }

    public static <S, T> Pair<S, T> makePair(S first, T second) {
        return new Pair(first, second);
    }

    public static class DefaultLexicographicPairComparator<F extends Comparable<F>, S extends Comparable<S>> implements Comparator<Pair<F, S>> {
        public DefaultLexicographicPairComparator() {
        }

        public int compare(Pair<F, S> o1, Pair<F, S> o2) {
            int firstCompare = ((Comparable)o1.getFirst()).compareTo(o2.getFirst());
            return firstCompare != 0?firstCompare:((Comparable)o1.getSecond()).compareTo(o2.getSecond());
        }
    }

    public static class LexicographicPairComparator<F, S> implements Comparator<Pair<F, S>> {
        Comparator<F> firstComparator;
        Comparator<S> secondComparator;

        public int compare(Pair<F, S> pair1, Pair<F, S> pair2) {
            int firstCompare = this.firstComparator.compare(pair1.getFirst(), pair2.getFirst());
            return firstCompare != 0?firstCompare:this.secondComparator.compare(pair1.getSecond(), pair2.getSecond());
        }

        public LexicographicPairComparator(Comparator<F> firstComparator, Comparator<S> secondComparator) {
            this.firstComparator = firstComparator;
            this.secondComparator = secondComparator;
        }
    }

    public static class ReverseSecondComparator<S, T extends Comparable<? super T>> implements Comparator<Pair<S, T>> {
        public ReverseSecondComparator() {
        }

        public int compare(Pair<S, T> p1, Pair<S, T> p2) {
            return ((Comparable)p2.getSecond()).compareTo(p1.getSecond());
        }
    }

    public static class SecondComparator<S, T extends Comparable<? super T>> implements Comparator<Pair<S, T>> {
        public SecondComparator() {
        }

        public int compare(Pair<S, T> p1, Pair<S, T> p2) {
            return ((Comparable)p1.getSecond()).compareTo(p2.getSecond());
        }
    }

    public static class ReverseFirstComparator<S extends Comparable<? super S>, T> implements Comparator<Pair<S, T>> {
        public ReverseFirstComparator() {
        }

        public int compare(Pair<S, T> p1, Pair<S, T> p2) {
            return ((Comparable)p2.getFirst()).compareTo(p1.getFirst());
        }
    }

    public static class FirstComparator<S extends Comparable<? super S>, T> implements Comparator<Pair<S, T>> {
        public FirstComparator() {
        }

        public int compare(Pair<S, T> p1, Pair<S, T> p2) {
            return ((Comparable)p1.getFirst()).compareTo(p2.getFirst());
        }
    }
}
