package game;

/**
 * Difficulty levels for the Sudoku.
 * The value is the number of givens to be displayed.
 */
public enum Difficulty {
    EASY(40),
    MEDIUM(32),
    HARD(26);

    private final int targetGivens;

    Difficulty(int targetGivens) {
        this.targetGivens = targetGivens;
    }

    /**
     * Returns the target number of givens for this difficulty level.
     * @return the target number of givens
     */
    public int getTargetGivens() {
        return targetGivens;
    }
}
