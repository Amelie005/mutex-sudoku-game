package game;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


class SudokuGameConcurrencyTest {
    private static final Puzzle PUZZLE = Puzzle.generatePuzzle(Difficulty.EASY, 42L);

    private static List<int[]> emptyCells() {
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!PUZZLE.isGiven(r, c)) {
                    cells.add(new int[]{r, c});
                }
            }
        }
        return cells;
    }

    private static int wrongValueFor(int row, int col) {
        return PUZZLE.getSolution(row, col) % 9 + 1; //always differs from the solution
    }

    /**
     * Many threads place wrong numbers at the same moment.
     * Exactly MAX_MISTAKES of them may count, all later ones must be rejected because the game is lost.
     */
    @RepeatedTest(100)
    void concurrentWrongMovesCountExactlyMaxMistakes() throws Exception {
        SudokuGame game = new SudokuGame(PUZZLE);
        List<int[]> cells = emptyCells().subList(0, 16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wrong = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(cells.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int[] cell : cells) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (game.place(cell[0], cell[1], wrongValueFor(cell[0], cell[1])) == SudokuGame.PlaceResult.WRONG) {
                        wrong.incrementAndGet();
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
        assertEquals(SudokuGame.MAX_MISTAKES, wrong.get());
        assertEquals(SudokuGame.MAX_MISTAKES, game.mistakes());
        assertEquals(SudokuGame.Status.LOST, game.status());
    }

    /**
     * Two writers fill disjoint halves of the board while a third thread keeps reading.
     * The game must end as WON, no matter how the threads interleave.
     */
    @RepeatedTest(100)
    void concurrentCorrectMovesEndInWon() throws Exception {
        SudokuGame game = new SudokuGame(PUZZLE);
        List<int[]> cells = emptyCells();
        int half = cells.size() / 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> a = pool.submit(() -> fill(game, cells.subList(0, half), start));
            Future<?> b = pool.submit(() -> fill(game, cells.subList(half, cells.size()), start));
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

    private static Object fill(SudokuGame game, List<int[]> cells, CountDownLatch start) throws InterruptedException {
        start.await();
        for (int[] cell : cells) {
            assertEquals(SudokuGame.PlaceResult.CORRECT,
                    game.place(cell[0], cell[1], PUZZLE.getSolution(cell[0], cell[1])));
        }
        return null;
    }

    @Test
    void singleThreadedBehaviourUnchanged() {
        SudokuGame game = new SudokuGame(PUZZLE);
        int[] cell = emptyCells().get(0);
        assertEquals(SudokuGame.PlaceResult.WRONG, game.place(cell[0], cell[1], wrongValueFor(cell[0], cell[1])));
        assertTrue(game.isWrong(cell[0], cell[1]));
        assertEquals(1, game.mistakes());
        assertTrue(game.erase(cell[0], cell[1]));
        assertEquals(SudokuGame.PlaceResult.CORRECT,
                game.place(cell[0], cell[1], PUZZLE.getSolution(cell[0], cell[1])));
        assertEquals(SudokuGame.Status.PLAYING, game.status());
    }
}