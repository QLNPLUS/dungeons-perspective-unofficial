package com.cleannrooster.dungeons_iso;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;

public class MixinPlugin  implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("dungeons_iso/mixins");
    /** The old net.caffeinemc Sodium sources remain disabled; Embeddium has its own hooks below. */
    private static final boolean ENABLE_SODIUM_COMPAT = false;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {

        if (!ENABLE_SODIUM_COMPAT && (mixinClassName.contains(".compat.sodium.")
                || mixinClassName.endsWith(".FabricBlockAccessMixin"))) {
            return false;
        }

        if (mixinClassName.contains(".compat.")) {
            String[] parts = mixinClassName.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("compat") && i + 1 < parts.length) {
                    String modId = parts[i + 1];
                    // The Forge mod list is not ready during early mixin selection. Probe the
                    // target resource instead; Class.forName would define it too early.
                    boolean loaded = modId.equals("embeddium")
                            ? ModCompat.isClassResourcePresent(targetClassName)
                            : ModCompat.isModLoaded(modId);
                    if (modId.equals("embeddium")) {
                        LOGGER.debug("Embeddium compatibility mixins {}",
                                loaded ? "enabled" : "disabled: target class resource not found");
                    }
                    return loaded;
                }
            }
            // This means there was a failure in parsing the mod id
            return false;
        }
        return true;
    }


    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
