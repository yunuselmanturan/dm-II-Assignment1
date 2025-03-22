package players;

import game.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Comparator;

public class Player20230808049 extends Player {
    private static final int MONTE_CARLO_PLAYOUTS = 20;    // Increased for better statistical accuracy
    private static final int MONTE_CARLO_MAX_STEPS = 250;  // Increased max steps
    private static final double EXPLORE_WEIGHT = 1.2;      // Weight for exploring new areas
    private static final double FUTURE_MOVES_WEIGHT = 0.8; // Weight for future move possibilities
    private final Random random;

    public Player20230808049(Board board) {
        super(board);
        this.random = new Random();
    }

    @Override
    public Move nextMove() {
        List<Move> possibleMoves = board.getPossibleMoves();
        if (possibleMoves.isEmpty()) {
            return null;  // No moves => game over
        }

        Move bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        // Evaluate each candidate move via Monte Carlo playouts
        for (Move move : possibleMoves) {
            // Simulate the move on a copy
            Board boardCopy = new Board(board);
            if (!boardCopy.applyMove(move)) {
                continue;
            }

            // Check how many future moves this opens up (avoid quick dead ends)
            List<Move> futureMoves = boardCopy.getPossibleMoves();
            double immediateOpenness = futureMoves.size() * FUTURE_MOVES_WEIGHT;
            
            // Run several weighted random playouts from this new state
            double totalCoverage = 0.0;
            for (int i = 0; i < MONTE_CARLO_PLAYOUTS; i++) {
                Board playoutBoard = new Board(boardCopy);
                int coverage = weightedRandomPlayout(playoutBoard, MONTE_CARLO_MAX_STEPS);
                totalCoverage += coverage;
            }

            // Compute average coverage + additional heuristics
            double avgCoverage = totalCoverage / MONTE_CARLO_PLAYOUTS;
            double moveToEdgeBonus = moveTowardsBoardEdge(move) ? 1.0 : 0.0;
            double finalScore = avgCoverage + immediateOpenness + moveToEdgeBonus;

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestMove = move;
            }
        }

        // If we have multiple similar high-scoring moves, pick one preferring edge moves
        // This helps break ties in a strategic way
        List<MoveScore> topMoves = new ArrayList<>();
        for (Move move : possibleMoves) {
            Board boardCopy = new Board(board);
            if (boardCopy.applyMove(move)) {
                double score = evaluateMoveHeuristic(board, move);
                topMoves.add(new MoveScore(move, score));
            }
        }
        
        // Sort by score descending
        topMoves.sort(Comparator.comparing(MoveScore::getScore).reversed());
        
        // If we have multiple top candidates with similar scores, pick the one that leads to more open space
        if (topMoves.size() >= 2 && Math.abs(topMoves.get(0).getScore() - topMoves.get(1).getScore()) < 0.5) {
            return findMoveWithMostFutureOptions(board, topMoves.subList(0, Math.min(3, topMoves.size())));
        }

        return bestMove != null ? bestMove : possibleMoves.get(0);
    }

    /**
     * Evaluates a move based on how it affects the board state
     */
    private double evaluateMoveHeuristic(Board board, Move move) {
        int currentRow = board.getPlayerRow();
        int currentCol = board.getPlayerCol();
        int boardSize = board.getSize();
        
        // Prefer moves that go toward the center early game
        boolean isEarlyGame = board.getScore() < (boardSize * boardSize) / 4;
        
        // Calculate how far we are from center
        int centerRow = boardSize / 2;
        int centerCol = boardSize / 2;
        double distanceToCenter = Math.sqrt(Math.pow(currentRow - centerRow, 2) + Math.pow(currentCol - centerCol, 2));
        
        // For mid-to-late game, prefer moves that lead to unexplored regions
        double score = isEarlyGame ? (boardSize - distanceToCenter) : distanceToCenter;
        
        return score;
    }

    /**
     * Finds which move leads to the most future options from the top candidates
     */
    private Move findMoveWithMostFutureOptions(Board board, List<MoveScore> topMoves) {
        Move bestMove = topMoves.get(0).getMove();
        int maxOptions = -1;
        
        for (MoveScore moveScore : topMoves) {
            Move move = moveScore.getMove();
            Board boardCopy = new Board(board);
            boardCopy.applyMove(move);
            
            int futureOptions = boardCopy.getPossibleMoves().size();
            if (futureOptions > maxOptions) {
                maxOptions = futureOptions;
                bestMove = move;
            }
        }
        
        return bestMove;
    }
    
    /**
     * Determines if a move heads toward the edge of the board
     * which can be strategic to maximize coverage
     */
    private boolean moveTowardsBoardEdge(Move move) {
        int dRow = move.getDRow();
        int dCol = move.getDCol();
        int playerRow = board.getPlayerRow();
        int playerCol = board.getPlayerCol();
        int boardSize = board.getSize();
        
        int newRow = playerRow + dRow;
        int newCol = playerCol + dCol;
        
        // Check if we're moving closer to an edge
        boolean closerToRowEdge = Math.min(newRow, boardSize - 1 - newRow) < Math.min(playerRow, boardSize - 1 - playerRow);
        boolean closerToColEdge = Math.min(newCol, boardSize - 1 - newCol) < Math.min(playerCol, boardSize - 1 - playerCol);
        
        return closerToRowEdge || closerToColEdge;
    }

    /**
     * Performs a weighted random playout starting from the given board state:
     * - Prioritizes moves that open up more future options
     * - Applies smart heuristics to simulate "good" play
     * Returns the coverage (number of visited squares) at the end.
     */
    private int weightedRandomPlayout(Board b, int maxSteps) {
        int steps = 0;
        while (!b.isGameOver() && steps < maxSteps) {
            List<Move> moves = b.getPossibleMoves();
            if (moves.isEmpty()) {
                break;
            }
            
            // Weight moves based on how many future options they open
            List<MoveWeight> weightedMoves = new ArrayList<>();
            double totalWeight = 0.0;
            
            for (Move move : moves) {
                Board tempBoard = new Board(b);
                tempBoard.applyMove(move);
                
                // Calculate weight based on how many future moves it opens up
                int futureMoves = tempBoard.getPossibleMoves().size();
                double weight = Math.pow(futureMoves + 1, EXPLORE_WEIGHT);
                
                weightedMoves.add(new MoveWeight(move, weight));
                totalWeight += weight;
            }
            
            // Select move using weighted probability
            double selection = random.nextDouble() * totalWeight;
            double cumulativeWeight = 0.0;
            Move selectedMove = moves.get(0); // Default
            
            for (MoveWeight mw : weightedMoves) {
                cumulativeWeight += mw.getWeight();
                if (selection <= cumulativeWeight) {
                    selectedMove = mw.getMove();
                    break;
                }
            }
            
            b.applyMove(selectedMove);
            steps++;
        }
        return b.getScore();
    }
    
    /**
     * Helper class to track move weights for weighted random selection
     */
    private static class MoveWeight {
        private final Move move;
        private final double weight;
        
        public MoveWeight(Move move, double weight) {
            this.move = move;
            this.weight = weight;
        }
        
        public Move getMove() {
            return move;
        }
        
        public double getWeight() {
            return weight;
        }
    }
    
    /**
     * Helper class to track move scores
     */
    private static class MoveScore {
        private final Move move;
        private final double score;
        
        public MoveScore(Move move, double score) {
            this.move = move;
            this.score = score;
        }
        
        public Move getMove() {
            return move;
        }
        
        public double getScore() {
            return score;
        }
    }
}
