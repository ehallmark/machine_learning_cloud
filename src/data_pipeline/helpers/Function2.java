package data_pipeline.helpers;

/**
 * Created by ehallmark on 11/10/17.
 */
public interface Function2<X1,X2,Y> {
    Y apply(X1 x1, X2 x2);
}
