package com.cleannrooster.dungeons_iso.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleannrooster.dungeons_iso.mod.Mod;

import java.lang.reflect.Method;

/**
 * Detects client mods that take ownership of the camera, so this mod can stop writing it.
 *
 * <p>{@code Camera.setup} runs after {@code Camera.update}. Spatial GUI and Cinematic Interactions
 * both rewrite position, rotation and the look vectors at the tail of {@code setup}, so whatever
 * this mod puts there from {@code update} is overwritten the moment one of them is active. Rather
 * than fight them for the same fields, the perspective keeps its terrain culling but yields the
 * camera: every write site is guarded by {@link com.cleannrooster.dungeons_iso.mod.Mod#externalCamera}.
 *
 * <p>Detection is by published API only, resolved by name so neither mod becomes a dependency:
 * <ul>
 *   <li>Spatial GUI — {@code org.tastytrash.spatialGUI.api.SpatialGUIAPI.isActive()} is true while
 *       an inventory or container is being shown on its 3D plane.</li>
 *   <li>Cinematic Interactions — {@code dev.dyslexic.cinematic.client.Scene.active()} is true while
 *       a workstation scene is running.</li>
 * </ul>
 *
 * <p>Everything here is safe to call on any side: a missing class just disables that probe, and a
 * method that fails or disappears disables itself for the rest of the session instead of throwing.
 */
public final class ExternalCameraCompat {
    private static final Logger LOGGER = LogManager.getLogger("dungeons_iso/compat");

    private static final String SPATIAL_GUI_API = "org.tastytrash.spatialGUI.api.SpatialGUIAPI";
    private static final String CINEMATIC_SCENE = "dev.dyslexic.cinematic.client.Scene";

    private static Method spatialGuiActive;
    private static Method spatialGuiOpening;
    private static Method cinematicSceneActive;
    private static boolean resolved;

    /** Last reported owner, so the yield and the return are logged once each rather than per frame. */
    private static String lastOwner = "";

    private ExternalCameraCompat() {
    }

    /**
     * True when another mod is driving the camera this frame. Cheap after the first call: two
     * reflective invocations, no allocation.
     */
    public static boolean isDrivingCamera() {
        String owner = currentOwner();
        if (owner.equals(lastOwner)) {
            return !owner.isEmpty();
        }
        if (!owner.isEmpty()) {
            // The pose numbers are the evidence for how far the partner mod's transition has to
            // travel: it starts from wherever this mod left the camera, so a pulled-back dungeon
            // view makes the same 300 ms move cover far more distance than a vanilla third-person
            // camera would.
            LOGGER.info("[dungeons_iso] camera yielded to {} from yaw {} pitch {} pos {}",
                    owner, currentCameraYaw(), currentCameraPitch(), currentCameraPos());
        } else if (!lastOwner.isEmpty()) {
            LOGGER.info("[dungeons_iso] camera taken back from {} at yaw {} pitch {} pos {} -> dungeon yaw {} pitch {}",
                    lastOwner, currentCameraYaw(), currentCameraPitch(), currentCameraPos(), Mod.yaw, Mod.pitch);
            // Start the hand-back blend from wherever the other mod left the view. Without it the
            // other mod's last pose disappears and this mod's pulled-back pose appears on the
            // next frame, which reads as a cut rather than the end of their animation.
            Mod.beginCameraResume(currentCameraYaw(), currentCameraPitch(), currentCameraPosition());
        }
        lastOwner = owner;
        return !owner.isEmpty();
    }

    /**
     * The live camera rotation, used as the starting point of the hand-back blend.
     *
     * <p>Read at the moment the other mod releases the view: it is still holding the previous
     * frame's pose then, so this is the exact angle the player was looking from.
     */
    private static float currentCameraYaw() {
        try {
            return net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getYaw();
        } catch (Throwable absent) {
            // No world, no camera. The blend then starts from this mod's own target, which is the
            // same result as not blending at all.
            return Mod.yaw;
        }
    }

    private static net.minecraft.util.math.Vec3d currentCameraPosition() {
        try {
            return net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        } catch (Throwable absent) {
            return null;
        }
    }

    private static String currentCameraPos() {
        try {
            net.minecraft.util.math.Vec3d pos = currentCameraPosition();
            if (pos == null) {
                return "unknown";
            }
            net.minecraft.util.math.Vec3d eye =
                    net.minecraft.client.MinecraftClient.getInstance().player == null ? null
                            : net.minecraft.client.MinecraftClient.getInstance().player.getEyePos();
            return eye == null ? pos.toString()
                    : String.format("%.2f blocks from the eye", pos.distanceTo(eye));
        } catch (Throwable absent) {
            return "unknown";
        }
    }

    private static float currentCameraPitch() {
        try {
            return net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPitch();
        } catch (Throwable absent) {
            return Mod.pitch;
        }
    }

    /**
     * Records this frame's owner, so every guard below reads one answer taken before any camera work.
     */
    public static void captureFrame() {
        String owner = currentOwner();
        Mod.externalCameraOwner = owner;
        Mod.externalCamera = !owner.isEmpty();
        isDrivingCamera();
    }

    /** Which mod owns the camera right now, or an empty string when this mod still does. */
    public static String currentOwner() {
        if (!resolved) {
            resolve();
        }
        if (cinematicSceneActive != null && invokeBoolean(cinematicSceneActive, true)) {
            return "Cinematic Interactions";
        }
        if (spatialGuiActive != null && invokeBoolean(spatialGuiActive, false)) {
            return "Spatial GUI";
        }
        if (spatialGuiOpening != null && invokeBoolean(spatialGuiOpening, false)) {
            return "Spatial GUI";
        }
        // Deliberately NOT Spatial GUI's closing animation.
        //
        // Its close lerps towards the Camera's live position and rotation — the values this mod
        // writes — and only then releases the view. Yielding through the close made it blend
        // towards vanilla's third-person pose instead, so the close ended on one framing and this
        // mod snapped to its own on the following frame: two movements with a visible seam between
        // them. Writing our pose during the close instead means its animation lands exactly where
        // this mod is going to be, so the two are one continuous move.
        return "";
    }

    private static boolean invokeBoolean(Method method, boolean cinematic) {
        try {
            Object value = method.invoke(null);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable broken) {
            // The class moved, the signature changed, or the mod unloaded its own state mid-frame.
            // Drop the probe instead of retrying the same failure every frame.
            if (cinematic) {
                cinematicSceneActive = null;
            } else {
                spatialGuiActive = null;
            }
            LOGGER.debug("[dungeons_iso] {} camera probe disabled: {}",
                    cinematic ? "Cinematic Interactions" : "Spatial GUI", broken.toString());
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        spatialGuiActive = lookUp(SPATIAL_GUI_API, "isActive");
        spatialGuiOpening = lookUp(SPATIAL_GUI_API, "isOpeningScreen");
        cinematicSceneActive = lookUp(CINEMATIC_SCENE, "active");
        if (spatialGuiActive != null || cinematicSceneActive != null) {
            LOGGER.debug("[dungeons_iso] external camera probes: Spatial GUI {}, Cinematic Interactions {}",
                    spatialGuiActive != null ? "present" : "absent",
                    cinematicSceneActive != null ? "present" : "absent");
        }
    }

    /**
     * Resolves a static no-argument method without initialising the target class: these mods build
     * their config and renderer objects in static initialisers, and this probe can run before
     * their client entrypoint has.
     */
    private static Method lookUp(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, false, ExternalCameraCompat.class.getClassLoader());
            Method method = type.getMethod(methodName);
            return method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class
                    ? method
                    : null;
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        } catch (NoSuchMethodException | SecurityException changed) {
            LOGGER.debug("[dungeons_iso] {} has no usable {}(), ignoring it", className, methodName);
            return null;
        }
    }
}
