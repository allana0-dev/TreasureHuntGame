package com.th.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.MLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AiBrain {

    // Neural network configuration.
    private BasicNetwork network;
    private final int inputNeurons = 8;   // [player_x, player_y, ai_x, ai_y, treasure_x, treasure_y, dx, dy]
    private final int outputNeurons = 4;  // Moves: 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT

    // For state switching.
    public enum AIState { PURSUING, WANDERING }
    private AIState state = AIState.WANDERING;
    private final float DETECTION_RANGE = 300f;  // Range for switching to PURSUING

    // For A* pathfinding in pursuing mode.
    private List<Vector2> pathToTreasure = new ArrayList<>();

    // Treasure chests, map dimensions, cell size.
    private List<TreasureChest> treasureChests;
    private int cellSize;
    private int mapPixelWidth, mapPixelHeight;

    // For wandering mode.
    private int stepsRemaining = 0;
    private int currentMove = -1;
    private List<Vector2> visitedPoints = new ArrayList<>();
    private final float VISITED_RADIUS = 50f;

    // External references.
    private AIPlayer aiPlayer;
    private Vector2 playerPosition;

    private float stateTransitionCooldown = 0;
    private final float STATE_TRANSITION_TIME = 2.0f; // seconds
    private Vector2 lastPathfindingAttempt = null;
    private int pathfindingFailureCount = 0;
    private Vector2 lastPosition = null;
    private int stuckCounter = 0;
    // Collision checker.
    public interface CollisionChecker {
        boolean isWalkable(Vector2 pos);
    }
    private CollisionChecker collisionChecker;

    // Random instance.
    private Random random = new Random();

    public AiBrain() {
        // Initialize neural network.
        network = new BasicNetwork();
        network.addLayer(new BasicLayer(null, true, inputNeurons));
        network.addLayer(new BasicLayer(new ActivationSigmoid(), true, 8));
        network.addLayer(new BasicLayer(new ActivationSigmoid(), false, outputNeurons));
        network.getStructure().finalizeStructure();
        network.reset();
        System.out.println("[AI INIT] Created a new Encog AI model.");
    }

    public int predictMove(double[] features) {
        if (features.length != inputNeurons)
            throw new IllegalArgumentException("Expected " + inputNeurons + " features.");
        MLData input = new BasicMLData(features);
        MLData output = network.compute(input);
        double[] out = output.getData();
        // Log the raw output vector for debugging.
        System.out.println("[NN] Features: " + vectorToString(features) + " | NN Output: " + vectorToString(out));
        int bestIndex = 0;
        for (int i = 1; i < out.length; i++) {
            if (out[i] > out[bestIndex])
                bestIndex = i;
        }
        System.out.println("[NN] Predicted Move: " + bestIndex);
        return bestIndex;
    }

    public void trainModel(MLDataSet trainingSet) {
        ResilientPropagation train = new ResilientPropagation(network, trainingSet);
        int epoch = 1;
        do {
            train.iteration();
            System.out.println("[TRAIN] Epoch #" + epoch + " Error: " + train.getError());
            epoch++;
        } while (train.getError() > 0.01 && epoch < 100);
        train.finishTraining();
    }

    public BasicNetwork getNetwork() {
        return network;
    }

    public void initMovement(AIPlayer aiPlayer, int mapPixelWidth, int mapPixelHeight, int cellSize, CollisionChecker collisionChecker) {
        this.aiPlayer = aiPlayer;
        this.mapPixelWidth = mapPixelWidth;
        this.mapPixelHeight = mapPixelHeight;
        this.cellSize = cellSize;
        this.collisionChecker = collisionChecker;

        System.out.println("[INIT] AI Movement initialized.");
    }

    public void setTreasureChests(List<TreasureChest> chests) {
        this.treasureChests = chests;
    }

    public void setPlayerPosition(Vector2 playerPos) {
        this.playerPosition = new Vector2(playerPos);
    }

    public void updateMovement(float delta) {
        if (aiPlayer == null || collisionChecker == null || playerPosition == null) {
            System.out.println("[ERROR] Missing required references for AI movement");
            return;
        }

        try {
            // Decide state with hysteresis to prevent rapid switching
            Vector2 nearestChest = getNearestChest();
            updateAiState(nearestChest);

            // Move based on state
            boolean moved = false;
            if (state == AIState.PURSUING && nearestChest != null) {
                moved = handlePursuingState(delta, nearestChest);
            }

            // Fall back to wandering if pursuing failed or we're in wandering state
            if (!moved) {
                handleWanderingState(delta);
            }

            // Add position to visited points and update direction
            updateVisitedPoints(aiPlayer.position);

            // Update AI direction based on movement
            updateAiDirection();
        } catch (Exception e) {
            // If anything goes wrong, log it and switch to wandering as a safety measure
            System.out.println("[CRITICAL ERROR] AI movement error: " + e.getMessage());
            e.printStackTrace();
            state = AIState.WANDERING;
            stepsRemaining = 0;
        }
    }

    // Method to update AI direction (for animation)
    private void updateAiDirection() {
        // This should update the currentMove based on actual movement
        if (lastPosition != null) {
            Vector2 movement = new Vector2(aiPlayer.position).sub(lastPosition);
            if (movement.len2() > 0.01f) {
                // Determine dominant direction
                if (Math.abs(movement.x) > Math.abs(movement.y)) {
                    currentMove = movement.x > 0 ? 3 : 2; // RIGHT or LEFT
                } else {
                    currentMove = movement.y > 0 ? 0 : 1; // UP or DOWN
                }
            }
        }
    }


    // Updated state management with hysteresis
    private void updateAiState(Vector2 nearestChest) {
        if (nearestChest != null) {
            float distance = aiPlayer.position.dst(nearestChest);

            // Add hysteresis: make it harder to change states
            if (state == AIState.WANDERING && distance <= DETECTION_RANGE) {
                state = AIState.PURSUING;
                System.out.println("[STATE] Switching to PURSUING. Distance to chest: " + distance);
                // Clear path when entering pursuing state
                pathToTreasure.clear();
            } else if (state == AIState.PURSUING && distance > DETECTION_RANGE * 1.5f) {
                state = AIState.WANDERING;
                System.out.println("[STATE] Switching to WANDERING. Distance to chest: " + distance);
            }
        } else {
            if (state != AIState.WANDERING) {
                System.out.println("[STATE] No chests available. Switching to WANDERING.");
                state = AIState.WANDERING;
            }
        }
    }
    private boolean handlePursuingState(float delta, Vector2 targetChest) {
        // Calculate grid positions
        Vector2 currentGrid = new Vector2(
            (int)(aiPlayer.position.x / cellSize),
            (int)(aiPlayer.position.y / cellSize)
        );
        Vector2 targetGrid = new Vector2(
            (int)(targetChest.x / cellSize),
            (int)(targetChest.y / cellSize)
        );

        // Check if we need to recalculate the path
        boolean needNewPath = pathToTreasure.isEmpty() ||
            aiPlayer.position.dst(targetChest) < cellSize * 1.5f ||
            pathRecalculationNeeded();

        // Only recalculate when needed
        if (needNewPath) {
            // Don't repeatedly try the same path if we're stuck
            if (lastPathfindingAttempt != null &&
                lastPathfindingAttempt.epsilonEquals(currentGrid, 0.1f) &&
                pathfindingFailureCount > 3) {

                System.out.println("[PATH] Too many failed pathfinding attempts. Switching to wandering.");
                pathfindingFailureCount = 0;
                state = AIState.WANDERING;
                return false;
            }

            // Find a path
            lastPathfindingAttempt = currentGrid;



            // Success! Reset failure count and use the new path
            pathfindingFailureCount = 0;
        }

        // Follow the path
        followPath(delta);
        return true;
    }

    // Helper method to move directly toward a target (simple approach)
    private void moveDirectlyToward(Vector2 target, float delta) {
        Vector2 direction = new Vector2(target).sub(aiPlayer.position).nor();
        float moveAmount = aiPlayer.speed * delta;

        // Try multiple step sizes
        for (float stepFactor = 1.0f; stepFactor >= 0.1f; stepFactor -= 0.2f) {
            float step = moveAmount * stepFactor;
            Vector2 newPos = new Vector2(aiPlayer.position).add(
                direction.x * step,
                direction.y * step
            );

            if (collisionChecker.isWalkable(newPos)) {
                aiPlayer.position.set(newPos);
                System.out.println("[DIRECT MOVE] Moving directly toward target: " + aiPlayer.position);
                return;
            }
        }
        System.out.println("[DIRECT MOVE] Failed to move directly toward target");
    }

    // Add this method to diagnose path problems
    private boolean checkPathValidity() {
        // Skip if no path
        if (pathToTreasure.isEmpty()) return false;

        // Check if all points in the path are walkable
        for (Vector2 point : pathToTreasure) {
            Vector2 worldPos = new Vector2(point).scl(cellSize).add(cellSize / 2f, cellSize / 2f);
            if (!collisionChecker.isWalkable(worldPos)) {
                System.out.println("[PATH INVALID] Found unwalkable point in path: " + worldPos);
                return false;
            }
        }

        // Check connections between consecutive points
        for (int i = 0; i < pathToTreasure.size() - 1; i++) {
            Vector2 curr = pathToTreasure.get(i);
            Vector2 next = pathToTreasure.get(i + 1);

            // Path should only move in cardinal directions one step at a time
            float dx = Math.abs(next.x - curr.x);
            float dy = Math.abs(next.y - curr.y);

            if ((dx > 1 || dy > 1) || (dx == 1 && dy == 1)) {
                System.out.println("[PATH INVALID] Non-cardinal or too long step: " + curr + " to " + next);
                return false;
            }
        }

        return true;
    }


    // Method to check if we need to recalculate our path
    private boolean pathRecalculationNeeded() {
        // If we have no path, we definitely need to recalculate
        if (pathToTreasure.isEmpty()) return true;

        // Check if we've been stuck for too long
        if (lastPosition != null &&
            aiPlayer.position.dst(lastPosition) < cellSize * 0.1f) {
            stuckCounter++;
            if (stuckCounter > 5) {
                System.out.println("[STUCK] AI appears to be stuck. Recalculating path.");
                stuckCounter = 0;
                return true;
            }
        } else {
            stuckCounter = 0;
        }

        // Occasionally recalculate path (every 3 seconds roughly)
        if (random.nextFloat() < 0.02f) {
            return true;
        }

        // Update last position for stuck detection
        lastPosition = new Vector2(aiPlayer.position);

        return false;
    }

    private void handleWanderingState(float delta) {
        // If we need a new move
        if (stepsRemaining <= 0 || currentMove == -1) {
            // Generate explorable target
            Vector2 wanderTarget = generateExploratoryTarget();

            // Prepare neural network input
            double[] features = new double[inputNeurons];
            features[0] = normalize(playerPosition.x, 0, mapPixelWidth);
            features[1] = normalize(playerPosition.y, 0, mapPixelHeight);
            features[2] = normalize(aiPlayer.position.x, 0, mapPixelWidth);
            features[3] = normalize(aiPlayer.position.y, 0, mapPixelHeight);
            features[4] = normalize(wanderTarget.x, 0, mapPixelWidth);
            features[5] = normalize(wanderTarget.y, 0, mapPixelHeight);

            // Calculate direction vector
            Vector2 direction = new Vector2(wanderTarget).sub(aiPlayer.position);
            float len = direction.len();
            if (len > 0) {
                direction.nor();
            } else {
                direction.set(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1).nor();
            }
            features[6] = direction.x;
            features[7] = direction.y;

            System.out.println("[WANDER] Target: " + wanderTarget + " | Direction: (" +
                features[6] + ", " + features[7] + ")");

            // Mix of neural network and direct logic
            float randomFactor = 0.3f; // 30% random moves
            if (random.nextFloat() < randomFactor) {
                currentMove = random.nextInt(outputNeurons);
                System.out.println("[WANDER] Random move: " + currentMove);
            } else {
                try {
                    currentMove = predictMove(features);
                    System.out.println("[WANDER] NN predicted move: " + currentMove);
                } catch (Exception e) {
                    System.out.println("[ERROR] NN prediction failed: " + e.getMessage());
                    currentMove = random.nextInt(outputNeurons);
                }
            }

            stepsRemaining = cellSize; // Move one cell
        }

        // Apply the move
        float moveAmount = Math.min(aiPlayer.speed * delta, stepsRemaining);
        Vector2 candidate = new Vector2(aiPlayer.position);
        switch (currentMove) {
            case 0: candidate.y += moveAmount; break;  // UP
            case 1: candidate.y -= moveAmount; break;  // DOWN
            case 2: candidate.x -= moveAmount; break;  // LEFT
            case 3: candidate.x += moveAmount; break;  // RIGHT
        }

        // Constrain to map bounds
        candidate.x = MathUtils.clamp(candidate.x, 0, mapPixelWidth - cellSize);
        candidate.y = MathUtils.clamp(candidate.y, 0, mapPixelHeight - cellSize);

        // Check for collisions
        if (!collisionChecker.isWalkable(candidate)) {
            System.out.println("[COLLISION] Blocked at " + candidate + ". Picking new move.");
            stepsRemaining = 0; // Force new move selection next update
            return;
        }

        // Apply the move
        aiPlayer.position.set(candidate);
        stepsRemaining -= moveAmount;
    }
    // Helper to normalize values between 0 and 1 for NN input
    private double normalize(float value, float min, float max) {
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }



    private void followPath(float delta) {
        if (pathToTreasure == null || pathToTreasure.isEmpty()) return;

        Vector2 nextTile = pathToTreasure.get(0);
        Vector2 nextPos = new Vector2(nextTile).scl(cellSize).add(cellSize / 2f, cellSize / 2f);

        // Check if we've reached the next waypoint
        if (aiPlayer.position.dst(nextPos) < cellSize / 4f) {
            pathToTreasure.remove(0);
            if (pathToTreasure.isEmpty()) return;
            nextTile = pathToTreasure.get(0);
            nextPos = new Vector2(nextTile).scl(cellSize).add(cellSize / 2f, cellSize / 2f);
        }
        // In followPath method, add this before calculating direction:

// Visualize the path for debugging
        if (!pathToTreasure.isEmpty()) {
            System.out.print("[PATH DEBUG] Current path: ");
            for (Vector2 point : pathToTreasure) {
                System.out.print("(" + point.x + "," + point.y + ") ");
            }
            System.out.println();
        }

// Make sure we're targeting the right cell
        Vector2 worldPosOfNextTile = new Vector2(nextTile).scl(cellSize).add(cellSize / 2f, cellSize / 2f);
        if (!collisionChecker.isWalkable(worldPosOfNextTile)) {
            System.out.println("[PATH ERROR] Next waypoint is not walkable: " + worldPosOfNextTile);
            pathToTreasure.remove(0);
            return;
        }

        // Calculate direction and move distance
        Vector2 direction = new Vector2(nextPos).sub(aiPlayer.position).nor();
        float moveAmount = aiPlayer.speed * delta;

        // Try multiple step sizes if needed
        boolean moved = false;
        for (float stepFactor = 1.0f; stepFactor >= 0.1f && !moved; stepFactor -= 0.3f) {
            float step = moveAmount * stepFactor;
            Vector2 newPos = new Vector2(aiPlayer.position).add(
                direction.x * step,
                direction.y * step
            );

            if (collisionChecker.isWalkable(newPos)) {
                aiPlayer.position.set(newPos);
                moved = true;
                System.out.println("[PATH MOVE] AI moved to: " + aiPlayer.position + " (step: " + stepFactor + ")");
            }
        }

        // If we still couldn't move, try finding an alternative path or switch to wandering
        if (!moved) {
            System.out.println("[PATH STUCK] AI couldn't move toward next waypoint. Clearing path.");
            pathToTreasure.clear();
            state = AIState.WANDERING;
            stepsRemaining = 0; // Force new move selection
        }
    }

    private Vector2 getNearestChest() {
        Vector2 nearest = null;
        float minDist = Float.MAX_VALUE;
        if (treasureChests == null) return null;
        for (TreasureChest chest : treasureChests) {
            if (chest.state == TreasureChest.ChestState.CLOSED) {
                float d = aiPlayer.position.dst(chest.position);
                if (d < minDist) {
                    minDist = d;
                    nearest = chest.position;
                }
            }
        }
        return nearest;
    }

    private Vector2 generateExploratoryTarget() {
        final int candidateCount = 10;
        Vector2 bestCandidate = null;
        float bestScore = -Float.MAX_VALUE;
        if (visitedPoints.isEmpty())
            return new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));

        for (int i = 0; i < candidateCount; i++) {
            Vector2 candidate = new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
            float score = 0;
            for (Vector2 v : visitedPoints) {
                score += candidate.dst(v);
            }
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        if (bestCandidate != null) {
            updateVisitedPoints(bestCandidate);
            return bestCandidate;
        }
        return new Vector2(random.nextInt(mapPixelWidth), random.nextInt(mapPixelHeight));
    }

    private void updateVisitedPoints(Vector2 pos) {
        for (Vector2 v : visitedPoints) {
            if (v.dst(pos) < VISITED_RADIUS)
                return;
        }
        visitedPoints.add(new Vector2(pos));
        if (visitedPoints.size() > 100)
            visitedPoints.remove(0);
    }

    // Helper to print arrays.
    private String vectorToString(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (double d : vector) {
            sb.append(String.format("%.2f", d)).append(", ");
        }
        sb.setLength(Math.max(0, sb.length()-2));
        sb.append("]");
        return sb.toString();
    }
    public int getCurrentMove() {
        return currentMove;
    }

}
