package data_pipeline.optimize.nn_optimization;

import lombok.Getter;
import org.deeplearning4j.nn.conf.graph.GraphVertex;

/**
 * Created by Evan on 11/26/2017.
 */
public class VertexWrapper {
    @Getter
    private String name;
    @Getter
    private GraphVertex vertex;
    @Getter
    private String[] inputs;
    public VertexWrapper(String name, GraphVertex vertex, String... inputs) {
        this.name=name;
        this.vertex=vertex;
        this.inputs=inputs;
    }
}
