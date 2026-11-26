package game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SudokuGameTest {

    private Puzzle puzzle;
    private SudokuGame game;
    private List<int[]> empty; //all non-given cells as {row, col}

    @BeforeEach
    void setUp() {
        puzzle = Puzzle.generatePuzzle(Difficulty.EASY, 42L);
        game = new SudokuGame(puzzle);
        empty = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!puzzle.isGiven(r, c)) {
                    empty.add(new int[]{r, c});
                }
            }
        }
    }

    //Helpers

    private int solution(int[] cell) {
        return puzzle.getSolution(cell[0], cell[1]);
    }

    /** A number that is guaranteed to differ from the solution of the cell. */
    private int wrong(int[] cell) {
        return solution(cell) % 9 + 1;
    }

    private SudokuGame.PlaceResult placeCorrect(int[] cell) {
        return game.place(cell[0], cell[1], solution(cell));
    }

    private SudokuGame.PlaceResult placeWrong(int[] cell) {
        return game.place(cell[0], cell[1], wrong(cell));
    }

    private void fillAllCorrectly() {
        for (int[] cell : empty) {
            placeCorrect(cell);
        }
    }

    private void makeThreeMistakes() {
        for (int i = 0; i < SudokuGame.MAX_MISTAKES; i++) {
            placeWrong(empty.get(i));
        }
    }

    private static boolean sameBlock(int[] a, int[] b) {
        return a[0] / 3 == b[0] / 3 && a[1] / 3 == b[1] / 3;
    }

    //Initial State

    @Test
    void newGameStartsWithGivensOnly() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(puzzle.getGivenValue(r, c), game.value(r, c));
                assertEquals(puzzle.isGiven(r, c), game.isGiven(r, c));
                assertFalse(game.isWrong(r, c));
            }
        }
        assertEquals(0, game.mistakes());
        assertEquals(SudokuGame.Status.PLAYING, game.status());
    }

    @Test
    void correctCountInitiallyCountsTheGivens() {
        assertEquals(81 - empty.size(), game.correctCount());
    }

    //Place

    @Test
    void placingCorrectNumberIsAccepted() {
        int[] cell = empty.get(0);
        assertEquals(SudokuGame.PlaceResult.CORRECT, placeCorrect(cell));
        assertEquals(solution(cell), game.value(cell[0], cell[1]));
        assertFalse(game.isWrong(cell[0], cell[1]));
        assertEquals(0, game.mistakes());
        assertEquals(81 - empty.size() + 1, game.correctCount());
    }

    @Test
    void placingWrongNumberStaysVisibleAndCountsAsMistake() {
        int[] cell = empty.get(0);
        assertEquals(SudokuGame.PlaceResult.WRONG, placeWrong(cell));
        assertEquals(wrong(cell), game.value(cell[0], cell[1]));
        assertTrue(game.isWrong(cell[0], cell[1]));
        assertEquals(1, game.mistakes());
        assertEquals(81 - empty.size(), game.correctCount());
        assertEquals(SudokuGame.Status.PLAYING, game.status());
    }

    @Test
    void placingOnGivenCellIsRejected() {
        int[] given = findGiven();
        int before = game.value(given[0], given[1]);
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(given[0], given[1], before % 9 + 1));
        assertEquals(before, game.value(given[0], given[1]));
        assertEquals(0, game.mistakes());
    }

    @Test
    void placingInvalidNumbersIsRejectedWithoutMistake() {
        int[] cell = empty.get(0);
        for (int number : new int[]{-1, 0, 10, 100}) {
            assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(cell[0], cell[1], number));
        }
        assertEquals(0, game.value(cell[0], cell[1]));
        assertEquals(0, game.mistakes());
    }

    @Test
    void placingOutsideTheBoardIsRejected() {
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(-1, 0, 5));
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(0, -1, 5));
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(9, 0, 5));
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(0, 9, 5));
        assertEquals(0, game.mistakes());
    }

    @Test
    void correctCellCannotBeChangedAnymore() {
        int[] cell = empty.get(0);
        placeCorrect(cell);
        assertEquals(SudokuGame.PlaceResult.REJECTED, game.place(cell[0], cell[1], wrong(cell)));
        assertEquals(solution(cell), game.value(cell[0], cell[1]));
        assertEquals(0, game.mistakes());
    }

    @Test
    void wrongNumberCanBeOverwrittenWithCorrectOne() {
        int[] cell = empty.get(0);
        placeWrong(cell);
        assertEquals(SudokuGame.PlaceResult.CORRECT, placeCorrect(cell));
        assertFalse(game.isWrong(cell[0], cell[1]));
        assertEquals(1, game.mistakes()); // the earlier mistake stays counted
    }

    //Win/Loss

    @Test
    void thirdMistakeLosesTheGame() {
        placeWrong(empty.get(0));
        placeWrong(empty.get(1));
        assertEquals(SudokuGame.Status.PLAYING, game.status());
        placeWrong(empty.get(2));
        assertEquals(SudokuGame.MAX_MISTAKES, game.mistakes());
        assertEquals(SudokuGame.Status.LOST, game.status());
    }

    @Test
    void nothingCanBeChangedAfterLosing() {
        makeThreeMistakes();
        int[] cell = empty.get(5);
        assertEquals(SudokuGame.PlaceResult.REJECTED, placeCorrect(cell));
        assertEquals(SudokuGame.PlaceResult.REJECTED, placeWrong(cell));
        assertFalse(game.toggleNote(cell[0], cell[1], 1));
        int[] wrongCell = empty.get(0); // holds a wrong number
        assertFalse(game.erase(wrongCell[0], wrongCell[1]));
        assertEquals(0, game.value(cell[0], cell[1]));
        assertEquals(SudokuGame.MAX_MISTAKES, game.mistakes());
        assertEquals(SudokuGame.Status.LOST, game.status());
    }

    @Test
    void solvingEveryCellWinsTheGame() {
        for (int i = 0; i < empty.size() - 1; i++) {
            placeCorrect(empty.get(i));
            assertEquals(SudokuGame.Status.PLAYING, game.status());
        }
        assertEquals(SudokuGame.PlaceResult.CORRECT, placeCorrect(empty.get(empty.size() - 1)));
        assertEquals(SudokuGame.Status.WON, game.status());
        assertEquals(81, game.correctCount());
    }

    @Test
    void gameCanBeWonAfterTwoMistakes() {
        placeWrong(empty.get(0));
        placeWrong(empty.get(1));
        fillAllCorrectly();
        assertEquals(SudokuGame.Status.WON, game.status());
        assertEquals(2, game.mistakes());
    }

    @Test
    void nothingCanBeChangedAfterWinning() {
        fillAllCorrectly();
        int[] cell = empty.get(0);
        assertEquals(SudokuGame.PlaceResult.REJECTED, placeWrong(cell));
        assertFalse(game.erase(cell[0], cell[1]));
        assertEquals(SudokuGame.Status.WON, game.status());
        assertEquals(0, game.mistakes());
    }

    //Notes

    @Test
    void toggleNoteSetsAndRemovesNote() {
        int[] cell = empty.get(0);
        assertFalse(game.hasNote(cell[0], cell[1], 4));
        assertTrue(game.toggleNote(cell[0], cell[1], 4));
        assertTrue(game.hasNote(cell[0], cell[1], 4));
        assertTrue(game.toggleNote(cell[0], cell[1], 4));
        assertFalse(game.hasNote(cell[0], cell[1], 4));
    }

    @Test
    void notesAreIndependentPerNumber() {
        int[] cell = empty.get(0);
        game.toggleNote(cell[0], cell[1], 2);
        game.toggleNote(cell[0], cell[1], 7);
        assertTrue(game.hasNote(cell[0], cell[1], 2));
        assertTrue(game.hasNote(cell[0], cell[1], 7));
        assertFalse(game.hasNote(cell[0], cell[1], 3));
    }

    @Test
    void toggleNoteRejectsGivenFilledAndInvalid() {
        int[] given = findGiven();
        assertFalse(game.toggleNote(given[0], given[1], 1));

        int[] filled = empty.get(0);
        placeWrong(filled);
        assertFalse(game.toggleNote(filled[0], filled[1], 1)); // cell already holds a number

        int[] cell = empty.get(1);
        assertFalse(game.toggleNote(cell[0], cell[1], 0));
        assertFalse(game.toggleNote(cell[0], cell[1], 10));
        assertFalse(game.toggleNote(-1, 0, 1));
        assertFalse(game.toggleNote(0, 9, 1));
    }

    @Test
    void placingAnyNumberClearsNotesOfThatCell() {
        int[] cell = empty.get(0);
        game.toggleNote(cell[0], cell[1], 1);
        game.toggleNote(cell[0], cell[1], 9);
        placeWrong(cell);
        for (int n = 1; n <= 9; n++) {
            assertFalse(game.hasNote(cell[0], cell[1], n));
        }
    }

    @Test
    void correctNumberIsRemovedFromNotesOfRowColumnAndBlockOnly() {
        int[] cell = empty.get(0);
        int n = solution(cell);
        int other = n % 9 + 1;

        int[] sameRow = null, sameCol = null, sameBlock = null, unrelated = null;
        for (int[] e : empty) {
            if (e == cell) continue;
            boolean row = e[0] == cell[0], col = e[1] == cell[1], block = sameBlock(e, cell);
            if (row && sameRow == null) sameRow = e;
            else if (col && sameCol == null) sameCol = e;
            else if (block && !row && !col && sameBlock == null) sameBlock = e;
            else if (!row && !col && !block && unrelated == null) unrelated = e;
        }
        assertNotNull(sameRow);
        assertNotNull(sameCol);
        assertNotNull(sameBlock);
        assertNotNull(unrelated);

        for (int[] c : new int[][]{sameRow, sameCol, sameBlock, unrelated}) {
            game.toggleNote(c[0], c[1], n);
            game.toggleNote(c[0], c[1], other);
        }

        assertEquals(SudokuGame.PlaceResult.CORRECT, placeCorrect(cell));

        for (int[] c : new int[][]{sameRow, sameCol, sameBlock}) {
            assertFalse(game.hasNote(c[0], c[1], n));
            assertTrue(game.hasNote(c[0], c[1], other)); // other numbers stay untouched
        }
        assertTrue(game.hasNote(unrelated[0], unrelated[1], n)); // unrelated cell keeps its note
        assertTrue(game.hasNote(unrelated[0], unrelated[1], other));
    }

    @Test
    void wrongNumberDoesNotTouchNotesOfOtherCells() {
        int[] cell = empty.get(0);
        int w = wrong(cell);
        int[] neighbour = null;
        for (int[] e : empty) {
            if (e != cell && e[0] == cell[0]) {
                neighbour = e;
                break;
            }
        }
        assertNotNull(neighbour);
        game.toggleNote(neighbour[0], neighbour[1], w);
        placeWrong(cell);
        assertTrue(game.hasNote(neighbour[0], neighbour[1], w));
    }

    //Erase

    @Test
    void eraseRemovesWrongNumber() {
        int[] cell = empty.get(0);
        placeWrong(cell);
        assertTrue(game.erase(cell[0], cell[1]));
        assertEquals(0, game.value(cell[0], cell[1]));
        assertFalse(game.isWrong(cell[0], cell[1]));
        assertEquals(1, game.mistakes()); // erasing does not undo the mistake
    }

    @Test
    void eraseClearsNotesOfEmptyCell() {
        int[] cell = empty.get(0);
        game.toggleNote(cell[0], cell[1], 3);
        game.toggleNote(cell[0], cell[1], 8);
        assertTrue(game.erase(cell[0], cell[1]));
        assertFalse(game.hasNote(cell[0], cell[1], 3));
        assertFalse(game.hasNote(cell[0], cell[1], 8));
    }

    @Test
    void eraseRejectsGivenAndCorrectCells() {
        int[] given = findGiven();
        assertFalse(game.erase(given[0], given[1]));
        assertEquals(puzzle.getGivenValue(given[0], given[1]), game.value(given[0], given[1]));

        int[] cell = empty.get(0);
        placeCorrect(cell);
        assertFalse(game.erase(cell[0], cell[1]));
        assertEquals(solution(cell), game.value(cell[0], cell[1]));
    }

    @Test
    void eraseOutsideTheBoardIsRejected() {
        assertFalse(game.erase(-1, 0));
        assertFalse(game.erase(0, 9));
    }

    //Rejected moves don't change anything

    @Test
    void rejectedMovesDoNotChangeMistakesOrStatus() {
        int[] given = findGiven();
        game.place(given[0], given[1], 5);
        game.place(0, 0, 0);
        game.erase(given[0], given[1]);
        game.toggleNote(given[0], given[1], 5);
        assertEquals(0, game.mistakes());
        assertEquals(SudokuGame.Status.PLAYING, game.status());
        assertEquals(81 - empty.size(), game.correctCount());
    }

    //Concurrency
    //The game is used by network listener threads and the UI thread at the same time.

    /**
     * Many threads place wrong numbers at the same moment.
     * Exactly MAX_MISTAKES of them may count, all later ones must be rejected because the game is lost.
     */
    @RepeatedTest(100)
    void concurrentWrongMovesCountExactlyMaxMistakes() throws Exception {
        List<int[]> cells = empty.subList(0, 16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wrongResults = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(cells.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int[] cell : cells) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (placeWrong(cell) == SudokuGame.PlaceResult.WRONG) {
                        wrongResults.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(SudokuGame.MAX_MISTAKES, wrongResults.get());
        assertEquals(SudokuGame.MAX_MISTAKES, game.mistakes());
        assertEquals(SudokuGame.Status.LOST, game.status());
    }

    /**
     * Two writers fill disjoint halves of the board while a third thread keeps reading.
     * The game must end as WON, no matter how the threads interleave.
     */
    @RepeatedTest(100)
    void concurrentCorrectMovesEndInWon() throws Exception {
        int half = empty.size() / 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> a = pool.submit(() -> fillCorrectly(empty.subList(0, half), start));
            Future<?> b = pool.submit(() -> fillCorrectly(empty.subList(half, empty.size()), start));
            Future<?> reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1000; i++) {
                    int n = game.correctCount();
                    assertTrue(n >= 0 && n <= 81);
                    game.status();
                }
                return null;
            });
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(81, game.correctCount());
        assertEquals(0, game.mistakes());
        assertEquals(SudokuGame.Status.WON, game.status());
    }

    private Object fillCorrectly(List<int[]> cells, CountDownLatch start) throws InterruptedException {
        start.await();
        for (int[] cell : cells) {
            assertEquals(SudokuGame.PlaceResult.CORRECT, placeCorrect(cell));
        }
        return null;
    }

    private int[] findGiven() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (puzzle.isGiven(r, c)) {
                    return new int[]{r, c};
                }
            }
        }
        throw new AssertionError("puzzle without givens");
    }
}