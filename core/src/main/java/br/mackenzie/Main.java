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

// --------------------------------------------------------
// NAVE — troca de sprite conforme direção
// --------------------------------------------------------
class PlayerShip extends GameObject {
    private float speed = 5f;
    private Texture texIdle, texLeft, texRight;

    // Invencibilidade temporária após levar dano
    private float invincibleTimer = 0f;
    private static final float INVINCIBLE_DURATION = 1.5f;

    public PlayerShip(Texture idle, Texture left, Texture right, float x, float y) {
        super(idle, x, y, 1f, 1f);
        this.texIdle  = idle;
        this.texLeft  = left;
        this.texRight = right;
    }

    @Override
    public void update(float delta) {
        // Conta down do tempo de invencibilidade
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

        // Pisca a nave durante invencibilidade
        sprite.setAlpha(isInvincible() ? (invincibleTimer % 0.2f < 0.1f ? 0.3f : 1f) : 1f);
    }

    public boolean isInvincible() {
        return invincibleTimer > 0;
    }

    public void hit() {
        invincibleTimer = INVINCIBLE_DURATION;
    }
}

// --------------------------------------------------------
// ASTEROIDES
// --------------------------------------------------------
class Asteroid extends GameObject {
    protected Vector2 velocity;

    public Asteroid(Texture texture, float x, float y, float size, float vx, float vy) {
        super(texture, x, y, size, size);
        this.velocity = new Vector2(vx, vy);
    }

    @Override
    public void update(float delta) {
        sprite.translate(velocity.x * delta, velocity.y * delta);
    }
}

class SmallAsteroid extends Asteroid {
    public SmallAsteroid(Texture texture, float x, float y) {
        super(texture, x, y, 0.7f, 0, -3f);
    }
}

class LargeAsteroid extends Asteroid {
    public LargeAsteroid(Texture texture, float x, float y) {
        super(texture, x, y, 1.3f, 0, -1.3f);
    }
}

// --------------------------------------------------------
// POWER-UP
// --------------------------------------------------------
class PowerUp extends GameObject {
    public PowerUp(Texture texture, float x, float y) {
        super(texture, x, y, 0.8f, 0.8f);
    }

    @Override
    public void update(float delta) {
        sprite.translateY(-2.5f * delta);
    }
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

    // Texturas do jogo
    Texture shipTex, shipLeftTex, shipRightTex;
    Texture bgFarTex, bgNearTex; // duas camadas para paralaxe
    Texture astPequenoTex, astGrandeTex, powerTex, shotTex;
    Texture startGameTex, gameOverTex;
    Texture heartTex; // ícone de vida (crie heart.png na pasta assets)

    PlayerShip player;
    Array<Asteroid>   asteroids;
    Array<GameObject> activePowerUps;
    Array<Sprite>     shotSprites;

    float spawnTimer;
    float shotCooldown;
    int   score = 0;
    int   lives = 3; // ← 3 vidas

    // Paralaxe — cada camada tem seu próprio Y
    float bgFarY  = 0f; // camada distante (mais lenta)
    float bgNearY = 0f; // camada próxima (mais rápida)

    Sound dropSound;
    Music music;

    BitmapFont  font;
    GlyphLayout glyphLayout;

    int screenW, screenH;

    // --------------------------------------------------------
    // CICLO DE VIDA
    // --------------------------------------------------------

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        viewport    = new FitViewport(8, 5);

        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, screenW, screenH);

        // Nave
        shipTex      = new Texture("bucket.png");
        shipLeftTex  = new Texture("bucket_left.png");
        shipRightTex = new Texture("bucket_right.png");

        // Fundo — dois arquivos diferentes para o efeito de profundidade.
        // Se tiver só um arquivo de fundo, use o mesmo para ambos:
        // bgFarTex = bgNearTex = new Texture("background.png");
        bgFarTex  = new Texture("background_far.png");   // camada de fundo (estrelas distantes)
        bgNearTex = new Texture("background_near.png");  // camada frontal (nebulosas, etc.)

        astPequenoTex = new Texture("inimigo_pequeno.png");
        astGrandeTex  = new Texture("inimigo_grande.png");
        powerTex      = new Texture("drop.png");
        shotTex       = new Texture("shot.png");

        startGameTex = new Texture("start_game.png");
        gameOverTex  = new Texture("game_over.png");
        heartTex     = new Texture("heart.png"); // ícone de coração para as vidas

        font        = new BitmapFont();
        font.setColor(Color.WHITE);
        glyphLayout = new GlyphLayout();

        dropSound = Gdx.audio.newSound(Gdx.files.internal("drop.mp3"));
        music     = Gdx.audio.newMusic(Gdx.files.internal("music.mp3"));
        music.setLooping(true);
        music.play();

        initGame();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        screenW = width;
        screenH = height;
        hudCamera.setToOrtho(false, width, height);
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        spriteBatch.dispose();
        shipTex.dispose();
        shipLeftTex.dispose();
        shipRightTex.dispose();
        bgFarTex.dispose();
        bgNearTex.dispose();
        astPequenoTex.dispose();
        astGrandeTex.dispose();
        powerTex.dispose();
        shotTex.dispose();
        startGameTex.dispose();
        gameOverTex.dispose();
        heartTex.dispose();
        dropSound.dispose();
        music.dispose();
        font.dispose();
    }

    // --------------------------------------------------------
    // INICIALIZAÇÃO / RESET
    // --------------------------------------------------------

    private void initGame() {
        player         = new PlayerShip(shipTex, shipLeftTex, shipRightTex, 3.5f, 0.2f);
        asteroids      = new Array<>();
        activePowerUps = new Array<>();
        shotSprites    = new Array<>();
        spawnTimer     = 0;
        shotCooldown   = 0;
        score          = 0;
        lives          = 3;   // reseta as vidas
        bgFarY         = 0f;
        bgNearY        = 0f;
    }

    // --------------------------------------------------------
    // LOOP PRINCIPAL
    // --------------------------------------------------------

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Lógica por estado
        switch (gameState) {
            case WAITING:
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                    gameState = GameState.PLAYING;
                }
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

        // ---- Renderização ----
        ScreenUtils.clear(Color.BLACK);

        // === PASSO 1: Mundo (câmera 8×5) ===
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        // Paralaxe: camada distante primeiro, depois a próxima
        spriteBatch.draw(bgFarTex,  0, bgFarY,       8, 5);
        spriteBatch.draw(bgFarTex,  0, bgFarY  + 5f, 8, 5);
        spriteBatch.draw(bgNearTex, 0, bgNearY,       8, 5);
        spriteBatch.draw(bgNearTex, 0, bgNearY + 5f, 8, 5);

        if (gameState == GameState.PLAYING || gameState == GameState.GAME_OVER) {
            for (Sprite s     : shotSprites)    s.draw(spriteBatch);
            for (Asteroid a   : asteroids)      a.draw(spriteBatch);
            for (GameObject p : activePowerUps) p.draw(spriteBatch);
            player.draw(spriteBatch);
        }

        spriteBatch.end();

        // === PASSO 2: HUD (câmera em pixels) ===
        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin();
        drawHUD();
        spriteBatch.end();
    }

    // --------------------------------------------------------
    // PARALAXE
    // --------------------------------------------------------

    private void updateParallax(float delta) {
        // Camada distante: mais lenta (0.8f)
        bgFarY -= 0.8f * delta;
        if (bgFarY <= -5f) bgFarY += 5f;

        // Camada próxima: mais rápida (2.0f) — cria ilusão de profundidade
        bgNearY -= 2.0f * delta;
        if (bgNearY <= -5f) bgNearY += 5f;
    }

    // --------------------------------------------------------
    // HUD
    // --------------------------------------------------------

    private void drawHUD() {
        float imgW = screenW * 0.60f;
        float imgH = imgW * 0.375f;
        float imgX = (screenW - imgW) / 2f;
        float imgY = (screenH - imgH) / 2f + 40;

        switch (gameState) {

            case WAITING:
                spriteBatch.draw(startGameTex, imgX, imgY, imgW, imgH);
                font.setColor(Color.WHITE);
                glyphLayout.setText(font, "Pressione ESPACO para comecar");
                font.draw(spriteBatch, "Pressione ESPACO para comecar",
                    (screenW - glyphLayout.width) / 2f, imgY - 10);
                break;

            case PLAYING:
                // Score
                font.setColor(Color.WHITE);
                font.draw(spriteBatch, "Score: " + score, 15, screenH - 10);

                // Vidas — desenha corações no canto superior direito
                drawLives();
                break;

            case GAME_OVER:
                // Score final
                font.setColor(Color.WHITE);
                font.draw(spriteBatch, "Score: " + score, 15, screenH - 10);
                drawLives();

                spriteBatch.draw(gameOverTex, imgX, imgY, imgW, imgH);
                glyphLayout.setText(font, "Pressione ESPACO para reiniciar");
                font.draw(spriteBatch, "Pressione ESPACO para reiniciar",
                    (screenW - glyphLayout.width) / 2f, imgY - 10);
                break;
        }
    }

    /** Desenha os ícones de vida no canto superior direito. */
    private void drawLives() {
        float heartSize = 28f;  // tamanho do coração em pixels
        float margin    = 10f;  // espaço entre corações
        float startX    = screenW - (lives * (heartSize + margin));
        float y         = screenH - heartSize - 10;

        for (int i = 0; i < lives; i++) {
            spriteBatch.draw(heartTex,
                startX + i * (heartSize + margin), y,
                heartSize, heartSize);
        }
    }

    // --------------------------------------------------------
    // LÓGICA DE JOGO
    // --------------------------------------------------------

    private void handleShots(float delta) {
        shotCooldown -= delta;

        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && shotCooldown <= 0) {
            Sprite shot = new Sprite(shotTex);
            shot.setSize(0.2f, 0.5f);
            shot.setPosition(
                player.getBounds().x + (player.getBounds().width / 2f) - 0.1f,
                player.getBounds().y + 0.8f);
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

            if (MathUtils.randomBoolean(0.7f)) {
                asteroids.add(new SmallAsteroid(astPequenoTex, x, 5f));
            } else {
                asteroids.add(new LargeAsteroid(astGrandeTex, x, 5f));
            }

            if (MathUtils.randomBoolean(0.2f)) {
                activePowerUps.add(new PowerUp(powerTex, MathUtils.random(0, 7f), 5f));
            }

            spawnTimer = 0;
        }
    }

    private void updateGameObjects(float delta) {

        // Asteroides — destruídos por tiros
        for (int i = asteroids.size - 1; i >= 0; i--) {
            Asteroid a = asteroids.get(i);
            a.update(delta);

            Rectangle astBounds = a.getBounds();
            boolean hit = false;

            for (int j = shotSprites.size - 1; j >= 0; j--) {
                Sprite s = shotSprites.get(j);
                Rectangle shotBounds = new Rectangle(s.getX(), s.getY(), s.getWidth(), s.getHeight());

                if (astBounds.overlaps(shotBounds)) {
                    asteroids.removeIndex(i);
                    shotSprites.removeIndex(j);
                    score++;
                    hit = true;
                    break;
                }
            }

            if (!hit && a.getBounds().y < -1f) {
                asteroids.removeIndex(i);
            }
        }

        // Power-ups — cada colisão tira 1 vida
        for (int i = activePowerUps.size - 1; i >= 0; i--) {
            GameObject obj = activePowerUps.get(i);
            obj.update(delta);

            if (obj.getBounds().overlaps(player.getBounds())) {

                // Só tira vida se a nave não estiver invencível
                if (!player.isInvincible()) {
                    dropSound.play();
                    lives--;          // perde 1 vida
                    player.hit();     // ativa invencibilidade temporária

                    activePowerUps.removeIndex(i);

                    if (lives <= 0) {
                        gameState = GameState.GAME_OVER; // acabaram as vidas
                        return;
                    }
                }

            } else if (obj.getBounds().y < -1f) {
                activePowerUps.removeIndex(i);
            }
        }
    }
}
