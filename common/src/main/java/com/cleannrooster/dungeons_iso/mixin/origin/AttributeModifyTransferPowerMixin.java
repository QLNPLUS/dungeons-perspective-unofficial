package com.cleannrooster.dungeons_iso.mixin.origin;

import io.github.edwinmindcraft.apoli.api.component.IPowerContainer;
import io.github.edwinmindcraft.apoli.api.power.configuration.ConfiguredModifier;
import io.github.edwinmindcraft.apoli.api.power.factory.PowerFactory;
import io.github.edwinmindcraft.apoli.common.power.AttributeModifyTransferPower;
import io.github.edwinmindcraft.apoli.common.registry.ApoliPowers;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = AttributeModifyTransferPower.class)
public abstract class AttributeModifyTransferPowerMixin {

    @Inject(
            method = "apply(Lnet/minecraft/entity/Entity;Lio/github/edwinmindcraft/apoli/api/power/factory/PowerFactory;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private static void dungeons$applyTransferPowers(
            Entity entity,
            PowerFactory<?> power,
            CallbackInfoReturnable<List<ConfiguredModifier<?>>> cir) {
        List<ConfiguredModifier<?>> transferPowers = new ArrayList<>();
        for (var holder : IPowerContainer.getPowers(entity, ApoliPowers.ATTRIBUTE_MODIFY_TRANSFER.get())) {
            transferPowers.addAll(holder.value().getFactory().apply(holder.value(), entity, power));
        }
        cir.setReturnValue(transferPowers);
    }
}
