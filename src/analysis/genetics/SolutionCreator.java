package analysis.genetics;

import java.util.Collection;
import java.util.List;
/**
 * Created by Evan on 2/19/2017.
 */
public interface SolutionCreator {
    Collection<Solution> nextRandomSolutions(int n);
}
