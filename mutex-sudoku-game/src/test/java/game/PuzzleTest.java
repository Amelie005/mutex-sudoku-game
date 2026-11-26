package game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PuzzleTest {

    @Test
    void encodeThenDecode() {
        Puzzle original = Puzzle.generatePuzzle(Difficulty.EASY, 42L);
        Puzzle decoded = Puzzle.decodePuzzle(original.encodePuzzle());
        assertEquals(original.encodePuzzle(), decoded.encodePuzzle());
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(original.getSolution(r, c), decoded.getSolution(r, c));
            }
        }
    }

    @Test
    void rejectsFullyFilledBoardWithDuplicates() {
        //81 times '1' means no empty cells, so the solver would count it as "1 solution"
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle("1".repeat(81)));
    }

    @Test
    void rejectsDuplicateInRow() {
        String s = "11" + "0".repeat(79);
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle(s));
    }

    @Test
    void rejectsDuplicateInColumn() {
        String s = "1" + "0".repeat(8) + "1" + "0".repeat(71);
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle(s));
    }

    @Test
    void rejectsDuplicateInBlock() {
        //cells (0,0) and (1,1) share a block but neither row nor column
        char[] cells = "0".repeat(81).toCharArray();
        cells[0] = '5';
        cells[10] = '5';
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle(new String(cells)));
    }

    @Test
    void rejectsNonAsciiDigits() {
        String s = "\u0663" + "0".repeat(80);
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle(s));
    }

    @Test
    void rejectsWrongLengthAndNull() {
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle("123"));
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle(null));
    }

    @Test
    void rejectsPuzzleWithoutUniqueSolution() {
        assertThrows(IllegalArgumentException.class, () -> Puzzle.decodePuzzle("0".repeat(81)));
    }

    @Test
    void generateValidPuzzle() {
        Puzzle puzzle = Puzzle.generatePuzzle(Difficulty.EASY, 42L);
        assertNotNull(puzzle);
    }

    @Test
    void decodePuzzlePassesValidPuzzle() {
        Puzzle puzzle = Puzzle.generatePuzzle(Difficulty.EASY, 42L);
        Puzzle decoded = Puzzle.decodePuzzle(puzzle.encodePuzzle());
        assertNotNull(decoded);
    }

    @Test
    void getGivenValue() {
        Puzzle puzzle = Puzzle.generatePuzzle(Difficulty.EASY, 42L);
        Puzzle decoded = Puzzle.decodePuzzle(puzzle.encodePuzzle());
        assertEquals(puzzle.getGivenValue(0, 0), decoded.getGivenValue(0, 0));
    }
}