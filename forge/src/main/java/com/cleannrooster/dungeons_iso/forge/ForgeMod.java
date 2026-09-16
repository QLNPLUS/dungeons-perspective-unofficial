package com.cleannrooster.dungeons_iso.forge;

import com.cleannrooster.dungeons_iso.ClientInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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
}
