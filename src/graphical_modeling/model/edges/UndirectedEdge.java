package graphical_modeling.model.edges;

import java.util.Objects;

/**
 * Created by ehallmark on 4/13/17.
 */
public class UndirectedEdge<T> extends Edge<T> {
    private static final long serialVersionUID = 1l;

    final int hash;
    public UndirectedEdge(T node1, T node2) {
        super(node1,node2);
        int h1 = node1.hashCode();
        int h2 = node2.hashCode();
        long hash = h1 + h2;
        if(hash>=Integer.MAX_VALUE||hash<=Integer.MIN_VALUE) {
            hash = Math.max(Objects.hash(node1,node2),Objects.hash(node2,node1));
        }
        this.hash=(int)hash;
    }

    // Checks whether connects the same pair of nodes
    // This checks the String valued labels, so make the sure labels are distinct!
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

        if (node2.equals(otherNode1) && node1.equals(otherNode2)) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
