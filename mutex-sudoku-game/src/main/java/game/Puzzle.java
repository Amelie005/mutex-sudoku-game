package game;

/**
 * Unchangeable Sudoku Puzzle containing givens and the unique solution.
 * Index = row * 9 + col, 0 = empty field
 */
public final class Puzzle {

    private final int[] givens;
    private final int[] solution;

    /**
     * Initializes a new Sudoku puzzle with the given givens and solution.
     * @param givens the initial numbers on the board
     * @param solution the unique solution to the puzzle
     */
    Puzzle(int[] givens, int[] solution) {
        if (givens.length != 81 || solution.length != 81) {
            throw new IllegalArgumentException("Board need to have 81 fields!");
        }
        this.givens = givens.clone();
        this.solution = solution.clone();
    }

    /**
     * Generates a new Sudoku puzzle with the specified difficulty and seed.
     * A puzzle with the same difficulty and same seed always delivers the same board.
     * @param difficulty the difficulty level of the puzzle
     * @param seed the seed for random number generation
     * @return the generated Sudoku puzzle
     */
    public static Puzzle generatePuzzle (Difficulty difficulty, long seed) {
        return SudokuGenerator.generate(difficulty, seed);
    }

    /**
     * Decodes a Sudoku puzzle from a string representation of 81 symbols.
     * '0' or '.' means empty field.
     * @param s the string representation of the puzzle
     * @return the from String s decoded puzzle
     */
    public static Puzzle decodePuzzle (String s) {
        if (s == null || s.length() != 81) {
            throw new IllegalArgumentException("81 symbols expected in String s!");
        }
    }
}
