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
     * Generates a new deterministic Sudoku puzzle with the specified difficulty and seed.
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
     * Together with the {@link #encodePuzzle} function it serves as an alternative
     * to using {@link #generatePuzzle}.
     * @param s the string representation of the puzzle
     * @return the from String s decoded puzzle
     */
    public static Puzzle decodePuzzle (String s) {
        if (s == null || s.length() != 81) {
            throw new IllegalArgumentException("81 symbols expected in String s!");
        }

        int[] g = new int[81];

        for (int i = 0; i < 81; i++) {
            char ch = s.charAt(i);
            g[i] = (ch == '.') ? 0 : Character.digit(ch, 10);
            if (g[i] < 0) {
                throw new IllegalArgumentException("Invalid character at position " + i);
            }
        }

        if (SudokuSolver.countSolutions(g, 2) != 1) {
            throw new IllegalArgumentException("Puzzle has no unique solution and is therefore invalid.");
        }
        return new Puzzle(g, SudokuSolver.solve(g));
    }

    /**
     * Transforms a sudoku puzzle board into a string representation of 81 symbols.
     * '0' or '.' means empty field.
     * Together with the {@link #decodePuzzle} function it serves as an alternative
     * to using {@link #generatePuzzle}.
     * @return the string representation of the puzzle
     */
    public String encodePuzzle() {
        StringBuilder sb = new StringBuilder(81); //only has capacity for 81 symbols, so no exception needed
        for (int v : givens) {
            sb.append(v);
        }
        return sb.toString();
    }

    /**
     * Returns the given value of the cell.
     * @param row the row index of the cell
     * @param col the column index of the cell
     * @return the given value of the cell
     */
    public int getGivenValue(int row, int col) {
        return givens[row * 9 + col];
    }

    /**
     * Checks if the cell at the specified position is a given (pre-filled) cell.
     * Useful for example, so that the ui can display such cells differently.
     * @param row the row index of the cell
     * @param col the column index of the cell
     * @return true if the cell is given, false otherwise
     */
    public boolean isGiven(int row, int col) {
        return givens[row * 9 + col] != 0;
    }

    /**
     * Returns the solution value of the cell.
     * @param row the row index of the cell
     * @param col the column index of the cell
     * @return the solution value of the cell
     */
    public int getSolution(int row, int col) {
        return solution[row * 9 + col];
    }
}
