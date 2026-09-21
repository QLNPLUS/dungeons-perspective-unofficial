package com.cleannrooster.dungeons_iso.api.cullers.room;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import com.cleannrooster.dungeons_iso.mixin.GameRendererAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;

import java.util.List;

/**
 * Draws the blocks the cullers removed back into the scene as translucent geometry.
 *
 * <p>Removing everything between the camera and the player is correct but looks wrong when the
 * player is deep inside something: 27 rays through fifteen blocks of sand excavate a shaft to the
 * surface, and what you see is a black pit. Drawing the removed volume back, with opacity rising
 * with distance from the player on screen, keeps the world reading as intact and leaves only a
 * see-through pocket around the player.
 *
 * <h2>Which faces to draw</h2>
 *
 * <p>The first attempt drew the hull of the removed volume — every face whose neighbour was not
 * also removed. That stacks: looking into a shaft you cross the hull at least twice, and at 0.85
 * alpha two layers already come out at 0.98, so the ghost read as solid.
 *
 * <p>The rule that does not stack is to draw the faces that <b>were being drawn before anything was
 * culled</b> — the ones vanilla's own face culling would keep. Interior blocks of the volume
 * contributed nothing to the original image and contribute nothing here, so the ghost has exactly
 * as many layers as the real terrain did. The newly exposed cut faces are not drawn at all, which
 * is right: they never existed.
 *
 * <h2>Why the geometry is cached</h2>
 *
 * <p>Deciding <i>what</i> to draw is expensive — a block state lookup, a model lookup and six
 * {@link Block#shouldDrawSide} tests per block — and it only changes when the cull set does.
 * Deciding <i>how opaque</i> each vertex is changes every frame, because it depends on where the
 * player is on screen. So the first is baked into a flat vertex array when the mask or snapshot
 * identity changes, and the per-frame path does nothing but project and write vertices.
 */
public final class GhostRenderer {

    private static final Logger LOGGER = LogManager.getLogger("dungeons_iso/ghost");

    /** Below this a quad contributes nothing worth submitting. */
    private static final float MIN_ALPHA = 0.02F;
    /**
     * Ghost geometry must test against existing terrain, but must not write its own depth. It is
     * drawn before entities so the entity pass can cover it when a removed face lies in front of an
     * entity. The stock entity-translucent layer writes ALL_MASK in 1.20.1, which would keep that
     * entity from passing the later depth test.
     */
    private static final RenderLayer GHOST_LAYER = RenderLayer.of(
            "dungeons_iso_ghost",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS,
            262_144,
            true,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(RenderPhase.BLOCK_ATLAS_TEXTURE)
                    .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false));
    /** Model quad lookup wants a Random; the value never matters for full blocks. */
    private static final Random RANDOM = Random.create();
    /** Reused for corner projection. Render thread only. */
    private static final float[] CORNER = new float[2];
    /** Reused for the player focus projection. Render thread only. */
    private static final float[] SCREEN = new float[2];
    // Per-quad scratch, hoisted out of the frame loop. Render thread only. These are the four
    // corners, four edge midpoints and centre of a 2x2 subdivision.
    private static final float[] nodeAlpha = new float[9];
    private static final float[] cornerX = new float[4];
    private static final float[] cornerY = new float[4];
    /** Floats per cached vertex: x, y, z (origin-relative), u, v, nx, ny, nz, r, g, b. */
    private static final int STRIDE = 11;
    /** Safety ceiling on cached geometry, so a pathological cull set cannot allocate without bound. */
    private static final int MAX_VERTICES = 120_000;
    /** Maximum amount of ghost geometry work allowed to land in one render pass. */
    private static final long BAKE_BUDGET_NANOS = 1_500_000L;
    /**
     * Rebake at least this often, so edits to the world do not leave the ghost stale forever.
     *
     * <p>Only a safety net: a changed cull set rebakes immediately. At two seconds it had become
     * the main reason the bake ran at all, once unchanged results stopped republishing — re-doing
     * every shouldDrawSide, model lookup and lightmap sample to reproduce what was already there.
     */
    private static final long MAX_CACHE_AGE_MS = 10_000L;

    // ---- geometry cache, render thread only ----
    private static SightlineMask cachedMask;
    private static RoomSnapshot cachedSnapshot;
    private static long cachedAtMs;
    private static final float[] EMPTY_FLOATS = new float[0];
    private static final int[] EMPTY_INTS = new int[0];
    private static float[] geometry = EMPTY_FLOATS;
    private static int[] lights = EMPTY_INTS;
    private static int vertexCount;
    private static BakeJob bakeJob;
    /** Positions are stored relative to this, so float precision stays usable far from origin. */
    private static double originX, originY, originZ;

    /** Size of the current bake, for the debug report. */
    public static volatile int lastVertexCount;
    /** Number of vertices actually submitted by the last render pass, for the debug report. */
    public static volatile int lastSubmittedVertexCount;
    private static volatile boolean renderErrorLogged;

    private GhostRenderer() {
    }

    /**
     * Called from the Forge world-render stage after terrain. The matrix stack carries the camera
     * projection and rotation; positions are offset by the camera manually to match vanilla chunks.
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
                              Camera camera, GameRenderer gameRenderer) {
        long started = System.nanoTime();
        try {
            renderInternal(matrices, buffers, camera, gameRenderer);
        } finally {
            CullDebug.recordGhostRender(System.nanoTime() - started);
        }
    }

    private static void renderInternal(MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
                                       Camera camera, GameRenderer gameRenderer) {
        if (!Mod.enabled || !Config.GSON.instance().ghostCulledBlocks) {
            invalidate();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.cameraEntity == null) {
            invalidate();
            return;
        }

        lastSubmittedVertexCount = 0;

        float maxAlpha = Math.max(0F, Math.min(1F, Config.GSON.instance().ghostMaxAlpha));
        if (maxAlpha <= MIN_ALPHA) {
            return;
        }
        float rangeScale = Math.max(0.5F, Math.min(3.0F, Config.GSON.instance().ghostScreenRangeScale));
        // Keep the transparent pocket deliberately small in open terrain. Underground, the
        // sightline scanner has separately confirmed a covered local space, so allow a modestly
        // wider pocket there without restoring the old large black opening.
        float clearLimit = SightlineScanner.INSTANCE.lastBroadUnderground ? 0.22F
                : SightlineScanner.INSTANCE.lastUnderground ? 0.18F : 0.12F;
        float clearAt = Math.min(clearLimit,
                Math.max(0F, Config.GSON.instance().ghostClearScreen) * rangeScale);
        // The ramp needs somewhere to happen, so the far edge is always kept ahead of the near one
        // however the two are configured. Both distances scale together to preserve the curve.
        float opaqueAt = Math.max(clearAt + 0.01F,
                Math.max(0F, Config.GSON.instance().ghostOpaqueScreen) * rangeScale);

        // Chunk meshes are rebuilt in batches. Keep the published cull geometry available while
        // that queue drains, otherwise the old invisible mesh has no translucent fallback for a
        // few frames and the blocking terrain appears to vanish instead of fading.
        boolean rebuilding = SectionRebuildQueue.INSTANCE.size() > 0;
        SightlineMask mask = SightlineScanner.INSTANCE.isActive() || rebuilding
                ? SightlineScanner.INSTANCE.mask() : null;
        if (mask != null && mask.suppressesCulling()) {
            mask = null;
        }
        RoomSnapshot snapshot = RoomScanner.INSTANCE.isActive() || rebuilding
                ? RoomScanner.INSTANCE.snapshot() : null;

        ensureGeometry(client, world, mask, snapshot);
        if (vertexCount == 0) {
            return;
        }

        // Opacity is driven by distance on screen, not in the world. Under an isometric camera a
        // world-space sphere around the player projects to an ellipse, and blocks far along the
        // view axis sit near the player in 3D while being nowhere near them as drawn.
        //
        // Projected through the camera basis and FOV rather than through a matrix, matching how
        // MouseMixin turns a cursor position into a world ray. Going through a matrix meant
        // depending on which matrix was actually in force, and getting that wrong does not fail
        // loudly — it silently drops the perspective divide, leaving a "screen distance" that is
        // really a camera-space offset in blocks and a pocket whose shape swims with camera yaw.
        MatrixStack.Entry entry = matrices.peek();
        Vec3d cameraPos = camera.getPos();
        Projector projector = new Projector(camera, gameRenderer);

        Vec3d focus = client.cameraEntity.getBoundingBox().getCenter();
        if (!projector.project(focus.x, focus.y, focus.z, cameraPos, SCREEN)) {
            // The player is behind the camera; nothing sensible to measure against.
            return;
        }
        float focusX = SCREEN[0];
        float focusY = SCREEN[1];
        lastVertexCount = vertexCount;

        VertexConsumer consumer = buffers.getBuffer(GHOST_LAYER);

        double offX = originX - cameraPos.x;
        double offY = originY - cameraPos.y;
        double offZ = originZ - cameraPos.z;

        // Screen x runs to +/- the aspect ratio in these units, y to +/- 1. Anything outside that
        // box is off screen.
        float halfWidth = client.getWindow().getFramebufferHeight() <= 0 ? 1.0F
                : (float) client.getWindow().getFramebufferWidth()
                        / client.getWindow().getFramebufferHeight();
        int submittedVertices = 0;

        for (int v = 0; v + 3 < vertexCount; v += 4) {
            int base = v * STRIDE;

            // The boundary is a closed shell, so a ray through it crosses twice — near side and
            // far. Dropping the far side leaves exactly one layer, which is what keeps the ghost
            // from compounding with itself.
            double faceX = originX + geometry[base] - cameraPos.x;
            double faceY = originY + geometry[base + 1] - cameraPos.y;
            double faceZ = originZ + geometry[base + 2] - cameraPos.z;
            if (faceX * geometry[base + 5] + faceY * geometry[base + 6]
                    + faceZ * geometry[base + 7] > 0) {
                continue;
            }

            // Project the corners once and keep them: the pocket test and the alpha both need them.
            boolean projected = true;
            float minSX = Float.MAX_VALUE, maxSX = -Float.MAX_VALUE;
            float minSY = Float.MAX_VALUE, maxSY = -Float.MAX_VALUE;
            for (int i = 0; i < 4 && projected; i++) {
                int o = (v + i) * STRIDE;
                projected = projector.project(originX + geometry[o], originY + geometry[o + 1],
                        originZ + geometry[o + 2], cameraPos, CORNER);
                if (!projected) {
                    break;
                }
                cornerX[i] = CORNER[0];
                cornerY[i] = CORNER[1];
                if (CORNER[0] < minSX) minSX = CORNER[0];
                if (CORNER[0] > maxSX) maxSX = CORNER[0];
                if (CORNER[1] < minSY) minSY = CORNER[1];
                if (CORNER[1] > maxSY) maxSY = CORNER[1];
            }
            // A corner behind the camera means the quad straddles the near plane and its screen
            // extent is meaningless. Those are right on top of the camera; drop them.
            if (!projected) {
                continue;
            }

            // Entirely off screen. The cull set is a volume around the camera-to-player line and a
            // good part of it lands outside the view, so this drops a real fraction of the geometry
            // for the cost of four comparisons — and the screen extent it tests was already
            // computed for the pocket check below, so it is genuinely free.
            if (minSX > halfWidth || maxSX < -halfWidth || minSY > 1.0F || maxSY < -1.0F) {
                continue;
            }

            // Most ghost faces are nowhere near the transparent player pocket. Keep those on the
            // original four-vertex path; only faces whose projected bounds touch the pocket need
            // the nine-point subdivision below. This keeps the visual fix for large faces without
            // paying nine projections for every visible ghost quad every frame.
            boolean intersectsClearPocket = maxSX >= focusX - clearAt && minSX <= focusX + clearAt
                    && maxSY >= focusY - clearAt && minSY <= focusY + clearAt;
            if (!intersectsClearPocket) {
                float a0 = screenAlpha(cornerX[0], cornerY[0], focusX, focusY,
                        clearAt, opaqueAt, maxAlpha);
                float a1 = screenAlpha(cornerX[1], cornerY[1], focusX, focusY,
                        clearAt, opaqueAt, maxAlpha);
                float a2 = screenAlpha(cornerX[2], cornerY[2], focusX, focusY,
                        clearAt, opaqueAt, maxAlpha);
                float a3 = screenAlpha(cornerX[3], cornerY[3], focusX, focusY,
                        clearAt, opaqueAt, maxAlpha);
                if (a0 <= MIN_ALPHA && a1 <= MIN_ALPHA && a2 <= MIN_ALPHA && a3 <= MIN_ALPHA) {
                    continue;
                }
                emitQuad(consumer, entry, v, offX, offY, offZ, a0, a1, a2, a3);
                submittedVertices += 4;
                continue;
            }

            // A large baked face can cover the entire player even when all four corners are outside
            // the clear pocket. Split it into four quads and sample the centre and edge midpoints,
            // so the transparent area is real geometry rather than a corner-only interpolation.
            nodeAlpha[0] = screenAlpha(cornerX[0], cornerY[0], focusX, focusY,
                    clearAt, opaqueAt, maxAlpha);
            nodeAlpha[1] = alphaAt(v, 0.5F, 0.0F, projector, cameraPos,
                    focusX, focusY, clearAt, opaqueAt, maxAlpha);
            nodeAlpha[2] = screenAlpha(cornerX[1], cornerY[1], focusX, focusY,
                    clearAt, opaqueAt, maxAlpha);
            nodeAlpha[3] = alphaAt(v, 1.0F, 0.5F, projector, cameraPos,
                    focusX, focusY, clearAt, opaqueAt, maxAlpha);
            nodeAlpha[4] = screenAlpha(cornerX[2], cornerY[2], focusX, focusY,
                    clearAt, opaqueAt, maxAlpha);
            nodeAlpha[5] = alphaAt(v, 0.5F, 1.0F, projector, cameraPos,
                    focusX, focusY, clearAt, opaqueAt, maxAlpha);
            nodeAlpha[6] = screenAlpha(cornerX[3], cornerY[3], focusX, focusY,
                    clearAt, opaqueAt, maxAlpha);
            nodeAlpha[7] = alphaAt(v, 0.0F, 0.5F, projector, cameraPos,
                    focusX, focusY, clearAt, opaqueAt, maxAlpha);
            nodeAlpha[8] = alphaAt(v, 0.5F, 0.5F, projector, cameraPos,
                    focusX, focusY, clearAt, opaqueAt, maxAlpha);

            if (nodeAlpha[0] <= MIN_ALPHA && nodeAlpha[1] <= MIN_ALPHA
                    && nodeAlpha[2] <= MIN_ALPHA && nodeAlpha[3] <= MIN_ALPHA
                    && nodeAlpha[4] <= MIN_ALPHA && nodeAlpha[5] <= MIN_ALPHA
                    && nodeAlpha[6] <= MIN_ALPHA && nodeAlpha[7] <= MIN_ALPHA
                    && nodeAlpha[8] <= MIN_ALPHA) {
                continue;
            }

            submittedVertices += emitSubQuad(consumer, entry, v, offX, offY, offZ,
                    0.0F, 0.0F, 0.5F, 0.5F, nodeAlpha[0], nodeAlpha[1], nodeAlpha[8], nodeAlpha[7]);
            submittedVertices += emitSubQuad(consumer, entry, v, offX, offY, offZ,
                    0.5F, 0.0F, 1.0F, 0.5F, nodeAlpha[1], nodeAlpha[2], nodeAlpha[3], nodeAlpha[8]);
            submittedVertices += emitSubQuad(consumer, entry, v, offX, offY, offZ,
                    0.5F, 0.5F, 1.0F, 1.0F, nodeAlpha[8], nodeAlpha[3], nodeAlpha[4], nodeAlpha[5]);
            submittedVertices += emitSubQuad(consumer, entry, v, offX, offY, offZ,
                    0.0F, 0.5F, 0.5F, 1.0F, nodeAlpha[7], nodeAlpha[8], nodeAlpha[5], nodeAlpha[6]);
        }

        // This pass is called outside vanilla's entity draw loop, so submit this layer explicitly.
        // Otherwise the ghost vertices can remain buffered until a later frame or render target.
        buffers.draw(GHOST_LAYER);
        lastSubmittedVertexCount = submittedVertices;
    }

    private static int emitSubQuad(VertexConsumer consumer, MatrixStack.Entry entry, int firstVertex,
                                   double offX, double offY, double offZ,
                                   float s0, float t0, float s1, float t1,
                                   float a0, float a1, float a2, float a3) {
        if (a0 <= MIN_ALPHA && a1 <= MIN_ALPHA && a2 <= MIN_ALPHA && a3 <= MIN_ALPHA) {
            return 0;
        }
        writeVertex(consumer, entry, firstVertex, offX, offY, offZ, s0, t0, a0);
        writeVertex(consumer, entry, firstVertex, offX, offY, offZ, s1, t0, a1);
        writeVertex(consumer, entry, firstVertex, offX, offY, offZ, s1, t1, a2);
        writeVertex(consumer, entry, firstVertex, offX, offY, offZ, s0, t1, a3);
        return 4;
    }

    private static void writeVertex(VertexConsumer consumer, MatrixStack.Entry entry, int firstVertex,
                                    double offX, double offY, double offZ,
                                    float s, float t, float alpha) {
        float x = interpolate(firstVertex, s, t, 0);
        float y = interpolate(firstVertex, s, t, 1);
        float z = interpolate(firstVertex, s, t, 2);
        int corner = (s >= 0.5F ? 1 : 0) + (t >= 0.5F ? 2 : 0);
        int o = (firstVertex + corner) * STRIDE;
        consumer.vertex(entry.getPositionMatrix(), (float) (offX + x), (float) (offY + y),
                        (float) (offZ + z))
                .color(interpolate(firstVertex, s, t, 8), interpolate(firstVertex, s, t, 9),
                        interpolate(firstVertex, s, t, 10), alpha)
                .texture(interpolate(firstVertex, s, t, 3), interpolate(firstVertex, s, t, 4))
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(lights[firstVertex + corner])
                .normal(entry.getNormalMatrix(), geometry[o + 5], geometry[o + 6], geometry[o + 7])
                .next();
    }

    private static void emitQuad(VertexConsumer consumer, MatrixStack.Entry entry, int firstVertex,
                                 double offX, double offY, double offZ,
                                 float a0, float a1, float a2, float a3) {
        for (int i = 0; i < 4; i++) {
            int o = (firstVertex + i) * STRIDE;
            float alpha = switch (i) {
                case 0 -> a0;
                case 1 -> a1;
                case 2 -> a2;
                default -> a3;
            };
            consumer.vertex(entry.getPositionMatrix(),
                            (float) (offX + geometry[o]),
                            (float) (offY + geometry[o + 1]),
                            (float) (offZ + geometry[o + 2]))
                    .color(geometry[o + 8], geometry[o + 9], geometry[o + 10], alpha)
                    .texture(geometry[o + 3], geometry[o + 4])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(lights[firstVertex + i])
                    .normal(entry.getNormalMatrix(), geometry[o + 5], geometry[o + 6], geometry[o + 7])
                    .next();
        }
    }

    private static float screenAlpha(float x, float y, float focusX, float focusY,
                                     float clearAt, float opaqueAt, float maxAlpha) {
        float dx = x - focusX;
        float dy = y - focusY;
        return curve((float) Math.sqrt(dx * dx + dy * dy), clearAt, opaqueAt, maxAlpha);
    }

    private static float alphaAt(int firstVertex, float s, float t, Projector projector,
                                 Vec3d cameraPos, float focusX, float focusY,
                                 float clearAt, float opaqueAt, float maxAlpha) {
        double wx = originX + interpolate(firstVertex, s, t, 0);
        double wy = originY + interpolate(firstVertex, s, t, 1);
        double wz = originZ + interpolate(firstVertex, s, t, 2);
        if (!projector.project(wx, wy, wz, cameraPos, CORNER)) {
            return 0.0F;
        }
        float dx = CORNER[0] - focusX;
        float dy = CORNER[1] - focusY;
        return curve((float) Math.sqrt(dx * dx + dy * dy), clearAt, opaqueAt, maxAlpha);
    }

    private static float interpolate(int firstVertex, float s, float t, int component) {
        float w0 = (1.0F - s) * (1.0F - t);
        float w1 = s * (1.0F - t);
        float w2 = s * t;
        float w3 = (1.0F - s) * t;
        return geometry[firstVertex * STRIDE + component] * w0
                + geometry[(firstVertex + 1) * STRIDE + component] * w1
                + geometry[(firstVertex + 2) * STRIDE + component] * w2
                + geometry[(firstVertex + 3) * STRIDE + component] * w3;
    }

    // ------------------------------------------------------------------ geometry cache

    /** Drops the cache and the arrays behind it. Safe to call every frame. */
    public static void invalidate() {
        cachedMask = null;
        cachedSnapshot = null;
        cachedAtMs = 0L;
        bakeJob = null;
        vertexCount = 0;
        lastSubmittedVertexCount = 0;
        // Released rather than merely ignored: at the vertex ceiling these are several megabytes,
        // and they are static, so holding them keeps the mask and snapshot alive with them.
        geometry = EMPTY_FLOATS;
        lights = EMPTY_INTS;
    }

    /** Keeps a renderer failure from disappearing behind a loader/event-bus boundary. */
    public static void reportRenderFailure(Throwable error) {
        if (!renderErrorLogged) {
            renderErrorLogged = true;
            LOGGER.error("Ghost block rendering failed; disabling this frame", error);
        }
    }

    /** Starts or advances a bake without blocking one render pass on the whole cull set. */
    private static void ensureGeometry(MinecraftClient client, ClientWorld world,
                                       SightlineMask mask, RoomSnapshot snapshot) {
        long now = System.currentTimeMillis();
        boolean stale = mask != cachedMask
                || snapshot != cachedSnapshot
                // Block edits do not replace the mask, so without this the ghost could keep drawing
                // geometry for blocks that have since been mined.
                || now - cachedAtMs > MAX_CACHE_AGE_MS;
        if (!stale && bakeJob == null) {
            return;
        }

        if (bakeJob == null || bakeJob.mask != mask || bakeJob.snapshot != snapshot) {
            bakeJob = new BakeJob(client, world, mask, snapshot);
        }
        advanceBake(now);
    }

    /** True if this block is one the cullers removed, from either source. */
    private static boolean isRemoved(SightlineMask mask, RoomSnapshot snapshot, int x, int y, int z) {
        if (mask != null && mask.isOccluding(x, y, z)) {
            return true;
        }
        return snapshot != null && snapshot.test(x, y, z) == RoomSnapshot.CULL;
    }

    /** Advances a pending bake for a small budget and publishes it only when complete. */
    private static void advanceBake(long now) {
        BakeJob job = bakeJob;
        long deadline = System.nanoTime() + BAKE_BUDGET_NANOS;
        do {
            if (!job.nextBlock()) {
                geometry = job.baker.verts.toFloatArray();
                lights = job.baker.lights.toIntArray();
                vertexCount = job.baker.lights.size();
                originX = job.originX;
                originY = job.originY;
                originZ = job.originZ;
                cachedMask = job.mask;
                cachedSnapshot = job.snapshot;
                cachedAtMs = now;
                bakeJob = null;
                CullDebug.recordGhostBake(System.nanoTime() - job.startedNanos,
                        job.baker.fluidVertices);
                return;
            }
            long packed = job.currentBlock;
            job.baker.block(BlockPos.unpackLongX(packed),
                    BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed));
        } while (System.nanoTime() < deadline);
    }

    /** Incremental render-thread job for one culling result. */
    private static final class BakeJob {
        final SightlineMask mask;
        final RoomSnapshot snapshot;
        final Baker baker;
        final long startedNanos = System.nanoTime();
        final double originX, originY, originZ;
        final LongIterator maskBlocks;
        final LongArrayList roomBlocks;
        int roomIndex;
        long currentBlock;

        BakeJob(MinecraftClient client, ClientWorld world, SightlineMask mask,
                RoomSnapshot snapshot) {
            this.mask = mask;
            this.snapshot = snapshot;
            Vec3d anchor = client.cameraEntity != null ? client.cameraEntity.getPos() : Vec3d.ZERO;
            this.originX = Math.floor(anchor.x);
            this.originY = Math.floor(anchor.y);
            this.originZ = Math.floor(anchor.z);
            this.maskBlocks = mask == null ? null : mask.blocks().iterator();
            this.roomBlocks = new LongArrayList();
            if (snapshot != null) {
                snapshot.forEachCulledBlock((x, y, z) ->
                        this.roomBlocks.add(BlockPos.asLong(x, y, z)));
            }
            this.baker = new Baker(client, world, mask, snapshot,
                    this.originX, this.originY, this.originZ);
        }

        boolean nextBlock() {
            if (this.maskBlocks != null && this.maskBlocks.hasNext()) {
                this.currentBlock = this.maskBlocks.nextLong();
                return true;
            }
            if (this.roomIndex < this.roomBlocks.size()) {
                this.currentBlock = this.roomBlocks.getLong(this.roomIndex++);
                return true;
            }
            return false;
        }
    }

    /** Walks the cull set once and flattens the visible faces into arrays. */
    private static final class Baker {
        final MinecraftClient client;
        final ClientWorld world;
        final FloatArrayList verts = new FloatArrayList();
        final IntArrayList lights = new IntArrayList();
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        final BlockPos.Mutable neighbour = new BlockPos.Mutable();

        final SightlineMask mask;
        final RoomSnapshot snapshot;
        final FluidCaptureConsumer fluidConsumer = new FluidCaptureConsumer(this);
        int fluidVertices;

        final double originX, originY, originZ;

        Baker(MinecraftClient client, ClientWorld world, SightlineMask mask, RoomSnapshot snapshot,
              double originX, double originY, double originZ) {
            this.client = client;
            this.world = world;
            this.mask = mask;
            this.snapshot = snapshot;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }

        void block(int x, int y, int z) {
            if (this.lights.size() >= MAX_VERTICES) {
                return;
            }
            this.pos.set(x, y, z);
            BlockState state = this.world.getBlockState(this.pos);
            if (state.isAir()) {
                return;
            }

            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                this.client.getBlockRenderManager().renderFluid(this.pos, this.world,
                        this.fluidConsumer, state, fluid);
                // A waterlogged block owns both meshes. Pure fluid blocks have no separate
                // block model, while stairs, fences, signs, etc. still need their solid model.
                if (state.getBlock() instanceof FluidBlock) {
                    return;
                }
            }

            BakedModel model = this.client.getBlockRenderManager().getModel(state);
            long seed = state.getRenderingSeed(this.pos);

            for (Direction dir : Direction.values()) {
                this.neighbour.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
                // Two conditions, and both matter.
                //
                // Vanilla's own visibility test, against the unmodified world: a face buried
                // between two solid blocks was never drawn, so ghosting it would add a layer of
                // alpha that was never in the original image.
                if (!Block.shouldDrawSide(state, this.world, this.pos, dir, this.neighbour)) {
                    continue;
                }
                // And only the outer boundary of what was removed. Faces onto other removed blocks
                // are interior to the hole. In a house that is every upper storey's floor and
                // ceiling, each a fully visible surface in its own right, and each adding another
                // layer of alpha until the stack goes opaque and hides the player underneath it.
                if (isRemoved(this.mask, this.snapshot,
                        this.neighbour.getX(), this.neighbour.getY(), this.neighbour.getZ())) {
                    continue;
                }
                RANDOM.setSeed(seed);
                emit(state, model.getQuads(state, dir, RANDOM), x, y, z,
                        WorldRenderer.getLightmapCoordinates(this.world, this.neighbour));
            }

            // Direction-less quads belong to no face and are always drawn.
            RANDOM.setSeed(seed);
            emit(state, model.getQuads(state, null, RANDOM), x, y, z,
                    WorldRenderer.getLightmapCoordinates(this.world, this.pos));
        }

        void addFluidVertex(float x, float y, float z, float u, float v,
                            float nx, float ny, float nz,
                            float r, float g, float b, int light) {
            if (this.lights.size() >= MAX_VERTICES) {
                return;
            }
            this.verts.add((float) (x - this.originX));
            this.verts.add((float) (y - this.originY));
            this.verts.add((float) (z - this.originZ));
            this.verts.add(u);
            this.verts.add(v);
            this.verts.add(nx);
            this.verts.add(ny);
            this.verts.add(nz);
            this.verts.add(r);
            this.verts.add(g);
            this.verts.add(b);
            this.lights.add(light);
            this.fluidVertices++;
        }

        private void emit(BlockState state, List<BakedQuad> quads, int bx, int by, int bz, int light) {
            for (int q = 0; q < quads.size(); q++) {
                if (this.lights.size() >= MAX_VERTICES) {
                    return;
                }
                BakedQuad quad = quads.get(q);
                int[] data = quad.getVertexData();
                int stride = data.length / 4;

                Direction face = quad.getFace();
                float nx = face == null ? 0.0F : face.getOffsetX();
                float ny = face == null ? 0.0F : face.getOffsetY();
                float nz = face == null ? 0.0F : face.getOffsetZ();

                // Grass and foliage textures are stored greyscale and coloured at render time from
                // the biome, so a quad emitted at flat white comes out grey. Vanilla multiplies in
                // this tint per quad; without it the ghost desaturates exactly the blocks people
                // notice most.
                float tintR = 1.0F, tintG = 1.0F, tintB = 1.0F;
                if (quad.hasColor()) {
                    int rgb = this.client.getBlockColors()
                            .getColor(state, this.world, this.pos, quad.getColorIndex());
                    tintR = ((rgb >> 16) & 0xFF) / 255.0F;
                    tintG = ((rgb >> 8) & 0xFF) / 255.0F;
                    tintB = (rgb & 0xFF) / 255.0F;
                }
                // Directional shading, the same top-bright/side-dark falloff the chunk mesh gets.
                // Without it the ghost is flat and reads as a decal rather than as the world.
                float shade = face == null ? 1.0F : this.world.getBrightness(face, quad.hasShade());
                tintR *= shade;
                tintG *= shade;
                tintB *= shade;

                for (int i = 0; i < 4; i++) {
                    int o = i * stride;
                    this.verts.add((float) (bx + Float.intBitsToFloat(data[o]) - this.originX));
                    this.verts.add((float) (by + Float.intBitsToFloat(data[o + 1]) - this.originY));
                    this.verts.add((float) (bz + Float.intBitsToFloat(data[o + 2]) - this.originZ));
                    this.verts.add(Float.intBitsToFloat(data[o + 4]));
                    this.verts.add(Float.intBitsToFloat(data[o + 5]));
                    this.verts.add(nx);
                    this.verts.add(ny);
                    this.verts.add(nz);

                    // The quad's own baked vertex colour is packed little-endian, red in the low
                    // byte — the order vanilla reads it back in.
                    int packed = data[o + 3];
                    this.verts.add((packed & 0xFF) / 255.0F * tintR);
                    this.verts.add(((packed >> 8) & 0xFF) / 255.0F * tintG);
                    this.verts.add(((packed >> 16) & 0xFF) / 255.0F * tintB);

                    this.lights.add(light);
                }
            }
        }
    }

    /** Captures vanilla fluid vertices into the same cached ghost format as block quads. */
    private static final class FluidCaptureConsumer implements VertexConsumer {
        private final Baker owner;
        private double x, y, z;
        private float u, v;
        private float nx, ny, nz;
        private float r = 1.0F, g = 1.0F, b = 1.0F;
        private int light = 0x00F000F0;

        FluidCaptureConsumer(Baker owner) {
            this.owner = owner;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.r = red / 255.0F;
            this.g = green / 255.0F;
            this.b = blue / 255.0F;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.light = u | (v << 16);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.nx = x;
            this.ny = y;
            this.nz = z;
            return this;
        }

        @Override
        public void next() {
            this.owner.addFluidVertex((float) this.x, (float) this.y, (float) this.z,
                    this.u, this.v, this.nx, this.ny, this.nz, this.r, this.g, this.b, this.light);
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            color(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
        }
    }

    // ------------------------------------------------------------------ projection

    /**
     * World to screen, in units where 1.0 is the distance from the centre of the screen to the top
     * edge — so a circle of a given radius in these units is a circle on screen at any camera
     * angle, which a matrix-based mapping was failing to be.
     *
     * <p>Built from the camera's own basis and field of view, the same way {@code MouseMixin} turns
     * a cursor position into a world ray, so the two agree by construction. Aspect ratio cancels
     * out: it appears when normalising x into NDC and again when converting NDC back into units of
     * screen height.
     */
    private static final class Projector {
        private final Vec3d forward;
        private final Vec3d right;
        private final Vec3d up;
        private final boolean ortho;
        /** Perspective: tan(fov/2). Orthographic: half the view height, in blocks. */
        final double scale;

        Projector(Camera camera, GameRenderer gameRenderer) {
            // Camera's view direction is the rotated +Z axis in Minecraft 1.20.1. A -Z basis
            // makes every point in front of the camera look behind it and disables the ghost pass.
            Vector3d f = camera.getRotation().transform(new Vector3d(0.0, 0.0, 1.0));
            // Forge 1.20.1's local +X camera basis points to screen-left.
            Vector3d r = camera.getRotation().transform(new Vector3d(-1.0, 0.0, 0.0));
            Vector3d u = camera.getRotation().transform(new Vector3d(0.0, 1.0, 0.0));
            this.forward = new Vec3d(f.x, f.y, f.z);
            this.right = new Vec3d(r.x, r.y, r.z);
            this.up = new Vec3d(u.x, u.y, u.z);

            this.ortho = Config.GSON.instance().ortho;
            if (this.ortho) {
                // Matches Ortho.createOrthoMatrix, whose half-height is getZoom() * 2.
                this.scale = Math.max(1.0E-4, Mod.getZoom() * 2.0);
            } else {
                double fov = ((GameRendererAccessor) gameRenderer)
                        .callGetFov(camera, com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta(), true);
                this.scale = Math.max(1.0E-4, Math.tan(Math.toRadians(fov) / 2.0));
            }
        }

        /** False when the point is behind the camera, where the divide is meaningless. */
        boolean project(double wx, double wy, double wz, Vec3d cameraPos, float[] out) {
            double dx = wx - cameraPos.x;
            double dy = wy - cameraPos.y;
            double dz = wz - cameraPos.z;

            double sx = dx * this.right.x + dy * this.right.y + dz * this.right.z;
            double sy = dx * this.up.x + dy * this.up.y + dz * this.up.z;

            if (this.ortho) {
                out[0] = (float) (sx / this.scale);
                out[1] = (float) (sy / this.scale);
                return true;
            }

            double depth = dx * this.forward.x + dy * this.forward.y + dz * this.forward.z;
            if (depth <= 1.0E-4) {
                return false;
            }
            out[0] = (float) (sx / (depth * this.scale));
            out[1] = (float) (sy / (depth * this.scale));
            return true;
        }
    }

    /**
     * Fully clear out to {@code clearAt}, then smoothstep up to {@code maxAlpha} at
     * {@code opaqueAt} — both measured on screen. The flat inner section is what guarantees a
     * genuinely see-through pocket around the player rather than a gradient that is merely faint
     * there.
     */
    private static float curve(float dist, float clearAt, float opaqueAt, float maxAlpha) {
        if (dist <= clearAt) {
            return 0F;
        }
        float t = Math.min(1.0F, (dist - clearAt) / (opaqueAt - clearAt));
        // Match the original 1.21.1 fade ramp while keeping a soft transition at both ends.
        return maxAlpha * t * t * (3.0F - 2.0F * t);
    }
}
