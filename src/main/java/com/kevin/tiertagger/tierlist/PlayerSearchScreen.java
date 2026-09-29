package com.kevin.tiertagger.tierlist;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.util.Toasts;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.uku3lig.ukulib.config.option.widget.TextInputWidget;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
public class PlayerSearchScreen extends CloseableScreen {
    private TextInputWidget textField;
    private Button searchButton;
    private boolean searching = false;
    private CompletableFuture<?> future = null;
    public PlayerSearchScreen(Screen parent) {
        super("Player Search", parent);
    }
    @Override
    protected void init() {
        String username = I18n.get("tiertagger.search.user");
        this.textField = this.addWidget(new TextInputWidget(this.width / 2 - 100, 116, 200, 20,
                "", s -> {
        }, username, s -> s.matches("[a-zA-Z0-9_-]+"), 32));
        this.searchButton = this.addRenderableWidget(
                Button.builder(Component.translatable("tiertagger.search"), button -> this.loadAndShowProfile())
                        .bounds(this.width / 2 - 100, this.height / 4 + 96 + 12, 200, 20)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, button -> {
                            if (this.future != null) {
                                this.future.cancel(true);
                            }
                            this.onClose();
                        })
                        .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20)
                        .build()
        );
        this.focusInput();
    }
    @Override
    public void tick() {
        super.tick();
        this.searchButton.active = this.inputValid() && !searching;
    }
    private void loadAndShowProfile() {
        String username = this.inputText();
        this.searching = true;
        this.searchButton.setMessage(Component.translatable("tiertagger.search.loading"));
        CompletableFuture<PlayerSkinWidget> skinFuture = CompletableFuture.supplyAsync(() -> {
            GameProfile profile = TierCache.lookupProfile(username)
                    .orElseGet(() -> new GameProfile(UUID.randomUUID(), username));
            Supplier<PlayerSkin> skinSupplier = Minecraft.getInstance().getSkinManager().createLookup(profile, true);
            PlayerSkinWidget skin = new PlayerSkinWidget(60, 144, Minecraft.getInstance().getEntityModels(), skinSupplier);
            skin.setPosition(this.width / 2 - 65, (this.height - 144) / 2);
            return skin;
        });
        this.future = TierCache.searchPlayer(username)
                .thenCombine(skinFuture, (info, skin) -> new PlayerInfoScreen(this, info, skin))
                .thenAccept(screen -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(screen)))
                .whenComplete((v, t) -> {
                    if (t != null) {
                        Toasts.send(Component.translatable("tiertagger.search.unknown"), null);
                    }
                    this.searching = false;
                    this.searchButton.setMessage(Component.translatable("tiertagger.search"));
                });
    }
    @Override
    public void resize(int width, int height) {
        String string = this.inputText();
        this.init(width, height);
        this.setInputText(string);
    }
    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 16777215);
        this.textField.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private String inputText() {
        return this.textField.getText();
    }
    private void setInputText(String value) {
        this.textField.setText(value);
    }
    private boolean inputValid() {
        return this.textField.isValid();
    }
    private void focusInput() {
        this.setInitialFocus(this.textField);
    }
}
