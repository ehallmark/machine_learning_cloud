package graphical_modeling.model.functions.heuristic;

import graphical_modeling.model.nodes.Node;

import java.util.List;
import java.util.function.Function;

/**
 * Created by ehallmark on 4/25/17.
 */
public class MinimalCliqueSizeHeuristic implements TriangulationHeuristic {
    @Override
    //TODO
    public Function<List<Node>, Integer> nextNodeToEliminateFunction() {
        return (nodes)-> {
            Node node = nodes.stream().reduce((n1, n2)->n1.getNeighbors().size()<n2.getNeighbors().size()?n1:n2).get();
            return nodes.indexOf(node);
        };
    }
}
