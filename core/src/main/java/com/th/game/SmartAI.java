package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

public class SmartAI extends AIPlayer {
    private List<Vector2> treasureHotspots;
    private Vector2 currentTarget;
    float targetUpdateTimer = 0;
    float targetUpdateInterval = 3.0f; // Change target every 3 seconds
    private Random random;
    private int currentRegion = 0; // Current region being explored
    private Direction currentDirection = Direction.DOWN;
    private int mapWidth, mapHeight;
    private Vector2[][] regionTargets; // Pre-calculated targets for each region
    private boolean hasVisitedTreasureLocation = false;
    private Function<Vector2, Boolean> isWalkableFunction;
    private Direction lastDirection = null;
    private int directionChangeCounter = 0;
    private static final int MIN_STEPS_BEFORE_REVERSAL = 10;

    public SmartAI(Vector2 position, String mapName) {
        super(position);
        treasureHotspots = new ArrayList<>();
        random = new Random();

        // Load treasure hotspots from the database
        loadTreasureHotspots(mapName);
    }

    // Set the walkable function reference
    public void setWalkableFunction(Function<Vector2, Boolean> isWalkable) {
        this.isWalkableFunction = isWalkable;
    }

    private void loadTreasureHotspots(String mapName) {
        try {
            List<TreasureCollectionData> collections = TrainingDataDAO.getCollectionDataByMap(mapName);

            if (collections.size() >= 3) {
                for (TreasureCollectionData data : collections) {
                    treasureHotspots.add(data.getTreasurePosition());
                }
                hasVisitedTreasureLocation = true;
                System.out.println("Loaded " + treasureHotspots.size() + " treasure hotspots for AI");
            } else {
                System.out.println("Not enough collection data for map: " + mapName);
                hasVisitedTreasureLocation = false;
            }
        } catch (SQLException e) {
            System.err.println("Error loading treasure hotspots: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Should be called once to set up the map dimensions
    public void setMapDimensions(int width, int height) {
        this.mapWidth = width;
        this.mapHeight = height;

        // Create region targets - dividing map into 9 regions (3x3 grid)
        regionTargets = new Vector2[9][3]; // 9 regions, 3 targets per region

        int regionWidth = mapWidth / 3;
        int regionHeight = mapHeight / 3;

        // For each region
        for (int region = 0; region < 9; region++) {
            int regionX = (region % 3) * regionWidth;
            int regionY = (region / 3) * regionHeight;

            // Create 3 random targets within this region
            for (int i = 0; i < 3; i++) {
                // Try to find a walkable position in this region
                Vector2 targetPos = findWalkablePositionInRegion(
                    regionX, regionY, regionWidth, regionHeight);
                regionTargets[region][i] = targetPos;
            }
        }
    }

    // Find a walkable position within a specific region
    private Vector2 findWalkablePositionInRegion(int regionX, int regionY, int width, int height) {
        // Try up to 10 times to find a walkable position
        for (int attempt = 0; attempt < 10; attempt++) {
            float x = regionX + random.nextFloat() * width;
            float y = regionY + random.nextFloat() * height;
            Vector2 pos = new Vector2(x, y);

            // Check if position is walkable
            if (isWalkableFunction != null && isWalkableFunction.apply(pos)) {
                return pos;
            }
        }

        // If we couldn't find a walkable position, return the center of the region
        // (we'll check walkability again when we try to move there)
        return new Vector2(regionX + width/2, regionY + height/2);
    }

    public void update(float delta, Vector2 playerPosition, ArrayList<TreasureChest> treasureChests, boolean isAreaWalkable) {
        // Update timer
        targetUpdateTimer += delta;

        // Increment counter each update to track how long we've been moving in this direction
        // Only increment if we haven't just changed directions
        if (directionChangeCounter >= 0) {
            directionChangeCounter++;
        }

        // Update target periodically or when reached
        if (currentTarget == null || targetUpdateTimer >= targetUpdateInterval ||
            (currentTarget != null && position.dst(currentTarget) < 32)) {

            // Reset timer
            targetUpdateTimer = 0;

            // Choose next target based on available treasure data
            if (hasVisitedTreasureLocation && !treasureHotspots.isEmpty() && random.nextFloat() < 0.3f) {
                // 30% chance to target a known treasure location
                currentTarget = treasureHotspots.get(random.nextInt(treasureHotspots.size()));
            } else {
                // Otherwise explore regions systematically
                chooseRegionalTarget();
            }

            // Verify target is walkable
            ensureWalkableTarget();

            // Set direction based on new target
            chooseDirectionToTarget();
        }
    }
    private void ensureWalkableTarget() {
        if (currentTarget == null || isWalkableFunction == null) return;

        // Check if current target is walkable
        if (!isWalkableFunction.apply(currentTarget)) {
            // Try to find a walkable target nearby
            for (int attempt = 0; attempt < 8; attempt++) {
                // Try different directions
                float angle = attempt * (360f / 8);
                float distance = 50; // Search 50 pixels away
                float x = currentTarget.x + (float)Math.cos(Math.toRadians(angle)) * distance;
                float y = currentTarget.y + (float)Math.sin(Math.toRadians(angle)) * distance;

                // Keep within map bounds
                x = Math.min(Math.max(x, 0), mapWidth - 1);
                y = Math.min(Math.max(y, 0), mapHeight - 1);

                Vector2 newTarget = new Vector2(x, y);
                if (isWalkableFunction.apply(newTarget)) {
                    currentTarget = newTarget;
                    return;
                }
            }

            // If we still can't find a walkable target, choose a new region
            currentRegion = (currentRegion + 1) % 9;
            chooseRegionalTarget();
        }
    }

    private void chooseRegionalTarget() {
        // Get a target from the current region
        int targetIndex = random.nextInt(3); // Each region has 3 targets
        currentTarget = regionTargets[currentRegion][targetIndex];

        // If we're close to the target or by random chance, move to next region
        if (position.dst(currentTarget) < 100 || random.nextFloat() < 0.2f) {
            // Move to the next region
            currentRegion = (currentRegion + 1) % 9;
        }
    }

    // Choose one of the four cardinal directions to move toward the target
    // Replace chooseDirectionToTarget method with this improved version
    private void chooseDirectionToTarget() {
        if (currentTarget == null) return;

        // Calculate the differences
        float dx = currentTarget.x - position.x;
        float dy = currentTarget.y - position.y;

        Direction preferredDirection;

        // Determine which direction (horizontal or vertical) to prioritize
        if (Math.abs(dx) > Math.abs(dy)) {
            // Prioritize horizontal movement
            preferredDirection = dx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            // Prioritize vertical movement
            preferredDirection = dy > 0 ? Direction.UP : Direction.DOWN;
        }

        // Check if this would result in an immediate reversal
        boolean wouldReverseDirection =
            (preferredDirection == Direction.UP && currentDirection == Direction.DOWN) ||
                (preferredDirection == Direction.DOWN && currentDirection == Direction.UP) ||
                (preferredDirection == Direction.LEFT && currentDirection == Direction.RIGHT) ||
                (preferredDirection == Direction.RIGHT && currentDirection == Direction.LEFT);

        // If it would reverse and we haven't moved enough in the current direction
        if (wouldReverseDirection && directionChangeCounter < MIN_STEPS_BEFORE_REVERSAL) {
            // Continue in the current direction until we've moved enough
            directionChangeCounter++;
        } else {
            // Otherwise, use the new direction
            lastDirection = currentDirection;
            currentDirection = preferredDirection;

            // Reset counter if we changed direction
            if (lastDirection != currentDirection) {
                directionChangeCounter = 0;
            } else {
                directionChangeCounter++;
            }
        }
    }

    public Vector2 getMovementDirection() {
        // Return a unit vector in one of the four cardinal directions
        switch (currentDirection) {
            case UP:
                return new Vector2(0, 1);
            case DOWN:
                return new Vector2(0, -1);
            case LEFT:
                return new Vector2(-1, 0);
            case RIGHT:
                return new Vector2(1, 0);
            default:
                return new Vector2(0, 0);
        }
    }

    public Direction getCurrentDirection() {
        return currentDirection;
    }

    public void addTreasureHotspot(Vector2 treasurePos) {
        treasureHotspots.add(new Vector2(treasurePos));
        hasVisitedTreasureLocation = true;
    }

    // For debugging
    public Vector2 getCurrentTarget() {
        return currentTarget;
    }
}
