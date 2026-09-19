package com.cleannrooster.dungeons_iso.forge;

import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.mojang.brigadier.Command;
import com.cleannrooster.dungeons_iso.api.cullers.room.GhostRenderer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ForgeMod.MOD_ID)
public final class ForgeMod {
    public static final String MOD_ID = "dungeons_iso";
    private static final Logger LOGGER = LogManager.getLogger("Dungeons Perspective");

    public ForgeMod() {
        LOGGER.info("Dungeons Perspective 1.20.1-another Forge entrypoint constructed");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            ClientInit.registerKeyBindings();
            for (var binding : ClientInit.getAllKeyBindings()) {
                event.register(binding);
            }
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(ClientInit::init);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ClientRenderEvents {
        private ClientRenderEvents() {
        }

        @SubscribeEvent
        public static void renderGhostBlocks(RenderLevelStageEvent event) {
            // Draw the translucent shell before entities, so entity models render over it instead
            // of being dimmed or hidden when a ghost face lies between them and the camera.
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                return;
            }

            try {
                GhostRenderer.render(event.getPoseStack(),
                        client.getBufferBuilders().getEntityVertexConsumers(),
                        event.getCamera(), client.gameRenderer);
            } catch (Throwable error) {
                GhostRenderer.reportRenderFailure(error);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ClientCommandEvents {
        private ClientCommandEvents() {
        }

        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    CommandManager.literal("dungeons_iso_debug")
                            .executes(context -> {
                                CullDebug.saveSnapshot();
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }
}
