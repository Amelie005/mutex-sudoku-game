# Mutex: A (multiplayer) Sudoku game
## Description
Mutex is a simple sudoku game developed for our distributed systems module.
Mutex can be played solo or with a friend or several.
The application uses Peer-to-peer (P2P) communication for real-time, serverless
gameplay. 

### Solo mode & Sudoku rules
When playing in solo mode, the game behaves like a classic sudoku:
You get a 9x9 grid of numbers from 1 to 9, with some cells already filled in 
(depending on your chosen difficulty level). Your mission is to fill the remaining cells with the 
correct numbers, following the standard sudoku rules:

1. Each row must contain all digits from 1 to 9 without repetition.
2. Each column must contain all digits from 1 to 9 without repetition.
3. Each of the nine 3x3 subgrids must contain all digits from 1 to 9 without repetition.

Once the entire grid is filled, you win. However, if you make 3 mistakes you automatically loose.
In the meantime, you have the option
to take notes for the possible candidates of each cell.



### Multiplayer mode
You and your friends all receive the same sudoku board. Whoever solves the grid correctly first, wins.
Keep in mind, that the 3-mistake rule applies here too.



## Developement 

## Installation 

## Usage

## Sources & references
References explaining several aspects of the implementation:
- [Backtracking](https://en.wikipedia.org/wiki/Backtracking)