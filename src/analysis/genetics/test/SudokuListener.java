package analysis.genetics.test;

import analysis.genetics.Listener;
import analysis.genetics.Solution;

import java.util.concurrent.TimeUnit;

/**
 * Created by Evan on 3/8/2017.
 */
public class SudokuListener implements Listener {
    @Override
    public void print(Solution _solution) {
        SudokuSolver solution = (SudokuSolver)_solution;
        solution.printPuzzle();
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}
