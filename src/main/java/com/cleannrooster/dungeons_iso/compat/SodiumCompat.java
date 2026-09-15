package com.cleannrooster.dungeons_iso.compat;

import com.cleannrooster.dungeons_iso.api.BlockCuller;
import com.cleannrooster.dungeons_iso.api.cullers.BlockDetector;
import com.cleannrooster.dungeons_iso.api.cullers.FloodCuller;
import com.cleannrooster.dungeons_iso.api.cullers.GenericCuller3;
import com.cleannrooster.dungeons_iso.api.cullers.GenericBlockCuller2;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SodiumCompat {
    public static List<BlockCuller> blockCullers = new ArrayList<>();
    public static BlockCuller detector = new BlockDetector();
    public static LinkedHashMap<BlockPos,BlockCuller.TransparentBlock> transparentBlocks;
    public static FloodCuller floodCuller = new FloodCuller();
    public static List<BlockPos> stream = List.of();
    private static final ThreadLocal<BuildContext> buildContext = new ThreadLocal<>();
    private static volatile CullingSnapshot activeSnapshot;
    private static boolean cullingSessionActive;
    static{
        blockCullers.addAll(List.of( new GenericCuller3(),floodCuller));
        transparentBlocks = new LinkedHashMap<>();
    }


    public static void run(){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.gameRenderer.getCamera() == null) {
            stop();
            return;
        }
        if (!cullingSessionActive) {
            Mod.startTime = client.world.getTime();
            cullingSessionActive = true;
        }
        Mod.shouldReload = true;
        Mod.endTime = 20;

        if(client.cameraEntity != null) {
            stream = floodCuller.getCulledBlocks(
                    client.player.getBlockPos().up(),
                    client.gameRenderer.getCamera(),
                    client.cameraEntity
            );
        }
        CullingSnapshot nextSnapshot = CullingSnapshot.capture(
                client.gameRenderer.getCamera(),
                client.cameraEntity,
                stream
        );
        boolean cullingStateChanged = activeSnapshot == null || !activeSnapshot.matches(nextSnapshot);
        activeSnapshot = nextSnapshot;

        // Culling is evaluated while Embeddium compiles render sections. Cover both
        // sides of the player/camera path so a section does not keep an old mesh after
        // the camera crosses a chunk boundary or changes direction.
        if (cullingStateChanged) {
            Box box = new Box(client.player.getEyePos(), client.gameRenderer.getCamera().getPos())
                    .expand(16.0, 16.0, 16.0);
            scheduleRebuildForBlockArea(box);
        }

/*        if(MinecraftClient.getInstance() != null  && Mod.enabled && ((MinecraftClientAccessor)MinecraftClient.getInstance()).shouldRebuild()) {
            if (MinecraftClient.getInstance().cameraEntity != null && MinecraftClient.getInstance().gameRenderer.getCamera() instanceof Camera camera) {

        for(BlockPos pos: BlockPos.iterate(
                BlockPos.ofFloored((int) box.minX - 8*2, (int) box.minY - 8*2, (int) box.minZ - 8*2),
                BlockPos.ofFloored((int) box.maxX + 8*2, (int) box.maxY + 8*2, (int) box.maxZ + 8*2))){

                    for (BlockCuller culler : SodiumCompat.blockCullers) {
                        if(culler.cullBlocks(pos, camera, MinecraftClient.getInstance().cameraEntity)){
                            BlockCuller.TransparentBlock block =  SodiumCompat.transparentBlocks.get(pos);
                            if( block != null) {
                                block.tickTransparency();
                            }



                        }
                        else{
                            BlockCuller.TransparentBlock block =  SodiumCompat.transparentBlocks.get(pos);
                            if( block != null) {
                                block.tickOpacity();
                            }

                        }



                    }

                }
            }
        }*/



        for(BlockCuller culler :blockCullers) {
            if(MinecraftClient.getInstance().player.age % culler.frequency() == 0) {
                culler.resetCulledBlocks();
            }


        }
    }

    private static void scheduleRebuildForBlockArea(Box box) {
        try {
            Class<?> rendererClass = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Object renderer = rendererClass.getMethod("instance").invoke(null);
            rendererClass.getMethod(
                    "scheduleRebuildForBlockArea",
                    int.class, int.class, int.class, int.class, int.class, int.class, boolean.class
            ).invoke(
                    renderer,
                    (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                    (int) Math.ceil(box.maxX), (int) Math.ceil(box.maxY), (int) Math.ceil(box.maxZ), true
            );
        } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
            // Keep the call isolated so a diagnostic build without the local Embeddium JAR can still compile.
        }
    }

    public static void stop() {
        cullingSessionActive = false;
        activeSnapshot = null;
        buildContext.remove();
        Mod.shouldReload = false;
        Mod.endTime = 0;
        Mod.dirty = false;
    }

    public static void beginChunkBuild() {
        CullingSnapshot snapshot = activeSnapshot;
        if (snapshot == null) {
            buildContext.remove();
        } else {
            buildContext.set(new BuildContext(snapshot));
        }
    }

    public static void endChunkBuild() {
        buildContext.remove();
    }

    public static boolean shouldCull(BlockPos blockPos, BlockState blockState) {
        return shouldCull(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockState);
    }

    /**
     * Answers culling queries against the immutable snapshot captured for one Embeddium build.
     * The result is cached per build because both the model renderer and neighbour-face tests can
     * ask about the same block several times.
     */
    public static boolean shouldCull(int x, int y, int z, BlockState blockState) {
        BuildContext context = buildContext.get();
        if (context == null || blockState == null) {
            return false;
        }

        long key = BlockPos.asLong(x, y, z);
        if (context.cullCache.containsKey(key)) {
            return context.cullCache.get(key);
        }

        BlockPos blockPos = new BlockPos(x, y, z);
        GenericCuller3 genericCuller = (GenericCuller3) blockCullers.get(0);
        boolean culled = genericCuller.shouldCull(blockPos, blockState, context.snapshot)
                || floodCuller.isAboveFlood(x, y, z, context.snapshot.floodColumnMinY);
        context.cullCache.put(key, culled);
        return culled;
    }

    private static final class BuildContext {
        private final CullingSnapshot snapshot;
        private final Long2BooleanOpenHashMap cullCache = new Long2BooleanOpenHashMap();

        private BuildContext(CullingSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    public static final class CullingSnapshot {
        public final Vec3d cameraPos;
        public final Vec3d entityPos;
        public final long worldTime;
        public final long startTime;
        public final long endTime;
        public final float zoom;
        public final List<BlockPos> floodBlocks;
        public final Long2IntOpenHashMap floodColumnMinY;

        private CullingSnapshot(Vec3d cameraPos, Vec3d entityPos, long worldTime, long startTime,
                                long endTime, float zoom, List<BlockPos> floodBlocks) {
            this.cameraPos = cameraPos;
            this.entityPos = entityPos;
            this.worldTime = worldTime;
            this.startTime = startTime;
            this.endTime = endTime;
            this.zoom = zoom;
            this.floodBlocks = List.copyOf(floodBlocks);
            this.floodColumnMinY = new Long2IntOpenHashMap();
            this.floodColumnMinY.defaultReturnValue(Integer.MAX_VALUE);
            for (BlockPos block : floodBlocks) {
                long column = columnKey(block.getX(), block.getZ());
                int current = this.floodColumnMinY.get(column);
                if (block.getY() < current) {
                    this.floodColumnMinY.put(column, block.getY());
                }
            }
        }

        private static CullingSnapshot capture(Camera camera, Entity cameraEntity, List<BlockPos> floodBlocks) {
            Vec3d entityPos = cameraEntity == null ? camera.getPos() : cameraEntity.getPos();
            long worldTime = cameraEntity == null ? 0L : cameraEntity.getWorld().getTime();
            return new CullingSnapshot(
                    camera.getPos(),
                    entityPos,
                    worldTime,
                    Mod.startTime,
                    Mod.endTime,
                    Mod.zoom,
                    floodBlocks
            );
        }

        private boolean matches(CullingSnapshot other) {
            return cameraPos.squaredDistanceTo(other.cameraPos) < 0.25
                    && entityPos.squaredDistanceTo(other.entityPos) < 0.25
                    && zoom == other.zoom
                    && transitionTicks() == other.transitionTicks()
                    && floodBlocks.equals(other.floodBlocks);
        }

        private long transitionTicks() {
            long elapsedTicks = Math.max(0L, worldTime - startTime);
            return Math.min(20L, Math.min(elapsedTicks, Math.max(0L, endTime)));
        }
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
