package com.kevin.tiertagger.mixin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.atomic.AtomicBoolean;
@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
    @Unique
    private static final AtomicBoolean hasCheckedVersion = new AtomicBoolean(false);
    protected MixinTitleScreen(Component title) {
        super(title);
    }
    @Inject(method = "init", at = @At("RETURN"))
    public void showUpdateScreen(CallbackInfo ci) {
        hasCheckedVersion.set(true);
    }
}
