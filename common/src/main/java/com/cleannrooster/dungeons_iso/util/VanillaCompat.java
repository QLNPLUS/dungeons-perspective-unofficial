package com.cleannrooster.dungeons_iso.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.Vec3d;

/** Small 1.20.1 equivalents for names introduced by later Minecraft versions. */
public final class VanillaCompat {
    private VanillaCompat() {
    }

    public static float tickDelta() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? 0.0F : client.getTickDelta();
    }

    public static Vec3d movement(Entity entity) {
        return entity.getVelocity();
    }

    public static float scale(LivingEntity entity) {
        return entity.getScaleFactor();
    }

    /** 1.20.1 has no common ProjectileItem interface for throwable items. */
    public static boolean isProjectileItem(Item item) {
        return item instanceof RangedWeaponItem
                || item instanceof TridentItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderPearlItem
                || item instanceof PotionItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof ExperienceBottleItem
                || item instanceof FireworkRocketItem;
    }

    public static double blockInteractionRange(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.interactionManager != null) {
            return client.interactionManager.getReachDistance();
        }
        return 4.5D;
    }

    public static double entityInteractionRange(PlayerEntity player) {
        return blockInteractionRange(player);
    }

    public static boolean canInteractWithEntity(PlayerEntity player, Entity entity, double additionalRange) {
        double range = entityInteractionRange(player) + additionalRange;
        return player.getEyePos().squaredDistanceTo(entity.getBoundingBox().getCenter()) <= range * range;
    }

    public static boolean canInteractWithBlock(PlayerEntity player, net.minecraft.util.math.BlockPos pos, double additionalRange) {
        double range = blockInteractionRange(player) + additionalRange;
        return player.getEyePos().squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }
}
