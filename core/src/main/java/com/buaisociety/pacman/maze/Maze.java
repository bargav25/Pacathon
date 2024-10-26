package com.buaisociety.pacman.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.GameManager;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.EntityType;
import com.buaisociety.pacman.entity.FruitEntity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.entity.GhostState;
import com.buaisociety.pacman.entity.PacmanEntity;
import com.buaisociety.pacman.event.EntityPreSpawnEvent;
import com.buaisociety.pacman.event.EntityRemoveEvent;
import com.buaisociety.pacman.event.EntitySpawnEvent;
import com.buaisociety.pacman.event.GlobalBehaviorEvent;
import com.buaisociety.pacman.sprite.CutoutSpriteSheet;
import com.buaisociety.pacman.sprite.GrayscaleSpriteSheet;
import com.buaisociety.pacman.sprite.Particle;
import com.buaisociety.pacman.sprite.TextSpriteSheet;
import com.buaisociety.pacman.util.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a maze in the game of Pacman. Mazes are responsible for keeping
 * track of the state of the tiles, the entities in the maze, and the number of
 * pellets remaining in the maze (the win condition for Pacman).
 *
 * <p>Mazes are also responsible for having spawn locations and other entity
 * data ready, so that the {@link GameManager} can easily manage the initial
 * game state.
 */
public class Maze implements Disposable {

    /**
     * All tiles are 8x8 pixels.
     */
    public static final int TILE_SIZE = 8;

    /**
     * Times for the ghosts to be in scatter mode, then chase mode, until finally the ghost is infinitely in chase mode.
     */
    public static final int[][] LEVEL_STATES = new int[][]{
        new int[]{420, 1200, 420, 1200, 300, 1200, 300, -1},
        new int[]{420, 1200, 120, 1200, 300, 61980, 1, -1},
        new int[]{300, 1200, 300, 1200, 300, 62220, 1, -1}
    };
    public static final int[] LEVEL_FRIGHT_TIMES = new int[]{360, 300, 240, 180, 120, 300, 120, 120, 60, 300, 120, 60, 60, 180, 60, 60, 0, 60, 0};


    protected @NotNull GameManager gameManager;
    protected @NotNull Sprite levelSprite;
    protected @NotNull GrayscaleSpriteSheet pelletSprite;
    protected @NotNull GrayscaleSpriteSheet powerPelletSprite;
    protected @NotNull CutoutSpriteSheet fruitSprite;
    protected @NotNull GrayscaleSpriteSheet bonusPointsSprite;
    protected @NotNull Tile[][] tiles;
    protected int totalPellets;
    protected int pelletsRemaining;
    protected @NotNull List<Entity> entities;
    protected int ticks;
    protected int freezeTicks;
    private int gameStartTicks;

    private @NotNull Vector2i tileDimensions;
    private @NotNull Vector2i pixelDimensions;
    private @NotNull Vector2i fruitSpawnPixel;
    private @NotNull List<Particle> particles;

    private int localChaseCounter;  // used to switch between chase and scatter
    private int frightenedTimer;  // 0 if not frightened, otherwise the number of ticks left
    private int localNumGhostsEaten;  // number of ghosts eaten from the current power pellet, tracked for bonuses
    private boolean isGhostChase;  // true if the ghosts are in chase mode, false if in scatter mode
    private int ghostChaseIndex;  // index of the current chase/scatter mode

    public Maze(
        @NotNull GameManager gameManager,
        @NotNull Sprite levelSprite,
        @NotNull TileState[][] tiles,
        @NotNull Vector2i fruitSpawnPixel
    ) {
        this.gameManager = gameManager;
        this.levelSprite = levelSprite;
        this.tiles = new Tile[tiles.length][tiles[0].length];
        for (int y = 0; y < tiles.length; y++) {
            for (int x = 0; x < tiles[y].length; x++) {
                this.tiles[y][x] = new Tile(this, new Vector2i(x, y), tiles[y][x]);
            }
        }
        this.entities = new ArrayList<>();
        this.tileDimensions = new Vector2i(tiles[0].length, tiles.length);
        this.pixelDimensions = new Vector2i(tileDimensions).mul(TILE_SIZE);
        this.fruitSpawnPixel = fruitSpawnPixel;
        this.particles = new ArrayList<>();
        initTiles();

        pelletSprite = new GrayscaleSpriteSheet(new Texture(Gdx.files.internal("sprites/pellet.png")), 8);
        pelletSprite.setColors(Color.CLEAR, new Color(0xffb897ff));
        powerPelletSprite = new GrayscaleSpriteSheet(new Texture(Gdx.files.internal("sprites/power-pellet.png")), 8);
        powerPelletSprite.setColors(Color.CLEAR, new Color(0xffb897ff));
        fruitSprite = new CutoutSpriteSheet(new Texture(Gdx.files.internal("sprites/fruit-sprite.png")), 16);
        bonusPointsSprite = new GrayscaleSpriteSheet(new Texture(Gdx.files.internal("sprites/bonus-points-sprite.png")), new Vector2i(8 * 4, 8 * 2));

        // Start with chase so the first update flips it to scatter
        isGhostChase = true;

        // Game is frozen for the first 4 seconds
        gameStartTicks = 60 * 4;
    }

    /**
     * Responsible for initializing the tiles array, and setting the initial variables of this Maze instance.
     */
    public void initTiles() {
        if (tiles == null) {
            throw new IllegalStateException("Tiles array must be initialized before calling initTiles()");
        }

        pelletsRemaining = 0;
        totalPellets = 0;
        for (Tile[] row : tiles) {
            for (Tile tile : row) {
                TileState state = tile.getState();
                if (state == TileState.PELLET || state == TileState.POWER_PELLET) {
                    pelletsRemaining++;
                    totalPellets++;
                }
            }
        }
    }

    public void reset() {
        frightenedTimer = 0;
        for (Entity entity : entities) {
            entity.reset();
        }
    }

    public @NotNull GameManager getLevelManager() {
        return gameManager;
    }

    public int getPelletsRemaining() {
        return pelletsRemaining;
    }

    public @NotNull PacmanEntity getPacman() {
        for (Entity entity : entities) {
            if (entity instanceof PacmanEntity) {
                return (PacmanEntity) entity;
            }
        }
        throw new IllegalStateException("Pacman not found in maze");
    }

    public void addParticle(@NotNull Particle particle) {
        particles.add(particle);
    }

    /**
     * Returns the list of entities in the maze.
     *
     * @return The list of entities in the maze.
     */
    public @NotNull List<Entity> getEntities() {
        return entities;
    }

    /**
     * Returns the dimensions of the maze in tiles.
     *
     * @return The dimensions of the maze in tiles.
     */
    public @NotNull Vector2ic getDimensions() {
        return tileDimensions;
    }

    /**
     * Returns the dimensions of the maze in pixels.
     *
     * @return The dimensions of the maze in pixels.
     */
    public @NotNull Vector2ic getPixelDimensions() {
        return pixelDimensions;
    }

    /**
     * Converts a position in pixels to a tile coordinate. This is useful for converting the position of an entity to a tile coordinate.
     *
     * @param position The position in pixels.
     * @return The tile coordinate.
     */
    public final @NotNull Vector2i toTileCoords(@NotNull Vector2dc position) {
        return new Vector2i((int) position.x() / TILE_SIZE, (int) position.y() / TILE_SIZE);
    }

    /**
     * Returns the tile at the given position.
     *
     * @param position The position of the tile.
     * @return The tile at the given position.
     */
    public final @NotNull Tile getTile(@NotNull Vector2i position) {
        return getTile(position.x, position.y);
    }

    /**
     * Returns the tile at the given position.
     *
     * @param x The x-coordinate of the tile.
     * @param y The y-coordinate of the tile.
     * @return The tile at the given position.
     */
    public final @NotNull Tile getTile(int x, int y) {
        y = (y + tileDimensions.y) % tileDimensions.y;
        x = (x + tileDimensions.x) % tileDimensions.x;
        return tiles[y][x];
    }

    /**
     * Wraps the given position to the dimensions of the maze.
     *
     * <p>This is used when an entity moves off the screen using a tunnel to
     * reappear on the other side of the screen.
     *
     * @param position The position to wrap.
     * @return The wrapped position.
     */
    public final @NotNull Vector2d getWrappedPosition(@NotNull Vector2d position) {
        double y = (position.y + pixelDimensions.y) % pixelDimensions.y;
        double x = (position.x + pixelDimensions.x) % pixelDimensions.x;
        return new Vector2d(x, y);
    }

    /**
     * Returns the number of ticks left in the frightened state. If the value is 0, then the game is not in the frightened state.
     *
     * @return The number of ticks left in the frightened state.
     */
    public int getFrightenedTimer() {
        return frightenedTimer;
    }

    /**
     * Ghosts change between chase and scatter mode on a timer (set by this maze class).
     *
     * @return true if the ghosts are in chase mode, false if in scatter mode
     */
    public boolean isGhostChase() {
        return isGhostChase;
    }

    /**
     * Returns <code>null</code> if the game is still running, or a {@link TerminalReason} if the game has ended.
     *
     * @return <code>null</code> if the game is still running, or a {@link TerminalReason}
     */
    public @Nullable TerminalReason getTerminalReason() {
        if (pelletsRemaining == 0)
            return TerminalReason.WIN;

        PacmanEntity pacman = getPacman();
        if (!pacman.isAlive()) {
            return TerminalReason.LOSE;
        }

        return null;
    }

    public void eatGhost(@NotNull PacmanEntity pacman, @NotNull GhostEntity ghost) {
        // Ghosts can only be eaten when they are frightened
        if (ghost.getState() == GhostState.CHASE || ghost.getState() == GhostState.SCATTER) {

            pacman.kill();
            return;
        }

        // Cannot eat a ghost that is already eaten
        if (ghost.getState() == GhostState.EATEN)
            return;

        ghost.setState(GhostState.EATEN);

        int addScore = 200 * (int) Math.pow(2, localNumGhostsEaten);
        gameManager.incrementScore(addScore);
        freezeTicks += 40;

        // We only have sprites for 200, 400, 800, and 1600 points
        int clampedScoreIndex = switch (localNumGhostsEaten) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 6;
            default -> 8;
        };

        Color[] colors = new Color[]{Color.CLEAR, Color.CYAN};
        Vector2i spriteTile = new Vector2i(clampedScoreIndex, 0);
        Particle particle = new Particle(bonusPointsSprite, spriteTile, colors);
        particle.setPosition(ghost.getPosition());
        particle.setVelocity(new Vector2d(0, 3));
        particle.setVelocityFor(5);
        particle.setLiveFor(40);
        particles.add(particle);

        // As more ghosts are eaten, the bonus points are worth more
        localNumGhostsEaten++;
    }

    public @NotNull TileState eatPellet(@NotNull PacmanEntity pacman, @NotNull Tile tile) {
        TileState state = tile.getState();

        // Normal pellets are eaten by Pacman, and the score is increased. Normal
        // pellets also release ghosts when a certain number of them are eaten.
        if (state == TileState.PELLET) {
            gameManager.incrementScore(10);
            pelletsRemaining--;
            tile.setState(TileState.SPACE);

            // Ghosts are released when a certain number of pellets are eaten. The
            // first 2 ghosts (blinky and pinky) are always released. Then each
            // subsequent ghost has a "cost" (that resets when a ghost is released).
            // Any extra ghosts (past the first 4) can be released at the same cost
            // as the final ghost.
            int level = gameManager.getLevel();
            // Allow a handicap to delay the speed changes per-level
            // makes the game easier
            level = Math.max(1, level - gameManager.getConfig().handicap);
            int[] bounds = switch (level) {
                case 1 -> new int[]{0, 0, 30, 60};
                case 2 -> new int[]{0, 0, 0, 50};
                default -> new int[]{0, 0, 0, 0};
            };

            int ghostIndex = 0;
            for (Entity temp : entities) {
                if (!(temp instanceof GhostEntity ghost))
                    continue;

                int bound = bounds[Math.min(ghostIndex, bounds.length - 1)];
                ghostIndex++;
                if (ghost.isReleased())
                    continue;

                // Either release the ghosts, or increment the counter
                if (ghost.getLocalDotCounter() >= bound) {
                    ghost.setReleased(true);
                } else {
                    ghost.incrementLocalDotCounter();
                }
            }
        } else if (state == TileState.POWER_PELLET) {
            localNumGhostsEaten = 0;
            gameManager.incrementScore(50);
            pelletsRemaining--;
            tile.setState(TileState.SPACE);

            // Allow a handicap to delay the speed changes per-level
            // makes the game easier
            int level  = gameManager.getLevel();
            level = Math.max(1, level - gameManager.getConfig().handicap);

            int levelIndex = level - 1;
            if (levelIndex >= LEVEL_FRIGHT_TIMES.length)
                levelIndex = LEVEL_FRIGHT_TIMES.length - 1;

            frightenedTimer = LEVEL_FRIGHT_TIMES[levelIndex];
            for (Entity temp : entities) {
                if (temp instanceof GhostEntity ghost) {
                    ghost.setState(GhostState.FRIGHTENED);
                }
            }
        } else {
            // Make sure we actually ate a pellet
            return state;
        }

        // Once 70 pellets are eaten, the first fruit is released
        int pelletsEaten = totalPellets - pelletsRemaining;
        if (pelletsEaten == 70 || pelletsEaten == 170) {
            spawnFruit();
        }

        return state;
    }

    public void spawnFruit() {
        // Remove the first fruit, if there
        entities.removeIf(entity -> entity instanceof FruitEntity);
        FruitEntity.Config config = new FruitEntity.Config();
        config.spawnPixel = new Vector2d(fruitSpawnPixel);

        // Fire the event to allow the fruit to be customized
        EntityPreSpawnEvent event = new EntityPreSpawnEvent(this, EntityType.FRUIT, config);
        gameManager.getEvents().fireEvent(event);
        if (event.isCancelled())
            return;

        FruitEntity fruit = new FruitEntity(this, (FruitEntity.Config) event.getConfig());
        EntitySpawnEvent spawnEvent = new EntitySpawnEvent(fruit);
        gameManager.getEvents().fireEvent(spawnEvent);
        if (spawnEvent.isCancelled())
            return;

        entities.add(fruit);
    }

    public void update() {
        ticks++;
        if (freezeTicks > 0) {
            freezeTicks--;
            return;
        }
        if (gameStartTicks > 0) {
            gameStartTicks--;
            return;
        }

        if (getTerminalReason() != null)
            return;

        if (frightenedTimer > 0) {
            frightenedTimer--;
        }

        // Try to flip-flop between chase and scatter. We also allow for negative values,
        // which prevent flip-flops (Used in the base game for permanent chase).
        if (localChaseCounter-- == 0) {
            isGhostChase = !isGhostChase;

            int levelIndex;
            if (gameManager.getLevel() >= 5)
                levelIndex = 2;
            else if (gameManager.getLevel() >= 2)
                levelIndex = 1;
            else
                levelIndex = 0;

            localChaseCounter = LEVEL_STATES[levelIndex][ghostChaseIndex++];

            // Fire an event to allow the chase/scatter times to be customized
            GlobalBehaviorEvent event = new GlobalBehaviorEvent(this, isGhostChase, localChaseCounter);
            gameManager.getEvents().fireEvent(event);
            isGhostChase = event.isChaseBehavior();
            localChaseCounter = event.getTimeLeft();
        }

        // Must be indexed to allow modification of entities list
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            entity.update();
        }

        // Remove dead ones
        Iterator<Entity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if (entity.isRemove()) {
                EntityRemoveEvent event = new EntityRemoveEvent(entity);
                gameManager.getEvents().fireEvent(event);
                if (event.isCancelled())
                    continue;

                iterator.remove();
            }
        }
    }

    public void render(@NotNull SpriteBatch batch) {
        batch.draw(levelSprite, 0, 0);

        // Power pellets should flicker on and off
        boolean flicker = ticks % 20 < 10;

        for (int y = 0; y < tiles.length; y++) {
            for (int x = 0; x < tiles[y].length; x++) {
                Tile tile = tiles[y][x];
                if (tile.getState() == TileState.PELLET) {
                    pelletSprite.render(batch, x * TILE_SIZE, y * TILE_SIZE);
                } else if (tile.getState() == TileState.POWER_PELLET && flicker) {
                    powerPelletSprite.render(batch, x * TILE_SIZE, y * TILE_SIZE);
                }
            }
        }

        // Render particles (typically bonus points)
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.render(batch);
            if (!particle.isAlive()) {
                iterator.remove();
            }
        }

        if (gameStartTicks > 0) {
            TextSpriteSheet text = getLevelManager().getTextSprite();
            text.getSpriteSheet().setColors(Color.CLEAR, Color.YELLOW);
            text.render(batch, 88, 120, "READY!");
            text.getSpriteSheet().setColors(Color.CLEAR, Color.WHITE);
        }

        for (Entity entity : entities) {
            entity.render(batch);
        }
    }

    @Override
    public void dispose() {
        for (Entity entity : entities) {
            entity.dispose();
        }
        levelSprite.getTexture().dispose();
        pelletSprite.dispose();
        powerPelletSprite.dispose();
        bonusPointsSprite.dispose();
        fruitSprite.dispose();
    }
}
