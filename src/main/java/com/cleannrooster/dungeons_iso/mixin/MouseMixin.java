package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.api.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.ElytraSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

@Mixin(Mouse.class)
public class MouseMixin implements MouseAccessor {
    @Shadow(remap = false)
    private boolean f_91520_;
    @Shadow(remap = false)
    private boolean f_91506_;

    @Shadow(remap = false)
    @Final
    private MinecraftClient f_91503_;
    @Unique
    private MinecraftClient client;
    @SuppressWarnings("unused")
    @Shadow(remap = false)
    private boolean f_91511_;
    @Shadow(remap = false)
    private double f_91507_;
    @Shadow(remap = false)
    private double f_91508_;


    private static int lockontime;
    @Unique
    @Nullable
    private Double lastX;
    @Unique
    @Nullable
    private Double lastY;
    @Unique
    private boolean cameraDragActive;
    @Unique
    private double cameraDragLastX;
    @Unique
    private double cameraDragLastY;

    @Inject(
            method = "lockCursor",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void lockCursorXIV(CallbackInfo info) {
        this.client = this.f_91503_;

        if (client.isWindowFocused()) {
            if (!f_91520_) {
                if (!MinecraftClient.IS_SYSTEM_MAC) {
                    KeyBinding.updatePressedStates();
                }

                f_91520_ = true;

                if (Mod.enabled) {
                    // Merely hide the cursor instead of "disabling" it
                    InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_NORMAL, f_91507_, f_91508_);
                } else {
                    InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_DISABLED, f_91507_, f_91508_);

                    f_91507_ = client.getWindow().getWidth() / 2.0;
                    f_91508_ = client.getWindow().getHeight() / 2.0;
                }

                client.setScreen(null);

                // This has protected access and i don't wanna AW it lmao hope this works
                //client.attackCooldown = 10000;

                f_91511_ = true;
            }
        }
    }

    /**
     * It doesn't make sense to "unlock" the cursor of an absolute pointing device.
     *
     * @author quaternary
     */
    @Inject(
            method = "unlockCursor",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void unlockCursorXIV(CallbackInfo info) {
        this.client = this.f_91503_;
        if (f_91520_) {
            f_91520_ = false;
            if (!Mod.enabled) {
                f_91507_ = client.getWindow().getWidth() / 2.0;
                f_91508_ = client.getWindow().getHeight() / 2.0;
            }
            InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_NORMAL, f_91507_, f_91508_);
        }
    }


    @Inject(
            method = "updateMouse",
            at = @At(value = "INVOKE", target = "net/minecraft/client/tutorial/TutorialManager.onUpdateMouse(DD)V")
    )
    private void updateMouseAXIV(
            CallbackInfo ci
    ) {
        this.client = this.f_91503_;
        GameRenderer renderer = client.gameRenderer;
        Window window = client.getWindow();
        Mouse mouse = client.mouse;

        Camera camera = renderer.getCamera();
        float tickDelta = client.getTickDelta();
        Entity cameraEntity = client.cameraEntity;
        boolean spell = false;

        if (Mod.enabled && cameraEntity != null && client.player != null) {
                boolean cameraDragging = client.options.pickItemKey.isPressed() || ClientInit.moveCameraBinding.isPressed();
                if (cameraDragging) {
                    if (!cameraDragActive) {
                        cameraDragActive = true;
                        cameraDragLastX = f_91507_;
                        cameraDragLastY = f_91508_;
                    }

                    // Keep the normal cursor mode and apply only the movement since the last event.
                    double dragDeltaX = f_91507_ - cameraDragLastX;
                    cameraDragLastX = f_91507_;
                    cameraDragLastY = f_91508_;
                    Mod.yaw += (float) (dragDeltaX / 8.0D);
                    Mod.pitch = 45;
                    Mod.crosshairTarget = null;
                } else {
                    if (cameraDragActive) {
                        cameraDragActive = false;
                    }

                    if(Mod.horizontalTarget != null){
                        if (lastX == null || lastY == null) {

                            lastX = f_91507_;
                            lastY = f_91508_;
                        }
                        if(lastY != null) {
                            int invertY = client.options.getInvertYMouse().getValue() ? -1 : 1;
                            Mod.horizontalTarget = new BlockHitResult(Mod.lastVertical.getPos().add(0, ((-(f_91508_ - lastY) * invertY) / 40), 0), Mod.lastVertical.getSide(), BlockPos.ofFloored(Mod.lastVertical.getPos().add(0, ((-(f_91508_ - lastY) * invertY) / 40), 0)), true);

                        }

                    }
                    else
                    if (lastX != null && lastY != null) {
                        InputUtil.setCursorParameters(
                                client.getWindow().getHandle(),
                                GLFW.GLFW_CURSOR_NORMAL,
                                lastX != null ? lastX : f_91507_,
                                lastY != null ? lastY : f_91508_
                        );
                        f_91507_ = lastX;
                        f_91508_ = lastY;
                        lastX = null;
                        lastY = null;
                    }

                    Vector2d res = new Vector2d(window.getFramebufferWidth(), window.getFramebufferHeight());
                    double aspect = res.x / res.y;
                    Vector2d coords = new Vector2d(mouse.getX(), mouse.getY()).div(res).mul(2.0).sub(new Vector2d(1.0));
                    double fov2 =
                            Math.toRadians(((GameRendererAccessor) renderer).callGetFov(camera, tickDelta, true)) / 2.0;
                    coords.x *= aspect;
                    coords.y = -coords.y;
                    Vector2d offsets = coords.mul(Math.tan(fov2));
                    Vector3d forward = camera.getRotation().transform(new Vector3d(0.0, 0.0, 1.0));
                    Vector3d right = camera.getRotation().transform(new Vector3d(-1.0, 0.0, 0.0));
                    Vector3d up = camera.getRotation().transform(new Vector3d(0.0, 1.0, 0.0));
                    Vector3d dir = forward.add(right.mul(offsets.x).add(up.mul(offsets.y))).normalize();
                    Vector3d orth =  camera.getRotation().transform(new Vector3d(0.0, 0.0, 1.0)).normalize();

                    Vec3d rayDir =  Config.GSON.instance().ortho ? new Vec3d(orth.x,orth.y,orth.z):new Vec3d(dir.x, dir.y, dir.z);
                    Box box = Box.of(Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),2,2,2)
                            .stretch(rayDir.multiply(renderer.getFarPlaneDistance()*2))
                            .expand(1.0, 1.0, 1.0);
                    Vec3d end = (Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos()).add(rayDir.multiply(renderer.getFarPlaneDistance()*2));
                    if(cameraEntity instanceof PlayerEntity player && player.isFallFlying()){


                        if(((CameraAccessor)camera).getPosBeforeModulation() != null){
                            HitResult hitResult0 = raycast(Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),end,new RaycastContextCull(
                                    Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),
                                    end,
                                    CustomShapeTypes.AIR_AT_LEVEL,
                                    RaycastContext.ShapeType.OUTLINE,
                                    RaycastContext.FluidHandling.NONE,
                                    cameraEntity
                            ),(innerContext, pos) -> {
                                BlockState blockState = client.player.getWorld().getBlockState(pos);
                                FluidState fluidState = client.player.getWorld().getFluidState(pos);
                                Vec3d vec3d = innerContext.getStart();
                                Vec3d vec3d2 = innerContext.getEnd();
                                VoxelShape voxelShape = innerContext.getBlockShape(blockState, client.player.getWorld(), pos);


                                BlockHitResult firstResult = voxelShape.raycast(vec3d, vec3d2, pos);

                                VoxelShape voxelShape2 = innerContext.getFluidShape(fluidState, client.player.getWorld(), pos);
                                BlockHitResult blockHitResult2 = voxelShape2.raycast(vec3d, vec3d2, pos);
                                double d = firstResult == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(firstResult.getPos());
                                double e = blockHitResult2 == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult2.getPos());
                                return d <= e ? firstResult : blockHitResult2;
                            }, (innerContext) -> {
                                Vec3d vec3d = innerContext.getStart().subtract(innerContext.getEnd());
                                return BlockHitResult.createMissed(innerContext.getEnd(), Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), BlockPos.ofFloored(innerContext.getEnd()));
                            });
                            if(hitResult0 instanceof BlockHitResult result) {
                                Mod.flyingYAddition = -2*Math.log(result.getPos().distanceTo(player.getEyePos()) / 8F);
                            }
                            end = hitResult0.getPos().add(0,Mod.flyingYAddition,0);

                        }
                    }


                    HitResult hitResult0 = raycast(Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),end,new RaycastContextCull(
                            Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),
                            end,
                            CustomShapeTypes.CULLED,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            cameraEntity
                    ),(innerContext, pos) -> {
                        BlockState blockState = client.player.getWorld().getBlockState(pos);
                        FluidState fluidState = client.player.getWorld().getFluidState(pos);
                        Vec3d vec3d = innerContext.getStart();
                        Vec3d vec3d2 = innerContext.getEnd();
                        VoxelShape voxelShape = innerContext.getBlockShape(blockState, client.player.getWorld(), pos);
                        BlockHitResult firstResult = client.world.raycastBlock(vec3d, vec3d2, pos, voxelShape, blockState);


                        VoxelShape voxelShape2 = innerContext.getFluidShape(fluidState, client.player.getWorld(), pos);
                        BlockHitResult blockHitResult2 = voxelShape2.raycast(vec3d, vec3d2, pos);
                        double d = firstResult == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(firstResult.getPos());
                        double e = blockHitResult2 == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult2.getPos());
                        return d <= e ? firstResult : blockHitResult2;
                    }, (innerContext) -> {
                        Vec3d vec3d = innerContext.getStart().subtract(innerContext.getEnd());
                        return BlockHitResult.createMissed(innerContext.getEnd(), Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), BlockPos.ofFloored(innerContext.getEnd()));
                    });
                    BlockHitResult scanUp = client.player.getWorld().raycast(
                            new RaycastContext(
                                    hitResult0.getPos(),Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,client.player)

                    );
                    BlockHitResult scanDown = client.player.getWorld().raycast(
                            new RaycastContext(
                                    scanUp.getPos(),scanUp.getPos().add(hitResult0.getPos().subtract(scanUp.getPos()).normalize().multiply(renderer.getFarPlaneDistance()*2)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,client.player)

                    );

                    if(Mod.crosshairTarget == null){
                        Mod.crosshairTarget = client.crosshairTarget;
                    }
                    HitResult hitResult = raycastExpanded(
                            cameraEntity,
                            scanDown.getPos(),
                            Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),
                            box,
                            entity -> !entity.isSpectator() && entity.canHit(),
                            renderer.getFarPlaneDistance()*2
                    ,0.5F);
                    HitResult hitResult2 = raycastExpanded(
                            cameraEntity,
                            Config.GSON.instance().ortho ? camera.getPos().add(new Vec3d(camera.getDiagonalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.x).multiply(-0.72)).add(new Vec3d(camera.getVerticalPlane()).multiply(Mod.zoomMetric*Mod.multiplyByZoom(Mod.zoom)).multiply(coords.y).multiply(0.72)) :camera.getPos(),
                            end,
                            box,
                            entity -> !entity.isSpectator() && entity.canHit(),
                            renderer.getFarPlaneDistance()*2
                            ,0.5F);
                    if (!(hitResult instanceof EntityHitResult result && result.getType().equals(HitResult.Type.ENTITY))) {
                        if ( !(hitResult2 instanceof EntityHitResult result2 && result2.getType().equals(HitResult.Type.ENTITY) && cameraEntity instanceof PlayerEntity player2 && player2.canSee(((EntityHitResult) hitResult2).getEntity()))) {

                            hitResult = scanDown;
                            if(cameraEntity instanceof PlayerEntity player && hitResult.getPos().distanceTo(cameraEntity.getEyePos()) > 4.5 && cameraEntity.getWorld().getBlockEntity(BlockPos.ofFloored(hitResult.getPos())) == null
                                    && !(cameraEntity.getWorld().getBlockState(BlockPos.ofFloored(hitResult.getPos())).getBlock() instanceof WallMountedBlock)
                                    && !(cameraEntity.getWorld().getBlockState(BlockPos.ofFloored(hitResult.getPos())).getBlock() instanceof DoorBlock)
                                    && !(cameraEntity.getWorld().getBlockState(BlockPos.ofFloored(hitResult.getPos())).getBlock() instanceof TrapdoorBlock)) {
                                hitResult = new BlockHitResult(new Vec3d(scanDown.getPos().getX(), player.getEyeY(), scanDown.getPos().getZ()), scanDown.getSide(),BlockPos.ofFloored(scanDown.getPos().getX(), cameraEntity.getEyeY(), scanDown.getPos().getZ()),false);
                            }
                        }
                        else{
                            if(hitResult2 != null) {
                                hitResult = hitResult2;
                            }

                        }

                    }
                    if(ClientInit.verticalBinding.wasPressed()){
                        Mod.verticalMode = !Mod.verticalMode;
                        Mod.horizontalTarget = Mod.verticalMode ? scanDown : null;
                        Mod.lastVertical = Mod.verticalMode ? scanDown : null;

                        if(Mod.verticalMode){
                            client.player.sendMessage(Text.translatable("Vertical look mode activated (Default Keybind: RIGHT ALT)"), true);
                        }
                    }



                    Mod.crosshairTarget = Mod.horizontalTarget != null ? Mod.horizontalTarget :  hitResult;

                }



        }
    }

    private static <T, C> T raycast(Vec3d start, Vec3d end, C context, BiFunction<C, BlockPos, T> blockHitFactory, Function<C, T> missFactory) {
        if (start.equals(end)) {
            return missFactory.apply(context);
        } else {
            double d = MathHelper.lerp(-1.0E-7, end.x, start.x);
            double e = MathHelper.lerp(-1.0E-7, end.y, start.y);
            double f = MathHelper.lerp(-1.0E-7, end.z, start.z);
            double g = MathHelper.lerp(-1.0E-7, start.x, end.x);
            double h = MathHelper.lerp(-1.0E-7, start.y, end.y);
            double i = MathHelper.lerp(-1.0E-7, start.z, end.z);
            int j = MathHelper.floor(g);
            int k = MathHelper.floor(h);
            int l = MathHelper.floor(i);
            BlockPos.Mutable mutable = new BlockPos.Mutable(j, k, l);
            T object = blockHitFactory.apply(context, mutable);
            if (object != null) {
                return object;
            } else {
                double m = d - g;
                double n = e - h;
                double o = f - i;
                int p = MathHelper.sign(m);
                int q = MathHelper.sign(n);
                int r = MathHelper.sign(o);
                double s = p == 0 ? Double.MAX_VALUE : (double)p / m;
                double t = q == 0 ? Double.MAX_VALUE : (double)q / n;
                double u = r == 0 ? Double.MAX_VALUE : (double)r / o;
                double v = s * (p > 0 ? 1.0 - MathHelper.fractionalPart(g) : MathHelper.fractionalPart(g));
                double w = t * (q > 0 ? 1.0 - MathHelper.fractionalPart(h) : MathHelper.fractionalPart(h));
                double x = u * (r > 0 ? 1.0 - MathHelper.fractionalPart(i) : MathHelper.fractionalPart(i));

                Object object2;
                do {
                    if (!(v <= 1.0) && !(w <= 1.0) && !(x <= 1.0)) {
                        return missFactory.apply(context);
                    }

                    if (v < w) {
                        if (v < x) {
                            j += p;
                            v += s;
                        } else {
                            l += r;
                            x += u;
                        }
                    } else if (w < x) {
                        k += q;
                        w += t;
                    } else {
                        l += r;
                        x += u;
                    }

                    object2 = blockHitFactory.apply(context, mutable.set(j, k, l));
                } while(object2 == null);

                return (T) object2;
            }
        }
    }
    @Nullable
     private static EntityHitResult raycastExpanded(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate, double maxDistance, float margin) {
        World world = entity.getWorld();
        double d = maxDistance;
        Entity entity2 = null;
        Vec3d vec3d = null;
        Iterator var12 = world.getOtherEntities(entity, box, predicate).iterator();

        while(true) {
            while(var12.hasNext()) {
                Entity entity3 = (Entity)var12.next();
                Box box2 = entity3.getBoundingBox().expand((double)entity3.getTargetingMargin()+margin);
                Optional<Vec3d> optional = box2.raycast(min, max);
                if (box2.contains(min)) {
                    if (d >= 0.0) {
                        entity2 = entity3;
                        vec3d = (Vec3d)optional.orElse(min);
                        d = 0.0;
                    }
                } else if (optional.isPresent()) {
                    Vec3d vec3d2 = (Vec3d)optional.get();
                    double e = min.squaredDistanceTo(vec3d2);
                    if (e < d || d == 0.0) {
                        if (entity3.getRootVehicle() == entity.getRootVehicle()) {
                            if (d == 0.0) {
                                entity2 = entity3;
                                vec3d = vec3d2;
                            }
                        } else {
                            entity2 = entity3;
                            vec3d = vec3d2;
                            d = e;
                        }
                    }
                }
            }

            if (entity2 == null || (entity instanceof LivingEntity living && !living.canSee(entity2)) || entity2.isInvisible() || (entity instanceof PlayerEntity player && entity2.isInvisibleTo(player))) {
                return null;
            }
            if(Mod.crosshairTarget instanceof EntityHitResult result && entity2.equals(result.getEntity())){
                lockontime++;
            }
            else{
                lockontime = 0;
            }
            lockontime = Math.min(160,lockontime);
            return new EntityHitResult(entity2, ClientInit.lockOn.isPressed() ? vec3d : new Vec3d(
                    MathHelper.lerp((double)lockontime/160D,entity2.getBoundingBox().getCenter().getX(),entity2.getX()),
                    MathHelper.lerp((double)lockontime/160D,entity2.getBoundingBox().getCenter().getY(), Math.min(entity2.getY() + entity2.getHeight(), entity2.getY() + entity2.getHeight()/2 + 0.1*entity2.getHeight()*Math.log(Math.max(1,entity.distanceTo(entity2))))),
                    MathHelper.lerp((double)lockontime/160D,entity2.getBoundingBox().getCenter().getZ(),entity2.getZ())));
        }
    }
    @Inject(
            method = {"updateMouse"}, at = {
            @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/network/ClientPlayerEntity.changeLookDirection(DD)V"
            )
    }, cancellable = true
    )
    private void updateMouseBXIV(CallbackInfo info) {
        if (Mod.enabled) {
            info.cancel();
        }
    }



    @Redirect(method = "onMouseScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"))


    private void scrollInHotbarXIV(PlayerInventory instance, double scrollAmount) {

            if (Mod.enabled && Config.GSON.instance().scrollWheelZoom ) {


                Mod.adjustZoom(-(float) scrollAmount * 0.2F, 5.0F);

            } else {

                instance.scrollInHotbar(scrollAmount);


            }


        }

    @Override
    public void setRightClick(boolean bool) {
        f_91506_ = bool;
    }
}
