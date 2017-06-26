package ui_models.portfolios.items;

import lombok.Getter;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import ui_models.attributes.value.ValueMapNormalizer;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class VectorizedItemWrapper {
    @Getter
    private INDArray vec;
    @Getter
    private Item item;
    public VectorizedItemWrapper(Item item, INDArray vec) {
        this.item=item;
        this.vec=vec;
    }

    @Override
    public boolean equals(Object other) {
        return item.getName().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return item.getName().hashCode();
    }

    @Override
    public String toString() {
        return item.getName().toString();
    }
}
