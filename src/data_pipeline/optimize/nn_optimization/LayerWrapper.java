package data_pipeline.optimize.nn_optimization;

import lombok.Getter;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.layers.Layer;

/**
 * Created by Evan on 11/26/2017.
 */
public class LayerWrapper {
    @Getter
    private String name;
    @Getter
    private Layer.Builder vertex;
    @Getter
    private String[] inputs;
    public LayerWrapper(String name, Layer.Builder vertex, String... inputs) {
        this.name=name;
        this.vertex=vertex;
        this.inputs=inputs;
    }
}
