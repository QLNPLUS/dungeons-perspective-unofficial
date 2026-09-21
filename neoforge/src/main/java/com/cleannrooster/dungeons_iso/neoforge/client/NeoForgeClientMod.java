package com.cleannrooster.dungeons_iso.neoforge.client;

import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.cleannrooster.dungeons_iso.config.ConfigScreen;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.server.command.CommandManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = "dungeons_iso", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NeoForgeClientMod {

    /**
     * Key bindings must be registered here on NeoForge — this event fires before
     * FMLClientSetupEvent, and NeoForge marks key mappings as "already processed"
     * if you try to register them any later.
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientInit.registerKeyBindings();
        for (KeyBinding binding : ClientInit.getAllKeyBindings()) {
            event.register(binding);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientInit::init);
    }
}

/** Separate subscriber on the FORGE bus for game-phase events (commands). */
@EventBusSubscriber(modid = "dungeons_iso", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
class NeoForgeClientGameEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            CommandManager.literal("dperspective")
                .then(CommandManager.literal("culling_debug")
                    .then(CommandManager.literal("start")
                        .executes(ctx -> startLiveLog(ctx, CullDebug.DEFAULT_LIVE_INTERVAL_MILLIS))
                        .then(CommandManager.argument("interval_ms",
                                IntegerArgumentType.integer(CullDebug.MIN_LIVE_INTERVAL_MILLIS,
                                        CullDebug.MAX_LIVE_INTERVAL_MILLIS))
                            .executes(ctx -> startLiveLog(ctx,
                                    IntegerArgumentType.getInteger(ctx, "interval_ms")))))
                    .then(CommandManager.literal("stop")
                        .executes(ctx -> {
                            CullDebug.stopLiveLog();
                            ctx.getSource().sendSuccess(() -> net.minecraft.text.Text.literal(
                                    CullDebug.liveStatus()), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(CommandManager.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> net.minecraft.text.Text.literal(
                                    CullDebug.liveStatus()), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> net.minecraft.text.Text.literal(
                                CullDebug.liveStatus()), false);
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    // ConfigScreen cannot load without YACL, so the guard stays outside it.
                    if (!com.cleannrooster.dungeons_iso.config.Config.GSON.hasScreen()) {
                        ctx.getSource().sendError(net.minecraft.text.Text.translatable(
                                "dungeons_iso.config.requires_yacl"));
                        return 0;
                    }
                    MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(ConfigScreen.create(null))
                    );
                    return Command.SINGLE_SUCCESS;
                })
        );
    }

    private static int startLiveLog(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int intervalMillis) {
        java.nio.file.Path file = CullDebug.startLiveLog(intervalMillis);
        if (file == null) {
            ctx.getSource().sendError(net.minecraft.text.Text.literal(
                    "Cannot start culling debug: no client world/player or log file error"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> net.minecraft.text.Text.literal(CullDebug.liveStatus()), false);
        return Command.SINGLE_SUCCESS;
    }
}
