package game;

import java.util.Arrays;

/**
 * Represents the sudoku game of one player (numbers, notes, mistakes, status).
 * Each peer gets their own instance.
 */
public final class SudokuGame {

    public static final int MAX_MISTAKES = 3;

    /**
     * An enum representing the possible status the player can have.
     */
    public enum Status { PLAYING, WON, LOST }

    /**
     * An enum representing the possible results of placing a number.
     */
    public enum PlaceResult {
        //correct number, field is now set
        CORRECT,
        //wrong number, mistakes count is increased
        WRONG,
        //not allowed (given, already correct, game is over or simply not allowed as a symbol)
        REJECTED
    }

    private final Puzzle puzzle;
    private final int[] values = new int[81];
    private final boolean[][] notes = new boolean[81][10]; // notes[cell][number]
    private int mistakes;
    private Status status = Status.PLAYING;

    public SudokuGame(Puzzle puzzle) {
        this.puzzle = puzzle;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                values[r * 9 + c] = puzzle.getGivenValue(r, c);
            }
        }
    }


    /**
     * Places a number on the board at the specified position.
     * Wrong numbers remain visible and counts as mistakes.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @param number The number to place (1-9)
     * @return The result of the placement
     */
    public PlaceResult place(int row, int col, int number) {
        if (!canEdit(row, col) || number < 1 || number > 9) {
            return PlaceResult.REJECTED;
        }
        int idx = row * 9 + col;
        values[idx] = number;
        clearNotes(idx);

        if (number == puzzle.getSolution(row, col)) {
            removeNoteFromRelatedCells(row, col, number);
            if (allCorrect()) {
                status = Status.WON;
            }
            return PlaceResult.CORRECT;
        }
        mistakes++;
        if (mistakes >= MAX_MISTAKES) {
            status = Status.LOST;
        }
        return PlaceResult.WRONG;
    }

    /**
     * Sets or removes a note on the board at the specified position.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @param number The number to set or remove (1-9)
     * @return True if the note was successfully set or removed, false otherwise
     */
    public boolean toggleNote(int row, int col, int number) {
        if (!canEdit(row, col) || number < 1 || number > 9 || values[row * 9 + col] != 0) {
            return false;
        }
        notes[row * 9 + col][number] = !notes[row * 9 + col][number];
        return true;
    }

    /**
     * Deletes a wrong number or the notes in the specified field.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @return True if the field was successfully erased, false otherwise
     */
    public boolean erase(int row, int col) {
        if (!canEdit(row, col)) {
            return false;
        }
        int idx = row * 9 + col;
        values[idx] = 0;
        clearNotes(idx);
        return true;
    }

    /**
     * Returns the value at the specified position.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @return The value at the specified position
     */
    public int value(int row, int col) {
        return values[row * 9 + col];
    }

    /**
     * Checks if the cell at the specified position is a given value.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @return True if the cell is a given value, false otherwise
     */
    public boolean isGiven(int row, int col) {
        return puzzle.isGiven(row, col);
    }

    /**
     * Set but worng numbers for displaying them as red-colored in the GUI.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @return True if the cell contains a wrong number, false otherwise
     */
    public boolean isWrong(int row, int col) {
        int v = values[row * 9 + col];
        return v != 0 && v != puzzle.getSolution(row, col);
    }

    /**
     * Checks if the cell at the specified position has a note.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @param number The number to check (1-9)
     * @return True if the cell has the note, false otherwise
     */
    public boolean hasNote(int row, int col, int number) {
        return notes[row * 9 + col][number];
    }

    /**
     * Returns the number of mistakes made by the player.
     * @return The number of mistakes
     */
    public int mistakes() {
        return mistakes;
    }

    /**
     * Returns the status of the players game.
     */
    public Status status() {
        return status;
    }

    /**
     * The amount of correctly filled fields for the implementation of a progress bar.
     * @return amount of correctly filled cells
     */
    public int correctCount() {
        int n = 0;
        for (int i = 0; i < 81; i++) {
            if (values[i] == puzzle.getSolution(i / 9, i % 9)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Checks if the cell at the specified position can be edited.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @return True if the cell can be edited, false otherwise
     */
    private boolean canEdit(int row, int col) {
        if (status != Status.PLAYING || row < 0 || row > 8 || col < 0 || col > 8) {
            return false;
        }
        if (puzzle.isGiven(row, col)) {
            return false;
        }
        int v = values[row * 9 + col];
        return v != puzzle.getSolution(row, col);
    }

    /**
     * Checks if all cells are correctly filled.
     * @return True if all cells are correctly filled, false otherwise
     */
    private boolean allCorrect() {
        return correctCount() == 81;
    }

    /**
     * Clears the notes for the specified cell.
     * @param cell The cell index
     */
    private void clearNotes(int cell) {
        Arrays.fill(notes[cell], false);
    }

    /**
     * Removes the notes from a cell and their related cells if the correct number has been set.
     * @param row The row index (0-8)
     * @param col The column index (0-8)
     * @param number The number to remove from notes (1-9)
     */
    private void removeNoteFromRelatedCells(int row, int col, int number) {
        for (int i = 0; i < 9; i++) {
            notes[row * 9 + i][number] = false; //row
            notes[i * 9 + col][number] = false; // column
        }
        int br = row / 3 * 3;
        int bc = col / 3 * 3;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                notes[(br + r) * 9 + bc + c][number] = false; //block
            }
        }
    }
}
