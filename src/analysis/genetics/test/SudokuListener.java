package analysis.genetics.test;

import analysis.genetics.Listener;
import analysis.genetics.Solution;

/**
 * Created by Evan on 3/8/2017.
 */
public class SudokuListener implements Listener {
    @Override
    public void print(Solution _solution) {
        SudokuSolver solution = (SudokuSolver)_solution;
        solution.printPuzzle();
        if(SudokuPuzzle.isValid(solution.puzzle)) {
            System.out.println("SUDOKU IS SOLVED");
        }
    }
}
