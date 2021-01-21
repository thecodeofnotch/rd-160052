package com.mojang.rubydung;

import com.mojang.rubydung.character.Zombie;
import com.mojang.rubydung.level.Chunk;
import com.mojang.rubydung.level.Level;
import com.mojang.rubydung.level.LevelRenderer;
import com.mojang.rubydung.level.Tessellator;
import com.mojang.rubydung.level.tile.Tile;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import javax.swing.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluPerspective;
import static org.lwjgl.util.glu.GLU.gluPickMatrix;

public class RubyDung implements Runnable {

    private final Timer timer = new Timer(60);

    private Level level;
    private LevelRenderer levelRenderer;
    private Entity player;

    private final List<Zombie> zombies = new ArrayList<>();

    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);

    /**
     * Screen resolution
     */
    private final int width = 1024;
    private final int height = 768;

    /**
     * Tile picking
     */
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    /**
     * HUD rendering
     */
    private final Tessellator tessellator = new Tessellator();

    /**
     * Selected tile in hand
     */
    private int selectedTileId = 1;

    /**
     * Initialize the game.
     * Setup display, keyboard, mouse, rendering and camera
     *
     * @throws LWJGLException Game could not be initialized
     */
    public void init() throws LWJGLException {
        // Write fog color
        this.fogColor.put(new float[]{
                14 / 255.0F,
                11 / 255.0F,
                10 / 255.0F,
                255 / 255.0F
        }).flip();

        // Set screen size
        Display.setDisplayMode(new DisplayMode(this.width, this.height));

        // Setup I/O
        Display.create();
        Keyboard.create();
        Mouse.create();

        // Setup rendering
        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(0.5F, 0.8F, 1.0F, 0.0F);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        // Create level and player (Has to be in main thread)
        this.level = new Level(256, 256, 64);
        this.levelRenderer = new LevelRenderer(this.level);
        this.player = new Player(this.level);

        // Grab mouse cursor
        Mouse.setGrabbed(true);

        // Spawn some zombies
        for (int i = 0; i < 100; ++i) {
            this.zombies.add(new Zombie(this.level, 0.0F, 0.0F, 0.0F));
        }
    }

    /**
     * Destroy mouse, keyboard and display
     */
    public void destroy() {
        this.level.save();

        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
    }

    /**
     * Main game thread
     * Responsible for the game loop
     */
    @Override
    public void run() {
        try {
            // Initialize the game
            init();
        } catch (Exception e) {
            // Show error message dialog and stop the game
            JOptionPane.showMessageDialog(null, e, "Failed to start RubyDung", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // To keep track of framerate
        int frames = 0;
        long lastTime = System.currentTimeMillis();

        try {
            // Start the game loop
            while (!Keyboard.isKeyDown(1) && !Display.isCloseRequested()) {
                // Update the timer
                this.timer.advanceTime();

                // Call the tick to reach updates 20 per seconds
                for (int i = 0; i < this.timer.ticks; ++i) {
                    tick();
                }

                // Render the game
                render(this.timer.partialTicks);

                // Increase rendered frame
                frames++;

                // Loop if a second passed
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    // Print amount of frames
                    System.out.println(frames + " fps, " + Chunk.updates);

                    // Reset global rebuild stats
                    Chunk.updates = 0;

                    // Increase last time printed and reset frame counter
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Destroy I/O and save game
            destroy();
        }
    }

    /**
     * Game tick, called exactly 20 times per second
     */
    private void tick() {
        // Listen for keyboard inputs
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {

                // Save the level
                if (Keyboard.getEventKey() == 28) { // Enter
                    this.level.save();
                }

                // Tile selection
                if (Keyboard.getEventKey() == 2) { // 1
                    this.selectedTileId = Tile.rock.id;
                }
                if (Keyboard.getEventKey() == 3) { // 2
                    this.selectedTileId = Tile.dirt.id;
                }
                if (Keyboard.getEventKey() == 4) { // 3
                    this.selectedTileId = Tile.stoneBrick.id;
                }
                if (Keyboard.getEventKey() == 5) { // 4
                    this.selectedTileId = Tile.wood.id;
                }

                // Spawn zombie
                if (Keyboard.getEventKey() == 34) { // G
                    this.zombies.add(new Zombie(this.level, this.player.x, this.player.y, this.player.z));
                }
            }
        }

        // Render zombies
        for (Zombie zombie : this.zombies) {
            zombie.tick();
        }

        // Tick player
        this.player.tick();
    }

    /**
     * Move and rotate the camera to players location and rotation
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void moveCameraToPlayer(float partialTicks) {
        Entity player = this.player;

        // Eye height
        glTranslatef(0.0f, 0.0f, -0.3f);

        // Rotate camera
        glRotatef(player.xRotation, 1.0f, 0.0f, 0.0f);
        glRotatef(player.yRotation, 0.0f, 1.0f, 0.0f);

        // Smooth movement
        double x = this.player.prevX + (this.player.x - this.player.prevX) * partialTicks;
        double y = this.player.prevY + (this.player.y - this.player.prevY) * partialTicks;
        double z = this.player.prevZ + (this.player.z - this.player.prevZ) * partialTicks;

        // Move camera to players location
        glTranslated(-x, -y, -z);
    }


    /**
     * Setup the normal player camera
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void setupCamera(float partialTicks) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Set camera perspective
        gluPerspective(70, width / (float) height, 0.05F, 1000F);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * Setup the HUD camera
     */
    private void setupOrthoCamera() {
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();

        // Set camera perspective
        GL11.glOrtho(0.0, this.width, this.height, 0.0, 100.0F, 300.0F);

        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();

        // Move camera to Z level -200
        GL11.glTranslatef(0.0f, 0.0f, -200.0f);
    }

    /**
     * Setup tile picking camera
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     * @param x            Screen position x
     * @param y            Screen position y
     */
    private void setupPickCamera(float partialTicks, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Reset buffer
        this.viewportBuffer.clear();

        // Get viewport value
        glGetInteger(GL_VIEWPORT, this.viewportBuffer);

        // Flip
        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);

        // Set matrix and camera perspective
        gluPickMatrix(x, y, 5.0f, 5.0f, this.viewportBuffer);
        gluPerspective(70.0f, this.width / (float) this.height, 0.05F, 1000.0F);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * @param partialTicks Overflow ticks to interpolate
     */
    private void pick(float partialTicks) {
        // Reset select buffer
        this.selectBuffer.clear();

        glSelectBuffer(this.selectBuffer);
        glRenderMode(GL_SELECT);

        // Setup pick camera
        this.setupPickCamera(partialTicks, this.width / 2, this.height / 2);

        // Render all possible pick selection faces to the target
        this.levelRenderer.pick(this.player);

        // Flip buffer
        this.selectBuffer.flip();
        this.selectBuffer.limit(this.selectBuffer.capacity());

        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        // Get amount of hits
        int hits = glRenderMode(GL_RENDER);
        for (int hitIndex = 0; hitIndex < hits; hitIndex++) {

            // Get name count
            int nameCount = this.selectBuffer.get();
            long minZ = this.selectBuffer.get();
            this.selectBuffer.get();

            // Check if the hit is closer to the camera
            if (minZ < closest || hitIndex == 0) {
                closest = minZ;
                hitNameCount = nameCount;

                // Fill names
                for (int nameIndex = 0; nameIndex < nameCount; nameIndex++) {
                    names[nameIndex] = this.selectBuffer.get();
                }
            } else {
                // Skip names
                for (int nameIndex = 0; nameIndex < nameCount; ++nameIndex) {
                    this.selectBuffer.get();
                }
            }
        }

        // Update hit result
        if (hitNameCount > 0) {
            this.hitResult = new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            this.hitResult = null;
        }
    }


    /**
     * Rendering the game
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void render(float partialTicks) {
        // Get mouse motion
        float motionX = Mouse.getDX();
        float motionY = Mouse.getDY();

        // Rotate the camera using the mouse motion input
        this.player.turn(motionX, motionY);

        // Pick tile
        pick(partialTicks);

        // Listen for mouse inputs
        while (Mouse.next()) {
            // Right click
            if (Mouse.getEventButton() == 1 && Mouse.getEventButtonState() && this.hitResult != null) {
                // Destroy the tile
                this.level.setTile(this.hitResult.x, this.hitResult.y, this.hitResult.z, 0);
            }

            // Left click
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && this.hitResult != null) {
                // Get target tile position
                int x = this.hitResult.x;
                int y = this.hitResult.y;
                int z = this.hitResult.z;

                // Get position of the tile using face direction
                if (this.hitResult.face == 0) y--;
                if (this.hitResult.face == 1) y++;
                if (this.hitResult.face == 2) z--;
                if (this.hitResult.face == 3) z++;
                if (this.hitResult.face == 4) x--;
                if (this.hitResult.face == 5) x++;

                // Set the tile
                this.level.setTile(x, y, z, this.selectedTileId);
            }
        }

        // Clear color and depth buffer and reset the camera
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Setup normal player camera
        setupCamera(partialTicks);

        // Setup fog
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glFogf(GL_FOG_START, -10);
        glFogf(GL_FOG_END, 20);
        glFog(GL_FOG_COLOR, this.fogColor);
        glDisable(GL_FOG);

        // Render bright tiles
        this.levelRenderer.render(0);

        // Render zombies
        for (Zombie zombie : this.zombies) {
            zombie.render(partialTicks);
        }

        // Enable fog to render shadow
        glEnable(GL_FOG);

        // Render dark tiles in shadow
        this.levelRenderer.render(1);

        // Finish rendering
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);

        // Render the actual hit
        if (this.hitResult != null) {
            this.levelRenderer.renderHit(this.hitResult);
        }

        // Draw player HUD
        drawGui(partialTicks);

        // Update the display
        Display.update();
    }

    /**
     * Draw HUD
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void drawGui(float partialTicks) {
        // Clear depth
        glClear(GL_DEPTH_BUFFER_BIT);

        // Setup HUD camera
        setupOrthoCamera();

        // Start tile display
        glPushMatrix();

        // Transform tile position to the top right corner
        glTranslated(this.width - 48, 48.0F, 0.0F);
        glScalef(48.0F, 48.0F, 48.0F);
        glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
        glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        glTranslatef(1.5F, -0.5F, -0.5F);

        // Setup tile rendering
        int id = Textures.loadTexture("/terrain.png", 9728);
        glBindTexture(GL_TEXTURE_2D, id);
        glEnable(GL_TEXTURE_2D);

        // Render selected tile in hand
        this.tessellator.init();
        Tile.tiles[this.selectedTileId].render(this.tessellator, this.level, 0, -2, 0, 0);
        this.tessellator.flush();

        // Finish tile rendering
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();

        // Cross hair position
        int x = this.width / 2;
        int y = this.height / 2;

        // Cross hair color
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Render cross hair
        this.tessellator.init();
        this.tessellator.vertex((float) (x + 1), (float) (y - 8), 0.0f);
        this.tessellator.vertex((float) (x - 0), (float) (y - 8), 0.0f);
        this.tessellator.vertex((float) (x - 0), (float) (y + 9), 0.0f);
        this.tessellator.vertex((float) (x + 1), (float) (y + 9), 0.0f);
        this.tessellator.vertex((float) (x + 9), (float) (y - 0), 0.0f);
        this.tessellator.vertex((float) (x - 8), (float) (y - 0), 0.0f);
        this.tessellator.vertex((float) (x - 8), (float) (y + 1), 0.0f);
        this.tessellator.vertex((float) (x + 9), (float) (y + 1), 0.0f);
        this.tessellator.flush();
    }

    /**
     * Entry point of the game
     *
     * @param args Program arguments (unused)
     */
    public static void main(String[] args) {
        new Thread(new RubyDung()).start();
    }
}
