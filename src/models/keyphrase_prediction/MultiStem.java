package models.keyphrase_prediction;

import lombok.Getter;
import lombok.NonNull;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;

/**
 * Created by Evan on 9/11/2017.
 */
public class MultiStem {
    protected String[] stems;
    @Getter
    protected int index;
    @Getter
    private int length;
    private Double unitHood;
    private Double termHood;
    private Double techScore;
    public MultiStem(@NonNull String[] stems, @NonNull int index) {
        this.stems=stems;
        this.index=index;
        this.length=stems.length;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof MultiStem)) return false;
        return Arrays.deepEquals(((MultiStem) other).stems, stems);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(stems);
    }

    @Override
    public String toString() {
        return "\""+String.join(" ",stems)+"\"";
    }
}
