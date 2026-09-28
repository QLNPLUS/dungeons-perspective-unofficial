package com.cleannrooster.dungeons_iso.api.cullers.room;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import com.cleannrooster.dungeons_iso.util.EntityVisibility;
import com.cleannrooster.dungeons_iso.api.Ortho;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dumps the state of both culling tiers to chat.
 *
 * <p>Every decision in this system is invisible from inside the game: a scan that bails, a shape
 * judged unresolved and skipped, a snapshot that is correct but whose sections were never
 * re-meshed, and a disabled gate all look identical — nothing happens. This prints the gates and
 * the last outcome of each scan so those cases can be told apart in one glance.
 */
public final class CullDebug {

    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("dungeons_iso/cull");

    private static String lastLogged = null;
    private static int tickCounter = 0;
    private static volatile boolean overlayEnabled;
    private static volatile long lastCompatNanos;
    private static volatile long maxCompatNanos;
    private static volatile long lastGhostRenderNanos;
    private static volatile long maxGhostRenderNanos;
    private static volatile long lastGhostBakeNanos;
    private static volatile int visibleSections = -1;
    private static volatile int ghostFluidVertices;
    private static final AtomicInteger FLUID_CULLED = new AtomicInteger();
    private static long lastAnomalyLogNanos;
    private static volatile boolean liveLogging;
    private static volatile Path liveLogPath;
    private static volatile int liveIntervalMillis;
    private static BufferedWriter liveWriter;
    private static long liveNextSampleNanos;
    private static long liveStartedNanos;
    private static int liveSampleCount;
    private static long frameStartNanos;
    private static volatile long lastFrameNanos;
    private static volatile long maxFrameNanos;
    private static final int FRAME_TIME_SAMPLE_CAPACITY = 120;
    private static final long[] FRAME_TIME_SAMPLES = new long[FRAME_TIME_SAMPLE_CAPACITY];
    private static int frameTimeSampleCount;
    private static int frameTimeSampleIndex;
    private static int entityRenderChecksThisFrame;
    private static int entityRenderPassedThisFrame;
    private static int entityRenderRejectedThisFrame;
    private static int entityRenderRejectedNearPlayerThisFrame;
    private static int entityRenderDistanceRescuedThisFrame;
    private static int entityRenderScreenCulledThisFrame;
    private static int rejectedEntitySampleCount;
    private static int distanceRescueSampleCount;
    private static int screenCullSampleCount;
    private static final int REJECTED_ENTITY_SAMPLE_CAPACITY = 8;
    private static final int DISTANCE_RESCUE_SAMPLE_CAPACITY = 8;
    private static final int SCREEN_CULL_SAMPLE_CAPACITY = 8;
    private static final String[] REJECTED_ENTITY_SAMPLES_THIS_FRAME =
            new String[REJECTED_ENTITY_SAMPLE_CAPACITY];
    private static final String[] DISTANCE_RESCUE_SAMPLES_THIS_FRAME =
            new String[DISTANCE_RESCUE_SAMPLE_CAPACITY];
    private static final String[] SCREEN_CULL_SAMPLES_THIS_FRAME =
            new String[SCREEN_CULL_SAMPLE_CAPACITY];
    private static volatile int lastEntityRenderChecks;
    private static volatile int lastEntityRenderPassed;
    private static volatile int lastEntityRenderRejected;
    private static volatile int lastEntityRenderRejectedNearPlayer;
    private static volatile int lastEntityRenderDistanceRescued;
    private static volatile int lastEntityRenderScreenCulled;
    private static volatile String[] lastRejectedEntitySamples = new String[0];
    private static volatile String[] lastDistanceRescueSamples = new String[0];
    private static volatile String[] lastScreenCullSamples = new String[0];
    private static final DateTimeFormatter SNAPSHOT_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public static final int DEFAULT_LIVE_INTERVAL_MILLIS = 250;
    public static final int MIN_LIVE_INTERVAL_MILLIS = 100;
    public static final int MAX_LIVE_INTERVAL_MILLIS = 60000;

    private CullDebug() {
    }

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        if (overlayEnabled) {
            resetFrameMetrics();
        }
        LOG.info("Realtime culling overlay {}", overlayEnabled ? "enabled" : "disabled");
    }

    private static void resetFrameMetrics() {
        frameTimeSampleCount = 0;
        frameTimeSampleIndex = 0;
        lastFrameNanos = 0L;
        maxFrameNanos = 0L;
        frameStartNanos = 0L;
        entityRenderChecksThisFrame = 0;
        entityRenderPassedThisFrame = 0;
        entityRenderRejectedThisFrame = 0;
        entityRenderRejectedNearPlayerThisFrame = 0;
        entityRenderDistanceRescuedThisFrame = 0;
        entityRenderScreenCulledThisFrame = 0;
        rejectedEntitySampleCount = 0;
        distanceRescueSampleCount = 0;
        screenCullSampleCount = 0;
        lastEntityRenderChecks = 0;
        lastEntityRenderPassed = 0;
        lastEntityRenderRejected = 0;
        lastEntityRenderRejectedNearPlayer = 0;
        lastEntityRenderDistanceRescued = 0;
        lastEntityRenderScreenCulled = 0;
        lastRejectedEntitySamples = new String[0];
        lastDistanceRescueSamples = new String[0];
        lastScreenCullSamples = new String[0];
    }

    /** Records the final vanilla shouldRender result while the overlay or live capture is active. */
    public static void recordEntityRenderCheck(Entity entity, boolean passed,
                                               double cameraX, double cameraY, double cameraZ) {
        if (!overlayEnabled && !liveLogging) {
            return;
        }
        entityRenderChecksThisFrame++;
        if (passed) {
            entityRenderPassedThisFrame++;
        } else {
            entityRenderRejectedThisFrame++;
            if (EntityVisibility.isProtected(entity)) {
                entityRenderRejectedNearPlayerThisFrame++;
                if (rejectedEntitySampleCount < REJECTED_ENTITY_SAMPLE_CAPACITY) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    double playerDistance = client.player == null ? -1.0D
                            : Math.sqrt(entity.squaredDistanceTo(client.player));
                    double cameraDistance = Math.sqrt(
                            entity.squaredDistanceTo(cameraX, cameraY, cameraZ));
                    REJECTED_ENTITY_SAMPLES_THIS_FRAME[rejectedEntitySampleCount++] =
                            Registries.ENTITY_TYPE.getId(entity.getType())
                                    + " pos=" + entity.getBlockPos()
                                    + " playerDist=" + decimal(playerDistance)
                                    + " cameraDist=" + decimal(cameraDistance);
                }
            }
        }
    }

    public static boolean isCollectingEntityVisibility() {
        return overlayEnabled || liveLogging;
    }

    /** Records entities rejected by the tighter screen projection after conservative culling. */
    public static void recordEntityScreenCull(Entity entity) {
        if (!isCollectingEntityVisibility()) {
            return;
        }
        entityRenderScreenCulledThisFrame++;
        if (screenCullSampleCount < SCREEN_CULL_SAMPLE_CAPACITY) {
            SCREEN_CULL_SAMPLES_THIS_FRAME[screenCullSampleCount++] =
                    Registries.ENTITY_TYPE.getId(entity.getType())
                            + " pos=" + entity.getBlockPos();
        }
    }

    /** Records cases where the displaced camera alone would trip an entity's distance limit. */
    public static void recordEntityDistanceRescue(Entity entity,
                                                  double cameraDistance,
                                                  double playerDistance) {
        if (!isCollectingEntityVisibility()) {
            return;
        }
        entityRenderDistanceRescuedThisFrame++;
        if (distanceRescueSampleCount < DISTANCE_RESCUE_SAMPLE_CAPACITY) {
            DISTANCE_RESCUE_SAMPLES_THIS_FRAME[distanceRescueSampleCount++] =
                    Registries.ENTITY_TYPE.getId(entity.getType())
                            + " pos=" + entity.getBlockPos()
                            + " reason=nearest-origin"
                            + " cameraDist=" + decimal(cameraDistance)
                            + " playerDist=" + decimal(playerDistance);
        }
    }

    /** Records a pickup entity rescued by the minimum bounding-box render distance. */
    public static void recordEntitySizeDistanceRescue(Entity entity, double distance,
                                                      double vanillaRange, double extendedRange) {
        if (!isCollectingEntityVisibility()) {
            return;
        }
        entityRenderDistanceRescuedThisFrame++;
        if (distanceRescueSampleCount < DISTANCE_RESCUE_SAMPLE_CAPACITY) {
            DISTANCE_RESCUE_SAMPLES_THIS_FRAME[distanceRescueSampleCount++] =
                    Registries.ENTITY_TYPE.getId(entity.getType())
                            + " pos=" + entity.getBlockPos()
                            + " reason=small-bounds-floor"
                            + " distance=" + decimal(distance)
                            + " vanillaRange=" + decimal(vanillaRange)
                            + " adjustedRange=" + decimal(extendedRange);
        }
    }

    public static void recordCompatNanos(long nanos) {
        lastCompatNanos = nanos;
        if (nanos > maxCompatNanos) {
            maxCompatNanos = nanos;
        }
    }

    public static void recordVisibleSections(int count) {
        visibleSections = count;
    }

    public static void recordFluidCulled() {
        FLUID_CULLED.incrementAndGet();
    }

    public static void recordGhostBake(long nanos, int fluidVertices) {
        lastGhostBakeNanos = nanos;
        ghostFluidVertices = fluidVertices;
    }

    public static void recordGhostRender(long nanos) {
        lastGhostRenderNanos = nanos;
        if (nanos > maxGhostRenderNanos) {
            maxGhostRenderNanos = nanos;
        }
        maybeLogAnomaly();
    }

    /** Starts a lightweight, line-oriented capture intended to run while the camera is moving. */
    public static Path startLiveLog(int intervalMillis) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return null;
        }

        stopLiveLog();
        int interval = Math.max(MIN_LIVE_INTERVAL_MILLIS,
                Math.min(MAX_LIVE_INTERVAL_MILLIS, intervalMillis));
        try {
            Path directory = client.runDirectory.toPath().resolve("dungeons_iso-debug");
            Files.createDirectories(directory);
            Path file = directory.resolve("culling-live-"
                    + LocalDateTime.now().format(SNAPSHOT_TIME) + "-" + System.nanoTime() + ".log");
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            writer.write("# dungeons_iso live culling diagnostic");
            writer.newLine();
            writer.write("# started=" + LocalDateTime.now());
            writer.newLine();
            writer.write("# intervalMs=" + interval);
            writer.newLine();
            writer.write("# samples are lightweight and do not include per-block neighborhood dumps");
            writer.newLine();
            writer.flush();

            liveWriter = writer;
            liveLogPath = file;
            liveIntervalMillis = interval;
            liveStartedNanos = System.nanoTime();
            liveNextSampleNanos = 0L;
            liveSampleCount = 0;
            resetFrameMetrics();
            liveLogging = true;
            LOG.info("Started live culling diagnostic: intervalMs={} file={}", interval,
                    file.toAbsolutePath());
            return file;
        } catch (IOException error) {
            LOG.error("Could not start live culling diagnostic", error);
            return null;
        }
    }

    /** Stops the live capture and closes its file. Safe to call when it is already stopped. */
    public static Path stopLiveLog() {
        BufferedWriter writer = liveWriter;
        Path path = liveLogPath;
        liveLogging = false;
        liveWriter = null;
        liveNextSampleNanos = 0L;
        if (writer != null) {
            try {
                writer.write("# stopped=" + LocalDateTime.now()
                        + " samples=" + liveSampleCount);
                writer.newLine();
                writer.flush();
                writer.close();
            } catch (IOException error) {
                LOG.warn("Could not close live culling diagnostic", error);
            }
            LOG.info("Stopped live culling diagnostic: file={}", path == null ? "unknown"
                    : path.toAbsolutePath());
        }
        return path;
    }

    public static boolean isLiveLogging() {
        return liveLogging;
    }

    public static String liveStatus() {
        if (!liveLogging) {
            return "culling live debug: stopped";
        }
        return "culling live debug: running intervalMs=" + liveIntervalMillis
                + " samples=" + liveSampleCount + " file="
                + (liveLogPath == null ? "unknown" : liveLogPath.toAbsolutePath());
    }

    /** Starts frame sampling when the overlay or live capture is active. */
    public static void frameStart() {
        if (!liveLogging && !overlayEnabled) {
            return;
        }
        frameStartNanos = System.nanoTime();
        entityRenderChecksThisFrame = 0;
        entityRenderPassedThisFrame = 0;
        entityRenderRejectedThisFrame = 0;
        entityRenderRejectedNearPlayerThisFrame = 0;
        entityRenderDistanceRescuedThisFrame = 0;
        entityRenderScreenCulledThisFrame = 0;
        rejectedEntitySampleCount = 0;
        distanceRescueSampleCount = 0;
        screenCullSampleCount = 0;
    }

    /** Publishes frame timing and entity visibility counts for the completed frame. */
    public static void frameEnd() {
        long start = frameStartNanos;
        if (start == 0L || (!liveLogging && !overlayEnabled)) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        frameStartNanos = 0L;
        lastFrameNanos = elapsed;
        if (elapsed > maxFrameNanos) {
            maxFrameNanos = elapsed;
        }
        FRAME_TIME_SAMPLES[frameTimeSampleIndex] = elapsed;
        frameTimeSampleIndex = (frameTimeSampleIndex + 1) % FRAME_TIME_SAMPLE_CAPACITY;
        frameTimeSampleCount = Math.min(frameTimeSampleCount + 1, FRAME_TIME_SAMPLE_CAPACITY);
        lastEntityRenderChecks = entityRenderChecksThisFrame;
        lastEntityRenderPassed = entityRenderPassedThisFrame;
        lastEntityRenderRejected = entityRenderRejectedThisFrame;
        lastEntityRenderRejectedNearPlayer = entityRenderRejectedNearPlayerThisFrame;
        lastEntityRenderDistanceRescued = entityRenderDistanceRescuedThisFrame;
        lastEntityRenderScreenCulled = entityRenderScreenCulledThisFrame;
        lastRejectedEntitySamples = java.util.Arrays.copyOf(
                REJECTED_ENTITY_SAMPLES_THIS_FRAME, rejectedEntitySampleCount);
        lastDistanceRescueSamples = java.util.Arrays.copyOf(
                DISTANCE_RESCUE_SAMPLES_THIS_FRAME, distanceRescueSampleCount);
        lastScreenCullSamples = java.util.Arrays.copyOf(
                SCREEN_CULL_SAMPLES_THIS_FRAME, screenCullSampleCount);
    }

    private static String rollingFrameTimeSummary() {
        if (frameTimeSampleCount == 0) {
            return "warming up";
        }
        long total = 0L;
        long peak = 0L;
        for (int i = 0; i < frameTimeSampleCount; i++) {
            long sample = FRAME_TIME_SAMPLES[i];
            total += sample;
            peak = Math.max(peak, sample);
        }
        return nanosToMillis(total / frameTimeSampleCount) + "ms avg / "
                + nanosToMillis(peak) + "ms peak, last " + frameTimeSampleCount + " frames";
    }

    private static String entityRenderSummary() {
        return "checks=" + lastEntityRenderChecks
                + " pass=" + lastEntityRenderPassed
                + " reject=" + lastEntityRenderRejected
                + " near-player reject=" + lastEntityRenderRejectedNearPlayer
                + " distance-rescued=" + lastEntityRenderDistanceRescued
                + " screen-culled=" + lastEntityRenderScreenCulled;
    }

    private static String formatRejectedEntitySamples() {
        String[] samples = lastRejectedEntitySamples;
        return samples.length == 0 ? "none" : String.join(";", samples);
    }

    private static void appendRejectedEntitySamples(StringBuilder out) {
        String[] samples = lastRejectedEntitySamples;
        out.append("entityRejectNearPlayerSamples.count=").append(samples.length).append('\n');
        for (int i = 0; i < samples.length; i++) {
            out.append("entityRejectNearPlayerSample[").append(i).append("]=")
                    .append(samples[i]).append('\n');
        }
    }

    private static String formatDistanceRescueSamples() {
        String[] samples = lastDistanceRescueSamples;
        return samples.length == 0 ? "none" : String.join(";", samples);
    }

    private static void appendDistanceRescueSamples(StringBuilder out) {
        String[] samples = lastDistanceRescueSamples;
        out.append("entityDistanceRescueSamples.count=").append(samples.length).append('\n');
        for (int i = 0; i < samples.length; i++) {
            out.append("entityDistanceRescueSample[").append(i).append("]=")
                    .append(samples[i]).append('\n');
        }
    }

    private static String formatScreenCullSamples() {
        String[] samples = lastScreenCullSamples;
        return samples.length == 0 ? "none" : String.join(";", samples);
    }

    private static void appendScreenCullSamples(StringBuilder out) {
        String[] samples = lastScreenCullSamples;
        out.append("entityScreenCullSamples.count=").append(samples.length).append('\n');
        for (int i = 0; i < samples.length; i++) {
            out.append("entityScreenCullSample[").append(i).append("]=")
                    .append(samples[i]).append('\n');
        }
    }

    private static void maybeLogAnomaly() {
        if (!Config.GSON.instance().cullDebugLog && !overlayEnabled) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastAnomalyLogNanos < 1_000_000_000L) {
            return;
        }

        SightlineMask mask = SightlineScanner.INSTANCE.mask();
        boolean suspicious = lastGhostRenderNanos >= 12_000_000L
                || lastCompatNanos >= 8_000_000L
                || SectionRebuildQueue.INSTANCE.size() >= 32
                || (mask != null && mask.blockCount() >= 7000)
                || (visibleSections == 0 && Mod.shouldRebuild());
        if (!suspicious) {
            return;
        }

        lastAnomalyLogNanos = now;
        RoomSnapshot room = RoomScanner.INSTANCE.snapshot();
        LOG.warn("culling anomaly: fps={} compat={}ms ghost={}ms bake={}ms visibleSections={} queue={} "
                        + "room={}cols/{}sec shape={}blocks/{}sec fluidCulled={} ghostFluidVerts={} "
                        + "enabled={} rebuild={} blocked={} camera={}",
                MinecraftClient.getInstance().getCurrentFps(), nanosToMillis(lastCompatNanos),
                nanosToMillis(lastGhostRenderNanos), nanosToMillis(lastGhostBakeNanos), visibleSections,
                SectionRebuildQueue.INSTANCE.size(), room == null ? 0 : room.columnCount(),
                room == null ? 0 : room.sections().size(), mask == null ? 0 : mask.blockCount(),
                mask == null ? 0 : mask.sections().size(), FLUID_CULLED.get(), ghostFluidVertices,
                Mod.enabled, Mod.shouldRebuild(), Mod.isBlocked, Mod.preMod);
    }

    private static String nanosToMillis(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }

    /** Draws a compact live panel when the existing culling-debug key toggles it on. */
    public static void renderOverlay(DrawContext context) {
        if (!overlayEnabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        RoomSnapshot room = RoomScanner.INSTANCE.snapshot();
        SightlineMask mask = SightlineScanner.INSTANCE.mask();
        int maskBlocks = mask == null ? 0 : mask.blockCount();
        int maskSections = mask == null ? 0 : mask.sections().size();
        int roomColumns = room == null ? 0 : room.columnCount();
        int roomSections = room == null ? 0 : room.sections().size();
        int queue = SectionRebuildQueue.INSTANCE.size();

        String[] lines = {
                "dungeons_iso debug",
                "FPS " + client.getCurrentFps() + " | frame " + rollingFrameTimeSummary(),
                "entity shouldRender " + entityRenderSummary(),
                "compat " + nanosToMillis(lastCompatNanos)
                        + "ms (max " + nanosToMillis(maxCompatNanos) + "ms)",
                "ghost " + nanosToMillis(lastGhostRenderNanos) + "ms (max "
                        + nanosToMillis(maxGhostRenderNanos) + "ms) bake "
                        + nanosToMillis(lastGhostBakeNanos) + "ms",
                "visible sections " + visibleSections + " | rebuild queue " + queue
                        + " (drain " + SectionRebuildQueue.INSTANCE.lastDrained() + "/tick)",
                "room " + roomColumns + " columns / " + roomSections + " sections"
                        + " | shape " + maskBlocks + " blocks / " + maskSections + " sections",
                "ghost vertices " + GhostRenderer.lastVertexCount + " -> "
                        + GhostRenderer.lastSubmittedVertexCount + " | fluid ghost " + ghostFluidVertices,
                "fluid culled " + FLUID_CULLED.get() + " | enabled " + Mod.enabled
                        + " rebuild " + Mod.shouldRebuild() + " blocked " + Mod.isBlocked,
                "camera " + (client.gameRenderer.getCamera() == null
                        ? "none" : client.gameRenderer.getCamera().getPos())
                        + " | zoom " + Mod.getZoom() + " ortho " + Config.GSON.instance().ortho
        };

        int y = 4;
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? 0xFFFFAA00 : overlayColor(i, queue, maskBlocks, visibleSections);
            context.drawTextWithShadow(client.textRenderer, lines[i], 4, y, color);
            y += 10;
        }
    }

    private static int overlayColor(int line, int queue, int maskBlocks, int visible) {
        if ((line == 4 && lastGhostRenderNanos >= 12_000_000L)
                || (line == 5 && queue >= 32)
                || (line == 6 && maskBlocks >= 7000)
                || (line == 5 && visible == 0 && Mod.shouldRebuild())) {
            return 0xFFFF5555;
        }
        return 0xFFE0E0E0;
    }

    /** Saves a self-contained report for a visual-culling failure. Client thread only. */
    public static Path saveSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return null;
        }

        try {
            Path directory = client.runDirectory.toPath().resolve("dungeons_iso-debug");
            Files.createDirectories(directory);
            Path file = directory.resolve("culling-"
                    + LocalDateTime.now().format(SNAPSHOT_TIME) + ".txt");
            Files.writeString(file, buildSnapshot(client), StandardCharsets.UTF_8);
            client.player.sendMessage(Text.literal("dungeons_iso debug saved: " + file), false);
            LOG.info("Saved culling diagnostic snapshot to {}", file.toAbsolutePath());
            return file;
        } catch (IOException error) {
            LOG.error("Could not save culling diagnostic snapshot", error);
            client.player.sendMessage(Text.literal("dungeons_iso debug save failed: "
                    + error.getMessage()), false);
            return null;
        }
    }

    private static String buildSnapshot(MinecraftClient client) {
        ClientWorld world = client.world;
        BlockPos playerPos = client.player.getBlockPos();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera == null ? Vec3d.ZERO : camera.getPos();
        RoomSnapshot room = RoomScanner.INSTANCE.snapshot();
        SightlineMask mask = SightlineScanner.INSTANCE.mask();

        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("dungeons_iso culling diagnostic\n");
        out.append("captured=").append(LocalDateTime.now()).append('\n');
        out.append("minecraft=1.20.1 Forge branch=1.20.1-another\n");
        out.append("dimension=").append(world.getRegistryKey().getValue()).append('\n');
        out.append("player.pos=").append(client.player.getPos())
                .append(" block=").append(playerPos)
                .append(" eyeY=").append(client.player.getEyeY()).append('\n');
        out.append("camera.pos=").append(cameraPos)
                .append(" entity=").append(client.cameraEntity == null
                        ? "null" : client.cameraEntity.getPos()).append('\n');
        out.append("camera.yaw=").append(Mod.yaw)
                .append(" pitch=").append(Mod.pitch)
                .append(" zoom=").append(Mod.getZoom())
                .append(" zoomMetric=").append(Mod.zoomMetric)
                .append(" preMod=").append(Mod.preMod).append('\n');
        out.append("gates.enabled=").append(Mod.enabled)
                .append(" shouldReload=").append(Mod.shouldReload)
                .append(" shouldRebuild=").append(Mod.shouldRebuild())
                .append(" blocked=").append(Mod.isBlocked)
                .append(" endTime=").append(Mod.endTime).append('\n');

        out.append("config.roomCulling=").append(Config.GSON.instance().roomCulling)
                .append(" shapeCulling=").append(Config.GSON.instance().shapeCulling)
                .append(" ortho=").append(Config.GSON.instance().ortho)
                .append(" frustumCulling=").append(Config.GSON.instance().frustumCulling)
                .append(" orthoFrustumMinScale=")
                .append(Mod.enabled && Config.GSON.instance().ortho
                        ? Ortho.CULLING_MIN_SCALE : 0.0F)
                .append(" disableOcclusion=").append(Config.GSON.instance().disableOcclusionCulling)
                .append(" terrainSilhouette=").append(Config.GSON.instance().terrainSilhouetteCulling)
                .append(" dilation=").append(Config.GSON.instance().terrainSilhouetteDilation)
                .append(" maxCulled=").append(Config.GSON.instance().sightlineMaxCulledBlocks)
                .append(" sectionsPerTick=").append(Config.GSON.instance().roomSectionsPerTick)
                .append(" ghost=").append(Config.GSON.instance().ghostCulledBlocks).append('\n');

        out.append("room.active=").append(RoomScanner.INSTANCE.isActive())
                .append(" result=").append(RoomScanner.INSTANCE.lastResult)
                .append(" columns=").append(room == null ? 0 : room.columnCount())
                .append(" sections=").append(room == null ? 0 : room.sections().size())
                .append(" airColumns=").append(RoomScanner.INSTANCE.lastAirColumns)
                .append(" openSky=").append(RoomScanner.INSTANCE.lastOpenSkyColumns).append('\n');
        out.append("shape.active=").append(SightlineScanner.INSTANCE.isActive())
                .append(" result=").append(SightlineScanner.INSTANCE.lastResult)
                .append(" blocks=").append(mask == null ? 0 : mask.blockCount())
                .append(" sections=").append(mask == null ? 0 : mask.sections().size())
                .append(" visibleFraction=").append(mask == null ? -1 : mask.visibleFraction())
                .append(" rays=").append(SightlineScanner.INSTANCE.lastRays)
                .append(" clearRays=").append(SightlineScanner.INSTANCE.lastClearRays)
                .append(" shapes=").append(SightlineScanner.INSTANCE.lastShapesFound)
                .append(" culledShapes=").append(SightlineScanner.INSTANCE.lastShapesCulled)
                .append(" underground=").append(SightlineScanner.INSTANCE.lastUnderground)
                .append(" broadUnderground=").append(SightlineScanner.INSTANCE.lastBroadUnderground)
                .append(" dilation=").append(SightlineScanner.INSTANCE.lastEffectiveDilation)
                .append(" maxBlocks=").append(SightlineScanner.INSTANCE.lastEffectiveBlockBudget)
                .append(" playerGroundProtected=").append(SightlineScanner.INSTANCE.lastPlayerGroundProtected)
                .append(" classes=").append(SightlineScanner.INSTANCE.lastShapeClasses).append('\n');

        out.append("renderer.visibleSections=").append(visibleSections)
                .append(" rebuildQueue=").append(SectionRebuildQueue.INSTANCE.size())
                .append(" drainedLastTick=").append(SectionRebuildQueue.INSTANCE.lastDrained())
                .append(" fluidCulled=").append(FLUID_CULLED.get()).append('\n');
        out.append("timing.compatMs=").append(nanosToMillis(lastCompatNanos))
                .append(" compatMaxMs=").append(nanosToMillis(maxCompatNanos))
                .append(" frameMs=").append(nanosToMillis(lastFrameNanos))
                .append(" frameMaxMs=").append(nanosToMillis(maxFrameNanos))
                .append(" entityChecks=").append(lastEntityRenderChecks)
                .append(" entityPass=").append(lastEntityRenderPassed)
                .append(" entityReject=").append(lastEntityRenderRejected)
                .append(" nearPlayerReject=").append(lastEntityRenderRejectedNearPlayer)
                .append(" distanceRescued=").append(lastEntityRenderDistanceRescued)
                .append(" screenCulled=").append(lastEntityRenderScreenCulled)
                .append(" ghostMs=").append(nanosToMillis(lastGhostRenderNanos))
                .append(" ghostMaxMs=").append(nanosToMillis(maxGhostRenderNanos))
                .append(" ghostBakeMs=").append(nanosToMillis(lastGhostBakeNanos))
                .append(" ghostVertices=").append(GhostRenderer.lastVertexCount)
                .append(" submittedVertices=").append(GhostRenderer.lastSubmittedVertexCount)
                .append(" fluidGhostVertices=").append(ghostFluidVertices).append('\n');

        appendRejectedEntitySamples(out);
        appendDistanceRescueSamples(out);
        appendScreenCullSamples(out);

        appendNeighborhood(out, world, "player", playerPos, 6, 6);
        appendNeighborhood(out, world, "camera",
                BlockPos.ofFloored(cameraPos.x, cameraPos.y, cameraPos.z), 4, 4);
        return out.toString();
    }

    private static void appendNeighborhood(StringBuilder out, ClientWorld world, String name,
                                           BlockPos center, int radius, int verticalRadius) {
        out.append('\n').append("--- neighborhood " + name + " center=")
                .append(center).append(" radius=").append(radius)
                .append(" vertical=").append(verticalRadius).append(" ---\n");
        out.append("legend .=air #=block F=fluid R=roomCull S=shapeCull X=both ?=unloaded\n");

        for (int y = center.getY() + verticalRadius; y >= center.getY() - verticalRadius; y--) {
            out.append("Y=").append(y).append('\n');
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                    out.append(marker(world, x, y, z));
                }
                out.append('\n');
            }
        }

        out.append("states (non-air):\n");
        for (int y = center.getY() + verticalRadius; y >= center.getY() - verticalRadius; y--) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    int room = RoomScanner.INSTANCE.test(x, y, z);
                    boolean shape = SightlineScanner.INSTANCE.shouldCull(x, y, z);
                    out.append(pos).append(" state=").append(state)
                            .append(" fluid=").append(state.getFluidState())
                            .append(" room=").append(room)
                            .append(" shape=").append(shape).append('\n');
                }
            }
        }
    }

    private static char marker(ClientWorld world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return '?';
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (state == null) {
            return '?';
        }
        boolean room = RoomScanner.INSTANCE.test(x, y, z) == RoomSnapshot.CULL;
        boolean shape = SightlineScanner.INSTANCE.shouldCull(x, y, z);
        if (room && shape) {
            return 'X';
        }
        if (room) {
            return 'R';
        }
        if (shape) {
            return 'S';
        }
        if (!state.getFluidState().isEmpty()) {
            return 'F';
        }
        return state.isAir() ? '.' : '#';
    }

    /**
     * Logs a one-line summary whenever the culling state changes, and once a second while nothing
     * is being culled. Goes quiet as soon as both tiers report a healthy snapshot, so it costs
     * nothing once things work. Called every client tick.
     */
    public static void tickLog() {
        writeLiveSampleIfDue();
        // Off unless asked for. This never falls silent on its own — the scan counter advances
        // every second, so the summary always differs from the last one and always logs.
        if (!Config.GSON.instance().cullDebugLog) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }

        RoomScanner room = RoomScanner.INSTANCE;
        SightlineScanner sight = SightlineScanner.INSTANCE;
        RoomSnapshot snap = room.snapshot();
        SightlineMask mask = sight.mask();

        boolean healthy = snap != null && mask != null
                && "ok".equals(room.lastResult) && "ok".equals(sight.lastResult);

        String summary = "enabled=" + Mod.enabled
                + " shouldRebuild=" + Mod.shouldRebuild()
                + " (shouldReload=" + Mod.shouldReload + " endTime=" + Mod.endTime + ")"
                + " | room active=" + room.isActive() + " scans=" + room.scanCount
                + " snap=" + (snap == null ? "none" : snap.columnCount() + "col/" + snap.sections().size() + "sec")
                + " last=[" + room.lastResult + "]"
                + " air=" + room.lastAirColumns + " sky=" + room.lastOpenSkyColumns
                + " | shape active=" + sight.isActive() + " casts=" + sight.castCount
                + " mask=" + (mask == null ? "none" : mask.blockCount() + "blk/" + mask.sections().size() + "sec")
                + " last=[" + sight.lastResult + "]"
                + " shapes=" + sight.lastShapesFound + " unresolved=" + sight.lastShapesIncomplete
                + "[cap=" + sight.lastUnresolvedByCap + " span=" + sight.lastUnresolvedBySpan
                + " height=" + sight.lastUnresolvedByHeight + "]"
                + " fellback=" + sight.lastShapesFellBack
                + " culled=" + sight.lastShapesCulled + " " + sight.lastShapeClasses
                + " | ghost verts=" + GhostRenderer.lastVertexCount
                + "/submitted=" + GhostRenderer.lastSubmittedVertexCount
                + "/fluid=" + ghostFluidVertices
                + " | queue=" + SectionRebuildQueue.INSTANCE.size()
                + " drained=" + SectionRebuildQueue.INSTANCE.lastDrained()
                + " | visible=" + visibleSections
                + " | compat=" + nanosToMillis(lastCompatNanos) + "ms"
                + " ghost=" + nanosToMillis(lastGhostRenderNanos) + "ms"
                + " fluidCulled=" + FLUID_CULLED.get();

        if (healthy && summary.equals(lastLogged)) {
            return;
        }
        if (!summary.equals(lastLogged)) {
            lastLogged = summary;
            LOG.info(summary);
        } else if (!healthy) {
            LOG.info(summary);
        }
    }

    private static void writeLiveSampleIfDue() {
        if (!liveLogging || liveWriter == null) {
            return;
        }
        long now = System.nanoTime();
        if (now < liveNextSampleNanos) {
            return;
        }
        liveNextSampleNanos = now + liveIntervalMillis * 1_000_000L;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        try {
            RoomSnapshot room = RoomScanner.INSTANCE.snapshot();
            SightlineMask mask = SightlineScanner.INSTANCE.mask();
            Camera camera = client.gameRenderer.getCamera();
            Vec3d cameraPos = camera == null ? Vec3d.ZERO : camera.getPos();
            int playerY = client.player.getBlockPos().getY();
            long elapsedNanos = now - liveStartedNanos;

            StringBuilder sample = new StringBuilder(900);
            sample.append("sample=").append(++liveSampleCount)
                    .append(" elapsedMs=").append(nanosToMillis(elapsedNanos))
                    .append(" fps=").append(client.getCurrentFps())
                    .append(" frameMs=").append(nanosToMillis(lastFrameNanos))
                    .append(" frameMaxMs=").append(nanosToMillis(maxFrameNanos))
                    .append(" entityChecks=").append(lastEntityRenderChecks)
                    .append(" entityPass=").append(lastEntityRenderPassed)
                    .append(" entityReject=").append(lastEntityRenderRejected)
                    .append(" nearPlayerReject=").append(lastEntityRenderRejectedNearPlayer)
                    .append(" distanceRescued=").append(lastEntityRenderDistanceRescued)
                    .append(" screenCulled=").append(lastEntityRenderScreenCulled)
                    .append(" rejectedSamples=").append(formatRejectedEntitySamples())
                    .append(" rescuedSamples=").append(formatDistanceRescueSamples())
                    .append(" screenCullSamples=").append(formatScreenCullSamples())
                    .append(" ortho=").append(Config.GSON.instance().ortho)
                    .append(" orthoFrustumMinScale=")
                    .append(Mod.enabled && Config.GSON.instance().ortho
                            ? Ortho.CULLING_MIN_SCALE : 0.0F)
                    .append(" compatMs=").append(nanosToMillis(lastCompatNanos))
                    .append(" ghostMs=").append(nanosToMillis(lastGhostRenderNanos))
                    .append(" bakeMs=").append(nanosToMillis(lastGhostBakeNanos))
                    .append(" visibleSections=").append(visibleSections)
                    .append(" rebuildQueue=").append(SectionRebuildQueue.INSTANCE.size())
                    .append(" drained=").append(SectionRebuildQueue.INSTANCE.lastDrained())
                    .append(" playerY=").append(playerY)
                    .append(" cameraY=").append(decimal(cameraPos.y))
                    .append(" cameraDeltaY=").append(decimal(cameraPos.y - client.player.getY()))
                    .append(" cameraDistance=").append(decimal(
                            cameraPos.distanceTo(client.player.getPos())))
                    .append(" yaw=").append(Mod.yaw)
                    .append(" pitch=").append(Mod.pitch)
                    .append(" roomBlocks=").append(room == null ? 0 : room.columnCount())
                    .append(" roomSections=").append(room == null ? 0 : room.sections().size())
                    .append(" roomBelowBlocks=").append(room == null ? 0
                            : room.culledBlockCountBelow(playerY))
                    .append(" roomBelowSections=").append(room == null ? 0
                            : room.culledSectionCountBelow(playerY))
                    .append(" shapeBlocks=").append(mask == null ? 0 : mask.blockCount())
                    .append(" shapeSections=").append(mask == null ? 0 : mask.sections().size())
                    .append(" shapeBelowBlocks=").append(mask == null ? 0
                            : mask.blockCountBelow(playerY))
                    .append(" shapeBelowSections=").append(mask == null ? 0
                            : mask.sectionCountBelow(playerY))
                    .append(" underground=").append(SightlineScanner.INSTANCE.lastUnderground)
                    .append(" broadUnderground=").append(SightlineScanner.INSTANCE.lastBroadUnderground)
                    .append(" shapeResult=").append(sanitize(SightlineScanner.INSTANCE.lastResult))
                    .append(" roomResult=").append(sanitize(RoomScanner.INSTANCE.lastResult))
                    .append(" ghostVertices=").append(GhostRenderer.lastVertexCount)
                    .append(" submittedVertices=").append(GhostRenderer.lastSubmittedVertexCount)
                    .append(" fluidGhostVertices=").append(ghostFluidVertices)
                    .append(" fluidCulled=").append(FLUID_CULLED.get())
                    .append(" shouldReload=").append(Mod.shouldReload)
                    .append(" blocked=").append(Mod.isBlocked)
                    .append(" enabled=").append(Mod.enabled);
            liveWriter.write(sample.toString());
            liveWriter.newLine();
            liveWriter.flush();
        } catch (IOException error) {
            LOG.error("Live culling diagnostic stopped because the log could not be written", error);
            stopLiveLog();
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "none";
        }
        return value.replace(' ', '_').replace('\n', '_').replace('\r', '_');
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    public static void report() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        line(client, Formatting.GOLD, "── dungeons_iso culling ──");

        // Gates first: all three must hold before either tier does anything at all.
        boolean rebuild = Mod.shouldRebuild();
        gate(client, "Mod.enabled", Mod.enabled);
        gate(client, "shouldRebuild (camera blocked)", rebuild);
        if (!rebuild) {
            line(client, Formatting.GRAY,
                    "   shouldReload=" + Mod.shouldReload + " endTime=" + Mod.endTime);
        }

        // Tier 1 — room roof.
        RoomScanner room = RoomScanner.INSTANCE;
        RoomSnapshot snap = room.snapshot();
        line(client, Formatting.AQUA, "Room culling");
        gate(client, "  config roomCulling", Config.GSON.instance().roomCulling);
        gate(client, "  active", room.isActive());
        line(client, Formatting.GRAY, "   scans=" + room.scanCount + "  last: " + room.lastResult);
        line(client, Formatting.GRAY, "   air columns=" + room.lastAirColumns
                + "  open sky=" + room.lastOpenSkyColumns
                + "  final columns=" + room.lastFinalColumns
                + "  sections=" + room.lastSectionCount);
        line(client, snap == null ? Formatting.RED : Formatting.GREEN,
                "   snapshot: " + (snap == null
                        ? "none published"
                        : snap.columnCount() + " columns, " + snap.sections().size()
                                + " sections, origin " + snap.origin().toShortString()));

        // Tier 2 — shape culling.
        SightlineScanner sight = SightlineScanner.INSTANCE;
        SightlineMask mask = sight.mask();
        line(client, Formatting.AQUA, "Shape culling");
        gate(client, "  config shapeCulling", Config.GSON.instance().shapeCulling);
        gate(client, "  active", sight.isActive());
        line(client, Formatting.GRAY, "   casts=" + sight.castCount + "  last: " + sight.lastResult);
        line(client, Formatting.GRAY, "   rays=" + sight.lastRays + " (clear " + sight.lastClearRays + ")"
                + "  shapes=" + sight.lastShapesFound
                + "  unresolved=" + sight.lastShapesIncomplete
                + "  below threshold=" + sight.lastShapesBelowThreshold
                + "  culled=" + sight.lastShapesCulled
                + "  underground=" + sight.lastUnderground
                + "  broadUnderground=" + sight.lastBroadUnderground
                + "  dilation=" + sight.lastEffectiveDilation
                + "  maxBlocks=" + sight.lastEffectiveBlockBudget
                + "  playerGroundProtected=" + sight.lastPlayerGroundProtected);
        line(client, mask == null ? Formatting.RED : Formatting.GREEN,
                "   mask: " + (mask == null
                        ? "none published"
                        : mask.blockCount() + " blocks, " + mask.sections().size() + " sections, visible "
                                + String.format("%.2f", mask.visibleFraction())
                                + (mask.suppressesCulling() ? " (suppressed)" : "")));

        // If the snapshots are right but nothing changed on screen, the backlog is the culprit.
        line(client, Formatting.YELLOW, "Rebuild queue: " + SectionRebuildQueue.INSTANCE.size()
                + " sections pending, " + Config.GSON.instance().roomSectionsPerTick + "/tick");
        line(client, Formatting.YELLOW, "Performance: fps=" + client.getCurrentFps()
                + " frame=" + rollingFrameTimeSummary()
                + " entity shouldRender " + entityRenderSummary()
                + " compat=" + nanosToMillis(lastCompatNanos) + "ms"
                + " ghost=" + nanosToMillis(lastGhostRenderNanos) + "ms"
                + " visible sections=" + visibleSections
                + " fluid culled=" + FLUID_CULLED.get());
    }

    private static void gate(MinecraftClient client, String name, boolean value) {
        line(client, value ? Formatting.GREEN : Formatting.RED, (value ? "  ✔ " : "  ✘ ") + name);
    }

    private static void line(MinecraftClient client, Formatting colour, String text) {
        client.player.sendMessage(Text.literal(text).formatted(colour), false);
    }
}
