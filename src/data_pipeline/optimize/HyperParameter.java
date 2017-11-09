package data_pipeline.optimize;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 11/9/17.
 */
public class HyperParameter<T> {
    protected AtomicReference<T> value;

    public T get() {
        return value.get();
    }
}
