package com.th.game;

import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SmartAI implements Steerable<Vector2> {
    // AI position and properties
    public Vector2 position;
    public int score = 0;
    private String currentMapName;
    private Direction currentDirection = Direction.DOWN;
    private Direction targetDirection = Direction.DOWN;
    private boolean[][] walkableMap;
    private boolean[][] visitedTiles;
    private int mapTileWidth;
    private int mapTileHeight;
    private int tileWidth;
    private int tileHeight;

    // Movement properties
    private float moveSpeed = 100f; // Pixels per second
    private float directionChangeTimer = 0f;
    private float directionChangeDuration = 2f; // Change direction every 2 seconds
    private float turnDuration = 0.3f; // Time it takes to turn
    private float currentTurnTime = 0f; // Current time spent turning
    private float minWalkDuration = 1.5f; // Minimum time to walk in a direction before turning again
    private float currentWalkTime = 0f; // Current time spent walking in the current direction
    private Random random = new Random();
    private boolean initialized = false;
    private int obstacleHitCount = 0;
    private float obstacleHitTimer = 0f;
    private boolean usingIntermediateTarget = false;
    private Vector2 previousTarget = new Vector2();

    // Target for movement (used when moving toward a specific location)
    private Vector2 targetPosition = new Vector2();
    private boolean hasTarget = false;
    private List<Vector2> unexploredTiles = new ArrayList<>();
    private float explorationUpdateInterval = 5.0f; // Update unexplored tiles list every 5 seconds
    private float explorationTimer = 0f;
    private float randomTargetChangeInterval = 8.0f; // Change targets randomly every 8 seconds
    private float randomTargetTimer = 0f;

    // Steering behavior properties
    private Vector2 linearVelocity = new Vector2();
    private float maxLinearSpeed = 100f;
    private float maxLinearAcceleration = 200f;
    private float maxAngularSpeed = 0f;
    private float maxAngularAcceleration = 0f;
    private float zeroLinearSpeedThreshold = 0.1f;
    private boolean tagged = false;
    private HistoricalAIData databaseManager;
    private boolean explorationMode = true; // Whether we're in exploration mode
    private float databaseUpdateTimer = 0f;
    private float databaseUpdateInterval = 1.0f; // Check database manager every second
    // Add these variables to SmartAI class
    private Vector2 lastPosition = new Vector2();
    private float stuckTimer = 0f;
    private float stuckThreshold = 1.5f; // If we don't move for 1.5 seconds, consider ourselves stuck

    // Movement state to control flow
    private enum MovementState {
        WALKING,
        TURNING,
        PAUSED
    }

    private MovementState movementState = MovementState.WALKING;
    private float pauseTimer = 0f;
    private float pauseDuration = 0.2f; // Slightly longer pause after turning (0.2s)
    private float minTimeBetweenDirectionChanges = 3.0f; // Minimum time between direction changes
    private float lastDirectionChangeTime = 0f; // Track when we last changed direction
    // 1. Add these fields to the SmartAI class
    private float normalMoveSpeed = 100f; // Original speed
    private float fastMoveSpeed = 250f;   // Speed when player collects treasure
    private boolean isSpeedBoostActive = false;
    private float speedBoostTimer = 0f;
    private float speedBoostDuration = 15f; // How long the speed boost lasts if AI doesn't find treasure

    // Simple AI states
    public enum AIState implements State<SmartAI> {
        ROAMING,       // Random wandering behavior
        EXPLORING,     // Systematically exploring the map
        SEEKING        // Moving toward a target (landmark or treasure)
        ;

        @Override
        public void enter(SmartAI smartAI) {
            if (this == EXPLORING) {
                smartAI.updateUnexploredTilesList();
            }
        }

        @Override
        public void update(SmartAI smartAI) {
        }

        @Override
        public void exit(SmartAI smartAI) {
        }

        @Override
        public boolean onMessage(SmartAI smartAI, Telegram telegram) {
            return false;
        }
    }

    // State machine
    private StateMachine<SmartAI, AIState> stateMachine;

    public SmartAI(Vector2 position, String currentMapName) {
        this.position = position;
        this.currentMapName = currentMapName;
        // Start with the EXPLORING state for systematic map exploration
        this.stateMachine = new DefaultStateMachine<>(this, AIState.EXPLORING);
        // Initialize the database manager
        this.databaseManager = new HistoricalAIData(this, currentMapName);

        // Initialize speed variables
        this.normalMoveSpeed = 100f;
        this.fastMoveSpeed = 200f;
        this.moveSpeed = normalMoveSpeed;
        // Update max linear speed to match move speed
        this.maxLinearSpeed = this.moveSpeed;
    }



    // Scan the map to identify all walkable areas
    public void scanWalkableAreas(GameScreen gameScreen, TiledMap tiledMap) {
        // Get map dimensions
        mapTileWidth = tiledMap.getProperties().get("width", Integer.class);
        mapTileHeight = tiledMap.getProperties().get("height", Integer.class);
        tileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        tileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

        // Initialize the walkable map array
        walkableMap = new boolean[mapTileWidth][mapTileHeight];
        visitedTiles = new boolean[mapTileWidth][mapTileHeight];

        // Scan each tile to check if it's walkable
        for (int x = 0; x < mapTileWidth; x++) {
            for (int y = 0; y < mapTileHeight; y++) {
                // Convert tile coordinates to pixel coordinates (center of tile)
                Vector2 pixelPos = new Vector2(
                    x * tileWidth + tileWidth / 2,
                    y * tileHeight + tileHeight / 2
                );

                // Check if the position is walkable
                walkableMap[x][y] = gameScreen.isWalkable(pixelPos);
            }
        }

        // Initialize the unexplored tiles list
        updateUnexploredTilesList();

        System.out.println("Map scanning complete. Walkable areas identified.");
        initialized = true;
    }

    // Update the list of unexplored tiles
    private void updateUnexploredTilesList() {
        unexploredTiles.clear();

        // Mark the current tile as visited
        int currentTileX = (int)(position.x / tileWidth);
        int currentTileY = (int)(position.y / tileHeight);

        // Clamp to valid array indices
        currentTileX = MathUtils.clamp(currentTileX, 0, mapTileWidth - 1);
        currentTileY = MathUtils.clamp(currentTileY, 0, mapTileHeight - 1);

        visitedTiles[currentTileX][currentTileY] = true;

        // Find unexplored walkable tiles
        for (int x = 0; x < mapTileWidth; x++) {
            for (int y = 0; y < mapTileHeight; y++) {
                if (walkableMap[x][y] && !visitedTiles[x][y]) {
                    unexploredTiles.add(new Vector2(
                        x * tileWidth + tileWidth / 2,
                        y * tileHeight + tileHeight / 2
                    ));
                }
            }
        }

        // If we've explored most of the map, reset the visited status
        // to allow re-exploration (useful for long-running scenarios)
        if (unexploredTiles.size() < mapTileWidth * mapTileHeight * 0.2) { // Less than 20% unexplored
            System.out.println("Map mostly explored. Resetting exploration state.");
            for (int x = 0; x < mapTileWidth; x++) {
                for (int y = 0; y < mapTileHeight; y++) {
                    // Keep current tile as visited, reset others
                    if (x != (int)(position.x / tileWidth) || y != (int)(position.y / tileHeight)) {
                        visitedTiles[x][y] = false;
                    }
                }
            }
            updateUnexploredTilesList(); // Recursive call is okay as we've reset the visited tiles

            // After reset, immediately choose a random far away target
            if (!unexploredTiles.isEmpty() && unexploredTiles.size() > 10) {
                // Pick a tile that's further away for more interesting movement
                List<Vector2> farTiles = new ArrayList<>();
                float farDistance = mapTileWidth * tileWidth * 0.5f; // Consider tiles at least 50% of map width away as "far"

                for (Vector2 tile : unexploredTiles) {
                    if (position.dst(tile) > farDistance) {
                        farTiles.add(tile);
                    }
                }

                // If we found far tiles, pick one randomly
                if (!farTiles.isEmpty()) {
                    targetPosition = farTiles.get(random.nextInt(farTiles.size()));
                    hasTarget = true;
                }
            }
        }
    }

    // Get the current direction for animation purposes
    public Direction getCurrentDirection() {
        return currentDirection;
    }

    // Find a random walkable position on the map
    private Vector2 findRandomWalkablePosition() {
        // Collect all walkable tiles
        List<Vector2> walkableTiles = new ArrayList<>();

        for (int x = 0; x < mapTileWidth; x++) {
            for (int y = 0; y < mapTileHeight; y++) {
                if (walkableMap[x][y]) {
                    walkableTiles.add(new Vector2(
                        x * tileWidth + tileWidth / 2,
                        y * tileHeight + tileHeight / 2
                    ));
                }
            }
        }

        if (walkableTiles.isEmpty()) {
            return new Vector2(position); // Fallback to current position
        }

        // Return a random walkable position
        return walkableTiles.get(random.nextInt(walkableTiles.size()));
    }

    // Find a random unexplored tile
    private Vector2 findRandomUnexploredTile() {
        if (unexploredTiles.isEmpty()) {
            updateUnexploredTilesList();
            if (unexploredTiles.isEmpty()) {
                return findRandomWalkablePosition();
            }
        }

        // Choose a random unexplored tile
        int randomIndex = random.nextInt(unexploredTiles.size());
        return unexploredTiles.get(randomIndex);
    }

    // 2. In the activateSpeedBoost method, update maxLinearSpeed
    public void activateSpeedBoost() {
        isSpeedBoostActive = true;
        speedBoostTimer = 0f;
        moveSpeed = fastMoveSpeed;
        maxLinearSpeed = moveSpeed; // Update maxLinearSpeed to match moveSpeed
        System.out.println("AI speed boost activated! New speed: " + moveSpeed);
    }


    // Method to update AI state
    public void update(float delta, GameScreen gameScreen) {
        // Update speed boost timer if active
        // In the update method where we handle speed boost timing:
        if (isSpeedBoostActive) {
            speedBoostTimer += delta;

            // If boost duration expired, revert to normal speed
            if (speedBoostTimer >= speedBoostDuration) {
                isSpeedBoostActive = false;
                moveSpeed = normalMoveSpeed;
                maxLinearSpeed = moveSpeed; // Update maxLinearSpeed to match moveSpeed
                System.out.println("AI speed boost expired. Speed returned to normal: " + moveSpeed);
            }
        }
        if (!initialized) {
            return;
        }

        if (position.dst(lastPosition) < 5f) {
            stuckTimer += delta;
            if (stuckTimer > stuckThreshold) {
                // We're stuck, force a new target
                hasTarget = false;
                stuckTimer = 0f;

                // If we're seeking, try to find an intermediate target
                if (stateMachine.getCurrentState() == AIState.SEEKING) {
                    Vector2 intermediateTarget = findIntermediateTarget(gameScreen);
                    if (intermediateTarget != null) {
                        targetPosition.set(intermediateTarget);
                        hasTarget = true;
                    } else {
                        // If can't find intermediate target, go back to exploring
                        stateMachine.changeState(AIState.EXPLORING);
                    }
                }
            }
        } else {
            // We're moving fine, reset stuck timer
            stuckTimer = 0f;
        }

        lastPosition.set(position);


        // Update database manager timer
        databaseUpdateTimer += delta;
        if (databaseUpdateTimer >= databaseUpdateInterval) {
            databaseUpdateTimer = 0;
            // Consult database manager for target updates
            boolean targetUpdated = databaseManager.updateAITarget(
                delta,
                gameScreen.getCurrentHint(),
                gameScreen.getLandmarks()
            );

            // If target was updated, we don't need further processing in this frame
            if (targetUpdated) {
                // The database manager has already called setTarget() if needed
                databaseUpdateTimer = 0;
            }
        }
        // Update exploration timer
        explorationTimer += delta;
        if (explorationTimer >= explorationUpdateInterval) {
            explorationTimer = 0;
            // ADD THIS CODE HERE: Check for nearby hotspots during exploration
            if (stateMachine.getCurrentState() == AIState.EXPLORING &&
                random.nextFloat() < 0.2f && // 20% chance each update cycle
                databaseManager != null) {
                boolean foundHotspot = databaseManager.checkForNearbyHotspots(position);
                if (foundHotspot) {
                    // If we found a hotspot, skip the rest of the exploration update
                    return;
                }
            }
            // Mark current tile as visited
            int tileX = (int)(position.x / tileWidth);
            int tileY = (int)(position.y / tileHeight);

            // Clamp to valid array indices
            tileX = MathUtils.clamp(tileX, 0, mapTileWidth - 1);
            tileY = MathUtils.clamp(tileY, 0, mapTileHeight - 1);

            visitedTiles[tileX][tileY] = true;

            // Occasionally update unexplored tiles list to avoid repeated computations
            if (stateMachine.getCurrentState() == AIState.EXPLORING) {
                Vector2 removed = null;
                for (Vector2 tile : unexploredTiles) {
                    int x = (int)(tile.x / tileWidth);
                    int y = (int)(tile.y / tileHeight);
                    if (x == tileX && y == tileY) {
                        removed = tile;
                        break;
                    }
                }
                if (removed != null) {
                    unexploredTiles.remove(removed);
                }

                // If the target is no longer unexplored, find a new one
                if (hasTarget) {
                    int targetTileX = (int)(targetPosition.x / tileWidth);
                    int targetTileY = (int)(targetPosition.y / tileHeight);
                    if (visitedTiles[targetTileX][targetTileY]) {
                        hasTarget = false;
                    }
                }
            }
        }

        // Update random target change timer
        randomTargetTimer += delta;
        if (randomTargetTimer >= randomTargetChangeInterval && stateMachine.getCurrentState() == AIState.EXPLORING) {
            randomTargetTimer = 0;
            // Randomly change targets occasionally for more varied exploration
            if (random.nextFloat() < 0.7f) { // 70% chance to change target
                hasTarget = false;
            }
        }


        // Update the state machine
        stateMachine.update();

        // First handle turning and pausing states
        if (movementState == MovementState.TURNING) {
            // We're in turning state - update the turn timer
            currentTurnTime += delta;

            // Don't move while turning
            linearVelocity.set(0, 0);

            // If we've completed the turn, start moving in the new direction
            if (currentTurnTime >= turnDuration) {
                currentDirection = targetDirection;
                currentTurnTime = 0f;
                movementState = MovementState.PAUSED; // Brief pause after turning
                pauseTimer = 0f;
                currentWalkTime = 0f; // Reset the walk time
            }

            // No further movement processing needed during turn
            return;
        }
        else if (movementState == MovementState.PAUSED) {
            // We're paused briefly after turning
            pauseTimer += delta;
            linearVelocity.set(0, 0);

            // If pause is done, resume walking
            if (pauseTimer >= pauseDuration) {
                movementState = MovementState.WALKING;
                currentWalkTime = 0f; // Reset walk time when starting to walk again
            }

            // No further movement processing needed during pause
            return;
        }

        // Update the walking time counter
        currentWalkTime += delta;

        // Now handle normal movement based on AI state
        switch (stateMachine.getCurrentState()) {
            case ROAMING:
                updateRoaming(delta, gameScreen);
                break;
            case EXPLORING:
                updateExploring(delta, gameScreen);
                break;
            case SEEKING:
                updateSeeking(delta, gameScreen);
                break;
        }

        // Calculate map boundaries in pixels
        float mapWidth = mapTileWidth * tileWidth;
        float mapHeight = mapTileHeight * tileHeight;

        // Enforce map boundaries
        position.x = MathUtils.clamp(position.x, 0, mapWidth - 64); // 64 is AI width
        position.y = MathUtils.clamp(position.y, 0, mapHeight - 64); // 64 is AI height
    }

    // Determine if a direction change requires turning
    private boolean needsToTurn(Direction newDirection) {
        // If current direction is horizontal and new direction is vertical, we need to turn
        boolean currentHorizontal = (currentDirection == Direction.LEFT || currentDirection == Direction.RIGHT);
        boolean newHorizontal = (newDirection == Direction.LEFT || newDirection == Direction.RIGHT);

        return currentHorizontal != newHorizontal;
    }

    // Set a new direction with proper turning animation
    private void setNewDirection(Direction newDirection) {
        // If we're already turning or the directions are the same, do nothing
        if (movementState == MovementState.TURNING || currentDirection == newDirection) {
            return;
        }

        // Don't change direction if we haven't walked for the minimum duration yet
        if (currentWalkTime < minWalkDuration) {
            return;
        }

        // Check if we need to turn (moving from horizontal to vertical or vice versa)
        if (needsToTurn(newDirection)) {
            targetDirection = newDirection;
            movementState = MovementState.TURNING;
            currentTurnTime = 0f;
            currentWalkTime = 0f; // Reset walk time when turning
        } else {
            // No turn needed, just change direction
            currentDirection = newDirection;
            currentWalkTime = 0f; // Reset walk time on direction change
        }
    }
    // Add this method to allow setting a target directly
    public void setTarget(Vector2 targetPos, boolean seekMode) {
        this.targetPosition.set(targetPos);
        this.hasTarget = true;

        if (seekMode) {
            this.stateMachine.changeState(AIState.SEEKING);
        }
    }
    public void setExplorationMode(boolean exploring) {
        this.explorationMode = exploring;
        if (exploring) {
            this.stateMachine.changeState(AIState.EXPLORING);
        }
    }


    // Calculate direction from velocity vector
    private Direction getDirectionFromVelocity(Vector2 velocity) {
        if (Math.abs(velocity.x) > Math.abs(velocity.y)) {
            // Moving horizontally
            return velocity.x > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            // Moving vertically
            return velocity.y > 0 ? Direction.UP : Direction.DOWN;
        }
    }

    // Exploring behavior - systematically visiting unexplored tiles
    private void updateExploring(float delta, GameScreen gameScreen) {
        directionChangeTimer += delta;

        // If we don't have a target or reached our target, find a random unexplored tile
        if (!hasTarget || position.dst(targetPosition) < 32) {
            targetPosition = findRandomUnexploredTile();
            hasTarget = true;
            directionChangeTimer = 0f;
        }

        // Move toward the target
        Vector2 direction = new Vector2(targetPosition).sub(position).nor();

        // Determine cardinal direction (choose the strongest component)
        if (Math.abs(direction.x) > Math.abs(direction.y)) {
            // Move horizontally
            direction.y = 0;
            direction.x = Math.signum(direction.x);
        } else {
            // Move vertically
            direction.x = 0;
            direction.y = Math.signum(direction.y);
        }

        // Calculate the new direction we want to move in
        Direction newDirection = getDirectionFromVelocity(direction);

        // If the direction is different, we need to handle turning
        if (newDirection != currentDirection) {
            setNewDirection(newDirection);

            // If we're now turning, return early
            if (movementState == MovementState.TURNING) {
                return;
            }
        }

        // Apply velocity in the current direction
        linearVelocity.set(0, 0);
        switch (currentDirection) {
            case UP:
                linearVelocity.set(0, maxLinearSpeed);
                break;
            case DOWN:
                linearVelocity.set(0, -maxLinearSpeed);
                break;
            case LEFT:
                linearVelocity.set(-maxLinearSpeed, 0);
                break;
            case RIGHT:
                linearVelocity.set(maxLinearSpeed, 0);
                break;
        }

        // Apply movement
        Vector2 oldPosition = new Vector2(position);
        position.add(linearVelocity.x * delta, linearVelocity.y * delta);

        // Check if the new position is walkable
        if (!gameScreen.isWalkable(position)) {
            position.set(oldPosition);
            hasTarget = false; // Force target recalculation
        }

        // Check if we've reached the target
        if (position.dst(targetPosition) < 32) {
            // Mark the target tile as visited
            int tileX = (int)(targetPosition.x / tileWidth);
            int tileY = (int)(targetPosition.y / tileHeight);

            // Clamp to valid array indices
            tileX = MathUtils.clamp(tileX, 0, mapTileWidth - 1);
            tileY = MathUtils.clamp(tileY, 0, mapTileHeight - 1);

            visitedTiles[tileX][tileY] = true;
            hasTarget = false; // Find a new target
        }
    }

    // Roaming behavior - wandering around randomly
    private void updateRoaming(float delta, GameScreen gameScreen) {
        directionChangeTimer += delta;

        // Periodically pick a new random target, but only if we've walked enough
        if ((directionChangeTimer >= directionChangeDuration && currentWalkTime >= minWalkDuration) || !hasTarget) {
            targetPosition = findRandomWalkablePosition();
            hasTarget = true;
            directionChangeTimer = 0f;
        }

        // Move toward the target
        Vector2 direction = new Vector2(targetPosition).sub(position).nor();

        // Determine cardinal direction (choose the strongest component)
        if (Math.abs(direction.x) > Math.abs(direction.y)) {
            // Move horizontally
            direction.y = 0;
            direction.x = Math.signum(direction.x);
        } else {
            // Move vertically
            direction.x = 0;
            direction.y = Math.signum(direction.y);
        }

        // Calculate the new direction we want to move in
        Direction newDirection = getDirectionFromVelocity(direction);

        // If the direction is different, we need to handle turning
        if (newDirection != currentDirection) {
            setNewDirection(newDirection);

            // If we're now turning, return early
            if (movementState == MovementState.TURNING) {
                return;
            }
        }

        // Apply velocity in the current direction
        linearVelocity.set(0, 0);
        switch (currentDirection) {
            case UP:
                linearVelocity.set(0, maxLinearSpeed);
                break;
            case DOWN:
                linearVelocity.set(0, -maxLinearSpeed);
                break;
            case LEFT:
                linearVelocity.set(-maxLinearSpeed, 0);
                break;
            case RIGHT:
                linearVelocity.set(maxLinearSpeed, 0);
                break;
        }

        // Apply movement
        Vector2 oldPosition = new Vector2(position);
        position.add(linearVelocity.x * delta, linearVelocity.y * delta);

        // Check if the new position is walkable
        if (!gameScreen.isWalkable(position)) {
            position.set(oldPosition);
            hasTarget = false; // Force target recalculation
        }

        // Check if we've reached the target
        if (position.dst(targetPosition) < 32) {
            hasTarget = false; // Pick a new target next update
        }
    }

    // Seeking behavior - moving toward a specific target with zig-zag pattern
// Seeking behavior - moving toward a specific target with improved obstacle handling
    private void updateSeeking(float delta, GameScreen gameScreen) {
        if (!hasTarget) {
            stateMachine.changeState(AIState.EXPLORING);
            return;
        }

        // Move one axis at a time in a zig-zag pattern
        directionChangeTimer += delta;
        Vector2 oldPosition = new Vector2(position);

        // Calculate direction to target
        Vector2 dirToTarget = new Vector2(targetPosition).sub(position);

        // Only consider direction change if we've walked long enough
        boolean canChangeDirection = currentWalkTime >= minWalkDuration &&
            (directionChangeTimer - lastDirectionChangeTime) >= minTimeBetweenDirectionChanges;

        // Decide which axis to move on
        boolean moveHorizontally;

        // If we're close to the target (within 150 pixels), move directly toward it
        if (dirToTarget.len() < 150) {
            if (Math.abs(dirToTarget.x) > Math.abs(dirToTarget.y)) {
                moveHorizontally = true;
            } else {
                moveHorizontally = false;
            }
        } else if (canChangeDirection) {
            // Further away, use zig-zag but with more intelligence
            // Favor the longer distance axis
            moveHorizontally = Math.abs(dirToTarget.x) > Math.abs(dirToTarget.y);

            // Occasionally flip it for less predictable movement
            if (random.nextFloat() < 0.2f) {
                moveHorizontally = !moveHorizontally;
            }
        } else {
            // Keep moving in current direction if we haven't walked long enough
            moveHorizontally = (currentDirection == Direction.LEFT || currentDirection == Direction.RIGHT);
        }

        // If we're close on one axis, focus on the other axis
        if (Math.abs(dirToTarget.x) < 32) moveHorizontally = false;
        if (Math.abs(dirToTarget.y) < 32) moveHorizontally = true;

        // Calculate new direction
        Direction newDirection;
        if (moveHorizontally) {
            // Move horizontally
            newDirection = dirToTarget.x > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            // Move vertically
            newDirection = dirToTarget.y > 0 ? Direction.UP : Direction.DOWN;
        }

        // Rest of method remains the same
        // If direction changed, handle turning
        if (newDirection != currentDirection) {
            setNewDirection(newDirection);

            // If we're now turning, return early
            if (movementState == MovementState.TURNING) {
                return;
            }
        }

        // Apply velocity based on current direction
        linearVelocity.set(0, 0);
        switch (currentDirection) {
            case UP:
                linearVelocity.set(0, maxLinearSpeed);
                break;
            case DOWN:
                linearVelocity.set(0, -maxLinearSpeed);
                break;
            case LEFT:
                linearVelocity.set(-maxLinearSpeed, 0);
                break;
            case RIGHT:
                linearVelocity.set(maxLinearSpeed, 0);
                break;
        }

        // Apply movement
        position.add(linearVelocity.x * delta, linearVelocity.y * delta);

        // Enforce map boundaries
        float mapWidth = mapTileWidth * tileWidth;
        float mapHeight = mapTileHeight * tileHeight;
        position.x = MathUtils.clamp(position.x, 0, mapWidth - 64);
        position.y = MathUtils.clamp(position.y, 0, mapHeight - 64);

        // Check if the new position is walkable
        if (!gameScreen.isWalkable(position)) {
            // Move back to old position
            position.set(oldPosition);

            // Handle obstacle by choosing a different direction
            if (moveHorizontally) {
                // We were moving horizontally and hit an obstacle
                // Try moving vertically instead
                if (dirToTarget.y > 0) {
                    setNewDirection(Direction.UP);
                } else {
                    setNewDirection(Direction.DOWN);
                }
            } else {
                // We were moving vertically and hit an obstacle
                // Try moving horizontally instead
                if (dirToTarget.x > 0) {
                    setNewDirection(Direction.RIGHT);
                } else {
                    setNewDirection(Direction.LEFT);
                }
            }

            // If we're consistently hitting obstacles, try a completely new target
            directionChangeTimer += directionChangeDuration * 2;

            // If we hit obstacles multiple times in quick succession, find a new path
            obstacleHitCount++;
            obstacleHitTimer = 0;

            if (obstacleHitCount >= 3) {
                // We've hit too many obstacles, try a new intermediate target
                Vector2 intermediateTarget = findIntermediateTarget(gameScreen);
                if (intermediateTarget != null) {
                    // Use this as a waypoint before continuing to the final target
                    previousTarget.set(targetPosition);
                    targetPosition.set(intermediateTarget);
                    usingIntermediateTarget = true;
                } else {
                    // If can't find intermediate target, just go back to exploring
                    hasTarget = false;
                    obstacleHitCount = 0;
                    stateMachine.changeState(AIState.EXPLORING);
                }
            }
        } else {
            // Successfully moved, so increment obstacle timer
            obstacleHitTimer += delta;

            // Reset obstacle count if we haven't hit an obstacle in a while
            if (obstacleHitTimer > 1.5f) {
                obstacleHitCount = 0;
            }

            // If using an intermediate target and we've reached it, go back to the original target
            if (usingIntermediateTarget && position.dst(targetPosition) < 32) {
                targetPosition.set(previousTarget);
                usingIntermediateTarget = false;
            }
        }

        // Check if we've reached the target
        if (position.dst(targetPosition) < 32 && !usingIntermediateTarget) {
            hasTarget = false;
            obstacleHitCount = 0;
            stateMachine.changeState(AIState.EXPLORING);
        }
    }

    // Find a suitable intermediate target to navigate around obstacles
    private Vector2 findIntermediateTarget(GameScreen gameScreen) {
        // Try to find a clear path in different directions
        Vector2[] possibleDirections = new Vector2[8];
        possibleDirections[0] = new Vector2(1, 0);   // Right
        possibleDirections[1] = new Vector2(-1, 0);  // Left
        possibleDirections[2] = new Vector2(0, 1);   // Up
        possibleDirections[3] = new Vector2(0, -1);  // Down
        possibleDirections[4] = new Vector2(1, 1);   // Up-Right
        possibleDirections[5] = new Vector2(1, -1);  // Down-Right
        possibleDirections[6] = new Vector2(-1, 1);  // Up-Left
        possibleDirections[7] = new Vector2(-1, -1); // Down-Left

        // Shuffle the directions to avoid predictable behavior
        List<Vector2> directions = new ArrayList<>();
        for (Vector2 dir : possibleDirections) {
            directions.add(dir);
        }
        Collections.shuffle(directions);

        // Try each direction
        for (Vector2 dir : directions) {
            // Try different distances
            for (float distance = 64; distance <= 192; distance += 32) {
                Vector2 testPoint = new Vector2(
                    position.x + dir.x * distance,
                    position.y + dir.y * distance
                );

                // Make sure it's within map boundaries
                float mapWidth = mapTileWidth * tileWidth;
                float mapHeight = mapTileHeight * tileHeight;
                if (testPoint.x < 0 || testPoint.x >= mapWidth - 64 ||
                    testPoint.y < 0 || testPoint.y >= mapHeight - 64) {
                    continue;
                }

                // Check if the position is walkable
                if (gameScreen.isWalkable(testPoint)) {
                    return testPoint;
                }
            }
        }

        // If we couldn't find a good intermediate target, return null
        return null;
    }
    // Method to process hints
// (this will be called by GameScreen)
    public void processHint(String hint, List<Landmark> landmarks) {
        // Force immediate hint processing
        if (databaseManager != null && !hint.isEmpty()) {
            // Reset the roaming state
            databaseManager.forceHintProcessing(hint, landmarks);
        }
    }
    // 4. Similarly, in notifyTreasureCollected, update maxLinearSpeed when disabling the boost
    public void notifyTreasureCollected(Vector2 treasurePosition, boolean collectedByPlayer) {
        // Notify the database manager
        if (databaseManager != null) {
            databaseManager.notifyTreasureCollected(treasurePosition);
        }

        // If player collected the treasure, activate speed boost
        if (collectedByPlayer) {
            activateSpeedBoost();
        } else {
            // If AI collected the treasure, deactivate speed boost
            isSpeedBoostActive = false;
            moveSpeed = normalMoveSpeed;
            maxLinearSpeed = moveSpeed; // Update maxLinearSpeed to match moveSpeed
            System.out.println("AI collected treasure. Speed returned to normal: " + moveSpeed);
        }

        // Reset target-seeking behavior
        hasTarget = false;
        stateMachine.changeState(AIState.EXPLORING);
    }



    // Steerable implementation (required for gdx-ai)
    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public float getOrientation() {
        return 0; // Not used in this simple implementation
    }

    @Override
    public void setOrientation(float orientation) {
        // Not used in this simple implementation
    }

    @Override
    public float vectorToAngle(Vector2 vector2) {
        return 0;
    }

    @Override
    public Vector2 angleToVector(Vector2 vector2, float v) {
        return null;
    }

    @Override
    public Location<Vector2> newLocation() {
        return null;
    }

    @Override
    public Vector2 getLinearVelocity() {
        return linearVelocity;
    }

    @Override
    public float getAngularVelocity() {
        return 0; // Not used in this simple implementation
    }

    @Override
    public float getBoundingRadius() {
        return 32; // Half of the AI's width/height
    }

    @Override
    public boolean isTagged() {
        return tagged;
    }

    @Override
    public void setTagged(boolean tagged) {
        this.tagged = tagged;
    }

    @Override
    public float getZeroLinearSpeedThreshold() {
        return zeroLinearSpeedThreshold;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {
        this.zeroLinearSpeedThreshold = value;
    }

    @Override
    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }

    @Override
    public void setMaxLinearSpeed(float maxLinearSpeed) {
        this.maxLinearSpeed = maxLinearSpeed;
    }

    @Override
    public float getMaxLinearAcceleration() {
        return maxLinearAcceleration;
    }

    @Override
    public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        this.maxLinearAcceleration = maxLinearAcceleration;
    }

    @Override
    public float getMaxAngularSpeed() {
        return maxAngularSpeed;
    }

    @Override
    public void setMaxAngularSpeed(float maxAngularSpeed) {
        this.maxAngularSpeed = maxAngularSpeed;
    }

    @Override
    public float getMaxAngularAcceleration() {
        return maxAngularAcceleration;
    }

    @Override
    public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        this.maxAngularAcceleration = maxAngularAcceleration;
    }
}
