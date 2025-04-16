package com.th.game;

import com.badlogic.gdx.math.Vector2;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

public class SmartAI extends AIPlayer {
    // Hotspot and target selection fields.
    private List<Vector2> treasureHotspots;
    private float baseSpeed;
    Vector2 currentTarget;
    private boolean boostActive = false; // Flag to indicate a one-time speed boost.
    private float boostTimer = 0f;             // Remaining time for active boost.
    private final float boostDuration = 3.0f;    // Duration for boost (e.g., 3 seconds)
    private int updatesSinceHotspot = 0;              // Counter for target updates without using a hotspot.
    private final int forceHotspotInterval = 3;         // Force hotspot use every 3 updates.
    float targetUpdateTimer = 0;
    float targetUpdateInterval = 3.0f; // Update target every 3 seconds.
    private Random random;
    private Direction currentDirection = Direction.DOWN;

    // Map and region fields.
    private int mapWidth, mapHeight;
    private Vector2[][] regionTargets; // 9 regions (3x3 grid) with 3 targets per region.
    private int[] regionVisitCounts;   // Track visits for each region.
    private boolean hasVisitedTreasureLocation = false;
    private Function<Vector2, Boolean> isWalkableFunction;

    // Speed boost fields.
    private final float boostMultiplier = 1.5f;  // Speed multiplier during boost.

    // Oscillation and edge handling.
    private static final float DIRECTION_TOLERANCE = 5f; // Threshold for changing direction
    private static final float EDGE_MARGIN = 32f;          // Margin from map edges
    private static final float STUCK_THRESHOLD = 1f;       // Minimum movement threshold
    private Vector2 previousPosition = new Vector2();

    // Direction update cooldown to reduce jitter.
    private static final float DIRECTION_COOLDOWN = 0.5f;   // In seconds
    private float directionCooldownTimer = 0f;

    public SmartAI(Vector2 position, String mapName) {
        super(position);
        this.baseSpeed = this.speed; // e.g., 150
        treasureHotspots = new ArrayList<>();
        random = new Random();
        previousPosition.set(position);
        loadTreasureHotspots(mapName);
    }

    // Set the walkable function.
    public void setWalkableFunction(Function<Vector2, Boolean> isWalkable) {
        this.isWalkableFunction = isWalkable;
    }

    // Set map dimensions, initialize regions and regional visit counts.
    public void setMapDimensions(int width, int height) {
        this.mapWidth = width;
        this.mapHeight = height;
        regionTargets = new Vector2[9][3];
        regionVisitCounts = new int[9]; // All starting at 0.
        int regionWidth = mapWidth / 3;
        int regionHeight = mapHeight / 3;
        for (int region = 0; region < 9; region++) {
            int regionX = (region % 3) * regionWidth;
            int regionY = (region / 3) * regionHeight;
            for (int i = 0; i < 3; i++) {
                regionTargets[region][i] = findWalkablePositionInRegion(regionX, regionY, regionWidth, regionHeight);
            }
        }
    }

    // Find a walkable position in a region; fallback to region center.
    private Vector2 findWalkablePositionInRegion(int regionX, int regionY, int width, int height) {
        for (int attempt = 0; attempt < 10; attempt++) {
            float x = regionX + random.nextFloat() * width;
            float y = regionY + random.nextFloat() * height;
            Vector2 pos = new Vector2(x, y);
            if (isWalkableFunction != null && isWalkableFunction.apply(pos)) {
                return pos;
            }
        }
        return new Vector2(regionX + width / 2f, regionY + height / 2f);
    }

    // Load treasure hotspots.
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

    // Trigger a one-frame speed boost.
    public void triggerSpeedBoost() {
        boostTimer = boostDuration;
    }


    // Update method: called each frame.
    public void update(float delta, Vector2 playerPosition, ArrayList<TreasureChest> treasureChests, boolean isAreaWalkable) {
        // Set speed based on boost flag.
        if (boostTimer > 0) {
            speed = baseSpeed * boostMultiplier;
            boostTimer -= delta;
        } else {
            speed = baseSpeed;
        }

        targetUpdateTimer += delta;
        directionCooldownTimer -= delta;

        // Update target if needed.
        if (currentTarget == null || targetUpdateTimer >= targetUpdateInterval || position.dst(currentTarget) < 32) {
            targetUpdateTimer = 0;
            updatesSinceHotspot++;
            boolean useHotspot = false;
            float hotspotProbability = Math.min(1.0f, 0.3f + 0.05f * treasureHotspots.size());
            if (hasVisitedTreasureLocation && !treasureHotspots.isEmpty()) {
                if (random.nextFloat() < hotspotProbability || updatesSinceHotspot >= forceHotspotInterval) {
                    useHotspot = true;
                }
            }
            if (useHotspot) {
                currentTarget = treasureHotspots.get(random.nextInt(treasureHotspots.size()));
                updatesSinceHotspot = 0;
            } else {
                chooseRegionalTarget();
            }
            ensureWalkableTarget();
            if (directionCooldownTimer <= 0) {
                updateDirectionToTarget();
                directionCooldownTimer = DIRECTION_COOLDOWN;
            }
        }

        // Edge avoidance: if near the border, choose a central target.
        if (position.x < EDGE_MARGIN || position.x > mapWidth - EDGE_MARGIN ||
            position.y < EDGE_MARGIN || position.y > mapHeight - EDGE_MARGIN) {
            currentTarget = new Vector2(mapWidth / 2f, mapHeight / 2f);
            updateDirectionToTarget();
            directionCooldownTimer = DIRECTION_COOLDOWN;
        }

        // Check if the AI is stuck (hasn't moved sufficiently).
        if (position.dst(previousPosition) < STUCK_THRESHOLD) {
            chooseRegionalTarget();
            ensureWalkableTarget();
            updateDirectionToTarget();
            directionCooldownTimer = DIRECTION_COOLDOWN;
        }
        previousPosition.set(position);

        // Before moving, check if the next step is walkable.
        Vector2 nextStep = position.cpy().add(getMovementDirection().scl(speed * delta));
        if (isWalkableFunction != null && !isWalkableFunction.apply(nextStep)) {
            chooseRegionalTarget();
            ensureWalkableTarget();
            updateDirectionToTarget();
            directionCooldownTimer = DIRECTION_COOLDOWN;
        }

        // Finally, move strictly in the current cardinal direction.
        position.add(getMovementDirection().scl(speed * delta));
    }

    // Choose a regional target that favors less-explored areas.
    private void chooseRegionalTarget() {
        int minVisitCount = Integer.MAX_VALUE;
        List<Integer> candidateRegions = new ArrayList<>();
        for (int region = 0; region < regionVisitCounts.length; region++) {
            if (regionVisitCounts[region] < minVisitCount) {
                minVisitCount = regionVisitCounts[region];
                candidateRegions.clear();
                candidateRegions.add(region);
            } else if (regionVisitCounts[region] == minVisitCount) {
                candidateRegions.add(region);
            }
        }
        int chosenRegion = candidateRegions.get(random.nextInt(candidateRegions.size()));
        regionVisitCounts[chosenRegion]++;
        int targetIndex = random.nextInt(3);
        currentTarget = regionTargets[chosenRegion][targetIndex];
    }

    // Ensure the target is walkable; if not, adjust.
    private void ensureWalkableTarget() {
        if (currentTarget == null || isWalkableFunction == null) return;
        if (!isWalkableFunction.apply(currentTarget)) {
            for (int attempt = 0; attempt < 8; attempt++) {
                float angle = attempt * (360f / 8);
                float distance = 50;
                float x = currentTarget.x + (float)Math.cos(Math.toRadians(angle)) * distance;
                float y = currentTarget.y + (float)Math.sin(Math.toRadians(angle)) * distance;
                x = Math.min(Math.max(x, 0), mapWidth - 1);
                y = Math.min(Math.max(y, 0), mapHeight - 1);
                Vector2 newTarget = new Vector2(x, y);
                if (isWalkableFunction.apply(newTarget)) {
                    currentTarget = newTarget;
                    return;
                }
            }
            chooseRegionalTarget();
        }
    }

    // Update the currentDirection based on the target, with a tolerance to reduce jitter.
// Update the currentDirection based on the target, with additional checks to avoid rapid oscillations.
    private void updateDirectionToTarget() {
        Vector2 diff = currentTarget.cpy().sub(position);
        Direction desiredDirection;

        // Determine the desired direction based on which axis has a significantly larger difference.
        if (Math.abs(diff.x) >= Math.abs(diff.y) + DIRECTION_TOLERANCE) {
            desiredDirection = diff.x > 0 ? Direction.RIGHT : Direction.LEFT;
        } else if (Math.abs(diff.y) >= Math.abs(diff.x) + DIRECTION_TOLERANCE) {
            desiredDirection = diff.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            desiredDirection = currentDirection; // If differences are minimal, maintain direction.
        }

        // Check if the new desired direction is opposite to the current direction.
        boolean isOpposite = (desiredDirection == Direction.UP && currentDirection == Direction.DOWN) ||
            (desiredDirection == Direction.DOWN && currentDirection == Direction.UP) ||
            (desiredDirection == Direction.LEFT && currentDirection == Direction.RIGHT) ||
            (desiredDirection == Direction.RIGHT && currentDirection == Direction.LEFT);

        // Only switch if not an immediate reversal OR if the AI is very close to the target (thus requiring a change).
        if (isOpposite && diff.len() > 32) {
            // Do not update; keep the current direction to prevent oscillation.
            return;
        } else {
            currentDirection = desiredDirection;
        }
    }


    // Return a cardinal unit vector based on currentDirection.
    public Vector2 getMovementDirection() {
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

    // Add a new hotspot (if treasure is collected, etc.).
    public void addTreasureHotspot(Vector2 treasurePos) {
        treasureHotspots.add(new Vector2(treasurePos));
        hasVisitedTreasureLocation = true;
    }

}
