package user_interface.ui_models.attributes;

import org.nd4j.linalg.primitives.Pair;

import java.util.List;

/**
 * Created by ehallmark on 12/7/17.
 */
public interface RangeAttribute {
    String valueSuffix();
    String getFullName();
    Object missing();
    List<Pair<Number,Number>> getRanges();
}
