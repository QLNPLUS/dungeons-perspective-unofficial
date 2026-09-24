package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundExecutor;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundListener;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class ForgeSoundSystemMixin {

    @Shadow private boolean started;
    @Shadow private SoundListener listener;
    @Shadow private SoundExecutor taskQueue;

    @Inject(method = "updateListenerPosition", at = @At("HEAD"), cancellable = true)
    private void dungeons_iso$repositionListener(Camera camera, CallbackInfo ci) {
        if (!Mod.enabled || !started || !camera.isReady()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        dungeons_iso$queueListenerUpdate(camera, client);
        ci.cancel();
    }

    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/Channel;createSource(Lnet/minecraft/client/sound/SoundEngine$RunMode;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private void dungeons_iso$updateListenerBeforeSoundStarts(SoundInstance sound, CallbackInfo ci) {
        if (sound.isRelative() || !Mod.enabled || !started) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        dungeons_iso$queueListenerUpdate(camera, client);
    }

    private void dungeons_iso$queueListenerUpdate(Camera camera, MinecraftClient client) {
        Config config = Config.GSON.instance();
        Vec3d playerPosition = client.player.getEyePos();
        Vec3d listenerPosition = camera.getPos().add(
                playerPosition.subtract(camera.getPos()).multiply(config.soundListenerBias));
        Vector3f forward = new Vector3f(camera.getHorizontalPlane());
        Vector3f up = new Vector3f(camera.getVerticalPlane());

        taskQueue.execute(() -> {
            listener.setPosition(listenerPosition);
            listener.setOrientation(forward, up);
        });
    }
}
