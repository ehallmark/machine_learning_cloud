package models.genetics.test;

import models.genetics.Solution;
import models.genetics.SolutionCreator;

import java.util.*;

/**
 * Created by Evan on 3/8/2017.
 */
public class SudokuCreator implements SolutionCreator {
    private static Random random = new Random(69);
    @Override
    public Collection<Solution> nextRandomSolutions(int n) {
        List<Solution> solutions = new ArrayList<>(n);
        for(int i = 0; i < n; i++) {
            int[][] grid = copyOf(SudokuPuzzle.grid);
            for(int r = 0; r < 9; r++) {
                for(int c = 0; c < 9; c++) {
                    if(!SudokuPuzzle.isFinalValue(r,c)) {
                        grid[r][c]=1+random.nextInt(9);
                    }
                }
            }
            SudokuSolver solution = new SudokuSolver(grid);
            solutions.add(solution);
        }
        return solutions;
    }

    static int[][] copyOf(int[][] grid) {
        int[][] copy = new int[9][9];
        for(int i = 0; i < 9; i++) {
            for(int j =0; j< 9; j++) {
                copy[i][j]=grid[i][j];
            }
        }
        return copy;
    }
}
