package game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SudokuSolverTest {

    //Well-known puzzle with unique solution
    private static final String CLASSIC =
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static final String CLASSIC_SOLUTION =
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179";

    //Very hard puzzle with a unique solution
    private static final String HARD =
            "800000000003600000070090200050007000000045700000100030001000068008500010090000400";
    private static final String HARD_SOLUTION =
            "812753649943682175675491283154237896369845721287169534521974368438526917796318452";

    //Al Escargot: famous for being extremely hard for humans, unique solution
    private static final String AL_ESCARGOT =
            "100007090030020008009600500005300900010080002600004000300000010040000007007000300";
    private static final String AL_ESCARGOT_SOLUTION =
            "162857493534129678789643521475312986913586742628794135356478219241935867897261354";

    //Helpers

    /** Parses 81 characters, '0' or '.' means empty. */
    private static int[] grid(String s) {
        assertEquals(81, s.length());
        int[] g = new int[81];
        for (int i = 0; i < 81; i++) {
            char ch = s.charAt(i);
            g[i] = ch == '.' ? 0 : ch - '0';
        }
        return g;
    }

    private static int[] set(int[] g, int row, int col, int value) {
        g[row * 9 + col] = value;
        return g;
    }

    /** Independent check (does not use the solver).
     * Every row, column and block holds 1-9 exactly once. */
    private static boolean isValidSolution(int[] g) {
        for (int unit = 0; unit < 9; unit++) {
            Set<Integer> row = new HashSet<>();
            Set<Integer> col = new HashSet<>();
            Set<Integer> block = new HashSet<>();
            for (int i = 0; i < 9; i++) {
                row.add(g[unit * 9 + i]);
                col.add(g[i * 9 + unit]);
                block.add(g[(unit / 3 * 3 + i / 3) * 9 + unit % 3 * 3 + i % 3]);
            }
            Set<Integer> all = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
            if (!row.equals(all) || !col.equals(all) || !block.equals(all)) {
                return false;
            }
        }
        return true;
    }

    /** First row holds 1..8, so cell (0,8) can only be a 9, unless the 9 is blocked elsewhere. */
    private static int[] firstRowOneToEight() {
        int[] g = new int[81];
        for (int c = 0; c < 8; c++) {
            set(g, 0, c, c + 1);
        }
        return g;
    }

    //Kandidaten

    @Test
    void candidatesOfEmptyBoardAreAllDigits() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), SudokuSolver.candidates(new int[81], 40));
    }

    @Test
    void candidatesExcludeDigitsOfRowColumnAndBlock() {
        int[] g = new int[81];
        set(g, 0, 5, 1); //same row as cell (0,0)
        set(g, 4, 0, 2); //same column
        set(g, 1, 1, 3); //same block
        set(g, 5, 5, 4); //unrelated cell, doesnt matter
        assertEquals(List.of(4, 5, 6, 7, 8, 9), SudokuSolver.candidates(g, 0));
    }

    @Test
    void candidatesWorkForCellsInTheLastRowColumnAndBlock() {
        int[] g = new int[81];
        set(g, 8, 0, 1); //same row as cell (8,8)
        set(g, 0, 8, 2); //same column
        set(g, 6, 6, 3); //same block
        assertEquals(List.of(4, 5, 6, 7, 8, 9), SudokuSolver.candidates(g, 80));
    }

    @Test
    void candidatesCanContainExactlyOneDigit() {
        assertEquals(List.of(9), SudokuSolver.candidates(firstRowOneToEight(), 8));
    }

    @Test
    void candidatesAreEmptyWhenEveryDigitIsBlocked() {
        int[] g = firstRowOneToEight();
        set(g, 1, 8, 9); //the 9 is blocked by the column
        assertTrue(SudokuSolver.candidates(g, 8).isEmpty());
    }

    //countSolutions

    @Test
    void countStopsAtTheLimit() {
        assertEquals(1, SudokuSolver.countSolutions(new int[81], 1));
        assertEquals(2, SudokuSolver.countSolutions(new int[81], 2));
        assertEquals(3, SudokuSolver.countSolutions(new int[81], 3));
    }

    @Test
    void uniquePuzzleHasExactlyOneSolution() {
        assertEquals(1, SudokuSolver.countSolutions(grid(CLASSIC), 2));
        assertEquals(1, SudokuSolver.countSolutions(grid(HARD), 2));
    }

    @Test
    void solvedBoardHasExactlyOneSolution() {
        assertEquals(1, SudokuSolver.countSolutions(grid(CLASSIC_SOLUTION), 2));
    }

    @Test
    void puzzleWithMissingDigitsHasMoreThanOneSolution() {
        //Without any 1 and 2 the two digits can be swapped, so the solution is not unique
        int[] g = grid(CLASSIC_SOLUTION);
        for (int i = 0; i < 81; i++) {
            if (g[i] == 1 || g[i] == 2) {
                g[i] = 0;
            }
        }
        assertEquals(2, SudokuSolver.countSolutions(g, 2));
    }

    @Test
    void contradictoryBoardHasNoSolution() {
        int[] g = firstRowOneToEight();
        set(g, 1, 8, 9);
        assertEquals(0, SudokuSolver.countSolutions(g, 2));
    }

    @Test
    void countDoesNotModifyTheInput() {
        int[] g = grid(CLASSIC);
        int[] copy = g.clone();
        SudokuSolver.countSolutions(g, 2);
        assertArrayEquals(copy, g);
    }

    //Solve

    @Test
    void solvesClassicPuzzle() {
        assertArrayEquals(grid(CLASSIC_SOLUTION), SudokuSolver.solve(grid(CLASSIC)));
    }

    @Test
    void solvesVeryHardPuzzle() {
        assertArrayEquals(grid(HARD_SOLUTION), SudokuSolver.solve(grid(HARD)));
    }

    @Test
    void solveKeepsGivensAndDoesNotModifyTheInput() {
        int[] g = grid(CLASSIC);
        int[] copy = g.clone();
        int[] solved = SudokuSolver.solve(g);
        assertNotNull(solved);
        assertArrayEquals(copy, g);
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) {
                assertEquals(g[i], solved[i]);
            }
        }
    }

    @Test
    void solveReturnsNullForUnsolvableBoard() {
        int[] g = firstRowOneToEight();
        set(g, 1, 8, 9);
        assertNull(SudokuSolver.solve(g));
    }

    @Test
    void solveOfSolvedBoardReturnsTheSameBoard() {
        assertArrayEquals(grid(CLASSIC_SOLUTION), SudokuSolver.solve(grid(CLASSIC_SOLUTION)));
    }

    @Test
    void solveOfEmptyBoardProducesValidSolution() {
        int[] solved = SudokuSolver.solve(new int[81]);
        assertNotNull(solved);
        assertTrue(isValidSolution(solved));
    }

    @Test
    void solvedBoardsAreValid() {
        assertTrue(isValidSolution(SudokuSolver.solve(grid(CLASSIC))));
        assertTrue(isValidSolution(SudokuSolver.solve(grid(HARD))));
    }

    @Test
    void solverAgreesWithGeneratedPuzzles() {
        for (long seed = 1; seed <= 3; seed++) {
            Puzzle puzzle = Puzzle.generatePuzzle(Difficulty.EASY, seed);
            int[] givens = grid(puzzle.encodePuzzle());
            assertEquals(1, SudokuSolver.countSolutions(givens, 2));
            int[] solved = SudokuSolver.solve(givens);
            assertNotNull(solved);
            for (int i = 0; i < 81; i++) {
                assertEquals(puzzle.getSolution(i / 9, i % 9), solved[i]);
            }
        }
    }

    //Al Escargot

    @Test
    void alEscargotHasNoConflictsAndExactlyOneSolution() {
        int[] g = grid(AL_ESCARGOT);
        assertFalse(SudokuSolver.hasConflicts(g));
        assertEquals(1, SudokuSolver.countSolutions(g, 2));
    }

    @Test
    void solvesAlEscargot() {
        int[] g = grid(AL_ESCARGOT);
        int[] solved = SudokuSolver.solve(g);
        assertArrayEquals(grid(AL_ESCARGOT_SOLUTION), solved);
        assertTrue(isValidSolution(solved));
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) {
                assertEquals(g[i], solved[i]); // the givens are kept
            }
        }
    }

    //hasConflicts

    @Test
    void emptyBoardHasNoConflicts() {
        assertFalse(SudokuSolver.hasConflicts(new int[81])); //zeros are not duplicates
    }

    @Test
    void validBoardsHaveNoConflicts() {
        assertFalse(SudokuSolver.hasConflicts(grid(CLASSIC)));
        assertFalse(SudokuSolver.hasConflicts(grid(HARD)));
        assertFalse(SudokuSolver.hasConflicts(grid(CLASSIC_SOLUTION)));
    }

    @Test
    void detectsDuplicateInRow() {
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 0, 0, 5), 0, 8, 5)));
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 8, 0, 5), 8, 8, 5)));
    }

    @Test
    void detectsDuplicateInColumn() {
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 0, 0, 5), 8, 0, 5)));
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 0, 8, 5), 8, 8, 5)));
    }

    @Test
    void detectsDuplicateInBlock() {
        // different row and different column, only the block is shared
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 0, 0, 5), 1, 1, 5)));
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 6, 6, 5), 8, 8, 5)));
        assertTrue(SudokuSolver.hasConflicts(set(set(new int[81], 0, 6, 5), 2, 8, 5)));
    }

    @Test
    void sameDigitInDifferentUnitsIsNoConflict() {
        //(0,0) and (4,5): different row, column and block
        assertFalse(SudokuSolver.hasConflicts(set(set(new int[81], 0, 0, 5), 4, 5, 5)));
    }

    @Test
    void boardOfOnesHasConflicts() {
        int[] g = new int[81];
        java.util.Arrays.fill(g, 1);
        assertTrue(SudokuSolver.hasConflicts(g));
    }
}