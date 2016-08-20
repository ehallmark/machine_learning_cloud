package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Created by ehallmark on 8/15/16.
 */
public class PCAPatent extends Patent {
    public PCAPatent(String name, INDArray vector) {
        super(name, vector);
    }
}
