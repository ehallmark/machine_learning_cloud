package graphical_modeling.util;

import java.util.Objects;

/**
 * Created by Evan on 4/16/2017.
 */
public class Pair<X,Y> {
    public final X _1;
    public final Y _2;

    public Pair(X _1, Y _2) {
        this._1=_1;
        this._2=_2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1,_2);
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof Pair)) return false;

        Pair p2 = (Pair)other;
        return (p2._1.equals(_1)&&p2._2.equals(_2));
    }

    @Override
    public String toString() {
        return _1.toString()+";"+_2.toString();
    }

    public static void main(String[] args) {

    }
}
