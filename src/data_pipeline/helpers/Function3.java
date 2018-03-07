package data_pipeline.helpers;

/**
 * Created by ehallmark on 11/10/17.
 */
public interface Function3<X1,X2,X3,Y> {
    Y apply(X1 x1, X2 x2, X3 x3);
}
