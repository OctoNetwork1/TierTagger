package com.kevin.tiertagger.config;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.model.GameMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.function.Consumer;
public class GamemodeSelectScreen extends CloseableScreen {
    private final String currentId;
    private final boolean allowNone;
    private final String group;
    private final Consumer<String> onSelect;
    private final Runnable afterSelect;
    public GamemodeSelectScreen(Screen parent, Component title, String currentId, boolean allowNone,
                                String group, Consumer<String> onSelect, Runnable afterSelect) {
        super(title, parent);
        this.currentId = currentId == null ? "" : currentId;
        this.allowNone = allowNone;
        this.group = group;
        this.onSelect = onSelect;
        this.afterSelect = afterSelect;
    }
    @Override
    protected void init() {
        List<GameMode> modes = TierCache.getGamemodes().stream()
                .filter(m -> !m.isNone())
                .filter(m -> group == null || group.equals(m.group()))
                .toList();
        int buttonWidth = 200;
        int x = this.width / 2 - buttonWidth / 2;
        int y = 32;
        int rowHeight = 22;
        if (allowNone) {
            boolean selected = this.currentId.isBlank();
            this.addRenderableWidget(
                    Button.builder(modeLabel(Component.translatable("tiertagger.config.mode.none"), selected), b -> pick(""))
                            .bounds(x, y, buttonWidth, 20)
                            .build()
            );
            y += rowHeight;
        }
        for (GameMode mode : modes) {
            boolean selected = mode.id().equalsIgnoreCase(this.currentId);
            this.addRenderableWidget(
                    Button.builder(modeLabel(mode.asStyled(false), selected), b -> pick(mode.id()))
                            .bounds(x, y, buttonWidth, 20)
                            .build()
            );
            y += rowHeight;
        }
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                        .bounds(x, this.height - 28, buttonWidth, 20)
                        .build()
        );
    }
    private static Component modeLabel(Component name, boolean selected) {
        Component label = selected
                ? Component.literal("> ").withStyle(ChatFormatting.YELLOW).append(name)
                : name;
        return label;
    }
    private void pick(String modeId) {
        this.onSelect.accept(modeId);
        this.afterSelect.run();
    }
    @Override
    public void extractRenderState(@NotNull net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 16777215);
    }
}
