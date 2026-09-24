package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientWorld.class)
public abstract class ForgeClientWorldSoundMixin {

    @Shadow
    private void playSound(double x, double y, double z, SoundEvent soundEvent,
                           SoundCategory category, float volume, float pitch,
                           boolean useDistance, long seed) {
        throw new AssertionError("Mixin shadow");
    }

    // Both packet sounds and locally played positional sounds reach this call.
    // Dispatch after Forge's PlayLevelSoundEvent but before camera-based culling
    // in ClientWorld's private playSound method.
    @Redirect(
            method = {
                    "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V",
                    "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZJ)V")
    )
    private void dungeons_iso$dispatchPositionalSound(ClientWorld world, double x, double y, double z,
                                                       SoundEvent soundEvent, SoundCategory category,
                                                       float volume, float pitch, boolean useDistance,
                                                       long seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Mod.enabled || client.player == null || client.gameRenderer == null) {
            playSound(x, y, z, soundEvent, category, volume, pitch, useDistance, seed);
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) {
            playSound(x, y, z, soundEvent, category, volume, pitch, useDistance, seed);
            return;
        }
        Vec3d playerPosition = client.player.getEyePos();
        Vec3d cameraPosition = camera.getPos();
        Vec3d listenerPosition = cameraPosition.add(
                playerPosition.subtract(cameraPosition).multiply(Config.GSON.instance().soundListenerBias));
        double listenerDistanceSquared = listenerPosition.squaredDistanceTo(x, y, z);

        PositionedSoundInstance sound = new PositionedSoundInstance(
                soundEvent, category, volume, pitch, Random.create(seed), x, y, z);
        if (useDistance && listenerDistanceSquared > 100.0) {
            client.getSoundManager().play(sound, (int) (Math.sqrt(listenerDistanceSquared) / 2.0));
        } else {
            client.getSoundManager().play(sound);
        }
    }
}
