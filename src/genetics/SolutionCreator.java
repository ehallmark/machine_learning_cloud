package genetics;

import java.util.Collection;

/**
 * Created by Evan on 2/19/2017.
 */
public interface SolutionCreator<T extends Solution> {
    Collection<T> nextRandomSolutions(int n);

}
