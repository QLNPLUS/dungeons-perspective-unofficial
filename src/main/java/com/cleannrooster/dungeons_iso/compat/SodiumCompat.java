package com.cleannrooster.dungeons_iso.compat;

import com.cleannrooster.dungeons_iso.api.BlockCuller;
import com.cleannrooster.dungeons_iso.api.cullers.BlockDetector;
import com.cleannrooster.dungeons_iso.api.cullers.FloodCuller;
import com.cleannrooster.dungeons_iso.api.cullers.GenericCuller3;
import com.cleannrooster.dungeons_iso.api.cullers.GenericBlockCuller2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

public class SodiumCompat {
    public static List<BlockCuller> blockCullers = new ArrayList<>();
    public static BlockCuller detector = new BlockDetector();
    public static LinkedHashMap<BlockPos,BlockCuller.TransparentBlock> transparentBlocks;
    public static FloodCuller floodCuller = new FloodCuller();
    public static List<BlockPos> stream = List.of();
    static{
        blockCullers.addAll(List.of( new GenericCuller3(),floodCuller));
        transparentBlocks = new LinkedHashMap<>();
    }


    public static void run(){
        Box box = new Box(MinecraftClient.getInstance().player.getEyePos(),MinecraftClient.getInstance().gameRenderer.getCamera().getPos()).expand(1,0,1);
        if(MinecraftClient.getInstance().cameraEntity!= null) {
            stream = floodCuller.getCulledBlocks(MinecraftClient.getInstance().player.getBlockPos().up(), MinecraftClient.getInstance().gameRenderer.getCamera(), MinecraftClient.getInstance().cameraEntity);
        }
        double dub = 1*MinecraftClient.getInstance().player.getPos().distanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
        box.stretch(dub,dub,dub);
            scheduleRebuildForBlockArea(box);

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
                    (int) box.minX, (int) box.minY, (int) box.minZ,
                    (int) box.maxX, (int) box.maxY, (int) box.maxZ, true
            );
        } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
            // Keep the call isolated so a diagnostic build without the local Embeddium JAR can still compile.
        }
    }
}
