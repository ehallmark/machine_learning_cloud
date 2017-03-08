package analysis.genetics.test;

import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Created by Evan on 3/8/2017.
 */
public class SudokuSolver implements Solution{
    private static Random random = new Random(69);
    double fitness;
    int[][] puzzle;
    public SudokuSolver(int[][] puzzle) {
        this.puzzle=puzzle;
    }
    static int scoreLine(int[] line) {
        if(line==null||(line.length!=9)) return 0;
        Set<Integer> checker = new HashSet<>();
        for(int i = 0; i < 9; i++) {
            checker.add(line[i]);
        }
        int score = 0;
        for(int i = 1; i <=9; i++) {
            if(checker.contains(i)) {
                score++;
            }
        }
        return score;
    }

    static int scorePuzzle(int[][] puzzle) {
        if(puzzle==null||puzzle.length!=9) return 0;
        int score = 0;
        for(int i = 0; i < 9; i++) {
            score += scoreLine(puzzle[i]);
            int[] col = new int[]{
                    puzzle[i][0],
                    puzzle[i][1],
                    puzzle[i][2],
                    puzzle[i][3],
                    puzzle[i][4],
                    puzzle[i][5],
                    puzzle[i][6],
                    puzzle[i][7],
                    puzzle[i][8],
            };
            score+=scoreLine(col);
        }
        // check 3x3 grids

        for(int i : new int[]{0,3,6}) {
            for(int j : new int[] {0,3,6}) {
                int[] col = new int[]{
                        puzzle[i + 0][j + 0],
                        puzzle[i + 0][j + 1],
                        puzzle[i + 0][j + 2],
                        puzzle[i + 1][j + 0],
                        puzzle[i + 1][j + 1],
                        puzzle[i + 1][j + 2],
                        puzzle[i + 2][j + 0],
                        puzzle[i + 2][j + 1],
                        puzzle[i + 2][j + 2],
                };
                score += scoreLine(col);
            }
        }
        return score;
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        fitness=scorePuzzle(puzzle)-SudokuPuzzle.error(puzzle);
    }

    @Override
    public Solution mutate() {
        int[][] newGrid = SudokuCreator.copyOf(puzzle);
        for(int i = 0; i < 9; i++) {
            for(int j= 0; j < 9; j++) {
                if(!SudokuPuzzle.isValidValue(i,j,puzzle[i][j])) {
                    newGrid[i][j]=1+random.nextInt(9);
                }
            }
        }
        return new SudokuSolver(newGrid);
    }

    @Override
    public Solution crossover(Solution other) {
         // swap values
        int[][] newGrid = SudokuCreator.copyOf(puzzle);
        for(int i = 0; i < 9; i++) {
            for(int j = 0; j < 9; j++) {
                if(!SudokuPuzzle.isValidValue(i,j,puzzle[i][j])) {
                    newGrid[i][j]=((SudokuSolver)other).puzzle[i][j];
                }
            }
        }
        return new SudokuSolver(newGrid);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(o.fitness(),fitness);
    }

    public void printPuzzle() {
        for(int i = 0; i < 9; i++) {
            for(int j = 0; j<9; j++) {
                if (SudokuPuzzle.isFinalValue(i, j)) {
                    System.out.print(" [" + puzzle[i][j]+"]");
                } else if(SudokuPuzzle.isValidValue(i,j,puzzle[i][j])) {
                    System.out.print(" " + puzzle[i][j]+" ");
                } else {
                    System.out.print(" *" + puzzle[i][j]+"*");
                }
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        int maxPopulationSize = 30;
        double probMutation = 0.1;
        double probCrossover = 0.1;
        SolutionCreator creator = new SudokuCreator();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,maxPopulationSize,new SudokuListener(),10);
        algorithm.simulate(100000,probMutation,probCrossover);
    }



}
