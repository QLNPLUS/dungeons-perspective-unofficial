package com.cleannrooster.dungeons_iso.mixin.compat.sodium;

import com.cleannrooster.dungeons_iso.compat.SodiumCompat;
import com.cleannrooster.dungeons_iso.mod.Mod;


import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

@Mixin(BlockRenderer.class)
@Pseudo
public abstract class MixinBlockRenderer  {


    @Inject(at = @At("HEAD"), method = "isFaceVisible", cancellable = true,remap = false)

    private void isFaceVisibleDungeons(BlockRenderContext ctx, Direction face, CallbackInfoReturnable<Boolean> ci) {
        if (Mod.enabled) {
            ci.setReturnValue(true);
        }

    }
    @Inject(at = @At("HEAD"), method = "renderModel", cancellable = true,remap = false)

    public void renderModel(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if(Mod.enabled && SodiumCompat.shouldCull(ctx.pos(), ctx.state())) {
            ci.cancel();
        }

    }

}
