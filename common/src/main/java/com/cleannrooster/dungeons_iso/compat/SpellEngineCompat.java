package com.cleannrooster.dungeons_iso.compat;

import net.minecraft.client.MinecraftClient;
public class SpellEngineCompat {
    public static boolean isCasting(){
        Object player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }
        try {
            Class<?> casterType = Class.forName("net.spell_engine.internals.casting.SpellCasterEntity");
            if (!casterType.isInstance(player)) {
                return false;
            }
            return (boolean) casterType.getMethod("isCastingSpell").invoke(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isHoldingStaff(){
        if (MinecraftClient.getInstance().player == null) {
            return false;
        }
        Object item = MinecraftClient.getInstance().player.getMainHandStack().getItem();
        try {
            Class<?> staffType = Class.forName("net.spell_engine.api.item.weapon.StaffItem");
            return staffType.isInstance(item);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
