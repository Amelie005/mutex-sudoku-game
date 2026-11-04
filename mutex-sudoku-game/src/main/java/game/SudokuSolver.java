package game;

import java.util.ArrayList;
import java.util.List;

/**
 * Backtracking-Solver (Board as int[81], 0 = empty, index = row * 9 + col).
 */
final class SudokuSolver {

    private SudokuSolver(){}

    /**
     * Returns the list of possible values for a given cell.
     * @param g the whole board as an array
     * @param cell the index of the cell for which to find candidates
     * @return the list of possible values for the given cell
     */
    static List<Integer> candidates(int[] g, int cell) {
        int row = cell / 9; //calculate which row the cell is in
        int col = cell % 9; //calculate which column the cell is in

        boolean[] taken = new boolean[10];   //taken[5] = true f.e. means number 5 is already set

        for (int i = 0; i < 9; i++) {
            taken[g[row * 9 + i]] = true;    //row
            taken[g[i * 9 + col]] = true;    //column
        }

        int blockRow = row / 3 * 3;
        int blockCol = col / 3 * 3;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                taken[g[(blockRow + r) * 9 + blockCol + c]] = true;   //block
            }
        }

        List<Integer> result = new ArrayList<>();
        for (int n = 1; n <= 9; n++) {
            if (!taken[n]) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * Counts solutions and terminates as soon as {@code limit} is reached.
     * @param grid the Sudoku board
     * @param limit the maximum number of solutions to find
     * @return the number of solutions found
     */
    static int countSolutions(int[] grid, int limit) {
        return count(grid.clone(), limit);
    }

    /**
     * Recursively counts the number of solutions for a given Sudoku board.
     * @param g the Sudoku board
     * @param limit the maximum number of solutions to find
     * @return the number of solutions found
     */
    private static int count(int[] g, int limit) {
        int best = -1;
        List<Integer> bestCands = null;
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) {
                continue;
            }
            List<Integer> cands = candidates(g, i);
            if (cands.isEmpty()) {
                return 0;
            }
            if (bestCands == null || cands.size() < bestCands.size()) {
                best = i;
                bestCands = cands;
                if (cands.size() == 1) {
                    break;
                }
            }
        }
        if (bestCands == null) {
            return 1; //no empty field and exactly one solution found
        }
        int total = 0;
        for (int n : bestCands) {
            g[best] = n;
            total += count(g, limit - total);
            g[best] = 0;
            if (total >= limit) {
                return total;
            }
        }
        return total;
    }

    /**
     * Solves the board recursively.
     * @param grid the Sudoku board
     * @return the solved Sudoku board or null if no solution exists
     */
    static int[] solve(int[] grid) {
        int[] g = grid.clone();
        return solveRec(g) ? g : null;
    }

    /**
     * Solves the Sudoku board recursively.
     * @param g the Sudoku board
     * @return true if a solution is found, false otherwise
     */
    private static boolean solveRec(int[] g) {
        int best = -1;
        List<Integer> bestCands = null;
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) {
                continue;
            }
            List<Integer> cands = candidates(g, i);
            if (cands.isEmpty()) {
                return false;
            }
            if (bestCands == null || cands.size() < bestCands.size()) {
                best = i;
                bestCands = cands;
            }
        }
        if (bestCands == null) {
            return true; // Brett voll
        }
        for (int n : bestCands) {
            g[best] = n;
            if (solveRec(g)) {
                return true;
            }
            g[best] = 0;
        }
        return false;
    }

}
