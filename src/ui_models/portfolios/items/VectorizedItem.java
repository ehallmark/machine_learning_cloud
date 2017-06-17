package ui_models.portfolios.items;

import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Created by Evan on 6/17/2017.
 */
public class VectorizedItem extends Item {
    @Getter
    private INDArray vec;
    public VectorizedItem(String name, INDArray vec) {
        super(name);
        this.vec=vec;
    }
}
