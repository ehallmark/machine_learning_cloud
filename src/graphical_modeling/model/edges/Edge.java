package graphical_modeling.model.edges;

import java.io.Serializable;

/**
 * Created by Evan on 4/23/2017.
 */
public abstract class Edge<T> implements Serializable {
    private static final long serialVersionUID = 1l;
    protected T node1;
    protected T node2;
    protected Edge(T node1, T node2) {
        this.node1=node1;
        this.node2=node2;
    }

    public abstract boolean equals(Object other);
    public abstract int hashCode();

    public T getNode1() {return node1; }
    public T getNode2() { return node2;}

}
