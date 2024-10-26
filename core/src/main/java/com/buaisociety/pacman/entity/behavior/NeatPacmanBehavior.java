package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
// import com.badlogic.gdx.maps.Map;
// import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.utils.Null;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.GameManager;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.PacmanEntity;
import com.buaisociety.pacman.entity.FruitEntity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.entity.GhostState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;



public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;
    private @Nullable FruitEntity fruit;

    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;

    private int lastStepLives = 3;

    private int numUpdatesSinceLastScore = 0;
    private int lastScore = 0;
    private int lastPelletCount = 0;
    // private Vector2d last_position = pacman.getPosition();

    private int mazeX = 28;
    private int mazeY = 36;
    private int[][] trackVisitState = new int[mazeX][mazeY];


    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
    }

/***************************************************************FUNC START************************************************************************/
    private int calculatePelletsInDirection(Direction direction) {
        int pelletCount = 0;
        Vector2i currentTilePosition = pacman.getTilePosition(); // Get Pacman's current tile position
        Maze maze = pacman.getMaze(); // Get the maze reference
        
        // Simulate movement in the specified direction
        while (true) {
            // Move to the next tile in the specified direction
            currentTilePosition.add(direction.asVector());
            
            // Get the next tile in the maze
            Tile currentTile = maze.getTile(currentTilePosition);
            
            // Check if the tile is passable (not a wall)
            if (!currentTile.getState().isPassable()) {
                break; // Stop if a wall is encountered
            }
            
            // Check if the tile contains a pellet
            if (currentTile.getState() == TileState.PELLET) {
                pelletCount++; // Increment the pellet count
            }

            

            if (currentTile.getState() == TileState.POWER_PELLET) {

                pelletCount++;
            }
        }
        
        return pelletCount;
    }


    private boolean isValidPosition(Maze maze, Vector2i position) {
        return position != null && maze != null && 
               position.x >= 0 && position.x < maze.getDimensions().x() &&
               position.y >= 0 && position.y < maze.getDimensions().y();
    }

    private int penalizePelletInteraction() {

        Vector2i currentTilePosition = pacman.getTilePosition(); // Get Pacman's current tile position
        Maze maze = pacman.getMaze(); // Get the maze reference
        
        // Get the next tile in the maze
        Tile currentTile = maze.getTile(currentTilePosition);
            
        // Check if the tile is passable (not a wall)
        if (!currentTile.getState().isPassable()) {
            return -10; // Stop if a wall is encountered
        }

        // Check if the tile contains a pellet
        if (currentTile.getState() == TileState.PELLET) {
            return 10;
        }

        if (currentTile.getState() == TileState.POWER_PELLET) {
           return 200;
        }

        return -1;   // Not sure one or zero.
    }

    private int penalizeGhostInteraction() {
        // Get Pacman's current tile position and direction
        Vector2i currentTilePosition = pacman.getTilePosition();
        Direction pacmanDirection = pacman.getDirection();
    
        // Iterate over all entities in the maze
        for (Entity entity : pacman.getMaze().getEntities()) {
            if (entity instanceof GhostEntity) {
                GhostEntity ghost = (GhostEntity) entity;
                Vector2i ghostPosition = ghost.getTilePosition();
                Direction ghostDirection = ghost.getDirection();
    
                // Calculate Manhattan distance between ghost and Pacman
                int distance = Math.abs(ghostPosition.x - currentTilePosition.x) + Math.abs(ghostPosition.y - currentTilePosition.y);
    
                // Apply penalty if Pacman and ghost are moving toward each other within 1 or 2 tiles with no walls in between
                if (distance <= 2) {
                    if (ghost.getState() == GhostState.CHASE || ghost.getState() == GhostState.SCATTER) {

                        if (isMovingTowardsEachOther(pacmanDirection, ghostDirection, currentTilePosition, ghostPosition, distance)){
                            return -5000;
                        }
                        return -1000;
                    }
                    if (ghost.getState() == GhostState.FRIGHTENED) {
                        return +500;
                    }

                }

                
    
                // Additional penalty if they are on the same tile
                if (currentTilePosition.equals(ghostPosition)) {
                    if (ghost.getState() == GhostState.CHASE || ghost.getState() == GhostState.SCATTER) {
                        return -5000;
                    }
                    if (ghost.getState() == GhostState.FRIGHTENED) {
                        return +1000;
                    }
                    if (ghost.getState() == GhostState.EATEN) {
                        return 0; // No penalty or reward if the ghost is eaten
                    }
                }
            }
        }
        return 0;
    }
    
    // Helper method to determine if Pacman and ghost are moving towards each other within 1 or 2 tiles with no walls in between
    private boolean isMovingTowardsEachOther(Direction pacmanDirection, Direction ghostDirection, Vector2i pacmanPosition, Vector2i ghostPosition, int distance) {
        int dx = ghostPosition.x - pacmanPosition.x;
        int dy = ghostPosition.y - pacmanPosition.y;
    
        // Check if Pacman and ghost are in the same row or column and moving towards each other
        boolean movingTowards = (dx > 0 && dx <= distance && pacmanDirection == Direction.RIGHT && ghostDirection == Direction.LEFT) ||
                                (dx < 0 && -dx <= distance && pacmanDirection == Direction.LEFT && ghostDirection == Direction.RIGHT) ||
                                (dy > 0 && dy <= distance && pacmanDirection == Direction.DOWN && ghostDirection == Direction.UP) ||
                                (dy < 0 && -dy <= distance && pacmanDirection == Direction.UP && ghostDirection == Direction.DOWN);
    
        if (movingTowards) {
            // Check for walls between Pacman and the ghost
            if (dx != 0) { // Moving horizontally
                int step = dx > 0 ? 1 : -1;
                for (int x = pacmanPosition.x + step; x != ghostPosition.x; x += step) {
                    Vector2i intermediatePosition = new Vector2i(x, pacmanPosition.y);
                    if (!pacman.getMaze().getTile(intermediatePosition).getState().isPassable()) {
                        return false; // Wall found in between
                    }
                }
            } else if (dy != 0) { // Moving vertically
                int step = dy > 0 ? 1 : -1;
                for (int y = pacmanPosition.y + step; y != ghostPosition.y; y += step) {
                    Vector2i intermediatePosition = new Vector2i(pacmanPosition.x, y);
                    if (!pacman.getMaze().getTile(intermediatePosition).getState().isPassable()) {
                        return false; // Wall found in between
                    }
                }
            }
        }
        
        return movingTowards;
    }

    // private int penalizeGhostInteraction() {
    //         // Get Pacman's current position
    //     Vector2d pacmanPosition = pacman.getPosition();
    
    //     // Iterate over all entities in the maze
    //     for (Entity entity : pacman.getMaze().getEntities()) {
    //         // Check if the entity is a ghost
    //         if (entity instanceof GhostEntity) {
    //             GhostEntity ghost = (GhostEntity) entity;
    //             // Get the ghost's position
    //             Vector2d ghostPosition = ghost.getPosition();
    
    //             // Check if ghost and pacman are in the same position
    //             if (pacmanPosition == ghostPosition) {
    //                 if (ghost.getState() == GhostState.CHASE || ghost.getState() == GhostState.SCATTER) {
    //                     return -5000;
    //                 }
    //                 if (ghost.getState() == GhostState.EATEN) {
    //                     return 0;         
    //                 }
    //                 return +1000;
    //             }
    //         }
    //     }
            
    //     return 0;
    // }

    private float penalizeFruitInteraction() {
        // Get Pacman's current tile position as integer coordinates
        int pacmanTileX = pacman.getTilePosition().x;
        int pacmanTileY = pacman.getTilePosition().y;
        
        // Iterate over all entities in the maze
        for (Entity entity : pacman.getMaze().getEntities()) {
            // Check if the entity is a FruitEntity
            if (entity instanceof FruitEntity) {
                FruitEntity fruit = (FruitEntity) entity;
                // Get the fruit's tile position as integer coordinates
                int fruitTileX = fruit.getTilePosition().x;
                int fruitTileY = fruit.getTilePosition().y;
                // Reward if Pacman is on the same tile as the fruit
                if (pacmanTileX == fruitTileX && pacmanTileY == fruitTileY) {
                    return 200;
                }
            }
        }
        return 0;
    }

    
    private int[] findNearestFruitPosition() {
        Maze maze = pacman.getMaze();
        Vector2i pacmanTile = pacman.getTilePosition();
        
        // Validate initial conditions
        if (maze == null || pacmanTile == null || !isValidPosition(maze, pacmanTile)) {
            return null;
        }
        
        Vector2i nearestFruitPosition = null;
        int minSteps = Integer.MAX_VALUE;
    
        Queue<Vector2i> queue = new LinkedList<>();
        queue.add(new Vector2i(pacmanTile)); // Create a copy
        Set<Vector2i> visited = new HashSet<>();
        visited.add(new Vector2i(pacmanTile)); // Create a copy
    
        int steps = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Vector2i currentPosition = queue.poll();
                if (currentPosition == null) continue;
    
                // Check for fruits at current position
                for (Entity entity : maze.getEntities()) {
                    if (entity instanceof FruitEntity) {
                        FruitEntity fruit = (FruitEntity) entity;
                        Vector2i fruitTilePos = maze.toTileCoords(fruit.getPosition());
                        
                        // Validate fruit position
                        if (fruitTilePos != null && isValidPosition(maze, fruitTilePos) && 
                            fruitTilePos.equals(currentPosition)) {
                            if (steps < minSteps) {
                                minSteps = steps;
                                nearestFruitPosition = new Vector2i(currentPosition); // Create a copy
                            }
                        }
                    }
                }
    
                // Explore neighboring tiles
                for (Direction direction : Direction.values()) {
                    // Calculate neighbor position
                    Vector2i neighbor = new Vector2i(
                        currentPosition.x + direction.getDx(),
                        currentPosition.y + direction.getDy()
                    );
                    
                    // Validate neighbor position
                    if (!isValidPosition(maze, neighbor)) {
                        continue;
                    }
    
                    Tile neighborTile = maze.getTile(neighbor);
                    
                    if (neighborTile != null && neighborTile.getState().isPassable() && 
                        !visited.contains(neighbor)) {
                        visited.add(new Vector2i(neighbor)); // Create a copy
                        queue.add(new Vector2i(neighbor)); // Create a copy
                    }
                }
            }
            steps++;
        }
    
        // If no fruit is found, return null; otherwise, return an array with x, y, and steps
        return nearestFruitPosition != null ? 
               new int[]{nearestFruitPosition.x, nearestFruitPosition.y, minSteps} : 
               null;
    }
    
    private float[] encodeNearestFruitPosition() {
        float[] defaultEncoding = new float[]{0f, 0f, 0f};
        
        // Validate Pacman state
        if (pacman == null) {
            return defaultEncoding;
        }
    
        // Find the nearest fruit position and steps using BFS-based Manhattan distance
        int[] result = findNearestFruitPosition();
    
        if (result == null) {
            return defaultEncoding;
        }
    
        try {
            // Get Pacman's current position as tile coordinates
            Vector2i pacmanTile = pacman.getTilePosition();
            if (pacmanTile == null) {
                return defaultEncoding;
            }
    
            // Calculate relative tile coordinates
            Vector2i relative = new Vector2i(result[0], result[1]).sub(pacmanTile);
    
            // Get and validate Pacman's direction
            Direction forward = pacman.getDirection();
            if (forward == null) {
                return defaultEncoding;
            }
            
            Direction right = forward.right();
    
            // Encode relative coordinates based on Pacman's current direction
            float fruitX = (float) (relative.x * forward.getDx() + relative.y * forward.getDy());
            float fruitY = (float) (relative.x * right.getDx() + relative.y * right.getDy());
    
            return new float[]{fruitX, fruitY, result[2]};
        } catch (Exception e) {
            // If any calculation fails, return the default encoding
            return defaultEncoding;
        }
    }

    // private Vector2i findNearestFruitPosition() {
    //     Maze maze = pacman.getMaze();
    //     Vector2d pacmanPosition = pacman.getPosition(); // Pacman's current position
    //     float minDistance = Float.MAX_VALUE;
    //     Vector2i nearestFruitPosition = null;
        
    //     // Iterate over all entities in the maze
    //     for (Entity entity : maze.getEntities()) {
    //         // Check if the entity is a FruitEntity
    //         if (entity instanceof FruitEntity) {
    //             FruitEntity fruit = (FruitEntity) entity;
    //             Vector2d fruitPosition = fruit.getPosition();
                
    //             // Calculate the distance from Pacman to this fruit
    //             float distance = (float) pacmanPosition.distance(fruitPosition);
                
    //             // If this fruit is closer than any previously found, update the nearest fruit
    //             if (distance < minDistance) {
    //                 minDistance = distance;
    //                 nearestFruitPosition = new Vector2i((int) fruitPosition.x(), (int) fruitPosition.y());
    //             }
    //         }
    //     }
        
    //     // Return the position of the nearest fruit, or null if no fruits are found
    //     return nearestFruitPosition;
    // }

    // private float[] encodeNearestFruitPosition() {
    //     // Find the nearest fruit position
    //     Vector2i nearestFruitPosition = findNearestFruitPosition();
    
    //     if (nearestFruitPosition == null) {
    //         // If no fruit is found, return zero features
    //         return new float[]{0f, 0f, 0f};
    //     }
    
    //     // Get Pacman's current position
    //     Vector2d pacmanPosition = pacman.getPosition();
    
    //     // Calculate relative coordinates
    //     Vector2d relative = new Vector2d(nearestFruitPosition.x, nearestFruitPosition.y).sub(pacmanPosition);
    
    //     // Encode relative coordinates based on Pacman's current direction
    //     Direction forward = pacman.getDirection();
    //     Direction right = forward.right();
    
    //     float fruitX = (float) (relative.x() * forward.getDx() + relative.y() * forward.getDy());
    //     float fruitY = (float) (relative.x() * right.getDx() + relative.y() * right.getDy());
    
    //     // Calculate the distance to the nearest fruit
    //     float distance = (float) relative.length();
    
    //     // Return the encoded features (normalized if necessary)
    //     return new float[]{fruitX, fruitY, distance};
    // }

    // private GhostEntity findNearestGhost() {
    //     // Get Pacman's current position
    //     Vector2d pacmanPosition = pacman.getPosition();
    //     float minDistance = Float.MAX_VALUE; // Start with a large value
    //     GhostEntity nearestGhost = null;
    
    //     // Iterate over all entities in the maze
    //     for (Entity entity : pacman.getMaze().getEntities()) {
    //         // Check if the entity is a ghost
    //         if (entity instanceof GhostEntity) {
    //             GhostEntity ghost = (GhostEntity) entity;
    //             // Get the ghost's position
    //             Vector2d ghostPosition = ghost.getPosition();
    
    //             // Calculate the distance to this ghost
    //             float distance = (float) pacmanPosition.distance(ghostPosition);
    
    //             // Update the nearest ghost if this ghost is closer
    //             if (distance < minDistance) {
    //                 minDistance = distance;
    //                 nearestGhost = ghost;
    //             }
    //         }
    //     }
    
    //     // Return the nearest ghost, or null if no ghosts are found
    //     return nearestGhost;
    // }

    // private float[] encodeNearestGhostMovement() {
    //     // Find the nearest ghost
    //     GhostEntity nearestGhost = findNearestGhost();
    
    //     if (nearestGhost == null) {
    //         // If no ghost is found, return zero features
    //         return new float[]{0f, 0f, 0f, 0f, 0f, 0f};
    //     }
    
    //     // Get Pacman's current position
    //     Vector2d pacmanPosition = pacman.getPosition();
    //     // Get the ghost's position
    //     Vector2d ghostPosition = nearestGhost.getPosition();
    
    //     // Calculate relative coordinates
    //     Vector2d relative = new Vector2d(ghostPosition.x(), ghostPosition.y()).sub(pacmanPosition);
    
    //     // Encode relative coordinates based on Pacman's current direction
    //     Direction forward = pacman.getDirection();
    //     Direction right = forward.right();
    
    //     float ghostX = (float) (relative.x() * forward.getDx() + relative.y() * forward.getDy());
    //     float ghostY = (float) (relative.x() * right.getDx() + relative.y() * right.getDy());
    
    //     // Get the direction of the nearest ghost
    //     Direction ghostDirection = nearestGhost.getDirection();
    
    //     // Encode the ghost's direction (1 if aligned with a given direction, otherwise 0)
    //     float isMovingForward = ghostDirection == forward ? 1f : 0f;
    //     float isMovingRight = ghostDirection == right ? 1f : 0f;
    //     float isMovingLeft = ghostDirection == forward.left() ? 1f : 0f;
    //     float isMovingBackward = ghostDirection == forward.behind() ? 1f : 0f;
    
    //     // Return the encoded features
    //     return new float[]{ghostX, ghostY, isMovingForward, isMovingRight, isMovingLeft, isMovingBackward};
    // }

    private int bfsDistance(Vector2i startTile, Vector2i endTile) {
        if (startTile.equals(endTile)) return 0; // If Pacman and ghost are on the same tile
    
        Queue<Vector2i> queue = new LinkedList<>();
        Set<Vector2i> visited = new HashSet<>();
        queue.add(startTile);
        visited.add(startTile);
    
        int steps = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Vector2i current = queue.poll();
                if (current.equals(endTile)) return steps;
    
                // Check neighboring tiles
                for (Direction direction : Direction.values()) {
                    int neighborX = current.x + direction.getDx();
                    int neighborY = current.y + direction.getDy();
                    Vector2i neighbor = new Vector2i(neighborX, neighborY);
    
                    // Check if the neighbor is within bounds and is passable
                    if (isValidPosition(pacman.getMaze(), neighbor)) {
                        Tile neighborTile = pacman.getMaze().getTile(neighbor);
                        if (neighborTile.getState().isPassable() && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
            steps++;
        }
        return Integer.MAX_VALUE; // Return a large value if no path is found
    }

    private float[] encodeAllGhosts() {
        // Get Pacman's current position as tile coordinates
        Vector2i pacmanTile = pacman.getTilePosition();
    
        // List to store encoded features for each ghost
        List<float[]> ghostEncodings = new ArrayList<>();
        boolean isAnyGhostFrightened = false;
    
        // Iterate over all entities in the maze and encode each ghost
        for (Entity entity : pacman.getMaze().getEntities()) {
            if (entity instanceof GhostEntity) {
                GhostEntity ghost = (GhostEntity) entity;
    
                // Get the ghost's position as tile coordinates
                Vector2i ghostTile = pacman.getMaze().toTileCoords(ghost.getPosition());
    
                // Calculate relative coordinates
                Vector2i relative = new Vector2i(ghostTile).sub(pacmanTile);
    
                // Encode relative coordinates based on Pacman's current direction
                Direction forward = pacman.getDirection();
                Direction right = forward.right();
    
                float ghostX = (float) (relative.x * forward.getDx() + relative.y * forward.getDy());
                float ghostY = (float) (relative.x * right.getDx() + relative.y * right.getDy());
    
                // Calculate BFS distance to the ghost
                int distance = bfsDistance(pacmanTile, ghostTile);
    
                // Check if the ghost is in the CHASE state
                float isChasing = ghost.getState() == GhostState.CHASE ? 1f : 0f;
    
                // Check if the ghost is in the FRIGHTENED state
                if (ghost.getState() == GhostState.FRIGHTENED) {
                    isAnyGhostFrightened = true;
                }
    
                // Add the encoded features for this ghost
                ghostEncodings.add(new float[]{ghostX, ghostY, isChasing, (float) distance});
            }
        }
    
        // If fewer than four ghosts are found, pad the remaining slots with zeros
        while (ghostEncodings.size() < 4) {
            ghostEncodings.add(new float[]{0f, 0f, 0f, 0f});
        }
    
        // Flatten the list of encoded features into a single array
        float[] allGhostsEncoding = new float[17]; // 16 for the four ghosts' features + 1 for the frightened flag
        int index = 0;
        for (float[] encoding : ghostEncodings) {
            allGhostsEncoding[index++] = encoding[0];
            allGhostsEncoding[index++] = encoding[1];
            allGhostsEncoding[index++] = encoding[2];
            allGhostsEncoding[index++] = encoding[3];
        }
    
        // Add the frightened state as the last feature
        allGhostsEncoding[16] = isAnyGhostFrightened ? 1f : 0f;
    
        // Return the final encoding with all ghost features (17 features total)
        return allGhostsEncoding;
    }
    
    // private float[] encodeAllGhosts() {
    //     // Get Pacman's current position
    //     Vector2d pacmanPosition = pacman.getPosition();
    
    //     // List to store encoded features for each ghost
    //     List<float[]> ghostEncodings = new ArrayList<>();
    //     boolean isAnyGhostFrightened = false; // Track if any ghost is in the FRIGHTENED state
    
    //     // Iterate over all entities in the maze and encode each ghost
    //     for (Entity entity : pacman.getMaze().getEntities()) {
    //         // Check if the entity is a ghost
    //         if (entity instanceof GhostEntity) {
    //             GhostEntity ghost = (GhostEntity) entity;
    //             // Get the ghost's position
    //             Vector2d ghostPosition = ghost.getPosition();
    
    //             // Calculate relative coordinates
    //             Vector2d relative = new Vector2d(ghostPosition.x(), ghostPosition.y()).sub(pacmanPosition);
    
    //             // Encode relative coordinates based on Pacman's current direction
    //             Direction forward = pacman.getDirection();
    //             Direction right = forward.right();
    
    //             float ghostX = (float) (relative.x() * forward.getDx() + relative.y() * forward.getDy());
    //             float ghostY = (float) (relative.x() * right.getDx() + relative.y() * right.getDy());
    
    //             // Calculate the distance to the ghost
    //             float distance = (float) relative.length();
    
    //             // Check if the ghost is in the CHASE state
    //             float isChasing = ghost.getState() == GhostState.CHASE ? 1f : 0f;
    
    //             // Check if the ghost is in the FRIGHTENED state
    //             if (ghost.getState() == GhostState.FRIGHTENED) {
    //                 isAnyGhostFrightened = true;
    //             }
    
    //             // Add the encoded features for this ghost
    //             ghostEncodings.add(new float[]{ghostX, ghostY, isChasing, distance});
    //         }
    //     }
    
    //     // If fewer than four ghosts are found, pad the remaining slots with zeros
    //     while (ghostEncodings.size() < 4) {
    //         ghostEncodings.add(new float[]{0f, 0f, 0f, 0f});
    //     }
    
    //     // Flatten the list of encoded features into a single array
    //     float[] allGhostsEncoding = new float[17]; // 16 for the four ghosts' features + 1 for the frightened flag
    //     int index = 0;
    //     for (float[] encoding : ghostEncodings) {
    //         allGhostsEncoding[index++] = encoding[0];
    //         allGhostsEncoding[index++] = encoding[1];
    //         allGhostsEncoding[index++] = encoding[2];
    //         allGhostsEncoding[index++] = encoding[3];
    //     }
    
    //     // Add the frightened state as the last feature
    //     allGhostsEncoding[16] = isAnyGhostFrightened ? 1f : 0f;
    
    //     // Return the final encoding with all ghost features (17 features total)
    //     return allGhostsEncoding;
    // }

    private Vector2i findNearestPowerPelletPosition() {
        // Get Pacman's current tile position
        Vector2i startTilePosition = pacman.getTilePosition();
        Maze maze = pacman.getMaze();
    
        // Validate initial position
        if (!isValidPosition(maze, startTilePosition)) {
            return null;
        }
    
        // Queue for BFS
        Queue<Vector2i> queue = new LinkedList<>();
        // Set to track visited tiles
        Set<Vector2i> visited = new HashSet<>();
    
        // Start the BFS from Pacman's current tile
        queue.add(startTilePosition);
        visited.add(startTilePosition);
    
        // Directions to explore: UP, DOWN, LEFT, RIGHT
        Direction[] directions = Direction.values();
    
        // Perform BFS
        while (!queue.isEmpty()) {
            Vector2i currentTilePosition = queue.poll();
    
            // Validate current position
            if (!isValidPosition(maze, currentTilePosition)) {
                continue;
            }
    
            // Check if this tile contains a power pellet
            Tile currentTile = maze.getTile(currentTilePosition);
            if (currentTile.getState() == TileState.POWER_PELLET) {
                return new Vector2i(currentTilePosition); // Return a copy of the position
            }
    
            // Explore neighboring tiles in each direction
            for (Direction direction : directions) {
                Vector2i neighborPosition = new Vector2i(currentTilePosition).add(direction.asVector());
    
                // If the neighbor is valid, passable and not visited
                if (!visited.contains(neighborPosition) && isValidPosition(maze, neighborPosition)) {
                    Tile neighborTile = maze.getTile(neighborPosition);
                    if (neighborTile.getState().isPassable()) {
                        queue.add(new Vector2i(neighborPosition)); // Add a copy of the position
                        visited.add(neighborPosition);
                    }
                }
            }
        }
    
        // If no power pellet is found, return null
        return null;
    }
    
    private float[] encodeNearestPowerPelletPosition() {
        // Default return value for when no power pellet is found
        float[] defaultEncoding = new float[]{0f, 0f, 0f};
    
        // Get Pacman's position and validate
        Vector2d pacmanPosition = pacman.getPosition();
        if (pacmanPosition == null) {
            return defaultEncoding;
        }
    
        // Perform BFS to find the nearest power pellet
        Vector2i nearestPelletPosition = findNearestPowerPelletPosition();
        
        if (nearestPelletPosition == null) {
            return defaultEncoding;
        }
    
        try {
            // Calculate relative coordinates
            Vector2d relative = new Vector2d(nearestPelletPosition.x, nearestPelletPosition.y)
                .sub(pacmanPosition);
    
            // Get Pacman's current direction and validate
            Direction forward = pacman.getDirection();
            if (forward == null) {
                return defaultEncoding;
            }
    
            Direction right = forward.right();
    
            // Encode relative coordinates based on Pacman's current direction
            float pelletX = (float) (relative.x() * forward.getDx() + relative.y() * forward.getDy());
            float pelletY = (float) (relative.x() * right.getDx() + relative.y() * right.getDy());
            float distance = (float) relative.length();
    
            return new float[]{pelletX, pelletY, distance};
        } catch (Exception e) {
            // If any calculation fails, return the default encoding
            return defaultEncoding;
        }
    }

private float[] encodeTopNPelletPositions(int topN) {
    float[] encodedPellets = new float[topN * 3];
    Arrays.fill(encodedPellets, 0f);

    // Get Pacman's current tile position
    Vector2i pacmanTilePosition = pacman.getTilePosition();
    Maze maze = pacman.getMaze();

    if (!isValidPosition(maze, pacmanTilePosition)) {
        return encodedPellets;
    }

    // Map to store pellet positions and their BFS distances
    Map<Vector2i, Integer> pelletDistances = new HashMap<>();

    // Perform BFS to find all pellets
    Queue<Vector2i> queue = new LinkedList<>();
    Set<Vector2i> visited = new HashSet<>();
    queue.add(pacmanTilePosition);
    visited.add(pacmanTilePosition);

    int steps = 0;
    int levelSize = 1; // Number of nodes at current level
    int nodesProcessed = 0; // Nodes processed at current level

    while (!queue.isEmpty()) {
        Vector2i currentTilePosition = queue.poll();
        nodesProcessed++;

        // Check if the current position is within maze bounds
        if (isValidPosition(maze, currentTilePosition)) {
            Tile currentTile = maze.getTile(currentTilePosition);

            if (currentTile.getState() == TileState.PELLET || 
                currentTile.getState() == TileState.POWER_PELLET) {
                pelletDistances.put(new Vector2i(currentTilePosition), steps);
            }

            // Explore neighbors
            for (Direction direction : Direction.values()) {
                Vector2i neighborPosition = new Vector2i(currentTilePosition).add(direction.asVector());
                
                if (!visited.contains(neighborPosition) && isValidPosition(maze, neighborPosition)) {
                    Tile neighborTile = maze.getTile(neighborPosition);
                    if (neighborTile.getState().isPassable()) {
                        queue.add(new Vector2i(neighborPosition));
                        visited.add(neighborPosition);
                    }
                }
            }
        }

        // Check if we've processed all nodes at current level
        if (nodesProcessed == levelSize) {
            steps++;
            levelSize = queue.size();
            nodesProcessed = 0;
        }
    }

    // Sort pellets by BFS distance
    List<Map.Entry<Vector2i, Integer>> sortedPellets = new ArrayList<>(pelletDistances.entrySet());
    sortedPellets.sort(Map.Entry.comparingByValue());

    int index = 0;
    
    // Encode the nearest pellets up to topN
    for (Map.Entry<Vector2i, Integer> entry : sortedPellets) {
        if (index >= topN * 3) break;

        Vector2i pelletPosition = entry.getKey();
        int bfsDistance = entry.getValue();

        Vector2d relative = new Vector2d(pelletPosition.x, pelletPosition.y)
            .sub(pacman.getPosition());

        // Encode based on Pacman's current direction
        Direction forward = pacman.getDirection();
        Direction right = forward.right();

        float pelletX = (float) (relative.x() * forward.getDx() + relative.y() * forward.getDy());
        float pelletY = (float) (relative.x() * right.getDx() + relative.y() * right.getDy());

        // Use BFS distance instead of Euclidean distance
        encodedPellets[index++] = pelletX;
        encodedPellets[index++] = pelletY;
        encodedPellets[index++] = bfsDistance;
    }

    return encodedPellets;
}
    

    private float calculateDistanceToWall(Direction direction) {


        int distance = 0;
        Vector2i currentTilePosition = pacman.getTilePosition(); // Get Pacman's current tile position
        Maze maze = pacman.getMaze(); // Get the maze reference
    
        // Simulate movement in the specified direction without actually moving Pacman
        while (true) {
            // Move to the next tile in the specified direction
            currentTilePosition.add(direction.asVector());
    
            // Get the next tile in the maze
            Tile currentTile = maze.getTile(currentTilePosition);
    
            // Check if 6the tile is passable (not a wall)
            if (!currentTile.getState().isPassable()) {
                break; // Stop if a wall is encountered
            }
    
            // Increase the distance if the tile is passable
            distance++;
        }
    
        return distance;
    }

    private int penalizeRemainingLives() {

        // Check remaining lives
        int currentLives = pacman.getMaze().getLevelManager().getExtraLives();

        if (currentLives > lastStepLives) {
            return 5000;
        }
        
        if (currentLives < lastStepLives) {
            return -10000;
        }
    
        lastStepLives = currentLives;
        return 0;
    }



    private float calculateDistanceToNearestFruit() {
        // Get Pacman's current position
        Vector2d pacmanPosition = pacman.getPosition();
        float minDistance = Float.MAX_VALUE; // Start with a large value
        
        // Iterate over all fruits in the maze
        for (Entity entity : pacman.getMaze().getEntities()) {
        // Check if the entity is a FruitEntity
        if (entity instanceof FruitEntity) {
            FruitEntity fruit = (FruitEntity) entity;
            // Get the fruit's position
            Vector2d fruitPosition = fruit.getPosition();

            // Calculate the distance to this fruit
            float distance = (float) pacmanPosition.distance(fruitPosition);

            // Update the minimum distance if this fruit is closer
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        }
    
        // If no fruits are found, return a large distance value (or handle as needed)
        return minDistance == Float.MAX_VALUE ? -1 : minDistance;
    }
/***************************************************************FUNC END************************************************************************/


    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }

        // SPECIAL TRAINING CONDITIONS

        int newScore = pacman.getMaze().getLevelManager().getScore();

        if (newScore > lastScore) {
            lastScore = newScore;
            numUpdatesSinceLastScore = 0;
        }

        if (numUpdatesSinceLastScore++ > 60 * 10) {
            pacman.kill();
            return Direction.UP;
        }
        
        

        // END OF SPECIAL TRAINING CONDITIONS

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();

        // Input nodes 1, 2, 3, and 4 show if the pacman can move in the forward, left, right, and behind directions
        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);

        float[] nearestPowerPelletEnc = encodeNearestPowerPelletPosition();
        float[] pelletFeatures = encodeTopNPelletPositions(4); // Encoding for 4 nearest pellets

        // float[] pelletFeatures = new float[12];


        float[] nearestFruitEnc = encodeNearestFruitPosition();
        // float[] nearestGhostEnc = encodeNearestGhostMovement();
        float[] ghostEnc = encodeAllGhosts();

        // // Randomly select a valid direction occasionally to encourage exploration
        // double explorationChance = 0.05; // 5% chance to explore randomly
        // if (Math.random() < explorationChance) {
        //     List<Direction> validDirections = new ArrayList<>();
        //     if (canMoveForward) validDirections.add(forward);
        //     if (canMoveLeft) validDirections.add(left);
        //     if (canMoveRight) validDirections.add(right);
        //     if (canMoveBehind) validDirections.add(behind);

        //     if (!validDirections.isEmpty()) {
        //         // Return a random valid direction
        //         return validDirections.get((int) (Math.random() * validDirections.size()));
        //     }
        // }

        float distToNearestFruit = calculateDistanceToNearestFruit();

        float distToNearestWallForward  = calculateDistanceToWall(forward);
        float distToNearestWallLeft = calculateDistanceToWall(left);
        float distToNearestWallRight = calculateDistanceToWall(right);
        float distToNearestWallBehind = calculateDistanceToWall(behind);

        float pilletsRemaining = pacman.getMaze().getPelletsRemaining();

        float pelletsForward = calculatePelletsInDirection(forward);
        float pelletsLeft = calculatePelletsInDirection(left);
        float pelletsRight = calculatePelletsInDirection(right);
        float pelletsBehind = calculatePelletsInDirection(behind);


        // Normalize distances to a reasonable range, e.g., 0-1 based on maximum possible distance
        float maxDistance = 100f; // Assume a maximum distance
        // distToNearestFruit = Math.min(distToNearestFruit / maxDistance, 1f);
        distToNearestWallForward = Math.min(distToNearestWallForward / maxDistance, 1f);
        distToNearestWallLeft = Math.min(distToNearestWallLeft / maxDistance, 1f);
        distToNearestWallRight = Math.min(distToNearestWallRight / maxDistance, 1f);
        distToNearestWallBehind = Math.min(distToNearestWallBehind / maxDistance, 1f);


        float[] input = new float[]{
            canMoveForward ? 1f : 0f,
            canMoveLeft ? 1f : 0f,
            canMoveRight ? 1f : 0f,
            canMoveBehind ? 1f : 0f,
            distToNearestWallForward,
            distToNearestWallLeft,
            distToNearestWallRight,
            distToNearestWallBehind,
            pelletsForward,
            pelletsLeft,
            pelletsRight,
            pelletsBehind,
            pelletFeatures[0],
            pelletFeatures[1],
            pelletFeatures[2],
            pelletFeatures[3],
            pelletFeatures[4],
            pelletFeatures[5],
            pelletFeatures[6],
            pelletFeatures[7],
            pelletFeatures[8],
            pelletFeatures[9],
            pelletFeatures[10],
            pelletFeatures[11],
            ghostEnc[0],
            ghostEnc[1],
            ghostEnc[2],
            ghostEnc[3],
            ghostEnc[4],
            ghostEnc[5],
            ghostEnc[6],
            ghostEnc[7],
            ghostEnc[8],
            ghostEnc[9],
            ghostEnc[10],
            ghostEnc[11],
            ghostEnc[12],
            ghostEnc[13],
            ghostEnc[14],
            ghostEnc[15],
            ghostEnc[16],
            nearestFruitEnc[0],
            nearestFruitEnc[1],
            nearestFruitEnc[2],
            nearestPowerPelletEnc[0],
            nearestPowerPelletEnc[1],
            nearestPowerPelletEnc[2]
        };
        
        // Print the input array
        // System.out.println("Input: " + Arrays.toString(input));
        
        // Now pass it to the calculate function
        float[] outputs = client.getCalculator().calculate(input).join();

        // Sort the directions by the network output in descending order
        List<Integer> sortedIndices = new ArrayList<>(List.of(0, 1, 2, 3));
        sortedIndices.sort((i1, i2) -> Float.compare(outputs[i2], outputs[i1]));

        // Go through the sorted indices and pick the first valid direction
        Direction newDirection = null;
        for (int i : sortedIndices) {
            switch (i) {
                case 0 -> {
                    if (canMoveForward) {
                        newDirection = forward;
                    }
                }
                case 1 -> {
                    if (canMoveLeft) {
                        newDirection = left;
                    }
                }
                case 2 -> {
                    if (canMoveRight) {
                        newDirection = right;
                    }
                }
                case 3 -> {
                    if (canMoveBehind) {
                        newDirection = behind;
                    }
                }
            }
            // Break the loop if a valid direction is found
            if (newDirection != null) {
                break;
            }
        }

        // If no valid direction was found, default to the original forward direction
        if (newDirection == null) {
            newDirection = forward;
        }

        // Keep track of visited tiles
        int pacmanX = pacman.getTilePosition().x;
        int pacmanY = pacman.getTilePosition().y;
        trackVisitState[pacmanX][pacmanY] += 1;

        // Penalize if visit a tile more than allowed threshold
        int maxAllowedVisit = 10;  // Set a threshold for allowed visits
        int visits = trackVisitState[pacmanX][pacmanY];
        
        // Apply a proportional penalty based on the number of visits exceeding the threshold
        if (visits > maxAllowedVisit) {
            int excessVisits = visits - maxAllowedVisit;
            scoreModifier -= excessVisits * 60;  // 10 is a scaling factor; adjust as needed
        }

        // Penalize Pellet interactions
        scoreModifier += penalizePelletInteraction();

        // Penalize ghost interactions
        scoreModifier += penalizeRemainingLives();
        
        scoreModifier += penalizeGhostInteraction();

        // Penalize eating fruits
        scoreModifier += penalizeFruitInteraction();

        client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier);
        return newDirection;

        // int index = 0;
        // float max = outputs[0];
        // for (int i = 1; i < outputs.length; i++) {
        //     if (outputs[i] > max) {
        //         max = outputs[i];
        //         index = i;
        //     }
        // }

        // Direction newDirection = switch (index) {
        //     case 0 -> pacman.getDirection();
        //     case 1 -> pacman.getDirection().left();
        //     case 2 -> pacman.getDirection().right();
        //     case 3 -> pacman.getDirection().behind();
        //     default -> throw new IllegalStateException("Unexpected value: " + index);
        // };


        // if (last_position == pacman.getPosition()) {
        //     scoreModifier -= 10;
        // }

        // last_position = pacman.getPosition();

        // lastPelletCount = pacman.getMaze().getPelletsRemaining();

        // client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier);
        // return newDirection;
    }

    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here
        /*
        if (pacman != null) {
            DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
            DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        }
         */
    }
}
