package com.cleannrooster.dungeons_iso.fabric;

import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.config.ConfigScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricTerrainCullingModels.register();

        // Create key bindings then register them with Fabric's KeyBindingHelper
        ClientInit.registerKeyBindings();
        for (KeyBinding binding : ClientInit.getAllKeyBindings()) {
            KeyBindingHelper.registerKeyBinding(binding);
        }
        ClientInit.init();

        // Register /dperspective client command to open the config screen
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("dperspective")
                .then(ClientCommandManager.literal("culling_debug")
                    .then(ClientCommandManager.literal("start")
                        .executes(ctx -> startLiveLog(ctx, CullDebug.DEFAULT_LIVE_INTERVAL_MILLIS))
                        .then(ClientCommandManager.argument("interval_ms",
                                IntegerArgumentType.integer(CullDebug.MIN_LIVE_INTERVAL_MILLIS,
                                        CullDebug.MAX_LIVE_INTERVAL_MILLIS))
                            .executes(ctx -> startLiveLog(ctx,
                                    IntegerArgumentType.getInteger(ctx, "interval_ms")))))
                    .then(ClientCommandManager.literal("stop")
                        .executes(ctx -> {
                            CullDebug.stopLiveLog();
                            ctx.getSource().sendFeedback(Text.literal(CullDebug.liveStatus()));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(CullDebug.liveStatus()));
                            return 1;
                        }))
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(CullDebug.liveStatus()));
                        return 1;
                    }))
                .executes(ctx -> {
                    // ConfigScreen cannot load without YACL, so the guard stays outside it.
                    if (!Config.GSON.hasScreen()) {
                        ctx.getSource().sendError(
                                Text.translatable("dungeons_iso.config.requires_yacl"));
                        return 0;
                    }
                    MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(ConfigScreen.create(null))
                    );
                    return 1;
                }))
        );
    }

    private static int startLiveLog(
            com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
            int intervalMillis) {
        java.nio.file.Path file = CullDebug.startLiveLog(intervalMillis);
        if (file == null) {
            ctx.getSource().sendError(Text.literal(
                    "Cannot start culling debug: no client world/player or log file error"));
            return 0;
        }
        ctx.getSource().sendFeedback(Text.literal(CullDebug.liveStatus()));
        return 1;
    }
}
