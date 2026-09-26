/*
 * Vendored from carlislefox/libgdx-texture-array-batch v1.2 (acbb5b7b515d0acf6dd57d43c98bf56c31d6008c).
 * https://github.com/carlislefox/libgdx-texture-array-batch
 * License: CC0-1.0, see TextureArraySpriteBatch.LICENSE.
 * Unciv changes: package, pre-flush/texture inspection hooks, and binding-cache
 * invalidation for dynamic texture uploads and nested offscreen batches.
 */
package com.unciv.ui.screens.basescreen;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;
import java.util.Arrays;

/** Draws batched quads using indices.
 * <p>
 * This is an optimised version of the SpriteBatch that maintains an LFU texture-cache to combine draw calls with different
 * textures effectively.
 * <p>
 * Use this Batch if you frequently utilise more than a single texture between calling {@link #begin()} and {@link #end()}. An
 * example would be if your Atlas is spread over multiple Textures or if you draw with individual Textures.
 * <p>
 * For best results, avoid calling begin() and end() more than once per frame unless absolutely necessary. This will
 * keep used textures in the cache for the duration of the frame and prevent binding the same texture(s) multiple
 * times per frame.
 *
 * @see Batch
 * @see SpriteBatch
 *
 * @author mzechner (Original SpriteBatch)
 * @author Nathan Sweet (Original SpriteBatch)
 * @author VaTTeRGeR (TextureArray Extension)
 * @author Fhox (Optimisations & Fixes)
 * @author fgnm (Fragment Shader) */
public class TextureArraySpriteBatch implements Batch {

    // GL state is shared by batches on the rendering thread. External texture operations
    // must invalidate the cache; normal draws only pay for a version comparison.
    private static long textureBindingVersion;
    private long bindingVersion;

    public static void invalidateTextureBindings() {
        textureBindingVersion++;
    }

    /** Texture currently assigned to a slot; valid up to getTextureLFUSize(). */
    protected Texture getTextureAtSlot(int slot) {
        return usedTextures[slot];
    }

    /** Called only for nonempty flushes, after cached bindings have been restored. */
    protected void prepareFlush() {
    }

    private void bindTexture(Texture texture, int slot) {
        boolean current = bindingVersion == textureBindingVersion;
        texture.bind(slot);
        invalidateTextureBindings();
        if (current) bindingVersion = textureBindingVersion;
    }

    private void restoreTextureBindings() {
        if (bindingVersion == textureBindingVersion) return;
        for (int slot = 0; slot < currentTextureLFUSize; slot++)
            usedTextures[slot].bind(slot);
        invalidateTextureBindings();
        bindingVersion = textureBindingVersion;
        // Offscreen icon rendering may also have changed the shader and blend state.
        getShader().bind();
        applyBlendState();
        Gdx.gl.glDepthMask(false);
    }

    // Constants

    private static final int SPRITE_VERTEX_SIZE = 5;

    private static final int SPRITE_FLOAT_SIZE = SPRITE_VERTEX_SIZE * 4;

    /** Precalculated as is required to determine the flush threshold every draw call */
    private final int SPRITE_ATTRIBUTE_LENGTH = SPRITE_FLOAT_SIZE + SPRITE_FLOAT_SIZE / SPRITE_VERTEX_SIZE;

    // State

    private int idx = 0;

    private final Mesh mesh;

    private final float[] vertices;

    /** The maximum number of available texture units for the fragment shader */
    private static int maxTextureUnits = -1;

    /** Textures in use (index: Texture Unit, value: Texture) */
    private final Texture[] usedTextures;

    /** LFU Array (index: Texture Unit Index - value: Access frequency) */
    private final int[] usedTexturesLFU;

    /** Gets sent to the fragment shader as a uniform "uniform sampler2d[X] u_textures" */
    private final IntBuffer textureUnitIndicesBuffer;

    private float invTexWidth = 0, invTexHeight = 0;

    private boolean drawing = false;

    private final Matrix4 transformMatrix = new Matrix4();

    private final Matrix4 projectionMatrix = new Matrix4();

    private final Matrix4 combinedMatrix = new Matrix4();

    private boolean blendingDisabled = false;


    private int blendSrcFunc = GL20.GL_SRC_ALPHA;

    private int blendDstFunc = GL20.GL_ONE_MINUS_SRC_ALPHA;

    private int blendSrcFuncAlpha = GL20.GL_SRC_ALPHA;

    private int blendDstFuncAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA;

    private ShaderProgram shader = null;

    private ShaderProgram customShader = null;

    private static String shaderErrorLog = null;

    private boolean ownsShader;

    private final Color color = new Color(1, 1, 1, 1);

    private float colorPacked = Color.WHITE_FLOAT_BITS;

    /** Number of render calls since the last {@link #begin()}. **/
    public int renderCalls = 0;

    /** Number of rendering calls, ever. Will not be reset unless set manually. **/
    public int totalRenderCalls = 0;

    /** The maximum number of sprites rendered in one batch so far. **/
    public int maxSpritesInBatch = 0;

    /** The current number of textures in the LFU cache. Gets reset when calling {@link #begin()} **/
    private int currentTextureLFUSize = 0;

    /** The current number of texture swaps in the LFU cache. Gets reset when calling {@link #begin()} **/
    private int currentTextureLFUSwaps = 0;

    /** Keep track of the last texture used for equals checks to short-circuit activateTexture() calls on the same Texture **/
    private Texture lastTexture = null;

    /** Keep track of the last texture index used to short-circuit activateTexture() calls on the same Texture **/
    private int lastTextureLFUIndex = -1;

    /** Constructs a new TextureArraySpriteBatch with a size of 1000, one buffer, and the default shader.
     * @see TextureArraySpriteBatch#TextureArraySpriteBatch(int, ShaderProgram) */
    public TextureArraySpriteBatch() {
        this(3000);
    }

    /** Constructs a TextureArraySpriteBatch with one buffer and the default shader.
     * @see TextureArraySpriteBatch#TextureArraySpriteBatch(int, ShaderProgram) */
    public TextureArraySpriteBatch(int size) {
        this(size, null);
    }

    /** Constructs a new TextureArraySpriteBatch. Sets the projection matrix to an orthographic projection with y-axis point
     * upwards, x-axis point to the right and the origin being in the bottom left corner of the screen. The projection will be
     * pixel perfect with respect to the current screen resolution.
     * <p>
     * The defaultShader specifies the shader to use. Note that the names for uniforms for this default shader are different than
     * the ones expect for shaders set with {@link #setShader(ShaderProgram)}.
     * @param size The max number of sprites in a single batch. Max of 8191.
     * @param defaultShader The default shader to use. This is not owned by the TextureArraySpriteBatch and must be disposed
     *           separately.
     * @throws IllegalStateException Thrown if the device does not support texture arrays. Make sure to implement a Fallback to
     *            {@link SpriteBatch} in case Texture Arrays are not supported on a clients device.
     * @see #createDefaultShader(int)
     * @see #getMaxTextureUnits() */
    public TextureArraySpriteBatch(int size, ShaderProgram defaultShader) throws IllegalStateException {
        // 32767 is max vertex index, so 32767 / 4 vertices per sprite = 8191 sprites max.
        if (size > 8191) throw new IllegalArgumentException("Can't have more than 8191 sprites per batch: " + size);

        getMaxTextureUnits();

        if (maxTextureUnits == 0) {
            throw new IllegalStateException(
                    "Texture Arrays are not supported on this device:\n" + shaderErrorLog);
        }

        if (defaultShader == null) {
            shader = createDefaultShader(maxTextureUnits);
            ownsShader = true;
        } else
            shader = defaultShader;

        usedTextures = new Texture[maxTextureUnits];
        usedTexturesLFU = new int[maxTextureUnits];

        // This contains the numbers 0 ... maxTextureUnits - 1. We send these to the shader as a uniform.
        textureUnitIndicesBuffer = BufferUtils.newIntBuffer(maxTextureUnits);
        for (int i = 0; i < maxTextureUnits; i++) {
            textureUnitIndicesBuffer.put(i);
        }
        textureUnitIndicesBuffer.flip();

        VertexDataType vertexDataType = (Gdx.gl30 != null) ? VertexDataType.VertexBufferObjectWithVAO : VertexDataType.VertexBufferObject;

        // The vertex data is extended with one float for the texture index.
        mesh = new Mesh(vertexDataType, false, size * 4, size * 6,
                new VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
                new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
                new VertexAttribute(Usage.Generic, 1, "texture_index"));

        projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        vertices = new float[size * (SPRITE_FLOAT_SIZE + 4)];

        int len = size * 6;
        short[] indices = new short[len];
        short j = 0;
        for (int i = 0; i < len; i += 6, j += 4) {
            indices[i] = j;
            indices[i + 1] = (short)(j + 1);
            indices[i + 2] = (short)(j + 2);
            indices[i + 3] = (short)(j + 2);
            indices[i + 4] = (short)(j + 3);
            indices[i + 5] = j;
        }

        mesh.setIndices(indices);

        // Pre bind the mesh to force the upload of indices data.
        mesh.getIndexData().bind();
        mesh.getIndexData().unbind();
    }

    /** Returns a new instance of the default shader used by TextureArraySpriteBatch for GL2 when no shader is specified.
     * @see #getMaxTextureUnits() */
    public ShaderProgram createDefaultShader(int maximumTextureUnits) {
        final String vertexShader = getVertexShader();
        final String fragmentShader = getFragmentShader(maximumTextureUnits);
        final ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);

        if (!shader.isCompiled()) {
            throw new IllegalArgumentException("Error compiling shader: " + shader.getLog());
        }

        return shader;
    }

    /**
     * Convenience override point
     * @return
     */
    protected String getVertexShader() {
        // The texture index is just passed to the fragment shader, maybe there's an more elegant way.
        return "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
                + "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
                + "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
                + "attribute float texture_index;\n" //
                + "uniform mat4 u_projTrans;\n" //
                + "varying vec4 v_color;\n" //
                + "varying vec2 v_texCoords;\n" //
                + "varying float v_texture_index;\n" //
                + "\n" //
                + "void main()\n" //
                + "{\n" //
                + "   v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
                + "   v_color.a = v_color.a * (255.0/254.0);\n" //
                + "   v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
                + "   v_texture_index = texture_index;\n" //
                + "   gl_Position =  u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
                + "}\n";
    }

    /**
     * Convenience override point.
     */
    protected String getFragmentShader(int maximumTextureUnits) {
        /*
        Although building a if else like this at runtime might seem insane, it turns out directly accessing the
        uniforms array with a variable is a non-standard operation and causes all sorts of artifacting on AMD and
        Samsung GPUs. This sidesteps that issue while letting us remain in the land of OpenGL 2.0.
         */
        final StringBuilder getTextureFunction = new StringBuilder("vec4 getTextureFromArray(vec2 uv) {\n");

        for (int i = 0; i < maximumTextureUnits; i++) {
            if (i != 0) getTextureFunction.append(" else ");
            getTextureFunction.append("if (v_texture_index < ").append(i).append(".5) return texture2D(u_textures[").append(i).append("], uv);\n");
        }

        getTextureFunction.append("}\n");

        // The texture is simply selected from an array of textures
        return "#ifdef GL_ES\n" //
                + "#define LOWP lowp\n" //
                + "precision mediump float;\n" //
                + "#else\n" //
                + "#define LOWP\n" //
                + "#endif\n" //
                + "varying LOWP vec4 v_color;\n" //
                + "varying vec2 v_texCoords;\n" //
                + "varying float v_texture_index;\n" //
                + "uniform sampler2D u_textures[" + maximumTextureUnits + "];\n" //
                + "\n"
                + getTextureFunction
                + "\n"
                + "void main()\n"//
                + "{\n" //
                + "    gl_FragColor = v_color * getTextureFromArray(v_texCoords);\n" //
                + "}";
    }

    @Override
    public void begin() {
        if (drawing) throw new IllegalStateException("TextureArraySpriteBatch.end must be called before begin.");

        lastTexture = null;
        lastTextureLFUIndex = -1;
        renderCalls = 0;

        currentTextureLFUSize = 0;
        // Even an empty nested batch changes shader and blend state.
        invalidateTextureBindings();
        bindingVersion = textureBindingVersion;
        currentTextureLFUSwaps = 0;

        Arrays.fill(usedTextures, null);
        Arrays.fill(usedTexturesLFU, 0);

        Gdx.gl.glDepthMask(false);

        if (customShader != null) {
            customShader.bind();
        } else {
            shader.bind();
        }

        setupMatrices();
        applyBlendState();

        drawing = true;
    }

    @Override
    public void end() {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before end.");

        if (idx > 0) flush();

        drawing = false;

        GL20 gl = Gdx.gl;

        gl.glDepthMask(true);

        if (isBlendingEnabled()) {
            gl.glDisable(GL20.GL_BLEND);
        }
    }

    @Override
    public void dispose() {
        mesh.dispose();

        if (ownsShader && shader != null) {
            shader.dispose();
        }
    }

    @Override
    public void setColor(Color tint) {
        color.set(tint);
        colorPacked = tint.toFloatBits();
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
        colorPacked = color.toFloatBits();
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setPackedColor(float packedColor) {
        Color.abgr8888ToColor(color, packedColor);
        this.colorPacked = packedColor;
    }

    @Override
    public float getPackedColor() {
        return colorPacked;
    }

    @Override
    public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(texture);

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }

        final float color = this.colorPacked;

        vertices[idx++] = x1;
        vertices[idx++] = y1;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x2;
        vertices[idx++] = y2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x3;
        vertices[idx++] = y3;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x4;
        vertices[idx++] = y4;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(texture);

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;
        final float fx2 = x + width;
        final float fy2 = y + height;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }

        float color = this.colorPacked;

        vertices[idx++] = x;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(texture);

        final float u = srcX * invTexWidth;
        final float v = (srcY + srcHeight) * invTexHeight;
        final float u2 = (srcX + srcWidth) * invTexWidth;
        final float v2 = srcY * invTexHeight;
        final float fx2 = x + srcWidth;
        final float fy2 = y + srcHeight;

        float color = this.colorPacked;

        vertices[idx++] = x;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(texture);

        final float fx2 = x + width;
        final float fy2 = y + height;

        float color = this.colorPacked;

        vertices[idx++] = x;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(Texture texture, float x, float y) {
        draw(texture, x, y, texture.getWidth(), texture.getHeight());
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(texture);

        final float fx2 = x + width;
        final float fy2 = y + height;
        final float u = 0;
        final float v = 1;
        final float u2 = 1;
        final float v2 = 0;

        float color = this.colorPacked;

        vertices[idx++] = x;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
        if (!drawing) {
            throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");
        }
        if (count % SPRITE_FLOAT_SIZE != 0) {
            throw new IllegalArgumentException("count must be a multiple of " + SPRITE_FLOAT_SIZE);
        }

        // Assigns a texture unit to this texture, flushing if none is available
        final float ti = activateTextureCached(texture);
        final int end = offset + count;

        // Process complete sprites so capacity is checked once per sprite rather than once per vertex.
        for (int srcPos = offset; srcPos < end;) {
            flushIfFull();

            for (int vertex = 0; vertex < 4; vertex++) {
                vertices[idx++] = spriteVertices[srcPos++];
                vertices[idx++] = spriteVertices[srcPos++];
                vertices[idx++] = spriteVertices[srcPos++];
                vertices[idx++] = spriteVertices[srcPos++];
                vertices[idx++] = spriteVertices[srcPos++];
                vertices[idx++] = ti;
            }
        }
    }

    @Override
    public void draw(TextureRegion region, float x, float y) {
        draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(region.getTexture());

        final float fx2 = x + width;
        final float fy2 = y + height;
        final float u = region.getU();
        final float v = region.getV2();
        final float u2 = region.getU2();
        final float v2 = region.getV();

        float color = this.colorPacked;

        vertices[idx++] = x;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = fy2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = fx2;
        vertices[idx++] = y;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(region.getTexture());

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        final float u = region.getU();
        final float v = region.getV2();
        final float u2 = region.getU2();
        final float v2 = region.getV();

        float color = this.colorPacked;

        vertices[idx++] = x1;
        vertices[idx++] = y1;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x2;
        vertices[idx++] = y2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x3;
        vertices[idx++] = y3;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x4;
        vertices[idx++] = y4;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(region.getTexture());

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u1, v1, u2, v2, u3, v3, u4, v4;
        if (clockwise) {
            u1 = region.getU2();
            v1 = region.getV2();
            u2 = region.getU();
            v2 = region.getV2();
            u3 = region.getU();
            v3 = region.getV();
            u4 = region.getU2();
            v4 = region.getV();
        } else {
            u1 = region.getU();
            v1 = region.getV();
            u2 = region.getU2();
            v2 = region.getV();
            u3 = region.getU2();
            v3 = region.getV2();
            u4 = region.getU();
            v4 = region.getV2();
        }

        float color = this.colorPacked;

        vertices[idx++] = x1;
        vertices[idx++] = y1;
        vertices[idx++] = color;
        vertices[idx++] = u1;
        vertices[idx++] = v1;
        vertices[idx++] = ti;

        vertices[idx++] = x2;
        vertices[idx++] = y2;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x3;
        vertices[idx++] = y3;
        vertices[idx++] = color;
        vertices[idx++] = u3;
        vertices[idx++] = v3;
        vertices[idx++] = ti;

        vertices[idx++] = x4;
        vertices[idx++] = y4;
        vertices[idx++] = color;
        vertices[idx++] = u4;
        vertices[idx++] = v4;
        vertices[idx++] = ti;
    }

    @Override
    public void draw(TextureRegion region, float width, float height, Affine2 transform) {
        if (!drawing) throw new IllegalStateException("TextureArraySpriteBatch.begin must be called before draw.");

        float[] vertices = this.vertices;

        flushIfFull();

        final float ti = activateTextureCached(region.getTexture());

        // construct corner points
        float x1 = transform.m02;
        float y1 = transform.m12;
        float x2 = transform.m01 * height + transform.m02;
        float y2 = transform.m11 * height + transform.m12;
        float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
        float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
        float x4 = transform.m00 * width + transform.m02;
        float y4 = transform.m10 * width + transform.m12;

        float u = region.getU();
        float v = region.getV2();
        float u2 = region.getU2();
        float v2 = region.getV();

        float color = this.colorPacked;

        vertices[idx++] = x1;
        vertices[idx++] = y1;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v;
        vertices[idx++] = ti;

        vertices[idx++] = x2;
        vertices[idx++] = y2;
        vertices[idx++] = color;
        vertices[idx++] = u;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x3;
        vertices[idx++] = y3;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v2;
        vertices[idx++] = ti;

        vertices[idx++] = x4;
        vertices[idx++] = y4;
        vertices[idx++] = color;
        vertices[idx++] = u2;
        vertices[idx++] = v;
        vertices[idx++] = ti;
    }

    /** Flushes if the vertices array cannot hold an additional sprite ((spriteVertexSize + 1) * 4 vertices) anymore. */
    private void flushIfFull() {
        // original Sprite attribute size plus one extra float per sprite vertex
        if (vertices.length - idx < SPRITE_ATTRIBUTE_LENGTH) {
            flush();
        }
    }

    @Override
    public void flush() {
        if (idx == 0) return;

        restoreTextureBindings();
        prepareFlush();
        // The hook may refresh a texture in its existing slot.
        bindingVersion = textureBindingVersion;

        renderCalls++;
        totalRenderCalls++;

        int spritesInBatch = idx / (SPRITE_FLOAT_SIZE + 4);
        if (spritesInBatch > maxSpritesInBatch) maxSpritesInBatch = spritesInBatch;
        int count = spritesInBatch * 6;

        // Set TEXTURE0 as active again before drawing.
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        Mesh mesh = this.mesh;
        mesh.setVertices(vertices, 0, idx);

        if (customShader != null) {
            mesh.render(customShader, GL20.GL_TRIANGLES, 0, count);
        } else {
            mesh.render(shader, GL20.GL_TRIANGLES, 0, count);
        }

        idx = 0;
        lastTexture = null;
        lastTextureLFUIndex = -1;
    }

    /**
     * Caches a reference to the last activated texture to short-circuit iterating through the Texture Array.
     */
    private int activateTextureCached(Texture texture) {
        if (texture == lastTexture) return lastTextureLFUIndex;
        lastTextureLFUIndex = activateTexture(texture);
        lastTexture = texture;

        // Increase the access counter
        usedTexturesLFU[lastTextureLFUIndex]++;

        return lastTextureLFUIndex;
    }

    /** Assigns Texture units and manages the LFU cache.
     * @param texture The texture that shall be loaded into the cache, if it is not already loaded.
     * @return The texture slot that has been allocated to the selected texture */
    private int activateTexture(Texture texture) {
        invTexWidth = 1.0f / texture.getWidth();
        invTexHeight = 1.0f / texture.getHeight();

        // This is our identifier for the textures
        final int textureHandle = texture.getTextureObjectHandle();

        // First try to see if the texture is already cached
        for (int i = 0; i < currentTextureLFUSize; i++) {
            // getTextureObjectHandle() just returns an int,
            // it's fine to call this method instead of caching the value.
            if (textureHandle == usedTextures[i].getTextureObjectHandle()) {
                return i;
            }
        }

        // If a free texture unit is available we just use it
        // If not we have to flush and then throw out the least accessed one.
        if (currentTextureLFUSize < maxTextureUnits) {
            // Put the texture into the next free slot
            usedTextures[currentTextureLFUSize] = texture;
            bindTexture(texture, currentTextureLFUSize);
            return currentTextureLFUSize++;
        }

        // We have to flush if there is something in the pipeline already,
        // otherwise the texture index of previously rendered sprites gets invalidated
        if (idx > 0) {
            flush();
        }

        int slot = 0;
        int slotVal = usedTexturesLFU[0];
        int max = 0;
        int average = 0;

        // We search for the best candidate for a swap (least accessed) and collect some data
        for (int i = 0; i < maxTextureUnits; i++) {
            final int val = usedTexturesLFU[i];
            max = Math.max(val, max);
            average += val;

            if (val <= slotVal) {
                slot = i;
                slotVal = val;
            }
        }

        // The LFU weights will be normalized to the range 0...100
        final int normalizeRange = 100;

        for (int i = 0; i < maxTextureUnits; i++) {
            usedTexturesLFU[i] = usedTexturesLFU[i] * normalizeRange / max;
        }

        average = (average * normalizeRange) / (max * maxTextureUnits);

        // Give the new texture a fair (average) chance of staying.
        usedTexturesLFU[slot] = average;
        usedTextures[slot] = texture;
        bindTexture(texture, slot);

        // For statistics
        currentTextureLFUSwaps++;

        return slot;
    }

    /** @return The number of texture swaps the LFU cache performed since calling {@link #begin()}. */
    public int getTextureLFUSwaps() {
        return currentTextureLFUSwaps;
    }

    /** @return The current number of textures in the LFU cache. Gets reset when calling {@link #begin()}. */
    public int getTextureLFUSize() {
        return currentTextureLFUSize;
    }

    /** @return The maximum number of textures that the LFU cache can hold. This limit is imposed by the driver.
     * @see #getMaxTextureUnits() */
    public int getTextureLFUCapacity() {
        return getMaxTextureUnits();
    }

    @Override
    public void disableBlending() {
        if (blendingDisabled) return;
        flush();
        blendingDisabled = true;
        if (drawing) applyBlendState();
    }

    @Override
    public void enableBlending() {
        if (!blendingDisabled) return;
        flush();
        blendingDisabled = false;
        if (drawing) applyBlendState();
    }

    @Override
    public void setBlendFunction(int srcFunc, int dstFunc) {
        setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    @Override
    public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {
        if (blendSrcFunc == srcFuncColor && blendDstFunc == dstFuncColor && blendSrcFuncAlpha == srcFuncAlpha && blendDstFuncAlpha == dstFuncAlpha) {
            return;
        }

        flush();

        blendSrcFunc = srcFuncColor;
        blendDstFunc = dstFuncColor;
        blendSrcFuncAlpha = srcFuncAlpha;
        blendDstFuncAlpha = dstFuncAlpha;

        if (drawing) applyBlendState();
    }

    /** Applies blending state when beginning a batch or after that state changes. */
    private void applyBlendState() {
        if (blendingDisabled) {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        } else {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            if (blendSrcFunc != -1) {
                Gdx.gl.glBlendFuncSeparate(blendSrcFunc, blendDstFunc, blendSrcFuncAlpha, blendDstFuncAlpha);
            }
        }
    }

    @Override
    public int getBlendSrcFunc() {
        return blendSrcFunc;
    }

    @Override
    public int getBlendDstFunc() {
        return blendDstFunc;
    }

    @Override
    public int getBlendSrcFuncAlpha() {
        return blendSrcFuncAlpha;
    }

    @Override
    public int getBlendDstFuncAlpha() {
        return blendDstFuncAlpha;
    }

    @Override
    public boolean isBlendingEnabled() {
        return !blendingDisabled;
    }

    @Override
    public boolean isDrawing() {
        return drawing;
    }

    @Override
    public Matrix4 getProjectionMatrix() {
        return projectionMatrix;
    }

    @Override
    public Matrix4 getTransformMatrix() {
        return transformMatrix;
    }

    @Override
    public void setProjectionMatrix(Matrix4 projection) {
        if (drawing) flush();
        projectionMatrix.set(projection);
        if (drawing) setupMatrices();
    }

    @Override
    public void setTransformMatrix(Matrix4 transform) {
        if (drawing) flush();
        transformMatrix.set(transform);
        if (drawing) setupMatrices();
    }

    public void setupMatrices() {
        combinedMatrix.set(projectionMatrix).mul(transformMatrix);

        if (customShader != null) {
            customShader.setUniformMatrix("u_projTrans", combinedMatrix);
            Gdx.gl20.glUniform1iv(customShader.fetchUniformLocation("u_textures", true), maxTextureUnits, textureUnitIndicesBuffer);
        } else {
            shader.setUniformMatrix("u_projTrans", combinedMatrix);
            Gdx.gl20.glUniform1iv(shader.fetchUniformLocation("u_textures", true), maxTextureUnits, textureUnitIndicesBuffer);
        }
    }

    /** Queries the number of supported textures in a texture array by trying the create the default shader.<br>
     * The first call of this method is very expensive, after that it simply returns a cached value.
     * @return the number of supported textures in a texture array or zero if this feature is unsupported on this device.
     * @see #setShader(ShaderProgram shader) */
    public int getMaxTextureUnits() {
        if (maxTextureUnits == -1) {
            // Query the number of available texture units and decide on a safe number of texture units to use
            IntBuffer texUnitsQueryBuffer = BufferUtils.newIntBuffer(32);
            Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, texUnitsQueryBuffer);
            int maxTextureUnitsLocal = texUnitsQueryBuffer.get();

            // Some OpenGL drivers (I'm looking at you, Intel!) do not report the right values,
            // so we take caution and test it first, reducing the number of slots if needed.
            // Will try to find the maximum amount of texture units supported.
            while (maxTextureUnitsLocal > 0) {
                try {
                    ShaderProgram tempProg = createDefaultShader(maxTextureUnitsLocal);
                    tempProg.dispose();
                    break;
                } catch (Exception e) {
                    maxTextureUnitsLocal /= 2;
                    shaderErrorLog = e.getMessage();
                }
            }

            maxTextureUnits = maxTextureUnitsLocal;
        }

        return maxTextureUnits;
    }

    /** Sets the shader to be used in a GLES 2.0 environment. Vertex position attribute is called "a_position", the texture
     * coordinates attribute is called "a_texCoord0", the color attribute is called "a_color", texture unit index is called
     * "texture_index", this needs to be converted to int with int(...) in the fragment shader. See
     * {@link ShaderProgram#POSITION_ATTRIBUTE}, {@link ShaderProgram#COLOR_ATTRIBUTE} and {@link ShaderProgram#TEXCOORD_ATTRIBUTE}
     * which gets "0" appended to indicate the use of the first texture unit. The combined transform and projection matrix is
     * uploaded via a mat4 uniform called "u_projTrans". The texture sampler array is passed via a uniform called "u_textures", see
     * {@link TextureArraySpriteBatch#createDefaultShader(int)} for reference.
     * <p>
     * Call this method with a null argument to use the default shader.
     * <p>
     * This method will flush the batch before setting the new shader, you can call it in between {@link #begin()} and
     * {@link #end()}.
     * @param shader the {@link ShaderProgram} or null to use the default shader.
     * @see #createDefaultShader(int)
     * @see #getMaxTextureUnits() */
    @Override
    public void setShader(ShaderProgram shader) {
        if (drawing) {
            flush();
        }

        customShader = shader;

        if (drawing) {
            if (customShader != null) {
                customShader.bind();
            } else {
                this.shader.bind();
            }

            setupMatrices();
        }
    }

    @Override
    public ShaderProgram getShader() {
        if (customShader != null) return customShader;
        return shader;
    }

}
