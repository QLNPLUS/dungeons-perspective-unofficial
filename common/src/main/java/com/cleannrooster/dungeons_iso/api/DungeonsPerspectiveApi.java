package com.cleannrooster.dungeons_iso.api;

import com.cleannrooster.dungeons_iso.api.cullers.room.CullingBackdrop;
import com.cleannrooster.dungeons_iso.api.cullers.room.GhostRenderer;
import com.cleannrooster.dungeons_iso.api.cullers.room.RoomScanner;
import com.cleannrooster.dungeons_iso.api.cullers.room.SectionRebuildQueue;
import com.cleannrooster.dungeons_iso.api.cullers.room.SightlineScanner;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Client API for integrations such as KubeJS.
 *
 * <p>Put scripts that call this class in a client-side script set. Calls made from another thread
 * are queued onto Minecraft's client thread before changing camera, culling, or render state.</p>
 */
public final class DungeonsPerspectiveApi {

    private DungeonsPerspectiveApi() {
    }

    /** Enables or disables the perspective. The transition is applied on the client thread. */
    public static void setEnabled(boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            apply(client, enabled);
        } else {
            client.execute(() -> apply(client, enabled));
        }
    }

    /** Toggles the perspective on the client thread. */
    public static void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            apply(client, !Mod.enabled);
        } else {
            client.execute(() -> apply(client, !Mod.enabled));
        }
    }

    /** Returns the current published state. */
    public static boolean isEnabled() {
        return Mod.enabled;
    }

    private static void apply(MinecraftClient client, boolean enabled) {
        if (Mod.enabled == enabled) {
            return;
        }

        if (enabled) {
            if (client.world == null || client.player == null) {
                return;
            }

            Perspective previous = client.options.getPerspective();
            Mod.lastPerspective = previous;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            if (previous == Perspective.THIRD_PERSON_FRONT) {
                Mod.yaw = ((180.0F + client.player.getYaw() + 180.0F) % 360.0F) - 180.0F;
                Mod.pitch = -client.player.getPitch();
            } else {
                Mod.yaw = client.player.getYaw();
                Mod.pitch = client.player.getPitch();
            }
            Mod.enabled = true;
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            client.worldRenderer.reload();
            return;
        }

        // Disable culling before rebuilding meshes. This makes every subsequent terrain query
        // return the normal-world answer while the reload restores sections removed by the mask.
        Mod.enabled = false;
        RoomScanner.INSTANCE.setActive(false);
        SightlineScanner.INSTANCE.setActive(false);
        SectionRebuildQueue.INSTANCE.clear();
        GhostRenderer.invalidate();
        CullingBackdrop.reset();

        if (client.world != null) {
            client.worldRenderer.reload();
        }
        if (Mod.lastPerspective != null) {
            client.options.setPerspective(Mod.lastPerspective);
            Mod.lastPerspective = null;
        }
    }
}
