package graphical_modeling.model.edges;

import java.util.Objects;

/**
 * Created by Evan on 4/23/2017.
 */
public class DirectedEdge<T> extends Edge<T> {
    private static final long serialVersionUID = 1l;

    public DirectedEdge(T node1, T node2) {
        super(node1,node2);
    }

    // Checks whether connects the same (directed) pair of nodes
    @Override
    public boolean equals(Object other) {
        if(!(other instanceof Edge)) {
            return false;
        }

        Edge otherEdge = (Edge)other;

        Object otherNode1 = otherEdge.getNode1();
        Object otherNode2 = otherEdge.getNode2();

        if(node1.equals(otherNode1)&&node2.equals(otherNode2)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
         return Objects.hash(node1,node2);
    }

}
