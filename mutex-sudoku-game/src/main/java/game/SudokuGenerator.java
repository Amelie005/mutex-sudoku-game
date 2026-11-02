package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates a new puzzle with a unique solution.
 * Deterministic per seed.
 */
final class SudokuGenerator {

    private SudokuGenerator() {}

    /**
     * Generates a new Sudoku puzzle with the specified difficulty and seed.
     * @param difficulty difficulty level of the puzzle
     * @param seed seed of the puzzle
     * @return newly generated puzzle
     */
    static Puzzle generate(Difficulty difficulty, long seed) {
        Random rand = new Random(seed);

        int[] solution = new int[81];
        fill(solution, rand);

        int[] puzzle = solution.clone();
        List<Integer> order = new ArrayList<>();

        for (int i = 0; i < 81; i++) {
            order.add(i);
        }
        Collections.shuffle(order, rand);

        int givens = 81;
        for (int idx : order) {
            if (givens <= difficulty.getTargetGivens()) { //once givens is smaller/equal than needed, stop
                break;
            }
            int backup = puzzle[idx];
            puzzle[idx] = 0;
            if (SudokuSolver.countSolutions(puzzle, 2) == 1) { //TODO: implement!
                givens--;
            } else {
                puzzle[idx] = backup;
            }
        }

        return new Puzzle(puzzle, solution);
    }

    /**
     * Fills an empty grid with a valid solution.
     * @param g the grid to fill
     * @param rand the random number generator
     * @return true if the grid was filled successfully, false otherwise
     */
    private static boolean fill(int[] g, Random rand) {
        int idx = -1;

        for (int i = 0; i < 81; i++) {
            if (g[i] == 0) { //if cell is empty
                idx = i;
                break;
            }
        }
        if (idx == -1) { //if no empty cell is found
            return true;
        }
        List<Integer> nums = new ArrayList<>();
        for (int n = 1; n <= 9; n++) { //add possible numbers
            nums.add(n);
        }
        Collections.shuffle(nums, rand);
        int mask = SudokuSolver.candidates(g, idx); //TODO: implement!
        for (int n : nums) {
            if ((mask & (1 << n)) == 0) { //make sure the number is valid
                continue;
            }
            g[idx] = n;
            if (fill(g, rand)) {
                return true;
            }
            g[idx] = 0;
        }
        return false;
    }

}
