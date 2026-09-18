package com.cleannrooster.dungeons_iso.api;

import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.compat.SodiumCompat;
import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import com.cleannrooster.dungeons_iso.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side control surface for the Dungeons Perspective state.
 *
 * <p>This class intentionally owns the complete transition instead of exposing
 * {@link Mod#enabled} as the control mechanism. A transition also needs to
 * preserve the previous vanilla perspective and reset the renderer state.</p>
 */
public final class DungeonsPerspectiveApi {
    private DungeonsPerspectiveApi() {
    }

    /** Returns whether the Dungeons Perspective is currently active. */
    public static boolean isEnabled() {
        return Mod.enabled;
    }

    /**
     * Enables or disables the perspective for the local client player.
     *
     * <p>Calls made before a world/player exists are ignored. Disabling still
     * respects the existing {@code force} configuration option.</p>
     *
     * @return the resulting enabled state
     */
    public static boolean setEnabled(boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return Mod.enabled;
        }

        ClientInit.initialize();
        Mod.startupHandled = true;

        if (enabled) {
            if (Mod.enabled) {
                return true;
            }

            Mod.enabled = true;
            Mod.lastPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            if (Mod.lastPerspective == Perspective.THIRD_PERSON_FRONT) {
                Mod.yaw = ((180 + client.player.getYaw() + 180) % 360) - 180;
                Mod.pitch = -client.player.getPitch();
            } else {
                Mod.yaw = client.player.getYaw();
                Mod.pitch = client.player.getPitch();
            }

            client.mouse.lockCursor();
            InputUtil.setCursorParameters(
                    client.getWindow().getHandle(),
                    GLFW.GLFW_CURSOR_NORMAL,
                    client.mouse.getX(),
                    client.mouse.getY()
            );
            Util.debug("Enabled Minecraft XIV");
            return true;
        }

        if (Config.GSON.instance().force || !Mod.enabled) {
            return Mod.enabled;
        }

        Mod.enabled = false;
        SodiumCompat.stop();
        if (Mod.lastPerspective != null) {
            client.options.setPerspective(Mod.lastPerspective);
        }
        if (client.currentScreen == null) {
            InputUtil.setCursorParameters(
                    client.getWindow().getHandle(),
                    GLFW.GLFW_CURSOR_DISABLED,
                    client.mouse.getX(),
                    client.mouse.getY()
            );
        }
        client.mouse.lockCursor();
        Util.debug("Disabled Minecraft XIV");
        return false;
    }

    /** Toggles the perspective through the same transition used by the mod. */
    public static boolean toggle() {
        return setEnabled(!Mod.enabled);
    }
}
