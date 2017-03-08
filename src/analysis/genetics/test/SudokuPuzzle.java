package analysis.genetics.test;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Evan on 3/8/2017.
 */
public class SudokuPuzzle {
    static int[][] grid;
    static int[][] actual;
    static {
        grid = new int[][] {
                new int[]{
                        0,0,0,0,4,8,2,0,6
                },
                new int[] {
                        0,0,0,0,0,7,1,0,0
                },
                new int[] {
                        0,0,4,0,5,0,0,0,0
                },
                new int[] {
                        0,0,7,0,5,0,0,6,0
                },
                new int[] {
                        0,8,0,0,0,0,0,0,0
                },
                new int[] {
                        6,2,0,0,0,3,0,7,0
                },
                new int[] {
                        0,0,3,9,0,6,4,0,0
                },
                new int[] {
                        0,0,2,0,0,1,0,0,3
                },
                new int[] {
                        4,0,0,0,0,0,0,2,9
                }
        };
        actual = new int[][] {
                new int[]{
                        7,3,5,1,4,8,2,9,6
                },
                new int[] {
                        2,9,8,6,3,7,1,4,5
                },
                new int[] {
                        1,6,4,2,5,9,8,3,7
                },
                new int[] {
                        3,4,7,5,1,2,9,6,8
                },
                new int[] {
                        5,8,9,7,6,4,3,1,2
                },
                new int[] {
                        6,2,1,8,9,3,5,7,4
                },
                new int[] {
                        8,7,3,9,2,6,4,5,1
                },
                new int[] {
                        9,5,2,4,7,1,6,8,3
                },
                new int[] {
                        4,1,6,3,8,5,7,2,9
                }
        };
    }

    static boolean isFinalValue(int row, int col) {
        return grid[row][col]!=0;
    }

    static double error(int[][] puzzle) {
        double error = 0d;
        for(int i = 0; i < 9; i++) {
            for(int j = 0; j < 9; j++) {
                if(puzzle[i][j]!=actual[i][j])
                error+=10;
            }
        }
        return error;
    }

    static boolean isValidRow(int[] line) {
        if(line==null||(line.length!=9)) return false;
        Set<Integer> checker = new HashSet<>();
        for(int i = 0; i < 9; i++) {
            checker.add(line[i]);
        }
        for(int i = 1; i <=9; i++) {
            if(!checker.contains(i)) {
                return false;
            }
        }
        return true;
    }

    static boolean isValid(int[][] puzzle) {
        if(puzzle==null||puzzle.length!=9) return false;
        int score = 0;
        for(int i = 0; i < 9; i++) {
            if(!isValidRow(puzzle[i])) return false;
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
            if(!isValidRow(col)) return false;
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
                if(!isValidRow(col)) {
                    return false;
                }
            }
        }
        return true;
    }
}
