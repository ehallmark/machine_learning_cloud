package models.keyphrase_prediction.stages;

import java.io.File;
import java.util.Collection;

/**
 * Created by ehallmark on 9/12/17.
 */
public interface Stage<V> {
    void loadData();
    V run(boolean run);
    V get();
    File getFile(int year);
}
