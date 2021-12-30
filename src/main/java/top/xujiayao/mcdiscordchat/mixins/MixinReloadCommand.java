package top.xujiayao.mcdiscordchat.mixins;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ReloadCommand;
import net.minecraft.server.command.ServerCommandSource;
import top.xujiayao.mcdiscordchat.utils.ConfigManager;

@Mixin(ReloadCommand.class)
public class MixinReloadCommand {

    /* Reloads the config when "/reload" is typed in-game
     * Contributed by FireNH
     */ 

    @Inject(method = "tryReloadDataPacks", at = @At("HEAD"))
    private static void tryReloadDataPacks(Collection<String> dataPacks, ServerCommandSource source, CallbackInfo info) {
        ConfigManager.initConfig();
    }
}