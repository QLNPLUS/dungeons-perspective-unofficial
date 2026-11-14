package com.cleannrooster.dungeons_iso.mod;

import com.cleannrooster.dungeons_iso.compat.DragonCompat;
import com.cleannrooster.dungeons_iso.config.Config;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.Objects;

public class Mod {
    public static BlockState prevblock;
    public static float yaw;
    public static float pitch = 45;
    public static double x;
    public static boolean shouldReload;
    public static double flyingYAddition;
    public static boolean shouldRebuild() {
        return Mod.shouldReload && Mod.endTime < 10;
    }
    public static double z;
    public static int cooldownIs;
    public static int cooldownWas;
    public static Vector2f unModMovement = new Vector2f();
    public static int useTimer;
    public static boolean noMouse;
    // Left-stick deflection from the last input tick, in key-input convention (x = forward,
    // y = sideways/left), pre-rotation. 0 when no controller / inside deadzone. Used by the
    // facing logic so the character faces the stick direction under joystick movement.
    public static float joystickRawFwd;
    public static float joystickRawSide;
    public static float zoomMetric;
    public static float factor;
    public static float factorScale = 0.5F;
    public static int zoomOutTimeNoDelay;
    public static float zoomTimeNoDelay;
    public static float factor2;
    public static long startZoomNoDelay;
    public static boolean isBlocked;
    public static float relativeYaw;
    public static float clipMetric = 0;
    public static int blockedTime;
    public static BlockHitResult hit;
    public static boolean forward;
    public static boolean notmoving;
    public static Vec3d preMod = Vec3d.ZERO;
    public static float livingPitch;
    public static float livingBodyYaw;
    public static float livingYaw;
    public static float livingHeadYaw;

    public static float getScaledFactor() {
        return factor;
    }

    public static float getZoom() {
        float modifier = 1F;

        boolean bool = Config.GSON.instance().clipToSpace && Mod.shouldRebuild();
        if (MinecraftClient.getInstance().world != null) {

            if (bool) {
                modifier = (Math.min(2F, ((float) 1F + ((DragonCompat.bool ? (float) (Math.max(0F, (float) MinecraftClient.getInstance().world.getTime() - (float) Mod.dragonTimeSince + com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta())) : Math.max(0F, 20F - (float) MinecraftClient.getInstance().world.getTime() + (float) Mod.dragonTime + com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta()))) / 20F)))  * net.minecraft.util.math.MathHelper.clamp(Config.GSON.instance().zoomFactor, 1F, 1.5F) * Mod.zoom;

            } else {

                if (Config.GSON.instance().clipToSpace) {
                    modifier = (Math.min(2F, ((float) 1F + ((DragonCompat.bool ? (float) (Math.max(0F, (float) MinecraftClient.getInstance().world.getTime() - (float) Mod.dragonTimeSince + com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta())) : Math.max(0F, 20F - (float) MinecraftClient.getInstance().world.getTime() + (float) Mod.dragonTime + com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta()))) / 20F))) * net.minecraft.util.math.MathHelper.clamp(Config.GSON.instance().zoomFactor, 1F, 1.5F) * Mod.zoom;

                } else {
                    modifier =  Mod.zoom;

                }
            }

            if (MinecraftClient.getInstance().cameraEntity instanceof LivingEntity living) {
                modifier *= living.getScaleFactor();
            }
        }
        if(Objects.isNull(hit) || !Config.GSON.instance().clipToSpace) {
            return modifier;
        }
        return (float) Math.max(0.5F*modifier*((net.minecraft.util.math.MathHelper.clamp((Mod.clipMetric+(Mod.notmoving ? 0 :  Mod.forward ? 0.4F: -1.0F)*com.cleannrooster.dungeons_iso.util.VanillaCompat.tickDelta()),16,32))/(32)),2F);
    }

    public static float zoom = 5.0F;
    /** Published for client integrations such as KubeJS; transitions still run on the client thread. */
    public static volatile boolean enabled = false;

    /**
     * True while another client mod owns the camera (Spatial GUI's 3D screens, Cinematic
     * Interactions' workstation scenes). Refreshed once per frame by
     * {@code MinecraftClientMixin.dungeons$refreshExternalCamera}. While it is set, this mod stops
     * writing camera position, rotation, projection and crosshair targeting — those all describe a
     * view that the other mod is already placing — but keeps culling terrain, which only ever
     * removes blocks between the camera and the player.
     */
    public static volatile boolean externalCamera = false;

    /** Which mod owns the camera, for diagnostics; empty when this mod still does. */
    public static volatile String externalCameraOwner = "";

    /**
     * The single guard for every site that writes camera position, rotation or projection. When
     * another mod is placing the view, this mod must not touch it: its own writes run earlier in
     * the frame ({@code Camera.update}) and would be overwritten anyway, and writing again here
     * only produces the jump between two poses that the compatibility report described.
     */
    public static boolean cameraYielded() {
        return enabled && externalCamera;
    }

    /**
     * How long the presentation blend takes when the camera comes back.
     *
     * <p>Position and angle ease together, over one window, from the pose the partner mod left the
     * camera at to this mod's own pose. Easing only the angle left the position to snap, which
     * showed as the camera arriving at the dungeon framing and only then swivelling round to the
     * dungeon angle — two movements where there should be one. Interpolating the two together
     * moves the camera along a single arc, so entering or leaving a partner screen reads as one
     * continuous change with no intermediate pose held on screen.
     *
     * <p>The window has to outlast the partner mod's own hand-back: Spatial GUI's close animation
     * finishes and *then* releases the view, so anything shorter would start after its pose had
     * already been shown. Cinematic Interactions ramps its own mix weight down over about a second
     * and its report was fixed by the same blend.
     */
    public static final long RESUME_BLEND_MILLIS = 420L;

    private static float resumeFromYaw;
    private static float resumeFromPitch;
    private static net.minecraft.util.math.Vec3d resumeFromPos;
    private static long resumeStartMillis;
    private static boolean resuming;

    /**
     * Starts the hand-back blend from the rotation the other mod left the camera at.
     *
     * <p>Only the <em>presentation</em> is interpolated. {@link #yaw} and {@link #pitch} stay
     * authoritative and jump immediately, because movement, facing and hit targeting are all
     * derived from them and a eased camera angle with an un-eased movement direction would send
     * the player somewhere other than where the view points.
     */
    public static void beginCameraResume(float cameFromYaw, float cameFromPitch,
                                         net.minecraft.util.math.Vec3d cameFromPos) {
        if (!Config.GSON.instance().smoothCameraHandback) {
            resuming = false;
            return;
        }
        resumeFromYaw = cameFromYaw;
        resumeFromPitch = cameFromPitch;
        resumeFromPos = cameFromPos;
        resumeStartMillis = System.currentTimeMillis();
        resuming = true;
    }

    /**
     * Where the partner mod left the camera, or null when no hand-back is running.
     *
     * <p>Read only, not consumed: position and angle are both interpolated away from it, so every
     * frame of the window needs the same starting point.
     */
    public static net.minecraft.util.math.Vec3d cameraResumePos() {
        return resuming ? resumeFromPos : null;
    }

    /** True while the hand-back blend is still running. */
    public static boolean isResuming() {
        return resuming && resumeProgress() < 1.0F;
    }

    /** Eased camera yaw for this frame, or the plain target once the blend window has passed. */
    public static float blendResumeYaw(float target) {
        float progress = resumeProgress();
        if (!Config.GSON.instance().smoothCameraHandback) {
            return target;
        }
        if (progress >= 1.0F) {
            return target;
        }
        // Shortest arc. A plain lerp between, say, 170 and -170 swings the long way round and turns
        // a 20-degree hand-back into a 340-degree spin.
        float delta = MathHelper.wrapDegrees(target - resumeFromYaw);
        return resumeFromYaw + delta * progress;
    }

    /**
     * Ceiling, in degrees per tick, on how fast the player's body may turn just after a hand-back.
     *
     * <p>Spatial GUI finishes a close by pointing the player's body along its own camera, then
     * releases. This mod's facing code then rotates the body back to its aim target — a step of
     * well over a hundred degrees, applied in a single tick, which is why the model appeared to
     * spin round to face its own back and then turn forward again.
     *
     * <p>Rate-limiting the turn is enough to fix that: the same rotation still happens, but over a
     * dozen ticks, so it reads as the character turning rather than as a glitch. Only the animation
     * rate is limited; the yaw itself still reaches the target and movement is unaffected.
     */
    private static final float MAX_BODY_TURN_PER_TICK = 18.0F;
    private static final int BODY_TURN_TICKS = 14;
    private static int bodyTurnTicksLeft;

    /** Called when the view comes back, to start the turn limit. */
    public static void beginBodyTurnLimit() {
        bodyTurnTicksLeft = BODY_TURN_TICKS;
    }

    /**
     * Narrows a body-rotation step if it is too large right after a hand-back.
     *
     * @param current the rotation the body has now
     * @param target  the rotation the facing code wants
     * @return the rotation to use this tick: the target, or a step towards it while the limit runs
     */
    public static float limitBodyTurn(float current, float target) {
        if (bodyTurnTicksLeft <= 0) {
            return target;
        }
        bodyTurnTicksLeft--;
        float step = MathHelper.wrapDegrees(target - current);
        if (Math.abs(step) <= MAX_BODY_TURN_PER_TICK) {
            bodyTurnTicksLeft = 0;
            return target;
        }
        return current + MathHelper.clamp(step, -MAX_BODY_TURN_PER_TICK, MAX_BODY_TURN_PER_TICK);
    }

    /** Eased camera pitch for this frame, or the plain target once the blend window has passed. */
    public static float blendResumePitch(float target) {
        float progress = resumeProgress();
        return progress >= 1.0F ? target : MathHelper.lerp(progress, resumeFromPitch, target);
    }

    /**
     * Eased distance of the camera from its pivot, in the same window as the angle.
     *
     * <p>The camera is placed at {@code pivot + direction * (distance * zoom)}, so the distance is
     * what actually decides how far back it sits. Spatial GUI hands the view back at vanilla's
     * third-person distance — a few blocks — while this mod's is much longer, so without easing
     * this channel the camera arrived at the right angle but from the wrong place: first a jump
     * outwards, then a rotation. Moving both together is what makes the handover one movement.
     */
    public static double blendResumeDistance(double from, double to) {
        float progress = resumeProgress();
        return progress >= 1.0F ? to : MathHelper.lerp(progress, from, to);
    }

    private static float resumeProgress() {
        if (!resuming) {
            return 1.0F;
        }
        float linear = MathHelper.clamp(
                (System.currentTimeMillis() - resumeStartMillis) / (float) RESUME_BLEND_MILLIS, 0.0F, 1.0F);
        if (linear >= 1.0F) {
            resuming = false;
            return 1.0F;
        }
        // Sine ease-in-out, matching what both partner mods use for their own camera moves.
        return 0.5F - 0.5F * (float) Math.cos(Math.PI * linear);
    }
    public static Perspective lastPerspective;
    public static HitResult crosshairTarget;
    public static Entity pickedTarget;
    public static Entity targeted;

    public static HitResult prevCrosshairTarget;
    public static Entity lockOnTarget;
    public static boolean dirty;
    public static long dirtyTime;
    public static long startTime;
    public static long endTime;
    public static boolean using;
    public static boolean contextToggle;
    public static boolean rotateToggle;

    public static HitResult mouseTarget;

    // Contextual interactable-block targeting. Independent from crosshairTarget / targeted /
    // lockOnTarget: combat targets and block-interaction targets must not overwrite each other.
    public static BlockPos targetedInteractable;
    public static BlockPos pendingInteractable;
    public static int pendingInteractableTicks;

    public static BlockHitResult horizontalTarget;

    public static long dragonTimeSince;
    public static long dragonTime;
    public static boolean dragonDirty;
    public static float zoomTime;
    public static long startZoom;
    public static float zoomOutTime;
    public static boolean verticalMode;

    public static boolean isInteractable(BlockHitResult result){
        BlockPos pos = result.getBlockPos();

        return (((MinecraftClient.getInstance().world.getBlockEntity(pos) != null ||
                 MinecraftClient.getInstance().world.getBlockState(result.getBlockPos()).streamTags().anyMatch(tag ->{
                      return tag.equals(BlockTags.ALL_SIGNS) ||  tag.equals(BlockTags.ANVIL) ||  tag.equals(BlockTags.BUTTONS) ||  tag.equals(BlockTags.CAMPFIRES)
                              || tag.equals(BlockTags.CAULDRONS) || tag.equals(BlockTags.FENCE_GATES) || tag.equals(BlockTags.TRAPDOORS)  || tag.equals(BlockTags.DOORS);
                 })
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof CraftingTableBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof FurnaceBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BarrelBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BellBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BedBlock

                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BeehiveBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BlastFurnaceBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof CakeBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof HopperBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof BeaconBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof LoomBlock
                // Menu/utility workstations with no block entity (siblings of crafting table / loom).
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof StonecutterBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof CartographyTableBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof SmithingTableBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof GrindstoneBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof ComposterBlock
                // Remote toggle.
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof LeverBlock
                // Empty-hand harvest / eat.
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof SweetBerryBushBlock
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof CaveVines
                || MinecraftClient.getInstance().world.getBlockState(pos).getBlock() instanceof CandleCakeBlock

        ) && MinecraftClient.getInstance().player.getPos().distanceTo(pos.toCenterPos())<
                com.cleannrooster.dungeons_iso.util.VanillaCompat.blockInteractionRange(MinecraftClient.getInstance().player)));
    }
    public static boolean isInteractable(BlockPos result){
        return (((MinecraftClient.getInstance().world.getBlockEntity(result)) != null ||
                MinecraftClient.getInstance().world.getBlockState(result).streamTags().anyMatch(tag ->{
                    return tag.equals(BlockTags.ALL_SIGNS) ||  tag.equals(BlockTags.ANVIL) ||  tag.equals(BlockTags.BUTTONS) ||  tag.equals(BlockTags.CAMPFIRES)
                            || tag.equals(BlockTags.CAULDRONS) || tag.equals(BlockTags.FENCE_GATES) || tag.equals(BlockTags.TRAPDOORS)  || tag.equals(BlockTags.DOORS);
                })
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof CraftingTableBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof FurnaceBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof BarrelBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof BellBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof BeehiveBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof BlastFurnaceBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof CakeBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof HopperBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof BeaconBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof LoomBlock
                // Menu/utility workstations with no block entity (siblings of crafting table / loom).
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof StonecutterBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof CartographyTableBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof SmithingTableBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof GrindstoneBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof ComposterBlock
                // Remote toggle.
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof LeverBlock
                // Empty-hand harvest / eat.
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof SweetBerryBushBlock
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof CaveVines
                || MinecraftClient.getInstance().world.getBlockState(result).getBlock() instanceof CandleCakeBlock

        ) && MinecraftClient.getInstance().player.getPos().distanceTo(result.toCenterPos())<
                com.cleannrooster.dungeons_iso.util.VanillaCompat.blockInteractionRange(MinecraftClient.getInstance().player));
    }
    public static int frustrumZoom;
    public static int cooldown;

}