package engine.core;

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.text.MessageFormat;
import java.util.*;

import engine.effects.*;
import engine.graphics.MarioBackground;
import engine.helper.*;
import engine.sprites.*;
import reinforcement.Objective;
import reinforcement.Point;

public class MarioWorld {
    public GameStatus gameStatus;
    public int pauseTimer = 0;
    public int fireballsOnScreen = 0;
    public int currentTimer = -1;
    public int initTimer = 0;
    public float cameraX;
    public float cameraY;
    public Mario mario;
    public MarioLevel level;
    public boolean visuals;
    public int currentTick;
    // Status
    public int coins, lives, collectCoin;

    public ArrayList<MarioEvent> lastFrameEvents;

    private MarioEvent[] killEvents;
    private ArrayList<MarioSprite> sprites;
    private ArrayList<Shell> shellsToCheck;
    private ArrayList<Fireball> fireballsToCheck;
    private ArrayList<MarioSprite> addedSprites;
    private ArrayList<MarioSprite> removedSprites;

    private ArrayList<MarioEffect> effects;

    private MarioBackground[] backgrounds = new MarioBackground[2];

    private Set<MarioSprite> listKill = new HashSet<>();
    private ArrayList<MarioSprite> aliveEnemy = new ArrayList<>();
    private ArrayList<Point> unbumpBlocks = new ArrayList<>();
    private ArrayList<Point> unCollectCoin = new ArrayList<>();

    private Objective coinsObjective = new Objective();
    private Objective blocksObjective = new Objective();
    private Objective enemiesObjective = new Objective();
    private float stallCounter = 0;
    public final static int MAX_STALL = 100;
    private boolean stallLose = false;
    public float getStallCounter() {
        return stallCounter;
    }

    private float stuckRightCounter = 0;
    private float stuckLeftCounter = 0;
    private final static int MAX_STUCK = 5;
    private boolean truncated = false;

    public boolean isTruncated() {
        return truncated;
    }

    private ArrayList<Float> xProgressHistory = new ArrayList<>();
    private final int NO_PROGRESS_WINDOW = 30;
    private final float NO_PROGRESS_THRESHOLD_PIXELS = 16 * 2;


    public List<MarioSprite> getNearestEnemies(){
        List<MarioSprite> out = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if(sprite.type == SpriteType.GOOMBA ||
                    sprite.type == SpriteType.GOOMBA_WINGED ||
                    sprite.type == SpriteType.GREEN_KOOPA ||
                    sprite.type == SpriteType.GREEN_KOOPA_WINGED ||
                    sprite.type == SpriteType.RED_KOOPA ||
                    sprite.type == SpriteType.RED_KOOPA_WINGED ||
                    sprite.type == SpriteType.SPIKY ||
                    sprite.type == SpriteType.SPIKY_WINGED
            ){
                out.add(sprite);
            }
        }
        return out;
    }

    public List<MarioSprite> getNearestBulletBill(){
        List<MarioSprite> out = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if(sprite.type == SpriteType.BULLET_BILL
            ){
                out.add(sprite);
            }
        }
        return out;
    }

    public List<MarioSprite> getNearestShell(){
        List<MarioSprite> out = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if(sprite.type == SpriteType.SHELL
            ){
                out.add(sprite);
            }
        }
        return out;
    }

    public List<MarioSprite> getNearestFlower(){
        List<MarioSprite> out = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if(sprite.type == SpriteType.ENEMY_FLOWER
            ){
                out.add(sprite);
            }
        }
        return out;
    }


    public List<MarioSprite> getNearestItems(){
        List<MarioSprite> out = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if(sprite.type == SpriteType.MUSHROOM ||
                    sprite.type == SpriteType.LIFE_MUSHROOM ||
                    sprite.type == SpriteType.FIRE_FLOWER
            ){
                out.add(sprite);
            }
        }

        return out;
    }

    public MarioWorld(MarioEvent[] killEvents) {
        this.pauseTimer = 0;
        this.gameStatus = GameStatus.RUNNING;
        this.sprites = new ArrayList<>();
        this.shellsToCheck = new ArrayList<>();
        this.fireballsToCheck = new ArrayList<>();
        this.addedSprites = new ArrayList<>();
        this.removedSprites = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.lastFrameEvents = new ArrayList<>();
        this.killEvents = killEvents;
    }

    public void initializeVisuals(GraphicsConfiguration graphicsConfig) {
        int[][] tempBackground = new int[][] {
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 },
                new int[] { 42 }
        };
        backgrounds[0] = new MarioBackground(graphicsConfig, MarioGame.width, tempBackground);
        tempBackground = new int[][] {
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 31, 32, 33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 34, 35, 36, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 31, 32, 33, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 34, 35, 36, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
        };
        backgrounds[1] = new MarioBackground(graphicsConfig, MarioGame.width, tempBackground);
    }

    public void initializeLevel(String level, int timer) {
        this.currentTimer = timer;
        this.initTimer = timer;
        this.level = new MarioLevel(level, this.visuals);

        this.mario = new Mario(this.visuals, this.level.marioTileX * 16, this.level.marioTileY * 16);
        this.mario.alive = true;
        this.mario.world = this;
        this.xProgressHistory = new ArrayList<>();
        this.sprites.add(this.mario);
        aliveEnemy.addAll(this.level.getEnemies());
        unbumpBlocks.addAll(this.level.getBumpableBlocks());
        unCollectCoin.addAll(this.level.getCoins());
        coinsObjective.addAll(this.level.getCoins());
        blocksObjective.addAll(this.level.getBumpableBlocks());
        enemiesObjective.addAllEnemy(this.level.getEnemies());
    }

    public ArrayList<MarioSprite> getEnemies() {
        ArrayList<MarioSprite> enemies = new ArrayList<>();
        for (MarioSprite sprite : sprites) {
            if (this.isEnemy(sprite)) {
                enemies.add(sprite);
            }
        }
        return enemies;
    }

    public MarioWorld clone() {
        MarioWorld world = new MarioWorld(this.killEvents);
        world.visuals = false;
        world.cameraX = this.cameraX;
        world.cameraY = this.cameraY;
        world.fireballsOnScreen = this.fireballsOnScreen;
        world.gameStatus = this.gameStatus;
        world.pauseTimer = this.pauseTimer;
        world.currentTimer = this.currentTimer;
        world.initTimer = this.initTimer;
        world.currentTick = this.currentTick;
        world.stallCounter = this.stallCounter;
        world.stallLose = this.stallLose;
        world.level = this.level.clone();
        world.xProgressHistory = new ArrayList<>(this.xProgressHistory);
        world.stuckRightCounter = stuckRightCounter;
        world.stuckLeftCounter = stuckLeftCounter;
        world.truncated = truncated;
        // Clone sprites
        for (MarioSprite sprite : this.sprites) {
            MarioSprite cloneSprite = sprite.clone();
            cloneSprite.world = world;
            if (cloneSprite.type == SpriteType.MARIO) {
                world.mario = (Mario) cloneSprite;
            }
            world.sprites.add(cloneSprite);
        }
        if (world.mario == null) {
            world.mario = (Mario) this.mario.clone();
        }

        // Stats
        world.coins = this.coins;
        world.lives = this.lives;

        // Deep copy collections using for loops
        world.listKill = new HashSet<>();
        for (MarioSprite s : this.listKill) {
            world.listKill.add(s.clone());
        }

        world.aliveEnemy = new ArrayList<>();
        for (MarioSprite s : this.aliveEnemy) {
            world.aliveEnemy.add(s.clone());
        }

        world.unbumpBlocks = new ArrayList<>();
        for (Point b : this.unbumpBlocks) {
            world.unbumpBlocks.add(new Point(b.getX(), b.getY()));
        }

        world.unCollectCoin = new ArrayList<>();
        for (Point c : this.unCollectCoin) {
            world.unCollectCoin.add(new Point(c.getX(), c.getY()));
        }

        world.coinsObjective = this.coinsObjective.clone();
        world.blocksObjective = this.blocksObjective.clone();
        world.enemiesObjective = this.enemiesObjective.clone();
        return world;
    }

    public void addEvent(EventType eventType, int eventParam) {
        int marioState = 0;
        if (this.mario.isLarge) {
            marioState = 1;
        }
        if (this.mario.isFire) {
            marioState = 2;
        }
        this.lastFrameEvents.add(new MarioEvent(eventType, eventParam, mario.x, mario.y, marioState, this.currentTick));
    }

    public void addEvent(EventType eventType, int eventParam, String initialCode) {
        int marioState = 0;
        if (this.mario.isLarge) {
            marioState = 1;
        }
        if (this.mario.isFire) {
            marioState = 2;
        }
        this.lastFrameEvents.add(
                new MarioEvent(eventType, eventParam, mario.x, mario.y, marioState, this.currentTick, initialCode));
    }

    public void addEffect(MarioEffect effect) {
        this.effects.add(effect);
    }

    public void addSprite(MarioSprite sprite) {
        this.addedSprites.add(sprite);
        sprite.alive = true;
        sprite.world = this;
        sprite.added();
        sprite.update();
    }

    public void kill(MarioSprite sprite, EventType killEvent) {
        this.listKill.add(sprite);
        this.addEvent(killEvent, sprite.type.getValue(), sprite.initialCode);
        aliveEnemy.removeIf(a -> Objects.equals(sprite.initialCode,
                MessageFormat.format("{0}_{1}_{2}", a.x, a.y, a.type.getValue())));
        this.stallCounter = 0;
        this.xProgressHistory.clear();
        if(sprite.type == SpriteType.BULLET_BILL ||
        sprite.type == SpriteType.ENEMY_FLOWER){
            return;
        }
        enemiesObjective.mark(sprite.initialCode);
    }

    public void removeSprite(MarioSprite sprite) {
        this.removedSprites.add(sprite);
        sprite.alive = false;
        sprite.removed();
        sprite.world = null;
    }

    public void checkShellCollide(Shell shell) {
        shellsToCheck.add(shell);
    }

    public void checkFireballCollide(Fireball fireball) {
        fireballsToCheck.add(fireball);
    }

    public void win() {
        this.addEvent(EventType.WIN, 0);
        this.gameStatus = GameStatus.WIN;
    }

    public void lose() {
        this.addEvent(EventType.LOSE, 0);
        this.gameStatus = GameStatus.LOSE;
        this.mario.alive = false;
    }

    public void timeout() {
        this.addEvent(EventType.TIME_OUT, 0);
        this.gameStatus = GameStatus.TIME_OUT;
        this.mario.alive = false;
    }

    public int[][] getSceneObservation(float centerX, float centerY, int detail) {
        int[][] ret = new int[MarioGame.tileWidth][MarioGame.tileHeight];
        int centerXInMap = (int) centerX / 16;
        int centerYInMap = (int) centerY / 16;

        for (int y = centerYInMap - MarioGame.tileHeight / 2,
             obsY = 0; y < centerYInMap + MarioGame.tileHeight / 2; y++, obsY++) {
            for (int x = centerXInMap - MarioGame.tileWidth / 2,
                 obsX = 0; x < centerXInMap + MarioGame.tileWidth / 2; x++, obsX++) {
                int currentX = x;
                if (currentX < 0) {
                    currentX = 0;
                }
                if (currentX > level.tileWidth - 1) {
                    currentX = level.tileWidth - 1;
                }
                int currentY = y;
                if (currentY < 0) {
                    currentY = 0;
                }
                if (currentY > level.tileHeight - 1) {
                    currentY = level.tileHeight - 1;
                }
                ret[obsX][obsY] = MarioForwardModel.getBlockValueGeneralization(this.level.getBlock(currentX, currentY),
                        detail);
            }
        }
        return ret;
    }

    public int[][] getEnemiesObservation(float centerX, float centerY, int detail) {
        int[][] ret = new int[MarioGame.tileWidth][MarioGame.tileHeight];
        int centerXInMap = (int) centerX / 16;
        int centerYInMap = (int) centerY / 16;

        for (int w = 0; w < ret.length; w++)
            for (int h = 0; h < ret[0].length; h++)
                ret[w][h] = 0;

        for (MarioSprite sprite : sprites) {
            if (sprite.type == SpriteType.MARIO)
                continue;
            if (sprite.getMapX() >= 0 &&
                    sprite.getMapX() > centerXInMap - MarioGame.tileWidth / 2 &&
                    sprite.getMapX() < centerXInMap + MarioGame.tileWidth / 2 &&
                    sprite.getMapY() >= 0 &&
                    sprite.getMapY() > centerYInMap - MarioGame.tileHeight / 2 &&
                    sprite.getMapY() < centerYInMap + MarioGame.tileHeight / 2) {
                int obsX = sprite.getMapX() - centerXInMap + MarioGame.tileWidth / 2;
                int obsY = sprite.getMapY() - centerYInMap + MarioGame.tileHeight / 2;
                ret[obsX][obsY] = MarioForwardModel.getSpriteTypeGeneralization(sprite.type, detail);
            }
        }
        return ret;
    }

    public int[][] getMergedObservation(float centerX, float centerY, int sceneDetail, int enemiesDetail) {
        int[][] ret = new int[MarioGame.tileWidth][MarioGame.tileHeight];
        int centerXInMap = (int) centerX / 16;
        int centerYInMap = (int) centerY / 16;

        for (int y = centerYInMap - MarioGame.tileHeight / 2,
             obsY = 0; y < centerYInMap + MarioGame.tileHeight / 2; y++, obsY++) {
            for (int x = centerXInMap - MarioGame.tileWidth / 2,
                 obsX = 0; x < centerXInMap + MarioGame.tileWidth / 2; x++, obsX++) {
                int currentX = x;
                if (currentX < 0) {
                    currentX = 0;
                }
                if (currentX > level.tileWidth - 1) {
                    currentX = level.tileWidth - 1;
                }
                int currentY = y;
                if (currentY < 0) {
                    currentY = 0;
                }
                if (currentY > level.tileHeight - 1) {
                    currentY = level.tileHeight - 1;
                }
                if (y > this.level.height + 32) {
                    int a = 5;
                }
                ret[obsX][obsY] = MarioForwardModel.getBlockValueGeneralization(this.level.getBlock(x, y), sceneDetail);

            }
        }

        for (MarioSprite sprite : sprites) {
            if (sprite.type == SpriteType.MARIO)
                continue;
            if (sprite.getMapX() >= 0 &&
                    sprite.getMapX() > centerXInMap - MarioGame.tileWidth / 2 &&
                    sprite.getMapX() < centerXInMap + MarioGame.tileWidth / 2 &&
                    sprite.getMapY() >= 0 &&
                    sprite.getMapY() > centerYInMap - MarioGame.tileHeight / 2 &&
                    sprite.getMapY() < centerYInMap + MarioGame.tileHeight / 2) {
                int obsX = sprite.getMapX() - centerXInMap + MarioGame.tileWidth / 2;
                int obsY = sprite.getMapY() - centerYInMap + MarioGame.tileHeight / 2;
                int tmp = MarioForwardModel.getSpriteTypeGeneralization(sprite.type, enemiesDetail);
                if (tmp != SpriteType.NONE.getValue()) {
                    ret[obsX][obsY] = tmp;
                }
            }
        }

        return ret;
    }

    private boolean isEnemy(MarioSprite sprite) {
        return sprite instanceof Enemy || sprite instanceof FlowerEnemy || sprite instanceof BulletBill;
    }

    public void update(boolean[] actions) {
        this.lastFrameEvents.clear();
        var beforeX = mario.x;
        var beforePosition = new int[] { (int) ((this.mario.x - this.cameraX) / 16), (int) (this.mario.y / 16) };
        if (this.gameStatus != GameStatus.RUNNING) {
            return;
        }
        if (this.pauseTimer > 0) {
            this.pauseTimer -= 1;
            if (this.visuals) {
                this.mario.updateGraphics();
            }
            return;
        }

        if (this.currentTimer > 0) {
            this.currentTimer -= 30;
            if (this.currentTimer <= 0) {
                this.currentTimer = 0;
                this.timeout();
                return;
            }
        }
        this.currentTick += 1;
        this.cameraX = this.mario.x - MarioGame.width / 2;
        if (this.cameraX + MarioGame.width > this.level.width) {
            this.cameraX = this.level.width - MarioGame.width;
        }
        if (this.cameraX < 0) {
            this.cameraX = 0;
        }
        this.cameraY = this.mario.y - MarioGame.height / 2;
        if (this.cameraY + MarioGame.height > this.level.height) {
            this.cameraY = this.level.height - MarioGame.height;
        }
        if (this.cameraY < 0) {
            this.cameraY = 0;
        }

        this.fireballsOnScreen = 0;
        for (MarioSprite sprite : sprites) {
            if (sprite.x < cameraX - 64 || sprite.x > cameraX + MarioGame.width + 64
                    || sprite.y > this.level.height + 32) {
                if (sprite.type == SpriteType.MARIO) {
                    this.addEvent(EventType.FALL_PIT, sprite.type.getValue());
                    this.lose();
                }
                this.removeSprite(sprite);
                if (this.isEnemy(sprite) && sprite.y > MarioGame.height + 32) {
                    this.kill(sprite, EventType.FALL_KILL);
                }
                continue;
            }
            if (sprite.type == SpriteType.FIREBALL) {
                this.fireballsOnScreen += 1;
            }
        }
        this.level.update((int) cameraX, (int) cameraY);

        for (int x = (int) cameraX / 16 - 1; x <= (int) (cameraX + MarioGame.width) / 16 + 1; x++) {
            for (int y = (int) cameraY / 16 - 1; y <= (int) (cameraY + MarioGame.height) / 16 + 1; y++) {
                int dir = 0;
                if (x * 16 + 8 > mario.x + 16)
                    dir = -1;
                if (x * 16 + 8 < mario.x - 16)
                    dir = 1;

                SpriteType type = level.getSpriteType(x, y);
                if (type != SpriteType.NONE) {
                    String spriteCode = level.getSpriteCode(x, y);
                    boolean found = false;
                    for (MarioSprite sprite : sprites) {
                        if (sprite.initialCode.equals(spriteCode)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        if (this.level.getLastSpawnTick(x, y) != this.currentTick - 1) {
                            MarioSprite sprite = type.spawnSprite(this.visuals, x, y, dir);
                            sprite.initialCode = spriteCode;
                            if (this.listKill.stream().noneMatch(l -> Objects.equals(l.initialCode, spriteCode))) {
                                this.addSprite(sprite);
                            }
                        }
                    }
                    this.level.setLastSpawnTick(x, y, this.currentTick);
                }

                if (dir != 0) {
                    ArrayList<TileFeature> features = TileFeature.getTileType(this.level.getBlock(x, y));
                    if (features.contains(TileFeature.SPAWNER)) {
                        if (this.currentTick % 100 == 0) {
                            addSprite(new BulletBill(this.visuals, x * 16 + 8 + dir * 8, y * 16 + 15, dir));
                        }
                    }
                }
            }
        }

        this.mario.actions = actions;
        for (MarioSprite sprite : sprites) {
            if (!sprite.alive) {
                continue;
            }
            sprite.update();
        }
        for (MarioSprite sprite : sprites) {
            if (!sprite.alive) {
                continue;
            }
            sprite.collideCheck();
        }

        for (Shell shell : shellsToCheck) {
            for (MarioSprite sprite : sprites) {
                if (sprite != shell && shell.alive && sprite.alive) {
                    if (sprite.shellCollideCheck(shell)) {
                        this.removeSprite(sprite);
                    }
                }
            }
        }
        shellsToCheck.clear();

        for (Fireball fireball : fireballsToCheck) {
            for (MarioSprite sprite : sprites) {
                if (sprite != fireball && fireball.alive && sprite.alive) {
                    if (sprite.fireballCollideCheck(fireball)) {
                        if (this.visuals) {
                            this.addEffect(new FireballEffect(fireball.x, fireball.y));
                        }
                        this.removeSprite(fireball);
                    }
                }
            }
        }
        fireballsToCheck.clear();

        var afterPosition = new int[] { (int) ((this.mario.x - this.cameraX) / 16), (int) (this.mario.y / 16) };
        if (afterPosition[1] == beforePosition[1] && afterPosition[0] == beforePosition[0]) {
            stallCounter++;
        } else {
            stallCounter = 0;
        }

        this.xProgressHistory.add(this.mario.x);

        // Keep history to the size of the check window
        while (this.xProgressHistory.size() > NO_PROGRESS_WINDOW) {
            this.xProgressHistory.remove(0);
        }

        if (this.xProgressHistory.size() == NO_PROGRESS_WINDOW) {
            // Find the min and max X position in the last 10 steps
            float minX = this.mario.x;
            float maxX = this.mario.x;
            for (Float xPos : this.xProgressHistory) {
                if (xPos < minX) minX = xPos;
                if (xPos > maxX) maxX = xPos;
            }

            if (maxX - minX <= NO_PROGRESS_THRESHOLD_PIXELS) {
                truncated = true;
                this.xProgressHistory.clear();
            }
        }

        if(stallCounter > MAX_STALL){
            stallCounter = 0;
        }

        float deltaX = mario.x - beforeX;
        boolean tryingToMoveRight = actions[MarioActions.RIGHT.getValue()];
        boolean tryingToMoveLeft = actions[MarioActions.LEFT.getValue()];
        boolean onGround = mario.onGround;
        boolean isStuck = Math.abs(deltaX) < 0.1f;

        if (tryingToMoveRight && onGround && isStuck) {
            stuckRightCounter++;
        }

        if (tryingToMoveLeft && onGround && isStuck) {
            stuckLeftCounter++;
        }

        if(stuckLeftCounter > MAX_STUCK || stuckRightCounter > MAX_STUCK){
            stuckLeftCounter = 0;
            stuckRightCounter = 0;
            truncated = true;
        }

        sprites.addAll(0, addedSprites);
        sprites.removeAll(removedSprites);
        addedSprites.clear();
        removedSprites.clear();

        // punishing forward model
        if (this.killEvents != null) {
            for (MarioEvent k : this.killEvents) {
                if (this.lastFrameEvents.contains(k)) {
                    this.lose();
                }
            }
        }
    }

    public void bump(int xTile, int yTile, boolean canBreakBricks) {
        int block = this.level.getBlock(xTile, yTile);
        ArrayList<TileFeature> features = TileFeature.getTileType(block);
        if (features.contains(TileFeature.BUMPABLE)) {
            unbumpBlocks.removeIf(b -> b.getX() == xTile && b.getY() == yTile);
            this.stallCounter = 0;
            this.xProgressHistory.clear();
            blocksObjective.mark(new Point(xTile, yTile));
            bumpInto(xTile, yTile - 1);
            this.addEvent(EventType.BUMP, MarioForwardModel.OBS_QUESTION_BLOCK);
            level.setBlock(xTile, yTile, 14);
            level.setShiftIndex(xTile, yTile, 4);

            if (features.contains(TileFeature.SPECIAL)) {
                if (!this.mario.isLarge) {
                    addSprite(new Mushroom(this.visuals, xTile * 16 + 9, yTile * 16 + 8));
                } else {
                    addSprite(new FireFlower(this.visuals, xTile * 16 + 9, yTile * 16 + 8));
                }
            } else if (features.contains(TileFeature.LIFE)) {
                addSprite(new LifeMushroom(this.visuals, xTile * 16 + 9, yTile * 16 + 8));
            } else {
                mario.collectCoin();
                if (this.visuals) {
                    this.addEffect(new CoinEffect(xTile * 16 + 8, (yTile) * 16));
                }
            }
        }

        if (MarioForwardModel.isCeilingBlockingTile(block + 16)) {
            if (block + 16 != MarioForwardModel.OBS_BRICK || !canBreakBricks) {
                this.addEvent(EventType.BONK, block + 16);
            }
        }

        if (features.contains(TileFeature.BREAKABLE)) {
            bumpInto(xTile, yTile - 1);
            if (canBreakBricks) {
                this.addEvent(EventType.BREAK, MarioForwardModel.OBS_BRICK);
                level.setBlock(xTile, yTile, 0);
                if (this.visuals) {
                    for (int xx = 0; xx < 2; xx++) {
                        for (int yy = 0; yy < 2; yy++) {
                            this.addEffect(new BrickEffect(xTile * 16 + xx * 8 + 4, yTile * 16 + yy * 8 + 4,
                                    (xx * 2 - 1) * 4, (yy * 2 - 1) * 4 - 8));
                        }
                    }
                }
            } else {
                if (MarioForwardModel.isCeilingBlockingTile(block + 16)) {
                    this.addEvent(EventType.BONK, block + 16);
                }
                level.setShiftIndex(xTile, yTile, 4);
            }
        }
    }

    public void bumpInto(int xTile, int yTile) {
        int block = level.getBlock(xTile, yTile);
        if (TileFeature.getTileType(block).contains(TileFeature.PICKABLE)) {
            this.addEvent(EventType.COLLECT, block);
            this.collectCoin(block, xTile, yTile);
            this.mario.collectCoin();
            level.setBlock(xTile, yTile, 0);
            if (this.visuals) {
                this.addEffect(new CoinEffect(xTile * 16 + 8, yTile * 16 + 8));
            }
        }

        for (MarioSprite sprite : sprites) {
            sprite.bumpCheck(xTile, yTile);
        }
    }

    public void render(Graphics og) {
        for (int i = 0; i < backgrounds.length; i++) {
            this.backgrounds[i].render(og, (int) cameraX, (int) cameraY);
        }
        for (MarioSprite sprite : sprites) {
            if (sprite.type == SpriteType.MUSHROOM || sprite.type == SpriteType.LIFE_MUSHROOM ||
                    sprite.type == SpriteType.FIRE_FLOWER || sprite.type == SpriteType.ENEMY_FLOWER) {
                sprite.render(og);
            }
        }
        this.level.render(og, (int) cameraX, (int) cameraY);
        for (MarioSprite sprite : sprites) {
            if (sprite.type != SpriteType.MUSHROOM && sprite.type != SpriteType.LIFE_MUSHROOM &&
                    sprite.type != SpriteType.FIRE_FLOWER && sprite.type != SpriteType.ENEMY_FLOWER) {
                sprite.render(og);
            }
        }
        for (int i = 0; i < this.effects.size(); i++) {
            if (this.effects.get(i).life <= 0) {
                this.effects.remove(i);
                i--;
                continue;
            }
            this.effects.get(i).render(og, cameraX, cameraY);
        }
    }

    public Boolean isSubGoalEnemyMet() {
        return aliveEnemy.isEmpty();
    }

    public Boolean isSubGoalCoinMet() {
        return unCollectCoin.isEmpty();
    }

    public Boolean isSubGoalBlockMet() {
        return unbumpBlocks.isEmpty();
    }

    public ArrayList<MarioSprite> getAliveEnemies() {
        return aliveEnemy;
    }

    public ArrayList<Point> getUnbumpBlocks() {
        return unbumpBlocks;
    }

    public ArrayList<Point> getUnCollectCoin() {
        return unCollectCoin;
    }

    public int getKillCount() {
        return this.level.getEnemies().size() - this.getAliveEnemies().size();
    }

    public int getHitBlockCount() {
        return this.level.getBumpableBlocks().size() - this.getUnbumpBlocks().size();
    }

    public int getCollectedCoinCount() {
        return this.level.getCoins().size() - this.getUnCollectCoin().size();
    }

    public void collectCoin(int block, int xTile, int yTile) {
        this.addEvent(EventType.COLLECT, block);
        this.collectCoin++;
        this.getUnCollectCoin().removeIf(c -> c.getX() == xTile && c.getY() == yTile);
        this.coinsObjective.mark(new Point(xTile, yTile));
        this.stallCounter = 0;
        this.xProgressHistory.clear();
    }

    public int[] getCoinsObjective() {
        return coinsObjective.getObjectives();
    }

    public int[] getBlocksObjective() {
        return blocksObjective.getObjectives();
    }

    public int[] getEnemiesObjective() {
        return enemiesObjective.getObjectives();
    }
}