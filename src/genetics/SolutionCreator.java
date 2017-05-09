package genetics;

import java.util.Collection;

/**
 * Created by Evan on 2/19/2017.
 */
public interface SolutionCreator {
    Collection<Solution> nextRandomSolutions(int n);

}
