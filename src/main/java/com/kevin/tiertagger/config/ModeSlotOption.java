package com.kevin.tiertagger.config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.WidgetCreator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
public final class ModeSlotOption implements WidgetCreator {
    private static final int ARROW_ZONE = 14;
    private final String labelKey;
    private final Supplier<String> currentId;
    private final boolean allowNone;
    private final boolean displayNone;
    private final String group;
    private final Consumer<String> setter;
    private final Runnable openDropdown;
    public ModeSlotOption(String labelKey, Supplier<String> currentId, boolean allowNone,
                          Consumer<String> setter, Runnable openDropdown) {
        this(labelKey, currentId, allowNone, allowNone, null, setter, openDropdown);
    }
    public ModeSlotOption(String labelKey, Supplier<String> currentId, boolean allowNone, boolean displayNone,
                          String group, Consumer<String> setter, Runnable openDropdown) {
        this.labelKey = labelKey;
        this.currentId = currentId;
        this.allowNone = allowNone;
        this.displayNone = displayNone;
        this.group = group;
        this.setter = setter;
        this.openDropdown = openDropdown;
    }
    @Override
    public AbstractWidget createWidget(int x, int y, int width, int height) {
        ModeSlotButton button = new ModeSlotButton(x, y, width, height, buildLabel());
        button.setOnSlotClick(zone -> {
            if (zone == Zone.DROPDOWN) {
                openDropdown.run();
                return;
            }
            int dir = zone == Zone.PREV ? -1 : 1;
            setter.accept(step(currentId.get(), allowNone, dir, group));
            button.setMessage(buildLabel());
        });
        return button;
    }
    private Component buildLabel() {
        String modeId = currentId.get();
        Component value;
        if (modeId == null || modeId.isBlank()) {
            value = displayNone
                    ? Component.translatable("tiertagger.config.mode.none").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tiertagger.config.rightMode.same").withStyle(ChatFormatting.GRAY);
        } else {
            value = com.kevin.tiertagger.TierCache.findModeOrUgly(modeId).asStyled(false);
        }
        return Component.translatable(labelKey)
                .append(Component.literal(" "))
                .append(value);
    }
    static String step(String current, boolean allowNone, int dir) {
        return step(current, allowNone, dir, null);
    }
    static String step(String current, boolean allowNone, int dir, String group) {
        List<String> ids = new ArrayList<>();
        if (allowNone) {
            ids.add("");
        }
        for (var mode : com.kevin.tiertagger.TierCache.getGamemodes()) {
            if (mode.isNone()) continue;
            if (group != null && !group.equals(mode.group())) continue;
            if (!ids.contains(mode.id())) {
                ids.add(mode.id());
            }
        }
        if (ids.isEmpty()) {
            return current == null ? "" : current;
        }
        String now = current == null ? "" : current;
        int idx = -1;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equalsIgnoreCase(now)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            idx = 0;
            for (int i = 0; i < ids.size(); i++) {
                if (!ids.get(i).isEmpty()) {
                    idx = i;
                    break;
                }
            }
        }
        return ids.get(Math.floorMod(idx + dir, ids.size()));
    }
    private enum Zone {
        PREV,
        DROPDOWN,
        NEXT
    }
    private static final class ModeSlotButton extends Button {
        private Consumer<Zone> onSlotClick = zone -> {};
        ModeSlotButton(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message, b -> {}, DEFAULT_NARRATION);
        }
        void setOnSlotClick(Consumer<Zone> handler) {
            this.onSlotClick = handler;
        }
        @Override
        public void onPress(InputWithModifiers modifiers) {
            onSlotClick.accept(Zone.DROPDOWN);
        }
        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (!this.active) {
                return;
            }
            double rel = event.x() - getX();
            Zone zone;
            if (rel < ARROW_ZONE) {
                zone = Zone.PREV;
            } else if (rel > getWidth() - ARROW_ZONE) {
                zone = Zone.NEXT;
            } else {
                zone = Zone.DROPDOWN;
            }
            playDownSound(Minecraft.getInstance().getSoundManager());
            onSlotClick.accept(zone);
        }
        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            extractDefaultSprite(graphics);
            var font = Minecraft.getInstance().font;
            int textY = getY() + (getHeight() - 8) / 2;
            int color = this.active ? 0xFFFFFFFF : 0xA0A0A0FF;
            int arrowW = font.width("▼");
            int labelW = font.width(getMessage());
            int total = labelW + 4 + arrowW;
            int startX = getX() + (getWidth() - total) / 2;
            graphics.text(font, getMessage(), startX, textY, color);
            graphics.text(font, "▼", startX + labelW + 4, textY, color);
        }
    }
}
