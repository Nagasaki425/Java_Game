package br.mackenzie;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

// ========================================================
// CLASSES BASE E ENTIDADES
// ========================================================

abstract class GameObject {
    protected Sprite sprite;
    protected Rectangle bounds;

    public GameObject(Texture texture, float x, float y, float width, float height) {
        this.sprite = new Sprite(texture);
        this.sprite.setSize(width, height);
        this.sprite.setPosition(x, y);
        this.bounds = new Rectangle(x, y, width, height);
    }

    public abstract void update(float delta);

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }

    public Rectangle getBounds() {
        bounds.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        return bounds;
    }
}

class PlayerShip extends GameObject {
    private float speed = 5f;
    private Texture texIdle, texLeft, texRight;
    private float invincibleTimer = 0f;
    private static final float INVINCIBLE_DURATION = 1.5f;

    public PlayerShip(Texture idle, Texture left, Texture right, float x, float y) {
        super(idle, x, y, 1f, 1f);
        this.texIdle = idle;
        this.texLeft = left;
        this.texRight = right;
    }

    @Override
    public void update(float delta) {
        if (invincibleTimer > 0) invincibleTimer -= delta;

        boolean moving = false;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            sprite.translateX(speed * delta);
            sprite.setTexture(texRight);
            moving = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            sprite.translateX(-speed * delta);
            sprite.setTexture(texLeft);
            moving = true;
        }

        if (!moving) sprite.setTexture(texIdle);
        sprite.setX(MathUtils.clamp(sprite.getX(), 0, 8f - sprite.getWidth()));
        sprite.setAlpha(isInvincible() ? (invincibleTimer % 0.2f < 0.1f ? 0.3f : 1f) : 1f);
    }

    public boolean isInvincible() { return invincibleTimer > 0; }
    public void hit() { invincibleTimer = INVINCIBLE_DURATION; }
}

class Asteroid extends GameObject {
    protected Vector2 velocity;
    public Asteroid(Texture texture, float x, float y, float size, float vx, float vy) {
        super(texture, x, y, size, size);
        this.velocity = new Vector2(vx, vy);
    }
    @Override
    public void update(float delta) { sprite.translate(velocity.x * delta, velocity.y * delta); }
}

class SmallAsteroid extends Asteroid {
    public SmallAsteroid(Texture texture, float x, float y) { super(texture, x, y, 0.7f, 0, -3f); }
}

class LargeAsteroid extends Asteroid {
    public LargeAsteroid(Texture texture, float x, float y) { super(texture, x, y, 1.3f, 0, -2.5f); }
}

class PowerUp extends GameObject {
    public PowerUp(Texture texture, float x, float y) { super(texture, x, y, 0.8f, 0.8f); }
    @Override
    public void update(float delta) { sprite.translateY(-2.5f * delta); }
}

// ========================================================
// CLASSE PRINCIPAL
// ========================================================

public class Main implements ApplicationListener {

    private enum GameState { WAITING, PLAYING, GAME_OVER }
    private GameState gameState = GameState.WAITING;

    SpriteBatch spriteBatch;
    FitViewport viewport;
    OrthographicCamera hudCamera;

    Texture shipTex, shipLeftTex, shipRightTex;
    Texture bgFarTex, bgNearTex;
    Texture astPequenoTex, astGrandeTex, powerTex, shotTex;
    Texture startGameTex, gameOverTex, heartTex;

    PlayerShip player;
    Array<Asteroid> asteroids;
    Array<GameObject> activePowerUps;
    Array<Sprite> shotSprites;

    float spawnTimer, shotCooldown;
    int score = 0, lives = 3;
    float bgFarY = 0f, bgNearY = 0f;

    Sound dropSound;
    Music music;
    BitmapFont font;
    int screenW, screenH;

    @Override
    public void create() {

        spriteBatch = new SpriteBatch();
        viewport = new FitViewport(8, 5);

        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, screenW, screenH);

        shipTex = new Texture("bucket.png");
        shipLeftTex = new Texture("bucket_left.png");
        shipRightTex = new Texture("bucket_right.png");
        bgFarTex = new Texture("background_far.png");
        bgNearTex = new Texture("background_near.png");
        astPequenoTex = new Texture("inimigo_pequeno.png");
        astGrandeTex = new Texture("inimigo_grande.png");
        powerTex = new Texture("drop.png");
        shotTex = new Texture("shot.png");
        startGameTex = new Texture("start_game.png");
        gameOverTex = new Texture("game_over.png");
        heartTex = new Texture("heart.png");

        font = new BitmapFont();
        dropSound = Gdx.audio.newSound(Gdx.files.internal("drop.mp3"));
        music = Gdx.audio.newMusic(Gdx.files.internal("music.mp3"));
        music.setLooping(true);
        music.play();

        initGame();
    }

    private void initGame() {
        player = new PlayerShip(shipTex, shipLeftTex, shipRightTex, 3.5f, 0.2f);
        asteroids = new Array<>();
        activePowerUps = new Array<>();
        shotSprites = new Array<>();
        spawnTimer = 0;
        shotCooldown = 0;
        score = 0;
        lives = 3;
        bgFarY = 0f;
        bgNearY = 0f;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // LÓGICA
        switch (gameState) {
            case WAITING:
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) gameState = GameState.PLAYING;
                break;
            case PLAYING:
                updateParallax(delta);
                player.update(delta);
                handleShots(delta);
                spawnObjects(delta);
                updateGameObjects(delta);
                break;
            case GAME_OVER:
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                    initGame();
                    gameState = GameState.PLAYING;
                }
                break;
        }

        // RENDERIZAÇÃO
        ScreenUtils.clear(Color.BLACK);

        // PASSO 1: Mundo (Câmera 8x5)
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        // Desenha o background_near atrás, preenchendo a tela e se movimentando
        spriteBatch.draw(bgNearTex, 0, bgNearY, 8, 5);
        spriteBatch.draw(bgNearTex, 0, bgNearY + 5f, 8, 5);

        // Desenha o background_far por cima, esticado na tela
        spriteBatch.draw(bgFarTex, 0, 4.4f, 8, 0.9f);

        if (gameState != GameState.WAITING) {
            for (Sprite s : shotSprites) s.draw(spriteBatch);
            for (Asteroid a : asteroids) a.draw(spriteBatch);
            for (GameObject p : activePowerUps) p.draw(spriteBatch);
            player.draw(spriteBatch);
        }
        spriteBatch.end();

        // PASSO 2: HUD (Pixels)
        hudCamera.update();
        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin();
        drawHUD();
        spriteBatch.end();
    }

    private void updateParallax(float delta) {
        bgNearY -= 2.0f * delta;
        if (bgNearY <= -5f) bgNearY += 5f;
    }

    private void drawHUD() {
        float imgW = screenW * 0.60f;
        float imgH = imgW * 0.375f;
        float imgX = (screenW - imgW) / 2f;
        float imgY = (screenH - imgH) / 2f;

        switch (gameState) {
            case WAITING:
                spriteBatch.draw(startGameTex, imgX, imgY, imgW, imgH);
                break;
            case PLAYING:
                drawScore();
                drawLives();
                break;
            case GAME_OVER:
                drawScore();
                drawLives();
                spriteBatch.draw(gameOverTex, imgX, imgY, imgW, imgH);
                break;
        }
    }

    private void drawScore() {
        font.setColor(Color.RED);
        font.getData().setScale(3.0f); // TAMANHO DO SCORE AUMENTADO
        font.draw(spriteBatch, "Score: " + score, 20, screenH - 20);
        font.getData().setScale(1.0f); // Reset
    }

    private void drawLives() {
        float heartSize = 60f;
        float margin = 10f;
        float startX = screenW - (lives * (heartSize + margin)) - 10;
        float y = screenH - heartSize - 10;

        for (int i = 0; i < lives; i++) {
            spriteBatch.draw(heartTex, startX + i * (heartSize + margin), y, heartSize, heartSize);
        }
    }

    private void handleShots(float delta) {
        shotCooldown -= delta;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && shotCooldown <= 0) {
            Sprite shot = new Sprite(shotTex);
            shot.setSize(0.2f, 0.5f);
            shot.setPosition(player.getBounds().x + (player.getBounds().width / 2f) - 0.1f, player.getBounds().y + 0.8f);
            shotSprites.add(shot);
            shotCooldown = 0.3f;
        }
        for (int i = shotSprites.size - 1; i >= 0; i--) {
            Sprite s = shotSprites.get(i);
            s.translateY(6f * delta);
            if (s.getY() > 5f) shotSprites.removeIndex(i);
        }
    }

    private void spawnObjects(float delta) {
        spawnTimer += delta;
        if (spawnTimer > 1.5f) {
            float x = MathUtils.random(0, 7f);
            if (MathUtils.randomBoolean(0.7f)) asteroids.add(new SmallAsteroid(astPequenoTex, x, 5f));
            else asteroids.add(new LargeAsteroid(astGrandeTex, x, 5f));
            if (MathUtils.randomBoolean(0.2f)) activePowerUps.add(new PowerUp(powerTex, MathUtils.random(0, 7f), 5f));
            spawnTimer = 0;
        }
    }

    private void updateGameObjects(float delta) {
        for (int i = asteroids.size - 1; i >= 0; i--) {
            Asteroid a = asteroids.get(i);
            a.update(delta);
            Rectangle astBounds = a.getBounds();
            for (int j = shotSprites.size - 1; j >= 0; j--) {
                Sprite s = shotSprites.get(j);
                if (astBounds.overlaps(new Rectangle(s.getX(), s.getY(), s.getWidth(), s.getHeight()))) {
                    asteroids.removeIndex(i);
                    shotSprites.removeIndex(j);
                    score++;
                    return;
                }
            }
            if (a.getBounds().y < -1f) asteroids.removeIndex(i);
        }

        for (int i = activePowerUps.size - 1; i >= 0; i--) {
            GameObject obj = activePowerUps.get(i);
            obj.update(delta);
            if (obj.getBounds().overlaps(player.getBounds()) && !player.isInvincible()) {
                dropSound.play();
                lives--;
                player.hit();
                activePowerUps.removeIndex(i);
                if (lives <= 0) gameState = GameState.GAME_OVER;
            } else if (obj.getBounds().y < -1f) activePowerUps.removeIndex(i);
        }
    }

    @Override public void resize(int width, int height) {
        viewport.update(width, height, true);
        screenW = width; screenH = height;
        hudCamera.setToOrtho(false, width, height);
    }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void dispose() {
        spriteBatch.dispose(); shipTex.dispose(); shipLeftTex.dispose(); shipRightTex.dispose();
        bgFarTex.dispose(); bgNearTex.dispose(); astPequenoTex.dispose(); astGrandeTex.dispose();
        powerTex.dispose(); shotTex.dispose(); startGameTex.dispose(); gameOverTex.dispose();
        heartTex.dispose(); dropSound.dispose(); music.dispose(); font.dispose();
    }
}
