package graphical_modeling.model.nodes;

import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 4/13/17.
 */
public class Node implements Serializable {
    private static final long serialVersionUID = 1l;
    protected static AtomicLong idCounter = new AtomicLong(0);
    @Getter
    protected final List<Node> neighbors;
    @Getter
    protected final List<Node> inBound;
    @Getter
    protected final List<Node> outBound;
    @Getter
    protected List<FactorNode> factors;
    @Getter
    protected final String label;
    @Getter
    protected int cardinality;

    public Node(String label, int cardinality) {
        this.label=label==null?"UNLABELED-"+idCounter.getAndIncrement():label;
        this.neighbors=new ArrayList<>();
        this.cardinality=cardinality;
        this.factors=new ArrayList<>();
        this.outBound = new ArrayList<>();
        this.inBound = new ArrayList<>();
    }

    public void removeNeighborConnections() {
        neighbors.forEach(n->n.removeNeighbor(this));
        outBound.forEach(n->n.removeParent(this));
        inBound.forEach(n->n.removeChild(this));
        this.outBound.clear();
        this.inBound.clear();
        this.neighbors.clear();
    }

    public List<Node> getParents() {
        return inBound;
    }

    public List<Node> getChildren() {
        return outBound;
    }

    public void removeNeighbor(Node neighbor) {
        int idx = neighbors.indexOf(neighbor);
        if(idx>=0) {
            neighbors.remove(idx);
        }
    }

    public void removeChild(Node neighbor) {
        int idx = outBound.indexOf(neighbor);
        if(idx>=0) {
            outBound.remove(idx);
        }
    }

    public void removeParent(Node neighbor) {
        int idx = inBound.indexOf(neighbor);
        if(idx>=0) {
            inBound.remove(idx);
        }
    }

    public void addNeighbor(Node neighbor) {
        if(!neighbors.contains(neighbor)) {
            neighbors.add(neighbor);
        }
    }

    public void addParent(Node neighbor) {
        if(!inBound.contains(neighbor)) {
            inBound.add(neighbor);
        }
    }

    public void addChild(Node neighbor) {
        if(!outBound.contains(neighbor)) {
            outBound.add(neighbor);
        }
    }

    public void addFactor(FactorNode otherFactor) {
        if(!factors.contains(otherFactor)) {
            factors.add(otherFactor);
        }
    }

    @Override
    public int hashCode() {
        return (label==null)? super.hashCode():label.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof Node)) {
            return false;
        }

        String otherLabel = ((Node) other).getLabel();

        if(otherLabel==null) return super.equals(other);

        return otherLabel.equals(label);
    }
}
